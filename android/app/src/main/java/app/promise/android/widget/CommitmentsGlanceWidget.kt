package app.promise.android.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
                        .background(widgetColor(day = PromiseColor.Background, night = PromiseDarkColor.Background))
                        .cornerRadius(20.dp)
                        .padding(12.dp),
                ) {
                    if (size.width >= 240.dp) {
                        ExpandedCommitmentsWidgetContent(
                            items = commitmentItems,
                            completedCount = completedCount,
                            totalCount = totalCount,
                            expandedItemId = widgetData.expandedItemId,
                        )
                    } else {
                        CompactCommitmentsWidgetContent(
                            items = commitmentItems,
                            completedCount = completedCount,
                            totalCount = totalCount,
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

@Composable
private fun widgetColor(day: Color, night: Color): ColorProvider {
    val context = LocalContext.current
    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    return ColorProvider(if (isDark) night else day)
}

@Composable
private fun CompactCommitmentsWidgetContent(
    items: List<WidgetCommitmentItem>,
    completedCount: Int,
    totalCount: Int,
) {
    val accentColor = widgetColor(day = PromiseColor.Accent, night = PromiseDarkColor.Accent)
    val textPrimaryColor = widgetColor(day = PromiseColor.TextPrimary, night = PromiseDarkColor.TextPrimary)
    val textSecondaryColor = widgetColor(day = PromiseColor.TextSecondary, night = PromiseDarkColor.TextSecondary)
    val surfaceMutedColor = widgetColor(day = PromiseColor.SurfaceMuted, night = PromiseDarkColor.SurfaceMuted)
    val primaryControlColor = widgetColor(day = PromiseColor.PrimaryControl, night = PromiseDarkColor.PrimaryControl)
    val onPrimaryControlColor = widgetColor(day = PromiseColor.OnPrimaryControl, night = PromiseDarkColor.OnPrimaryControl)
    val completedBtnBg = widgetColor(day = PromiseColor.Outline, night = PromiseDarkColor.Outline)
    val completedBtnText = widgetColor(day = PromiseColor.TextSecondary, night = PromiseDarkColor.TextSecondary)

    val progressPercent = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 0

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "COMMITMENTS",
            style = TextStyle(
                color = accentColor,
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
                    .background(surfaceMutedColor)
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
                            color = textPrimaryColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = "No commitments due today",
                        style = TextStyle(
                            color = textSecondaryColor,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .padding(2.dp)
                    .background(surfaceMutedColor)
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
                            color = textPrimaryColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "$completedCount/$totalCount Done",
                        style = TextStyle(
                            color = textSecondaryColor,
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
                        backgroundColor = if (topPending.isCompleted) completedBtnBg else primaryControlColor,
                        contentColor = if (topPending.isCompleted) completedBtnText else onPrimaryControlColor,
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
) {
    val accentColor = widgetColor(day = PromiseColor.Accent, night = PromiseDarkColor.Accent)
    val textPrimaryColor = widgetColor(day = PromiseColor.TextPrimary, night = PromiseDarkColor.TextPrimary)
    val textSecondaryColor = widgetColor(day = PromiseColor.TextSecondary, night = PromiseDarkColor.TextSecondary)
    val surfaceMutedColor = widgetColor(day = PromiseColor.SurfaceMuted, night = PromiseDarkColor.SurfaceMuted)
    val surfaceRaisedColor = widgetColor(day = PromiseColor.SurfaceRaised, night = PromiseDarkColor.SurfaceRaised)
    val primaryControlColor = widgetColor(day = PromiseColor.PrimaryControl, night = PromiseDarkColor.PrimaryControl)
    val onPrimaryControlColor = widgetColor(day = PromiseColor.OnPrimaryControl, night = PromiseDarkColor.OnPrimaryControl)
    val commitBadgeBg = widgetColor(day = PromiseColor.Accent.copy(alpha = 0.12f), night = PromiseDarkColor.Accent.copy(alpha = 0.18f))
    val commitBadgeText = accentColor
    val completedBtnBg = widgetColor(day = PromiseColor.Outline, night = PromiseDarkColor.Outline)
    val completedBtnText = textSecondaryColor

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
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "$completedCount/$totalCount Done",
                style = TextStyle(
                    color = textSecondaryColor,
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
                    .background(surfaceMutedColor)
                    .cornerRadius(14.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No commitments for today 🎉",
                        style = TextStyle(
                            color = textPrimaryColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Open Promise to add commitments",
                        style = TextStyle(
                            color = textSecondaryColor,
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
                        .background(if (isExpanded) surfaceRaisedColor else surfaceMutedColor)
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
                                .background(commitBadgeBg)
                                .cornerRadius(6.dp)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "COMMIT",
                                style = TextStyle(
                                    color = commitBadgeText,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }

                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = item.title,
                                style = TextStyle(
                                    color = if (item.isCompleted) textSecondaryColor else textPrimaryColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = TextStyle(
                                        color = textSecondaryColor,
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
                                backgroundColor = if (item.isCompleted) completedBtnBg else primaryControlColor,
                                contentColor = if (item.isCompleted) completedBtnText else onPrimaryControlColor,
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
                                    color = accentColor,
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
