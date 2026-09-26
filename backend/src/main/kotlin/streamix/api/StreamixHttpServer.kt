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
data class UpdateCommentRequest(val content: String)
data class VoteCommentRequest(val vote: Int?)
data class CreateSocialReportRequest(
    val targetUserId: String? = null,
    val targetContentId: String? = null,
    val type: ReportType,
    val description: String
)
data class AdminUserActionRequest(
    val action: String,
    val reason: String? = null,
    val suspendedUntil: String? = null
)
data class AdminRoleRequest(val role: AdminRole)
data class AdminReportUpdateRequest(val status: ReportStatus, val assignedTo: String? = null)
data class CreateAnnouncementRequest(
    val title: String,
    val body: String,
    val imageUrl: String? = null
)

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

        http.createContext(SocialApiContract.REPORTS) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val reporter = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            if (!method(exchange, "POST")) return@createContext

            val request = runCatching {
                gson.fromJson(exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }, CreateSocialReportRequest::class.java)
            }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid report request"))

            if (request.targetUserId.isNullOrBlank() && request.targetContentId.isNullOrBlank()) {
                return@createContext respond(exchange, 400, mapOf("error" to "targetUserId or targetContentId is required"))
            }
            if (request.description.isBlank()) {
                return@createContext respond(exchange, 400, mapOf("error" to "description is required"))
            }

            if (request.targetUserId != null && auth.users.findById(request.targetUserId) == null) {
                return@createContext respond(exchange, 404, mapOf("error" to "target user not found"))
            }

            val report = AdminReport(
                id = java.util.UUID.randomUUID().toString(),
                reporterId = reporter.id,
                targetUserId = request.targetUserId?.trim(),
                targetContentId = request.targetContentId?.trim(),
                type = request.type,
                description = request.description.trim(),
                status = ReportStatus.PENDING,
                createdAt = java.time.Instant.now().toString()
            )
            respond(exchange, 201, auth.reports.create(report))
        }

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
            if (!method(exchange, "POST")) return@createContext
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

            val parts = exchange.requestURI.path
                .removePrefix("/api/v1/users/me/library/")
                .trim('/')
                .split("/")
            val mediaId = parts.firstOrNull()?.toLongOrNull()
                ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid mediaId"))

            fun findExisting(): UserAnimeState? =
                auth.libraryService.find(user.id, mediaId)

            fun body(): String =
                exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

            when {
                parts.size == 1 -> when (exchange.requestMethod.uppercase()) {
                    "GET" -> {
                        val state = findExisting()
                            ?: return@createContext respond(exchange, 404, mapOf("error" to "library item not found"))
                        respond(exchange, 200, state)
                    }
                    "PUT" -> {
                        val request = runCatching {
                            gson.fromJson(body(), UpsertLibraryRequest::class.java)
                        }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid library request"))
                        val state = UserAnimeState(
                            mediaId = mediaId,
                            status = request.status,
                            progress = request.progress,
                            score = request.score,
                            isFavorite = request.isFavorite,
                            notes = request.notes
                        )
                        runCatching {
                            auth.libraryService.upsert(user.id, state)
                        }.getOrElse {
                            return@createContext respond(exchange, 400, mapOf("error" to (it.message ?: "invalid library state")))
                        }.let { respond(exchange, 200, it) }
                    }
                    "DELETE" -> {
                        auth.libraryService.delete(user.id, mediaId)
                        respond(exchange, 200, mapOf("status" to "deleted"))
                    }
                    else -> method(exchange, "GET")
                }
                parts.size == 2 && parts[1] == "status" && exchange.requestMethod.equals("PUT", true) -> {
                    val request = runCatching { gson.fromJson(body(), LibraryStatusRequest::class.java) }.getOrNull()
                        ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid status request"))
                    val current = findExisting() ?: UserAnimeState(mediaId = mediaId)
                    respond(exchange, 200, auth.libraryService.upsert(user.id, current.copy(status = request.status, updatedAt = null)))
                }
                parts.size == 2 && parts[1] == "progress" && exchange.requestMethod.equals("PUT", true) -> {
                    val request = runCatching { gson.fromJson(body(), LibraryProgressRequest::class.java) }.getOrNull()
                        ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid progress request"))
                    if (request.progress < 0) return@createContext respond(exchange, 400, mapOf("error" to "progress must be non-negative"))
                    val current = findExisting() ?: UserAnimeState(mediaId = mediaId)
                    respond(exchange, 200, auth.libraryService.upsert(user.id, current.copy(progress = request.progress, updatedAt = null)))
                }
                parts.size == 2 && parts[1] == "score" && exchange.requestMethod.equals("PUT", true) -> {
                    val request = runCatching { gson.fromJson(body(), LibraryScoreRequest::class.java) }.getOrNull()
                        ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid score request"))
                    if (request.score != null && (request.score.isNaN() || request.score.isInfinite() || request.score < 0.0 || request.score > 100.0))
                        return@createContext respond(exchange, 400, mapOf("error" to "score must be between 0 and 100"))
                    val current = findExisting() ?: UserAnimeState(mediaId = mediaId)
                    respond(exchange, 200, auth.libraryService.upsert(user.id, current.copy(score = request.score, updatedAt = null)))
                }
                else -> respond(exchange, 404, mapOf("error" to "route not found"))
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
                        "" -> when {
                            exchange.requestMethod.equals("DELETE", true) -> {
                                if (!auth.socialService.deleteComment(commentId, viewer.id))
                                    return@createContext respond(exchange, 404, mapOf("error" to "comment not found"))
                                respond(exchange, 200, mapOf("status" to "deleted"))
                            }
                            exchange.requestMethod.equals("PUT", true) -> {
                                val request = runCatching { gson.fromJson(bodyText(), UpdateCommentRequest::class.java) }.getOrNull()
                                    ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid comment request"))
                                if (request.content.isBlank())
                                    return@createContext respond(exchange, 400, mapOf("error" to "content is required"))
                                val result = auth.socialService.editComment(commentId, viewer.id, request.content)
                                    ?: return@createContext respond(exchange, 404, mapOf("error" to "comment not found"))
                                respond(exchange, 200, result)
                            }
                            else -> respond(exchange, 405, mapOf("error" to "method not allowed"))
                        }
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
                    respond(exchange, 200, auth.chatService.global(viewer.id, page, perPage))
                }
                "POST" -> {
                    val request = runCatching {
                        gson.fromJson(
                            exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                            SendChatMessageRequest::class.java
                        )
                    }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid chat request"))
                    try {
                        respond(exchange, 201, auth.chatService.sendGlobal(viewer.id, request.content))
                    } catch (error: IllegalArgumentException) {
                        respond(exchange, chatErrorStatus(error), mapOf("error" to (error.message ?: "invalid chat message")))
                    }
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
                    respond(exchange, 200, auth.chatService.anime(viewer.id, mediaId, page, perPage))
                }
                "POST" -> {
                    val request = runCatching {
                        gson.fromJson(
                            exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() },
                            SendChatMessageRequest::class.java
                        )
                    }.getOrNull() ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid chat request"))
                    try {
                        respond(exchange, 201, auth.chatService.sendAnime(viewer.id, mediaId, request.content))
                    } catch (error: IllegalArgumentException) {
                        respond(exchange, chatErrorStatus(error), mapOf("error" to (error.message ?: "invalid chat message")))
                    }
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
            if (auth.users.findById(otherUserId) == null) {
                return@createContext respond(exchange, 404, mapOf("error" to "user not found"))
            }
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
                        try {
                            respond(exchange, 201, auth.chatService.sendMessage(viewer.id, otherUserId, request.content))
                        } catch (error: IllegalArgumentException) {
                            respond(exchange, chatErrorStatus(error), mapOf("error" to (error.message ?: "invalid message")))
                        }
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

        http.createContext(AdminApiContract.DASHBOARD) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val actor = authenticatedAdminActor(exchange, auth) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            try {
                respond(exchange, 200, auth.adminService.dashboard(actor.id))
            } catch (error: SecurityException) {
                respond(exchange, 403, mapOf("error" to (error.message ?: "permission denied")))
            }
        }

        http.createContext(AdminApiContract.USERS) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val actor = authenticatedAdminActor(exchange, auth) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            try {
                val q = query(exchange, "q")?.trim().orEmpty()
                val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                respond(exchange, 200, auth.adminService.users(actor.id, q, page, perPage))
            } catch (error: SecurityException) {
                respond(exchange, 403, mapOf("error" to (error.message ?: "permission denied")))
            }
        }

        http.createContext("/api/v1/admin/users/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val actor = authenticatedAdminActor(exchange, auth) ?: return@createContext
            val relative = exchange.requestURI.path.removePrefix("/api/v1/admin/users/").trim('/')
            val parts = relative.split("/")
            val targetId = parts.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return@createContext respond(exchange, 400, mapOf("error" to "user id is required"))
            try {
                when {
                    parts.size == 1 && exchange.requestMethod.equals("GET", true) ->
                        respond(exchange, 200, auth.adminService.user(actor.id, targetId))
                    parts.size == 2 && parts[1] == "role" && exchange.requestMethod.equals("PUT", true) -> {
                        val request = gson.fromJson(readBody(exchange), AdminRoleRequest::class.java)
                        respond(exchange, 200, auth.adminService.setRole(actor.id, targetId, request.role))
                    }
                    parts.size == 2 && parts[1] == "actions" && exchange.requestMethod.equals("POST", true) -> {
                        val request = gson.fromJson(readBody(exchange), AdminUserActionRequest::class.java)
                        val result = when (request.action.lowercase()) {
                            "warn" -> auth.adminService.warn(actor.id, targetId, request.reason)
                            "activate" -> auth.adminService.setStatus(actor.id, targetId, ModerationStatus.ACTIVE, request.reason)
                            "suspend" -> auth.adminService.setStatus(actor.id, targetId, ModerationStatus.SUSPENDED, request.reason, request.suspendedUntil)
                            "ban" -> auth.adminService.setStatus(actor.id, targetId, ModerationStatus.BANNED, request.reason)
                            else -> return@createContext respond(exchange, 400, mapOf("error" to "unsupported user action"))
                        }
                        respond(exchange, 200, result)
                    }
                    else -> respond(exchange, 404, mapOf("error" to "route not found"))
                }
            } catch (error: SecurityException) {
                respond(exchange, 403, mapOf("error" to (error.message ?: "permission denied")))
            } catch (error: IllegalArgumentException) {
                respond(exchange, 400, mapOf("error" to (error.message ?: "invalid admin request")))
            } catch (error: IllegalStateException) {
                respond(exchange, 404, mapOf("error" to (error.message ?: "target not found")))
            }
        }

        http.createContext(AdminApiContract.REPORTS) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val actor = authenticatedAdminActor(exchange, auth) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            try {
                val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                respond(exchange, 200, auth.adminService.reports(actor.id, page, perPage))
            } catch (error: SecurityException) {
                respond(exchange, 403, mapOf("error" to (error.message ?: "permission denied")))
            }
        }

        http.createContext("/api/v1/admin/reports/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val actor = authenticatedAdminActor(exchange, auth) ?: return@createContext
            if (!method(exchange, "PUT")) return@createContext
            val reportId = exchange.requestURI.path.removePrefix("/api/v1/admin/reports/").trim('/')
            if (reportId.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "report id is required"))
            try {
                val current = auth.reports.find(reportId)
                    ?: return@createContext respond(exchange, 404, mapOf("error" to "report not found"))
                val request = gson.fromJson(readBody(exchange), AdminReportUpdateRequest::class.java)
                respond(exchange, 200, auth.adminService.updateReport(
                    actor.id,
                    current.copy(status = request.status, assignedTo = request.assignedTo ?: current.assignedTo)
                ))
            } catch (error: SecurityException) {
                respond(exchange, 403, mapOf("error" to (error.message ?: "permission denied")))
            }
        }

        http.createContext(AdminApiContract.ANNOUNCEMENTS) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val actor = authenticatedAdminActor(exchange, auth) ?: return@createContext
            try {
                when (exchange.requestMethod.uppercase()) {
                    "GET" -> {
                        val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 30
                        respond(exchange, 200, auth.announcementService.list(actor.id, page, perPage))
                    }
                    "POST" -> {
                        val request = gson.fromJson(readBody(exchange), CreateAnnouncementRequest::class.java)
                        respond(exchange, 201, auth.announcementService.create(actor.id, request.title, request.body, request.imageUrl))
                    }
                    else -> method(exchange, "GET")
                }
            } catch (error: SecurityException) {
                respond(exchange, 403, mapOf("error" to (error.message ?: "permission denied")))
            }
        }

        http.createContext("/api/v1/admin/announcements/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val actor = authenticatedAdminActor(exchange, auth) ?: return@createContext
            val parts = exchange.requestURI.path.removePrefix("/api/v1/admin/announcements/").trim('/').split("/")
            val id = parts.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: return@createContext respond(exchange, 400, mapOf("error" to "announcement id is required"))
            try {
                when {
                    parts.size == 2 && parts[1] == "publish" && exchange.requestMethod.equals("POST", true) ->
                        respond(exchange, 200, auth.announcementService.publish(actor.id, id))
                    parts.size == 1 && exchange.requestMethod.equals("DELETE", true) ->
                        respond(exchange, 200, mapOf("deleted" to auth.announcementService.delete(actor.id, id)))
                    else -> respond(exchange, 404, mapOf("error" to "route not found"))
                }
            } catch (error: SecurityException) {
                respond(exchange, 403, mapOf("error" to (error.message ?: "permission denied")))
            } catch (error: IllegalStateException) {
                respond(exchange, 404, mapOf("error" to (error.message ?: "announcement not found")))
            }
        }

        http.createContext(AdminApiContract.AUDIT_LOG) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val actor = authenticatedAdminActor(exchange, auth) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            try {
                val limit = query(exchange, "limit")?.toIntOrNull()?.coerceIn(1, 500) ?: 100
                respond(exchange, 200, auth.adminService.auditLog(actor.id, limit))
            } catch (error: SecurityException) {
                respond(exchange, 403, mapOf("error" to (error.message ?: "permission denied")))
            }
        }

        http.createContext(BackendApiContract.PROVIDERS) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            respond(exchange, 200, controlApi?.status() ?: emptyList<Any>())
        }

        http.createContext(BackendApiContract.PROVIDER_STATUS) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            respond(exchange, 200, controlApi?.status() ?: emptyList<Any>())
        }

        http.createContext(BackendApiContract.PROVIDER_UPDATES) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            respond(exchange, 200, controlApi?.updates() ?: emptyList<Any>())
        }

        http.createContext(BackendApiContract.PROVIDER_UPDATE_CHECK) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "POST")) return@createContext
            val providerId = exchange.requestURI.path
                .removePrefix("/api/v1/providers/")
                .removeSuffix("/updates/check")
                .trim('/')
            if (providerId.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "provider id is required"))
            val api = controlApi ?: return@createContext respond(exchange, 503, mapOf("error" to "control api is not configured"))
            runSuspendValue { api.checkUpdate(providerId) }.let { respond(exchange, 200, it) }
        }

        http.createContext(BackendApiContract.PROVIDER_UPDATE_QUEUE) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "POST")) return@createContext
            val providerId = query(exchange, "providerId")?.trim().orEmpty()
            if (providerId.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "providerId is required"))
            val api = controlApi ?: return@createContext respond(exchange, 503, mapOf("error" to "control api is not configured"))
            respond(exchange, 200, api.queueUpdate(providerId))
        }

        http.createContext(BackendApiContract.PROVIDER_UPDATE) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "POST")) return@createContext
            val providerId = exchange.requestURI.path
                .removePrefix("/api/v1/providers/")
                .removeSuffix("/update")
                .trim('/')
            if (providerId.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "provider id is required"))
            val api = controlApi ?: return@createContext respond(exchange, 503, mapOf("error" to "control api is not configured"))
            runSuspendValue { api.runUpdate(providerId) }.let { respond(exchange, 200, it) }
        }

        http.createContext(BackendApiContract.PROVIDER_ACTIVATE) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "POST")) return@createContext
            val providerId = exchange.requestURI.path
                .removePrefix("/api/v1/providers/")
                .removeSuffix("/update/activate")
                .trim('/')
            if (providerId.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "provider id is required"))
            val api = controlApi ?: return@createContext respond(exchange, 503, mapOf("error" to "control api is not configured"))
            runSuspendValue { api.activateUpdate(providerId) }.let { respond(exchange, 200, it) }
        }

        http.createContext(BackendApiContract.PROVIDER_ROLLBACK) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "POST")) return@createContext
            val providerId = exchange.requestURI.path
                .removePrefix("/api/v1/providers/")
                .removeSuffix("/rollback")
                .trim('/')
            if (providerId.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "provider id is required"))
            val api = controlApi ?: return@createContext respond(exchange, 503, mapOf("error" to "control api is not configured"))
            val request = runCatching { gson.fromJson(readBody(exchange), Map::class.java) }.getOrNull()
                ?: return@createContext respond(exchange, 400, mapOf("error" to "invalid rollback request"))
            val targetVersion = request["targetVersion"]?.toString()?.trim()
                ?: return@createContext respond(exchange, 400, mapOf("error" to "targetVersion is required"))
            runSuspendValue { api.rollback(providerId, targetVersion) }.let { respond(exchange, 200, it) }
        }

        http.createContext(BackendApiContract.PROVIDER_INCIDENTS) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            val api = controlApi ?: return@createContext respond(exchange, 503, mapOf("error" to "control api is not configured"))
            val relative = exchange.requestURI.path.removePrefix(BackendApiContract.PROVIDER_INCIDENTS).trim('/')
            when {
                relative.isBlank() && exchange.requestMethod.equals("GET", true) ->
                    respond(exchange, 200, api.incidents())
                relative.endsWith("/resolve") && exchange.requestMethod.equals("POST", true) -> {
                    val id = relative.removeSuffix("/resolve").trim('/')
                    if (id.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "incident id is required"))
                    val resolved = api.resolveIncident(id)
                    respond(exchange, if (resolved) 200 else 404, mapOf("resolved" to resolved))
                }
                else -> method(exchange, "GET")
            }
        }

        http.createContext(BackendApiContract.EXTRACTORS) { exchange ->
            requireControlAccess(exchange) ?: return@createContext
            if (!method(exchange, "GET")) return@createContext
            val api = controlApi ?: return@createContext respond(exchange, 503, mapOf("error" to "control api is not configured"))
            respond(exchange, 200, api.extractors())
        }

        http.createContext(NotificationApiContract.NOTIFICATIONS) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            if (!method(exchange, "GET")) return@createContext
            val page = query(exchange, "page")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val perPage = query(exchange, "perPage")?.toIntOrNull()?.coerceIn(1, 100) ?: 30
            respond(exchange, 200, auth.notificationService.list(viewer.id, page, perPage))
        }

        http.createContext(NotificationApiContract.UNREAD_COUNT) { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            if (!exchange.requestMethod.equals("GET", true)) return@createContext method(exchange, "GET")
            respond(exchange, 200, mapOf("count" to auth.notificationService.unreadCount(viewer.id)))
        }

        http.createContext("/api/v1/notifications/") { exchange ->
            val auth = requireAuthRuntime(exchange) ?: return@createContext
            val token = bearerToken(exchange) ?: return@createContext unauthorized(exchange)
            val viewer = auth.auth.currentUser(token) ?: return@createContext unauthorized(exchange)
            val relative = exchange.requestURI.path.removePrefix("/api/v1/notifications/").trim('/')
            when {
                relative == "read-all" && exchange.requestMethod.equals("POST", true) -> {
                    respond(exchange, 200, mapOf("marked" to auth.notificationService.markAllRead(viewer.id)))
                }
                relative.endsWith("/read") && exchange.requestMethod.equals("POST", true) -> {
                    val id = relative.removeSuffix("/read").trim('/')
                    if (id.isBlank()) return@createContext respond(exchange, 400, mapOf("error" to "invalid notification id"))
                    if (!auth.notificationService.markRead(viewer.id, id)) {
                        return@createContext respond(exchange, 404, mapOf("error" to "notification not found"))
                    }
                    respond(exchange, 200, mapOf("status" to "read"))
                }
                else -> respond(exchange, 404, mapOf("error" to "route not found"))
            }
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

    private fun authenticatedAdminActor(exchange: HttpExchange, auth: AuthRuntime): ShinigamiUser? {
        val token = bearerToken(exchange) ?: run {
            unauthorized(exchange)
            return null
        }
        return auth.auth.currentUser(token) ?: run {
            unauthorized(exchange)
            null
        }
    }

    private fun requireControlAccess(exchange: HttpExchange): AuthRuntime? {
        val auth = requireAuthRuntime(exchange) ?: return null
        val actor = authenticatedAdminActor(exchange, auth) ?: return null
        if (!auth.adminAccess.can(actor, AdminPermission.ACCESS_BACKEND_CONTROL)) {
            respond(exchange, 403, mapOf("error" to "backend control access denied"))
            return null
        }
        return auth
    }

    private fun readBody(exchange: HttpExchange): String =
        exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

    private fun <T> runSuspendValue(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }

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

    private fun chatErrorStatus(error: IllegalArgumentException): Int =
        when {
            error.message == "messaging is blocked" -> 403
            error.message == "recipient not found" || error.message == "sender not found" -> 404
            else -> 400
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
