package com.baseprovider.streamix

/**
 * Streamix-owned JavaScript execution boundary.
 *
 * QuickJS/Rhino are implementation details. Providers and extractors should
 * target this contract so either engine can be replaced independently.
 */
interface StreamixJs {
    fun evaluate(script: String): String
}
