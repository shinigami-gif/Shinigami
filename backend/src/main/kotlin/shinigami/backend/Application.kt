package shinigami.backend

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
)

fun Application.module() {
    install(CallLogging)
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/health") {
            call.respondText(
                """{"status":"ok","service":"shinigami-backend"}""",
                ContentType.Application.Json,
            )
        }
    }
}
