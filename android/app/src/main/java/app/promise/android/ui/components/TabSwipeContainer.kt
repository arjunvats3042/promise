package app.promise.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.promise.android.ui.navigation.TabSwipeDirection

@Composable
fun TabSwipeContainer(
    currentIndex: Int,
    tabCount: Int,
    enabled: Boolean,
    onSwipe: (TabSwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    modalBlocking: Boolean = false,
    textFieldOwnsFocus: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier,
        content = content,
    )
}
