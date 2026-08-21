package app.promise.android.data.users

import app.promise.android.data.network.toApiException
import app.promise.android.domain.LookupUser
import app.promise.android.domain.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val api: UserApi,
) : UserRepository {
    override suspend fun lookupByEmail(email: String): LookupUser {
        return try {
            val dto = api.lookupByEmail(email.trim().lowercase())
            LookupUser(id = dto.id, name = dto.name, email = dto.email)
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }
}
