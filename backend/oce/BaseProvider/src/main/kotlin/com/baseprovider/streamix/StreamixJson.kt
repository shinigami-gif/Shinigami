package com.baseprovider.streamix

/**
 * Streamix-owned JSON boundary.
 *
 * The current runtime can bridge this contract to Gson/Jackson/serialization.
 * No provider should need to know which JSON engine is underneath.
 */
interface StreamixJson {
    fun parseObject(json: String): Map<String, Any?>
    fun parseArray(json: String): List<Any?>
    fun stringify(value: Any?): String
}
