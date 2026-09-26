package com.baseprovider.streamix

/**
 * Streamix-owned cryptography boundary.
 *
 * The current implementation may delegate to the existing cryptography
 * provider. Keeping this contract separate prevents provider code from being
 * coupled to a specific crypto library.
 */
interface StreamixCrypto {
    fun sha256(input: ByteArray): ByteArray
    fun aesGcmDecrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray? = null
    ): ByteArray
}
