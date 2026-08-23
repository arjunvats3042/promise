package app.promise.android.ui.components

import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.promise.android.BuildConfig
import app.promise.android.ui.theme.PromiseThemeColors
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AvatarSize(val dp: Dp, val fontSize: Int) {
    SM(28.dp, 11),
    MD(40.dp, 14),
    LG(56.dp, 20),
    XL(80.dp, 28),
}

private object AvatarMemoryCache {
    private val cache = LruCache<String, ImageBitmap>(50)

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

private fun resolveAvatarUrl(rawPath: String): String {
    if (rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
        return rawPath
    }
    if (rawPath.startsWith("/media/") || rawPath.startsWith("media/")) {
        val base = BuildConfig.API_BASE_URL.removeSuffix("/api/v1/").removeSuffix("/api/v1").removeSuffix("/")
        val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        return "$base$path"
    }
    return rawPath
}

@Composable
fun PromiseAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    photoPath: String? = null,
    size: AvatarSize = AvatarSize.MD,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val colors = PromiseThemeColors.current
    val actualBg = backgroundColor ?: colors.surfaceMuted
    val actualBorder = borderColor ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    var loadedBitmap by remember(photoPath) {
        mutableStateOf<ImageBitmap?>(
            if (!photoPath.isNullOrBlank()) {
                val resolved = resolveAvatarUrl(photoPath)
                AvatarMemoryCache.get(resolved) ?: run {
                    val file = File(photoPath)
                    if (file.exists() && file.length() > 0) {
                        try {
                            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()?.also {
                                AvatarMemoryCache.put(resolved, it)
                            }
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                }
            } else null
        )
    }

    LaunchedEffect(photoPath) {
        if (!photoPath.isNullOrBlank() && loadedBitmap == null) {
            val resolved = resolveAvatarUrl(photoPath)
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                withContext(Dispatchers.IO) {
                    try {
                        val url = URL(resolved)
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.doInput = true
                        conn.connect()
                        if (conn.responseCode in 200..299) {
                            conn.inputStream.use { input ->
                                val bmp = BitmapFactory.decodeStream(input)
                                if (bmp != null) {
                                    val imageBitmap = bmp.asImageBitmap()
                                    AvatarMemoryCache.put(resolved, imageBitmap)
                                    withContext(Dispatchers.Main) {
                                        loadedBitmap = imageBitmap
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Fall back to initials
                    }
                }
            }
        }
    }

    val baseModifier = modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(actualBg)
        .border(1.dp, actualBorder, CircleShape)

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }.let {
        if (contentDescription != null) {
            it.semantics { this.contentDescription = contentDescription }
        } else it
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center,
    ) {
        val bmp = loadedBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials.take(2).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = size.fontSize.sp,
                    lineHeight = size.fontSize.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
fun PromiseAvatarStack(
    members: List<Pair<String, String?>>, // (initials, photoPath)
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.SM,
    maxVisible: Int = 4,
) {
    val colors = PromiseThemeColors.current
    val visibleMembers = members.take(maxVisible)
    val remaining = members.size - visibleMembers.size

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleMembers.forEachIndexed { index, (initials, photo) ->
            val offset = if (index > 0) (-8 * index).dp else 0.dp
            PromiseAvatar(
                initials = initials,
                photoPath = photo,
                size = size,
                borderColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.offset(x = offset),
            )
        }
        if (remaining > 0) {
            val offset = (-8 * visibleMembers.size).dp
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceMuted)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$remaining",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
