package app.promise.android.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class ProfilePhotoStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("promise_profile_prefs", Context.MODE_PRIVATE)
    private val _photoUri = MutableStateFlow<String?>(loadSavedPhotoUri())
    val photoUri: StateFlow<String?> = _photoUri.asStateFlow()

    private fun loadSavedPhotoUri(): String? {
        val path = prefs.getString(KEY_PHOTO_PATH, null) ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    fun readBytesFromUri(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    fun readPhotoBytes(): ByteArray? {
        return try {
            val path = _photoUri.value ?: return null
            val file = File(path)
            if (file.exists()) file.readBytes() else null
        } catch (_: Exception) {
            null
        }
    }

    fun savePhotoFromUri(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val avatarFile = File(context.filesDir, AVATAR_FILENAME)
            FileOutputStream(avatarFile).use { output ->
                inputStream.copyTo(output)
            }
            val absolutePath = avatarFile.absolutePath
            prefs.edit().putString(KEY_PHOTO_PATH, absolutePath).apply()
            _photoUri.value = absolutePath
            absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun clearPhoto() {
        try {
            val avatarFile = File(context.filesDir, AVATAR_FILENAME)
            if (avatarFile.exists()) {
                avatarFile.delete()
            }
        } catch (_: Exception) {
            // Ignore deletion errors
        }
        prefs.edit().remove(KEY_PHOTO_PATH).apply()
        _photoUri.value = null
    }

    companion object {
        private const val KEY_PHOTO_PATH = "profile_photo_path"
        private const val AVATAR_FILENAME = "profile_avatar.jpg"
    }
}
