package app.promise.android.data.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryTokenStore : TokenStore {
    private val mutex = Mutex()
    private var token: String? = null

    override suspend fun saveRefreshToken(token: String) {
        mutex.withLock { this.token = token }
    }

    override suspend fun readRefreshToken(): String? {
        return mutex.withLock { token }
    }

    override suspend fun clear() {
        mutex.withLock { token = null }
    }
}
