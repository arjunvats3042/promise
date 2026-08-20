package app.promise.android.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf

data class TabSwipeHost(
    val currentIndex: Int,
    val tabCount: Int,
    val enabled: Boolean,
    val onSwipe: (TabSwipeDirection) -> Unit,
)

val LocalTabSwipeHost = staticCompositionLocalOf<TabSwipeHost?> { null }
