package app.promise.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.rememberReduceMotion

@Composable
fun PromiseSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.sm),
) {
    val colors = PromiseThemeColors.current
    val reduceMotion = rememberReduceMotion()

    if (reduceMotion) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(colors.surfaceMuted),
        )
    } else {
        val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
        val alpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "shimmer_alpha",
        )
        Box(
            modifier = modifier
                .clip(shape)
                .background(colors.surfaceMuted.copy(alpha = alpha)),
        )
    }
}

@Composable
fun PromiseFeedSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.inset)
            .semantics { contentDescription = "Loading dashboard" },
    ) {
        Spacer(modifier = Modifier.height(Spacing.lg))
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                PromiseSkeletonBox(modifier = Modifier.size(width = 120.dp, height = 16.dp))
                Spacer(modifier = Modifier.height(Spacing.xs))
                PromiseSkeletonBox(modifier = Modifier.size(width = 180.dp, height = 28.dp))
            }
            PromiseSkeletonBox(modifier = Modifier.size(44.dp), shape = CircleShape)
        }

        Spacer(modifier = Modifier.height(Spacing.lg))
        // AI / Attention card skeleton
        PromiseSkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(Radius.md),
        )

        Spacer(modifier = Modifier.height(Spacing.section))

        // Section 1: Commitments
        PromiseSkeletonBox(modifier = Modifier.size(width = 140.dp, height = 20.dp))
        Spacer(modifier = Modifier.height(Spacing.md))
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PromiseSkeletonBox(modifier = Modifier.size(22.dp), shape = CircleShape)
                Spacer(modifier = Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    PromiseSkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    PromiseSkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
        }

        Spacer(modifier = Modifier.height(Spacing.section))

        // Section 2: Practices
        PromiseSkeletonBox(modifier = Modifier.size(width = 120.dp, height = 20.dp))
        Spacer(modifier = Modifier.height(Spacing.md))
        repeat(2) {
            PromiseSkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                shape = RoundedCornerShape(Radius.md),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@Composable
fun PromiseListSkeleton(
    itemCount: Int = 4,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.inset, vertical = Spacing.md)
            .semantics { contentDescription = "Loading list" },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(itemCount) {
            PromiseSkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                shape = RoundedCornerShape(Radius.md),
            )
        }
    }
}

@Composable
fun PromiseDetailSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.inset, vertical = Spacing.md)
            .semantics { contentDescription = "Loading details" },
    ) {
        Spacer(modifier = Modifier.height(Spacing.md))
        PromiseSkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(28.dp))
        Spacer(modifier = Modifier.height(Spacing.sm))
        PromiseSkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(16.dp))

        Spacer(modifier = Modifier.height(Spacing.section))
        PromiseSkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(Radius.md),
        )

        Spacer(modifier = Modifier.height(Spacing.section))
        PromiseSkeletonBox(modifier = Modifier.fillMaxWidth(0.3f).height(18.dp))
        Spacer(modifier = Modifier.height(Spacing.sm))
        PromiseSkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(Radius.md),
        )
    }
}
