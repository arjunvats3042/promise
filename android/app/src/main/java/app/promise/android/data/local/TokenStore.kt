package app.promise.android.data.local

interface TokenStore {
    suspend fun saveRefreshToken(token: String)

    suspend fun readRefreshToken(): String?

    suspend fun clear()
}
