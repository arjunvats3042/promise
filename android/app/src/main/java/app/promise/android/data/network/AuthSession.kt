package app.promise.android.data.network

import app.promise.android.domain.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthSession @Inject constructor() {
    @Volatile
    var accessToken: String? = null
        private set

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun setUser(user: User) {
        _user.value = user
    }

    fun clear() {
        accessToken = null
        _user.value = null
    }
}
