package streamix.core

class StreamixProviderRegistry(providers: Iterable<StreamixProvider> = emptyList()) {
    private val entries = linkedMapOf<String, StreamixProvider>()
    init { providers.forEach(::register) }

    fun register(provider: StreamixProvider) {
        require(provider.id.isNotBlank()) { "provider id must not be blank" }
        require(entries.putIfAbsent(provider.id.lowercase(), provider) == null) {
            "Provider already registered: " + provider.id
        }
    }
    fun get(id: String): StreamixProvider? = entries[id.lowercase()]
    fun all(): List<StreamixProvider> = entries.values.toList()
}
