package app.promise.android.data.local

import android.content.Context
import app.promise.android.domain.User
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class UserSessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("promise_user_session_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveUser(user: User) {
        val serialized = json.encodeToString(user)
        prefs.edit().putString(KEY_CACHED_USER, serialized).apply()
    }

    fun readUser(): User? {
        val raw = prefs.getString(KEY_CACHED_USER, null) ?: return null
        return runCatching { json.decodeFromString<User>(raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_CACHED_USER).apply()
    }

    companion object {
        private const val KEY_CACHED_USER = "cached_user_json"
    }
}
