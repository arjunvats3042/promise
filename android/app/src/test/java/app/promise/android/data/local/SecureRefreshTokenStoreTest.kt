package app.promise.android.data.local

import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecureRefreshTokenStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun roundTripEncryptsAndReads() = runBlocking {
        val store = store()
        store.saveRefreshToken("sid.secret")
        assertEquals("sid.secret", store.readRefreshToken())
        val raw = File(folder.root, "refresh.bin").readBytes()
        val asText = raw.toString(Charsets.ISO_8859_1)
        assertEquals(false, asText.contains("sid.secret"))
    }

    @Test
    fun clearRemovesBlob() = runBlocking {
        val store = store()
        store.saveRefreshToken("sid.secret")
        store.clear()
        assertNull(store.readRefreshToken())
    }

    @Test
    fun missingFileIsNull() = runBlocking {
        assertNull(store().readRefreshToken())
    }

    @Test
    fun saveRecreatesKeyAfterInvalidAlgorithmParameter() = runBlocking {
        val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var recreated = 0
        val provider = object : RefreshKeyProvider {
            override fun getOrCreateKey(): SecretKey = key
            override fun recreateKey(): SecretKey {
                recreated += 1
                return key
            }
        }
        var encryptCalls = 0
        val cipher = object : AesGcmRefreshCipher() {
            override fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
                encryptCalls += 1
                if (encryptCalls == 1) {
                    throw java.security.InvalidAlgorithmParameterException("simulated")
                }
                return super.encrypt(key, plaintext)
            }
        }
        val store = SecureRefreshTokenStore(
            file = File(folder.root, "refresh-retry.bin"),
            keyProvider = provider,
            cipher = cipher,
        )
        store.saveRefreshToken("sid.secret")
        assertEquals(1, recreated)
        assertEquals(2, encryptCalls)
        assertEquals("sid.secret", store.readRefreshToken())
    }

    private fun store(): SecureRefreshTokenStore {
        return SecureRefreshTokenStore(
            file = File(folder.root, "refresh.bin"),
            keyProvider = object : RefreshKeyProvider {
                private val key: SecretKey = KeyGenerator.getInstance("AES").apply {
                    init(256)
                }.generateKey()
                override fun getOrCreateKey(): SecretKey = key
            },
        )
    }
}
