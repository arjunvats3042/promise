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
import androidx.glance.layout.fillMaxHeight
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CommitmentsGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CommitmentsGlanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PromiseWidgetUpdater.fetchAndPushWidgetData(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
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
        val isDark = WidgetThemeHelper.isDarkTheme(context)
        val theme = if (isDark) CommitmentsThemeTokens.Dark else CommitmentsThemeTokens.Light

        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                var rawJson = prefs[WIDGET_DATA_PREF_KEY]
                if (rawJson.isNullOrBlank()) {
                    val cached = PromiseWidgetUpdater.getCachedWidgetData(context)
                    if (cached.items.isNotEmpty()) {
                        rawJson = cached.toJson()
                    }
                }
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
                    if (size.width >= 200.dp) {
                        ExpandedCommitmentsWidgetContent(
                            items = commitmentItems,
                            completedCount = completedCount,
                            totalCount = totalCount,
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
        private val HORIZONTAL_RECTANGLE = DpSize(220.dp, 120.dp)
    }
}

internal data class CommitmentsThemeTokens(
    val background: ColorProvider,
    val surfaceRaised: ColorProvider,
    val accent: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val completedBtnBg: ColorProvider,
    val completedBtnText: ColorProvider,
    val pendingBtnBg: ColorProvider,
    val pendingBtnText: ColorProvider,
    val overdueText: ColorProvider,
) {
    companion object {
        val Light = CommitmentsThemeTokens(
            background = ColorProvider(PromiseColor.Background),
            surfaceRaised = ColorProvider(PromiseColor.SurfaceRaised),
            accent = ColorProvider(Color(0xFF4F46E5)),
            textPrimary = ColorProvider(PromiseColor.TextPrimary),
            textSecondary = ColorProvider(PromiseColor.TextSecondary),
            completedBtnBg = ColorProvider(Color(0xFF10B981).copy(alpha = 0.16f)),
            completedBtnText = ColorProvider(Color(0xFF047857)),
            pendingBtnBg = ColorProvider(PromiseColor.SurfaceMuted),
            pendingBtnText = ColorProvider(PromiseColor.TextSecondary),
            overdueText = ColorProvider(Color(0xFFDC2626)),
        )

        val Dark = CommitmentsThemeTokens(
            background = ColorProvider(PromiseDarkColor.Background),
            surfaceRaised = ColorProvider(PromiseDarkColor.SurfaceRaised),
            accent = ColorProvider(Color(0xFF818CF8)),
            textPrimary = ColorProvider(PromiseDarkColor.TextPrimary),
            textSecondary = ColorProvider(PromiseDarkColor.TextSecondary),
            completedBtnBg = ColorProvider(Color(0xFF059669).copy(alpha = 0.25f)),
            completedBtnText = ColorProvider(Color(0xFF34D399)),
            pendingBtnBg = ColorProvider(PromiseDarkColor.SurfaceMuted),
            pendingBtnText = ColorProvider(PromiseDarkColor.TextSecondary),
            overdueText = ColorProvider(Color(0xFFF87171)),
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
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tracked Header
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openHomeAction()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TASKS",
                style = TextStyle(
                    color = theme.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "$completedCount/$totalCount",
                style = TextStyle(
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(openHomeAction()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "All clear",
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        text = "+ Add Task",
                        style = TextStyle(
                            color = theme.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        } else {
            val displayItems = items.take(2)
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                displayItems.forEach { item ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(theme.surfaceRaised)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(openCommitmentAction(item.id)),
                        ) {
                            Text(
                                text = item.title,
                                style = TextStyle(
                                    color = if (item.isCompleted) theme.textSecondary else theme.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            if (item.isOverdue && !item.isCompleted) {
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = "Overdue",
                                    style = TextStyle(
                                        color = theme.overdueText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Button(
                            text = if (item.isCompleted) "✓" else "○",
                            onClick = if (item.isCompleted) {
                                openCommitmentAction(item.id)
                            } else {
                                actionRunCallback<ToggleCommitmentActionCallback>(
                                    actionParametersOf(
                                        ToggleCommitmentActionCallback.ITEM_ID_PARAM to item.id,
                                        ToggleCommitmentActionCallback.ACTION_TYPE_PARAM to "TOGGLE_COMPLETE",
                                    ),
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (item.isCompleted) theme.completedBtnBg else theme.pendingBtnBg,
                                contentColor = if (item.isCompleted) theme.completedBtnText else theme.pendingBtnText,
                            ),
                            modifier = GlanceModifier
                                .width(26.dp)
                                .height(24.dp)
                                .cornerRadius(6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedCommitmentsWidgetContent(
    items: List<WidgetCommitmentItem>,
    completedCount: Int,
    totalCount: Int,
    theme: CommitmentsThemeTokens,
) {
    val progressPercent = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 0

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Column: Stats & Progress
        Column(
            modifier = GlanceModifier
                .width(95.dp)
                .fillMaxHeight()
                .clickable(openHomeAction()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "COMMITMENTS",
                style = TextStyle(
                    color = theme.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = "$progressPercent%",
                style = TextStyle(
                    color = theme.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = "$completedCount of $totalCount done",
                style = TextStyle(
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Right Column: Priority Task List
        if (items.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .clickable(openHomeAction()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "All caught up",
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "+ Add Commitment",
                        style = TextStyle(
                            color = theme.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        } else {
            val displayItems = items.take(2)
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                displayItems.forEach { item ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(theme.surfaceRaised)
                            .cornerRadius(10.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(openCommitmentAction(item.id)),
                        ) {
                            Text(
                                text = item.title,
                                style = TextStyle(
                                    color = if (item.isCompleted) theme.textSecondary else theme.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                            if (item.isOverdue && !item.isCompleted) {
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = "Overdue",
                                    style = TextStyle(
                                        color = theme.overdueText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            } else if (item.dueTimeFormatted.isNotBlank() && !item.isCompleted) {
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = item.dueTimeFormatted,
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
                            text = if (item.isCompleted) "✓" else "○",
                            onClick = if (item.isCompleted) {
                                openCommitmentAction(item.id)
                            } else {
                                actionRunCallback<ToggleCommitmentActionCallback>(
                                    actionParametersOf(
                                        ToggleCommitmentActionCallback.ITEM_ID_PARAM to item.id,
                                        ToggleCommitmentActionCallback.ACTION_TYPE_PARAM to "TOGGLE_COMPLETE",
                                    ),
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (item.isCompleted) theme.completedBtnBg else theme.pendingBtnBg,
                                contentColor = if (item.isCompleted) theme.completedBtnText else theme.pendingBtnText,
                            ),
                            modifier = GlanceModifier
                                .width(26.dp)
                                .height(24.dp)
                                .cornerRadius(6.dp),
                        )
                    }
                }
            }
        }
    }
}
