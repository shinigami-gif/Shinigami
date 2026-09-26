package streamix.api

data class AuthSessionRequest(
    val provider: String,
    val credential: String
)
