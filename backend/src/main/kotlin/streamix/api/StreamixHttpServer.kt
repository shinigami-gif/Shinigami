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
