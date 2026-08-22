package app.promise.android.domain

interface UserRepository {
    suspend fun lookupByEmail(email: String): LookupUser
    suspend fun searchUsers(query: String): List<LookupUser>
}
