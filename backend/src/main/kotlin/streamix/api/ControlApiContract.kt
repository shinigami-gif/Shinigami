package streamix.api

/**
 * Technical/backend control-plane contract.
 *
 * This namespace is intentionally distinct from /api/v1/admin/*.
 * It is for infrastructure/provider/runtime administration only.
 */
object ControlApiContract {
    const val SNAPSHOT = "/api/v1/control/snapshot"
    const val PROVIDERS = "/api/v1/control/providers"
    const val EXTRACTORS = "/api/v1/control/extractors"
    const val INCIDENTS = "/api/v1/control/incidents"
    const val UPDATES = "/api/v1/control/updates"
    const val RUNTIME = "/api/v1/control/runtime"
    const val NETWORK = "/api/v1/control/network"
    const val CONFIG = "/api/v1/control/config"
}
