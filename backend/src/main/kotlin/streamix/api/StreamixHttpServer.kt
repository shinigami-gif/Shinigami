package streamix.api

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import streamix.auth.AuthRuntime
import streamix.auth.ExternalIdentityVerifier
import streamix.runtime.StreamixService
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

data class CreateActivityRequest(
    val type: String,
    val text: String? = null,
    val mediaId: Long? = null,
    val mediaTitle: String? = null
)

data class CreateReplyRequest(val text: String)
data class CreateCommentRequest(val mediaId: Long, val content: String, val parentCommentId: String? = null)
data class VoteCommentRequest(val vote: Int?)
data class CreateForumThreadRequest(val title: String, val body: String, val mediaIds: List<Long> = emptyList())
data class CreateForumCommentRequest(val content: String, val parentCommentId: String? = null)

interface CanonicalAnimeRequestResolver {
    suspend fun resolve(anilistId: Long): CanonicalAnimeIdentity?
}

class StreamixHttpServer(
    private val service: StreamixService,
    private val controlApi: ProviderControlApi? = null,
    private val canonicalResolver: CanonicalAnimeRequestResolver? = null,
    private val authRuntime: AuthRuntime? = null,
    private val authVerifier: ExternalIdentityVerifier? = null,
    private val host: String = "0.0.0.0",
    private val port: Int = 8080,
    private val gson: Gson = Gson()
) : AutoCloseable {
    private var server: HttpServer? = null

    fun start() {
        check(server == null) { "HTTP server is already started" }
        val http = HttpServer.create(InetSocketAddress(host, port), 0)
        http.executor = Executors.newCachedThreadPool()

        http.createContext(BackendApiContract.HEALTH) { exchange ->
            respond(exchange, 200, mapOf("status" to "ok"))
        }

        http.createContext(UserApiContract.SESSION) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            when (exchange.requestMethod.uppercase()) {
                "GET" -> {
                    val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
                    val user = auth.auth.currentUser(token)
                        ?: return@createContext unauthorized(exchange)
                    respond(exchange, 200, SessionResponse(user = user))
                }
                "POST" -> {
                    val verifier = authVerifier
                        ?: return@createContext respond(
                            exchange,
                            503,
                            mapOf("error" to "authentication verifier is not configured")
                        )
                    val request = try {
                        gson.fromJson(
                            exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                            AuthSessionRequest::class.java
                        )
                    } catch (_: Throwable) {
                        null
                    } ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid auth request"))

                    try {
                        respond(
                            exchange,
                            200,
                            auth.auth.signIn(
                                provider = request.provider,
                                credential = request.credential,
                                verifier = verifier
                            )
                        )
                    } catch (error: IllegalArgumentException) {
                        respond(exchange, 401, mapOf("error" to (error.message ?: "invalid credentials")))
                    }
                }
                else -> method(exchange, "GET")
            }
        }

        http.createContext(UserApiContract.LOGOUT) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            if (!method(exchange, "POST")) return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            auth.auth.logout(token)
            respond(exchange, 200, mapOf("status" to "logged_out"))
        }

        http.createContext(UserApiContract.ME) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val user = auth.auth.currentUser(token)
                ?: return@createContext unauthorized(exchange)
            runSuspend(exchange) { auth.userService.me(user.id) }
        }

        http.createContext(UserApiContract.UPDATE_ME) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            if (!method(exchange, "PUT")) return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val user = auth.auth.currentUser(token)
                ?: return@createContext unauthorized(exchange)
            val request = try {
                gson.fromJson(
                    exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                    UpdateProfileRequest::class.java
                )
            } catch (_: Throwable) {
                null
            } ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid profile request"))

            if (request.username.trim().isBlank()) {
                return@createContext respond(exchange, 400, mapOf("error" to "username is required"))
            }

            runSuspend(exchange) {
                auth.userService.updateProfile(
                    userId = user.id,
                    username = request.username,
                    displayName = request.displayName,
                    bio = request.bio,
                    avatarUrl = request.avatarUrl,
                    bannerUrl = request.bannerUrl
                )
            }
        }

        http.createContext(UserApiContract.FOLLOW) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val actor = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            val targetId = exchange.requestURI.path.removePrefix("/api/v1/users/").removeSuffix("/follow")
            if (exchange.requestMethod.uppercase() != "POST") return@createContext method(exchange, "POST")
            val enabled = query(exchange, "enabled")?.toBooleanStrictOrNull()
                ?: return@createContext respond(exchange, 400, mapOf("error" to "enabled is required"))
            val user = runCatching { auth.users.setRelationship(actor.id, targetId, following = enabled) }
                .getOrElse { return@createContext respond(exchange, 404, mapOf("error" to (it.message ?: "user not found"))) }
            respond(exchange, 200, user)
        }

        http.createContext(UserApiContract.BLOCK) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val actor = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            val targetId = exchange.requestURI.path.removePrefix("/api/v1/users/").removeSuffix("/block")
            if (exchange.requestMethod.uppercase() != "POST") return@createContext method(exchange, "POST")
            val enabled = query(exchange, "enabled")?.toBooleanStrictOrNull()
                ?: return@createContext respond(exchange, 400, mapOf("error" to "enabled is required"))
            val user = runCatching { auth.users.setRelationship(actor.id, targetId, blocked = enabled) }
                .getOrElse { return@createContext respond(exchange, 404, mapOf("error" to (it.message ?: "user not found"))) }
            respond(exchange, 200, user)
        }

        http.createContext("/api/v1/users/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            val path = exchange.requestURI.path
            if (path.endsWith("/followers") || path.endsWith("/following")) {
                val targetId = path.removePrefix("/api/v1/users/").removeSuffix("/followers").removeSuffix("/following")
                val target = runCatching { auth.users.findById(targetId) }.getOrNull()
                    ?: return@createContext respond(exchange, 404, mapOf("error" to "user not found"))
                val all = auth.users.search("", 1, 100000)
                val following = path.endsWith("/following")
                val users = all.filter { candidate ->
                    runCatching {
                        if (following) auth.users.relationship(target.id, candidate.id).following
                        else auth.users.relationship(candidate.id, target.id).following
                    }.getOrDefault(false)
                }
                return@createContext respond(exchange, 200, UserPage(users, 1, users.size, false, users.size.toLong()))
            }
            if (path.endsWith("/follow") || path.endsWith("/block")) return@createContext
            val targetId = path.removePrefix("/api/v1/users/").trim('/')
                .takeIf { it.isNotBlank() }
                ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid user id"))
            val profile = runCatching { auth.userService.profile(targetId, viewer.id) }.getOrElse {
                return@createContext respond(exchange, 404, mapOf("error" to "user not found"))
            }
            respond(exchange, 200, profile)
        }

        http.createContext(UserApiContract.SEARCH) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            auth.auth.currentUser(token)
                ?: return@createContext unauthorized(exchange)
            val query = query(exchange, "q").orEmpty()
            val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            runSuspend(exchange) { auth.userService.search(query, page, perPage) }
        }

        http.createContext("/api/v1/users/me/library/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val user = auth.auth.currentUser(token)
                ?: return@createContext unauthorized(exchange)
            val mediaId = exchange.requestURI.path
                .removePrefix("/api/v1/users/me/library/")
                .toLongOrNull()
                ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid mediaId"))

            when (exchange.requestMethod.uppercase()) {
                "GET" -> {
                    val state = auth.libraryService.find(user.id, mediaId)
                        ?: return@createContext respond(exchange, 404, mapOf("error" to "library item not found"))
                    respond(exchange, 200, state)
                }
                "DELETE" -> {
                    auth.libraryService.delete(user.id, mediaId)
                    respond(exchange, 200, mapOf("status" to "deleted"))
                }
                else -> method(exchange, "GET")
            }
        }

        http.createContext(UserLibraryApiContract.LISTS) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val user = auth.auth.currentUser(token)
                ?: return@createContext unauthorized(exchange)
            val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 50
            runSuspend(exchange) { auth.libraryService.list(user.id, page, perPage) }
        }

        http.createContext(SocialApiContract.FEED) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            if (!method(exchange, "GET")) return@createContext
            val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            respond(exchange, 200, auth.socialService.feed(viewer.id, page, perPage))
        }

        http.createContext(SocialApiContract.ACTIVITIES) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            val suffix = exchange.requestURI.path.removePrefix(SocialApiContract.ACTIVITIES).trim('/')
            if (suffix.isBlank()) {
                when (exchange.requestMethod.uppercase()) {
                    "GET" -> {
                        val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                        respond(exchange, 200, auth.socialService.activities(viewer.id, page, perPage))
                    }
                    "POST" -> {
                        val body = runCatching {
                            gson.fromJson(
                                exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                                CreateActivityRequest::class.java
                            )
                        }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid request"))
                        if (body.type.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "type is required"))
                        respond(exchange, 201, auth.socialService.createActivity(viewer.id, body.type.trim(), body.text, body.mediaId, body.mediaTitle))
                    }
                    else -> method(exchange, "GET")
                }
                return@createContext
            }

            val parts = suffix.split("/")
            val activityId = parts.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid activity id"))
            when (parts.drop(1).joinToString("/")) {
                "" -> when (exchange.requestMethod.uppercase()) {
                    "GET" -> {
                        val activity = auth.socialService.activity(activityId, viewer.id)
                            ?: return@createContext respond(exchange, 404, mapOf("error" to "activity not found"))
                        respond(exchange, 200, activity)
                    }
                    "DELETE" -> {
                        if (!auth.socialService.deleteActivity(activityId, viewer.id))
                            return@createContext respond(exchange, 404, mapOf("error" to "activity not found"))
                        respond(exchange, 200, mapOf("status" to "deleted"))
                    }
                    else -> method(exchange, "GET")
                }
                "replies" -> when (exchange.requestMethod.uppercase()) {
                    "GET" -> {
                        val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                        respond(exchange, 200, auth.socialService.replies(activityId, viewer.id, page, perPage))
                    }
                    "POST" -> {
                        val body = runCatching {
                            gson.fromJson(
                                exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                                CreateReplyRequest::class.java
                            )
                        }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid reply request"))
                        if (body.text.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "text is required"))
                        respond(exchange, 201, auth.socialService.createReply(activityId, viewer.id, body.text.trim()))
                    }
                    else -> method(exchange, "GET")
                }
                "like" -> if (exchange.requestMethod.equals("POST", true)) {
                    val result = auth.socialService.likeActivity(activityId, viewer.id)
                        ?: return@createContext respond(exchange, 404, mapOf("error" to "activity not found"))
                    respond(exchange, 200, result)
                } else method(exchange, "POST")
                "subscribe" -> if (exchange.requestMethod.equals("POST", true)) {
                    val result = auth.socialService.subscribeActivity(activityId, viewer.id)
                        ?: return@createContext respond(exchange, 404, mapOf("error" to "activity not found"))
                    respond(exchange, 200, result)
                } else method(exchange, "POST")
                else -> respond(exchange, 404, mapOf("error" to "route not found"))
            }
        }

        http.createContext("/api/v1/social/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            val path = exchange.requestURI.path
            val base = "/api/v1/social/"
            val relative = path.removePrefix(base).trim('/')

            fun bodyText(): String = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            when {
                relative.startsWith("activities/") -> {
                    val parts = relative.split("/")
                    val activityId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                        ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid activity id"))
                    when (parts.drop(2).joinToString("/")) {
                        "" -> when (exchange.requestMethod.uppercase()) {
                            "GET" -> {
                                val activity = auth.socialService.activity(activityId, viewer.id)
                                    ?: return@createContext respond(exchange, 404, mapOf("error" to "activity not found"))
                                respond(exchange, 200, activity)
                            }
                            "DELETE" -> {
                                if (!auth.socialService.deleteActivity(activityId, viewer.id))
                                    return@createContext respond(exchange, 404, mapOf("error" to "activity not found"))
                                respond(exchange, 200, mapOf("status" to "deleted"))
                            }
                            else -> method(exchange, "GET")
                        }
                        "replies" -> when (exchange.requestMethod.uppercase()) {
                            "GET" -> {
                                val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                                respond(exchange, 200, auth.socialService.replies(activityId, viewer.id, page, perPage))
                            }
                            "POST" -> {
                                val request = runCatching { gson.fromJson(bodyText(), CreateReplyRequest::class.java) }.getOrNull()
                                    ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid reply request"))
                                if (request.text.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "text is required"))
                                respond(exchange, 201, auth.socialService.createReply(activityId, viewer.id, request.text.trim()))
                            }
                            else -> method(exchange, "GET")
                        }
                        "like" -> if (exchange.requestMethod.equals("POST", true)) {
                            val result = auth.socialService.likeActivity(activityId, viewer.id)
                                ?: return@createContext respond(exchange, 404, mapOf("error" to "activity not found"))
                            respond(exchange, 200, result)
                        } else method(exchange, "POST")
                        "subscribe" -> if (exchange.requestMethod.equals("POST", true)) {
                            val result = auth.socialService.subscribeActivity(activityId, viewer.id)
                                ?: return@createContext respond(exchange, 404, mapOf("error" to "activity not found"))
                            respond(exchange, 200, result)
                        } else method(exchange, "POST")
                        else -> respond(exchange, 404, mapOf("error" to "route not found"))
                    }
                }

                relative == "comments" -> when (exchange.requestMethod.uppercase()) {
                    "GET" -> {
                        val mediaId = query(exchange, "mediaId")?.toLongOrNull()
                            ?: return@createContext respond(exchange, 400, mapOf("error" to "mediaId is required"))
                        val parent = query(exchange, "parentCommentId")
                        val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                        respond(exchange, 200, auth.socialService.comments(mediaId, viewer.id, parent, page, perPage))
                    }
                    "POST" -> {
                        val request = runCatching { gson.fromJson(bodyText(), CreateCommentRequest::class.java) }.getOrNull()
                            ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid comment request"))
                        if (request.mediaId <= 0 || request.content.isBlank())
                            return@createContext respond(exchange, 400, mapOf("error" to "mediaId and content are required"))
                        respond(exchange, 201, auth.socialService.createComment(request.mediaId, viewer.id, request.content.trim(), request.parentCommentId))
                    }
                    else -> method(exchange, "GET")
                }

                relative.startsWith("comments/") -> {
                    val parts = relative.split("/")
                    val commentId = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                        ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid comment id"))
                    when (parts.drop(2).joinToString("/")) {
                        "" -> if (exchange.requestMethod.equals("DELETE", true)) {
                            if (!auth.socialService.deleteComment(commentId, viewer.id))
                                return@createContext respond(exchange, 404, mapOf("error" to "comment not found"))
                            respond(exchange, 200, mapOf("status" to "deleted"))
                        } else method(exchange, "DELETE")
                        "vote" -> if (exchange.requestMethod.equals("POST", true)) {
                            val request = runCatching { gson.fromJson(bodyText(), VoteCommentRequest::class.java) }.getOrNull()
                                ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid vote request"))
                            if (request.vote != null && request.vote !in -1..1)
                                return@createContext respond(exchange, 400, mapOf("error" to "vote must be -1, 0, or 1"))
                            val result = auth.socialService.voteComment(commentId, viewer.id, request.vote)
                                ?: return@createContext respond(exchange, 404, mapOf("error" to "comment not found"))
                            respond(exchange, 200, result)
                        } else method(exchange, "POST")
                        "replies" -> if (exchange.requestMethod.equals("GET", true)) {
                            val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                            respond(exchange, 200, auth.socialService.commentReplies(commentId, viewer.id, page, perPage))
                        } else method(exchange, "GET")
                        else -> respond(exchange, 404, mapOf("error" to "route not found"))
                    }
                }

                relative == "forum/threads" -> when (exchange.requestMethod.uppercase()) {
                    "GET" -> {
                        val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                        respond(exchange, 200, auth.socialService.forumThreads(query(exchange, "q"), viewer.id, page, perPage))
                    }
                    "POST" -> {
                        val request = runCatching { gson.fromJson(bodyText(), CreateForumThreadRequest::class.java) }.getOrNull()
                            ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid forum thread request"))
                        if (request.title.isBlank() || request.body.isBlank())
                            return@createContext respond(exchange, 400, mapOf("error" to "title and body are required"))
                        respond(exchange, 201, auth.socialService.createForumThread(viewer.id, request.title.trim(), request.body.trim(), request.mediaIds))
                    }
                    else -> method(exchange, "GET")
                }

                relative.startsWith("forum/threads/") -> {
                    val parts = relative.split("/")
                    val threadId = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                        ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid thread id"))
                    when (parts.drop(3).joinToString("/")) {
                        "" -> when (exchange.requestMethod.uppercase()) {
                            "GET" -> {
                                val thread = auth.socialService.forumThread(threadId, viewer.id)
                                    ?: return@createContext respond(exchange, 404, mapOf("error" to "thread not found"))
                                respond(exchange, 200, thread)
                            }
                            "DELETE" -> {
                                if (!auth.socialService.deleteForumThread(threadId, viewer.id))
                                    return@createContext respond(exchange, 404, mapOf("error" to "thread not found"))
                                respond(exchange, 200, mapOf("status" to "deleted"))
                            }
                            else -> method(exchange, "GET")
                        }
                        "comments" -> when (exchange.requestMethod.uppercase()) {
                            "GET" -> {
                                val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                                respond(exchange, 200, auth.socialService.forumComments(threadId, viewer.id, page, perPage))
                            }
                            "POST" -> {
                                val request = runCatching { gson.fromJson(bodyText(), CreateForumCommentRequest::class.java) }.getOrNull()
                                    ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid forum comment request"))
                                if (request.content.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "content is required"))
                                respond(exchange, 201, auth.socialService.createForumComment(threadId, viewer.id, request.content.trim(), request.parentCommentId))
                            }
                            else -> method(exchange, "GET")
                        }
                        "like" -> if (exchange.requestMethod.equals("POST", true)) {
                            val result = auth.socialService.likeThread(threadId, viewer.id)
                                ?: return@createContext respond(exchange, 404, mapOf("error" to "thread not found"))
                            respond(exchange, 200, result)
                        } else method(exchange, "POST")
                        "subscribe" -> if (exchange.requestMethod.equals("POST", true)) {
                            val result = auth.socialService.subscribeThread(threadId, viewer.id)
                                ?: return@createContext respond(exchange, 404, mapOf("error" to "thread not found"))
                            respond(exchange, 200, result)
                        } else method(exchange, "POST")
                        else -> respond(exchange, 404, mapOf("error" to "route not found"))
                    }
                }

                relative == "notifications" -> if (exchange.requestMethod.equals("GET", true)) {
                    val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
                    respond(exchange, 200, auth.socialService.notifications(viewer.id, page, perPage))
                } else method(exchange, "GET")

                relative == "notifications/unread-count" -> if (exchange.requestMethod.equals("GET", true)) {
                    respond(exchange, 200, mapOf("count" to auth.socialService.unreadNotificationCount(viewer.id)))
                } else method(exchange, "GET")

                relative.startsWith("notifications/") && relative.endsWith("/read") -> if (exchange.requestMethod.equals("POST", true)) {
                    val notificationId = relative.removePrefix("notifications/").removeSuffix("/read").trim('/')
                    if (notificationId.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "invalid notification id"))
                    if (!auth.socialService.markNotificationRead(notificationId, viewer.id))
                        return@createContext respond(exchange, 404, mapOf("error" to "notification not found"))
                    respond(exchange, 200, mapOf("status" to "read"))
                } else method(exchange, "POST")

                else -> respond(exchange, 404, mapOf("error" to "route not found"))
            }
        }


        http.createContext(ChatApiContract.GLOBAL_MESSAGES) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            when (exchange.requestMethod.uppercase()) {
                "GET" -> {
                    val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                    respond(exchange, 200, auth.chatService.global(page, perPage))
                }
                "POST" -> {
                    val request = runCatching {
                        gson.fromJson(
                            exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                            SendChatMessageRequest::class.java
                        )
                    }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid chat request"))
                    respond(exchange, 201, auth.chatService.sendGlobal(viewer.id, request.content))
                }
                else -> method(exchange, "GET")
            }
        }

        http.createContext("/api/v1/chat/anime/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            val parts = exchange.requestURI.path.removePrefix("/api/v1/chat/anime/").trim('/').split("/")
            val mediaId = parts.firstOrNull()?.toLongOrNull()
                ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid mediaId"))
            if (parts.size != 2 || parts[1] != "messages") {
                return@createContext respond(exchange, 404, mapOf("error" to "route not found"))
            }
            when (exchange.requestMethod.uppercase()) {
                "GET" -> {
                    val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                    respond(exchange, 200, auth.chatService.anime(mediaId, page, perPage))
                }
                "POST" -> {
                    val request = runCatching {
                        gson.fromJson(
                            exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                            SendChatMessageRequest::class.java
                        )
                    }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid chat request"))
                    respond(exchange, 201, auth.chatService.sendAnime(viewer.id, mediaId, request.content))
                }
                else -> method(exchange, "GET")
            }
        }

        http.createContext(ChatApiContract.MESSAGES) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            if (!method(exchange, "GET")) return@createContext
            val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 30
            respond(exchange, 200, auth.chatService.conversations(viewer.id, page, perPage))
        }

        http.createContext(ChatApiContract.MESSAGE_UNREAD_COUNT) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            if (!method(exchange, "GET")) return@createContext
            respond(exchange, 200, mapOf("count" to auth.chatService.unreadCount(viewer.id)))
        }

        http.createContext("/api/v1/messages/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            val suffix = exchange.requestURI.path.removePrefix("/api/v1/messages/").trim('/')
            val parts = suffix.split("/")
            val otherUserId = parts.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid user id"))
            if (parts.size == 1) {
                when (exchange.requestMethod.uppercase()) {
                    "GET" -> {
                        val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                        respond(exchange, 200, auth.chatService.messages(viewer.id, otherUserId, page, perPage))
                    }
                    "POST" -> {
                        val request = runCatching {
                            gson.fromJson(
                                exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                                SendMessageRequest::class.java
                            )
                        }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid message request"))
                        respond(exchange, 201, auth.chatService.sendMessage(viewer.id, otherUserId, request.content))
                    }
                    else -> method(exchange, "GET")
                }
            } else if (parts.size == 2 && parts[1] == "read") {
                if (!method(exchange, "POST")) return@createContext
                respond(exchange, 200, mapOf("marked" to auth.chatService.markRead(viewer.id, otherUserId)))
            } else {
                respond(exchange, 404, mapOf("error" to "route not found"))
            }
        }

        http.createContext(BackendApiContract.SEARCH) { exchange ->
            if (!method(exchange, "GET")) return@createContext
            val query = query(exchange, "q")?.trim().orEmpty()
            if (query.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "q is required"))
            val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            runSuspend(exchange) { service.search(query, page) }
        }

        http.createContext(BackendApiContract.PROVIDERS) { exchange ->
            if (!method(exchange, "GET")) return@createContext
            respond(exchange, 200, controlApi?.status() ?: emptyList<Any>())
        }

        http.createContext(BackendApiContract.PROVIDER_STATUS) { exchange ->
            if (!method(exchange, "GET")) return@createContext
            respond(exchange, 200, controlApi?.status() ?: emptyList<Any>())
        }

        http.createContext("/api/v1/anime/") { exchange ->
            if (!method(exchange, "GET")) return@createContext
            val parts = exchange.requestURI.path.removePrefix("/api/v1/anime/").split("/")
            when {
                parts.size == 1 && parts[0].toLongOrNull() != null -> {
                    runSuspend(exchange) {
                        val identity = canonicalResolver?.resolve(parts[0].toLong())
                            ?: return@runSuspend respond(exchange, 501, mapOf("error" to "canonical anime resolver is not configured"))
                        service.detail(identity)
                    }
                }
                parts.size == 2 && parts[1] == "episodes" && parts[0].toLongOrNull() != null -> {
                    runSuspend(exchange) {
                        val identity = canonicalResolver?.resolve(parts[0].toLong())
                            ?: return@runSuspend respond(exchange, 501, mapOf("error" to "canonical anime resolver is not configured"))
                        service.episodes(identity)
                    }
                }
                parts.size == 4 && parts[1] == "episode" && parts[3] == "streams" && parts[0].toLongOrNull() != null -> {
                    val providerId = query(exchange, "providerId").orEmpty()
                    val providerEpisodeId = query(exchange, "episodeId").orEmpty()
                    val episodeUrl = query(exchange, "url").orEmpty()
                    val number = parts[2].toIntOrNull()
                    if (providerId.isBlank() || providerEpisodeId.isBlank() || episodeUrl.isBlank() || number == null) {
                        return@createContext respond(exchange, 400, mapOf("error" to "providerId, episodeId and url are required"))
                    }
                    runSuspend(exchange) {
                        service.streams(EpisodeRef(providerId, number, providerEpisodeId = providerEpisodeId, url = episodeUrl))
                    }
                }
                else -> respond(exchange, 404, mapOf("error" to "route not found"))
            }
        }

        http.start()
        server = http
    }

    override fun close() {
        server?.stop(1)
        server = null
    }

    private fun requireAuthRuntime(exchange: HttpExchange): AuthRuntime? {
        val auth = authRuntime
        if (auth != null) return auth
        respond(exchange, 503, mapOf("error" to "authentication runtime is not configured"))
        return null
    }

    private fun bearerToken(exchange: HttpExchange): String? {
        val value = exchange.requestHeaders.getFirst("Authorization") ?: return null
        if (!value.startsWith("Bearer ", ignoreCase = true)) return null
        return value.substring(7).trim().takeIf { it.isNotEmpty() }
    }

    private fun unauthorized(exchange: HttpExchange) {
        exchange.responseHeaders.set("WWW-Authenticate", "Bearer")
        respond(exchange, 401, mapOf("error" to "authentication required"))
    }

    private fun method(exchange: HttpExchange, expected: String): Boolean {
        if (exchange.requestMethod.equals(expected, ignoreCase = true)) return true
        respond(exchange, 405, mapOf("error" to "method not allowed"))
        return false
    }

    private fun query(exchange: HttpExchange, name: String): String? =
        exchange.requestURI.rawQuery?.split("&")?.mapNotNull { part ->
            val pair = part.split("=", limit = 2)
            if (pair.firstOrNull() == name) URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            else null
        }?.firstOrNull()

    private fun respond(exchange: HttpExchange, status: Int, body: Any) {
        val bytes = gson.toJson(body).toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun runSuspend(exchange: HttpExchange, block: suspend () -> Any) {
        try {
            kotlinx.coroutines.runBlocking { respond(exchange, 200, block()) }
        } catch (error: Throwable) {
            respond(exchange, 500, mapOf("error" to (error.message ?: error::class.simpleName)))
        }
    }
}
