package app.promise.android.data.local

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

open class AesGcmRefreshCipher(
    private val random: SecureRandom = SecureRandom(),
) {
    open fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Android Keystore (esp. StrongBox) rejects caller-provided GCM IVs on encrypt.
        // Let the provider generate the IV, then persist it with the ciphertext.
        if (isAndroidKeystoreKey(key)) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } else {
            val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val iv = cipher.iv
        require(iv != null && iv.size == IV_BYTES) { "unexpected GCM IV length" }
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(VERSION) + iv + ciphertext
    }

    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        require(blob.size > 1 + IV_BYTES) { "invalid blob" }
        require(blob[0] == VERSION) { "unsupported blob version" }
        val iv = blob.copyOfRange(1, 1 + IV_BYTES)
        val ciphertext = blob.copyOfRange(1 + IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun isAndroidKeystoreKey(key: SecretKey): Boolean {
        return key.javaClass.name.contains("AndroidKeyStore", ignoreCase = true) ||
            key.format == null
    }

    private companion object {
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

