package app.promise.android.data.local

import app.promise.android.core.AppLog
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SecureRefreshTokenStore(
    private val file: File,
    private val keyProvider: RefreshKeyProvider,
    private val cipher: AesGcmRefreshCipher = AesGcmRefreshCipher(),
) : TokenStore {
    private val mutex = Mutex()

    override suspend fun saveRefreshToken(token: String) {
        mutex.withLock {
            val plain = token.toByteArray(StandardCharsets.UTF_8)
            val blob = try {
                cipher.encrypt(keyProvider.getOrCreateKey(), plain)
            } catch (e: InvalidAlgorithmParameterException) {
                AppLog.d(TAG, "encrypt params failed; recreating key")
                cipher.encrypt(keyProvider.recreateKey(), plain)
            } catch (e: InvalidKeyException) {
                AppLog.d(TAG, "encrypt key failed; recreating key")
                cipher.encrypt(keyProvider.recreateKey(), plain)
            }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(blob)
            if (!tmp.renameTo(file)) {
                file.writeBytes(blob)
                tmp.delete()
            }
        }
    }

    override suspend fun readRefreshToken(): String? {
        return mutex.withLock {
            if (!file.exists()) return@withLock null
            val blob = file.readBytes()
            if (blob.isEmpty()) return@withLock null
            val plain = cipher.decrypt(keyProvider.getOrCreateKey(), blob)
            String(plain, StandardCharsets.UTF_8)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            if (file.exists()) {
                file.writeBytes(ByteArray(file.length().toInt().coerceAtLeast(1)))
                file.delete()
            }
        }
    }

    private companion object {
        const val TAG = "PromiseAuth"
    }
}
