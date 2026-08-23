package app.promise.android.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NotificationReadStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _readIds = MutableStateFlow<Set<String>>(loadReadIds())
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    private fun loadReadIds(): Set<String> {
        return prefs.getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
    }

    fun isRead(id: String): Boolean {
        return _readIds.value.contains(id)
    }

    fun markAsRead(id: String) {
        if (id.isBlank()) return
        val updated = _readIds.value + id
        _readIds.value = updated
        prefs.edit().putStringSet(KEY_READ_IDS, updated).apply()
    }

    fun markAllAsRead(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val updated = _readIds.value + ids
        _readIds.value = updated
        prefs.edit().putStringSet(KEY_READ_IDS, updated).apply()
    }

    fun clearAll() {
        _readIds.value = emptySet()
        prefs.edit().remove(KEY_READ_IDS).apply()
    }

    companion object {
        private const val PREFS_NAME = "promise_notification_reads"
        private const val KEY_READ_IDS = "read_notification_ids"
    }
}
