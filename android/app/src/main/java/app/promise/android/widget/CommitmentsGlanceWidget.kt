package app.promise.android.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.promise.android.ui.theme.PromiseColor
import app.promise.android.ui.theme.PromiseDarkColor

class CommitmentsGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommitmentsGlanceWidget()
}

class CommitmentsGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SQUARE,
            HORIZONTAL_RECTANGLE,
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val theme = if (isDark) CommitmentsThemeTokens.Dark else CommitmentsThemeTokens.Light

        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val rawJson = prefs[WIDGET_DATA_PREF_KEY]
                val widgetData = PromiseWidgetData.fromJson(rawJson)
                val commitmentItems = widgetData.items.filter { it.type == WidgetItemType.COMMITMENT }
                val completedCount = commitmentItems.count { it.isCompleted }
                val totalCount = commitmentItems.size
                val size = LocalSize.current

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(theme.background)
                        .cornerRadius(20.dp)
                        .padding(12.dp),
                ) {
                    if (size.width >= 240.dp) {
                        ExpandedCommitmentsWidgetContent(
                            items = commitmentItems,
                            completedCount = completedCount,
                            totalCount = totalCount,
                            expandedItemId = widgetData.expandedItemId,
                            theme = theme,
                        )
                    } else {
                        CompactCommitmentsWidgetContent(
                            items = commitmentItems,
                            completedCount = completedCount,
                            totalCount = totalCount,
                            theme = theme,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val SMALL_SQUARE = DpSize(120.dp, 120.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(240.dp, 120.dp)
    }
}

internal data class CommitmentsThemeTokens(
    val background: ColorProvider,
    val surfaceMuted: ColorProvider,
    val surfaceRaised: ColorProvider,
    val accent: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val primaryControl: ColorProvider,
    val onPrimaryControl: ColorProvider,
    val commitBadgeBg: ColorProvider,
    val commitBadgeText: ColorProvider,
    val completedBtnBg: ColorProvider,
    val completedBtnText: ColorProvider,
) {
    companion object {
        val Light = CommitmentsThemeTokens(
            background = ColorProvider(PromiseColor.Background),
            surfaceMuted = ColorProvider(PromiseColor.SurfaceMuted),
            surfaceRaised = ColorProvider(PromiseColor.SurfaceRaised),
            accent = ColorProvider(PromiseColor.Accent),
            textPrimary = ColorProvider(PromiseColor.TextPrimary),
            textSecondary = ColorProvider(PromiseColor.TextSecondary),
            primaryControl = ColorProvider(PromiseColor.PrimaryControl),
            onPrimaryControl = ColorProvider(PromiseColor.OnPrimaryControl),
            commitBadgeBg = ColorProvider(PromiseColor.Accent.copy(alpha = 0.12f)),
            commitBadgeText = ColorProvider(PromiseColor.Accent),
            completedBtnBg = ColorProvider(PromiseColor.Outline),
            completedBtnText = ColorProvider(PromiseColor.TextSecondary),
        )

        val Dark = CommitmentsThemeTokens(
            background = ColorProvider(PromiseDarkColor.Background),
            surfaceMuted = ColorProvider(PromiseDarkColor.SurfaceMuted),
            surfaceRaised = ColorProvider(PromiseDarkColor.SurfaceRaised),
            accent = ColorProvider(PromiseDarkColor.Accent),
            textPrimary = ColorProvider(PromiseDarkColor.TextPrimary),
            textSecondary = ColorProvider(PromiseDarkColor.TextSecondary),
            primaryControl = ColorProvider(PromiseDarkColor.PrimaryControl),
            onPrimaryControl = ColorProvider(PromiseDarkColor.OnPrimaryControl),
            commitBadgeBg = ColorProvider(PromiseDarkColor.Accent.copy(alpha = 0.18f)),
            commitBadgeText = ColorProvider(PromiseDarkColor.Accent),
            completedBtnBg = ColorProvider(PromiseDarkColor.Outline),
            completedBtnText = ColorProvider(PromiseDarkColor.TextSecondary),
        )
    }
}

@Composable
private fun CompactCommitmentsWidgetContent(
    items: List<WidgetCommitmentItem>,
    completedCount: Int,
    totalCount: Int,
    theme: CommitmentsThemeTokens,
) {
    val progressPercent = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 0

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "COMMITMENTS",
            style = TextStyle(
                color = theme.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(theme.surfaceMuted)
                    .cornerRadius(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier.padding(8.dp),
                ) {
                    Text(
                        text = "All clear! ✨",
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = "No commitments due today",
                        style = TextStyle(
                            color = theme.textSecondary,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .padding(2.dp)
                    .background(theme.surfaceMuted)
                    .cornerRadius(50.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier.padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "$progressPercent%",
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "$completedCount/$totalCount Done",
                        style = TextStyle(
                            color = theme.textSecondary,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            val topPending = items.firstOrNull { !it.isCompleted } ?: items.firstOrNull()
            if (topPending != null) {
                Button(
                    text = if (topPending.isCompleted) "DONE ✓" else "COMPLETE",
                    onClick = actionRunCallback<ToggleCommitmentActionCallback>(
                        actionParametersOf(
                            ToggleCommitmentActionCallback.ITEM_ID_PARAM to topPending.id,
                            ToggleCommitmentActionCallback.ACTION_TYPE_PARAM to "TOGGLE_COMPLETE",
                        ),
                    ),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = if (topPending.isCompleted) theme.completedBtnBg else theme.primaryControl,
                        contentColor = if (topPending.isCompleted) theme.completedBtnText else theme.onPrimaryControl,
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .cornerRadius(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpandedCommitmentsWidgetContent(
    items: List<WidgetCommitmentItem>,
    completedCount: Int,
    totalCount: Int,
    expandedItemId: String?,
    theme: CommitmentsThemeTokens,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "COMMITMENTS",
                style = TextStyle(
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "$completedCount/$totalCount Done",
                style = TextStyle(
                    color = theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(theme.surfaceMuted)
                    .cornerRadius(14.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No commitments for today 🎉",
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Open Promise to add commitments",
                        style = TextStyle(
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        } else {
            val displayItems = items.take(3)
            displayItems.forEach { item ->
                val isExpanded = expandedItemId == item.id

                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(if (isExpanded) theme.surfaceRaised else theme.surfaceMuted)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(
                                actionRunCallback<ToggleCommitmentActionCallback>(
                                    actionParametersOf(
                                        ToggleCommitmentActionCallback.ITEM_ID_PARAM to item.id,
                                        ToggleCommitmentActionCallback.ACTION_TYPE_PARAM to "TOGGLE_EXPAND",
                                    ),
                                ),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .padding(end = 8.dp)
                                .background(theme.commitBadgeBg)
                                .cornerRadius(6.dp)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "COMMIT",
                                style = TextStyle(
                                    color = theme.commitBadgeText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }

                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = item.title,
                                style = TextStyle(
                                    color = if (item.isCompleted) theme.textSecondary else theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = TextStyle(
                                        color = theme.textSecondary,
                                        fontSize = 9.sp,
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Button(
                            text = if (item.isCompleted) "DONE ✓" else "COMPLETE",
                            onClick = actionRunCallback<ToggleCommitmentActionCallback>(
                                actionParametersOf(
                                    ToggleCommitmentActionCallback.ITEM_ID_PARAM to item.id,
                                    ToggleCommitmentActionCallback.ACTION_TYPE_PARAM to "TOGGLE_COMPLETE",
                                ),
                            ),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (item.isCompleted) theme.completedBtnBg else theme.primaryControl,
                                contentColor = if (item.isCompleted) theme.completedBtnText else theme.onPrimaryControl,
                            ),
                            modifier = GlanceModifier
                                .height(26.dp)
                                .cornerRadius(13.dp),
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Status: ${if (item.isCompleted) "Completed" else "Pending"} • ${item.dueTimeFormatted}",
                                style = TextStyle(
                                    color = theme.accent,
                                    fontSize = 10.sp,
                                ),
                                modifier = GlanceModifier.defaultWeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}
