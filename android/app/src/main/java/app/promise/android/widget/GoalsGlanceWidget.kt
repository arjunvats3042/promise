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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GoalsGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalsGlanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        CoroutineScope(Dispatchers.IO).launch {
            PromiseWidgetUpdater.fetchAndPushWidgetData(context)
        }
    }
}

class GoalsGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SQUARE,
            HORIZONTAL_RECTANGLE,
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val theme = if (isDark) GoalsThemeTokens.Dark else GoalsThemeTokens.Light

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
                val goalItems = widgetData.items.filter { it.type == WidgetItemType.GOAL }
                val completedCount = goalItems.count { it.isCompleted }
                val totalCount = goalItems.size
                val size = LocalSize.current

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(theme.background)
                        .cornerRadius(20.dp)
                        .padding(12.dp),
                ) {
                    if (size.width >= 220.dp) {
                        ExpandedGoalsWidgetContent(
                            items = goalItems,
                            completedCount = completedCount,
                            totalCount = totalCount,
                            theme = theme,
                        )
                    } else {
                        CompactGoalsWidgetContent(
                            items = goalItems,
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

internal data class GoalsThemeTokens(
    val background: ColorProvider,
    val surfaceMuted: ColorProvider,
    val surfaceRaised: ColorProvider,
    val accent: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val primaryControl: ColorProvider,
    val onPrimaryControl: ColorProvider,
    val goalBadgeBg: ColorProvider,
    val goalBadgeText: ColorProvider,
    val completedBtnBg: ColorProvider,
    val completedBtnText: ColorProvider,
    val pendingBtnBg: ColorProvider,
    val pendingBtnText: ColorProvider,
) {
    companion object {
        val Light = GoalsThemeTokens(
            background = ColorProvider(PromiseColor.Background),
            surfaceMuted = ColorProvider(PromiseColor.SurfaceMuted),
            surfaceRaised = ColorProvider(PromiseColor.SurfaceRaised),
            accent = ColorProvider(PromiseColor.Accent),
            textPrimary = ColorProvider(PromiseColor.TextPrimary),
            textSecondary = ColorProvider(PromiseColor.TextSecondary),
            primaryControl = ColorProvider(PromiseColor.PrimaryControl),
            onPrimaryControl = ColorProvider(PromiseColor.OnPrimaryControl),
            goalBadgeBg = ColorProvider(Color(0xFF2563EB).copy(alpha = 0.12f)),
            goalBadgeText = ColorProvider(Color(0xFF1D4ED8)),
            completedBtnBg = ColorProvider(Color(0xFF10B981).copy(alpha = 0.18f)),
            completedBtnText = ColorProvider(Color(0xFF047857)),
            pendingBtnBg = ColorProvider(PromiseColor.PrimaryControl),
            pendingBtnText = ColorProvider(PromiseColor.OnPrimaryControl),
        )

        val Dark = GoalsThemeTokens(
            background = ColorProvider(PromiseDarkColor.Background),
            surfaceMuted = ColorProvider(PromiseDarkColor.SurfaceMuted),
            surfaceRaised = ColorProvider(PromiseDarkColor.SurfaceRaised),
            accent = ColorProvider(PromiseDarkColor.Accent),
            textPrimary = ColorProvider(PromiseDarkColor.TextPrimary),
            textSecondary = ColorProvider(PromiseDarkColor.TextSecondary),
            primaryControl = ColorProvider(PromiseDarkColor.PrimaryControl),
            onPrimaryControl = ColorProvider(PromiseDarkColor.OnPrimaryControl),
            goalBadgeBg = ColorProvider(Color(0xFF60A5FA).copy(alpha = 0.18f)),
            goalBadgeText = ColorProvider(Color(0xFF93C5FD)),
            completedBtnBg = ColorProvider(Color(0xFF059669).copy(alpha = 0.25f)),
            completedBtnText = ColorProvider(Color(0xFF34D399)),
            pendingBtnBg = ColorProvider(PromiseDarkColor.PrimaryControl),
            pendingBtnText = ColorProvider(PromiseDarkColor.OnPrimaryControl),
        )
    }
}

@Composable
private fun CompactGoalsWidgetContent(
    items: List<WidgetCommitmentItem>,
    completedCount: Int,
    totalCount: Int,
    theme: GoalsThemeTokens,
) {
    val progressPercent = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 0

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header with open action
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(openHomeAction()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "🎯 GOALS",
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
                    .fillMaxWidth()
                    .defaultWeight()
                    .background(theme.surfaceMuted)
                    .cornerRadius(12.dp)
                    .clickable(openHomeAction()),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier.padding(6.dp),
                ) {
                    Text(
                        text = "All done! 🎯",
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "Tap to open",
                        style = TextStyle(
                            color = theme.textSecondary,
                            fontSize = 9.sp,
                        ),
                    )
                }
            }
        } else {
            // Progress Capsule
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(theme.surfaceMuted)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(openHomeAction()),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$progressPercent% Done",
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "($completedCount/$totalCount)",
                        style = TextStyle(
                            color = theme.textSecondary,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(4.dp))

            // Show top actionable items
            val displayItems = items.take(2)
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                displayItems.forEach { item ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(theme.surfaceRaised)
                            .cornerRadius(8.dp)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(openGoalAction(item.id)),
                        ) {
                            Text(
                                text = item.title,
                                style = TextStyle(
                                    color = if (item.isCompleted) theme.textSecondary else theme.textPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                maxLines = 1,
                            )
                        }

                        Button(
                            text = if (item.isCompleted) "✓" else "+",
                            onClick = actionRunCallback<ToggleCommitmentActionCallback>(
                                actionParametersOf(
                                    ToggleCommitmentActionCallback.ITEM_ID_PARAM to item.id,
                                    ToggleCommitmentActionCallback.ACTION_TYPE_PARAM to "TOGGLE_COMPLETE",
                                ),
                            ),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (item.isCompleted) theme.completedBtnBg else theme.pendingBtnBg,
                                contentColor = if (item.isCompleted) theme.completedBtnText else theme.pendingBtnText,
                            ),
                            modifier = GlanceModifier
                                .width(28.dp)
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
private fun ExpandedGoalsWidgetContent(
    items: List<WidgetCommitmentItem>,
    completedCount: Int,
    totalCount: Int,
    theme: GoalsThemeTokens,
) {
    val progressPercent = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 0

    Column(
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        // Dynamic Header: Brand + Progress + Live Sync
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.clickable(openHomeAction()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🎯 DAILY GOALS",
                    style = TextStyle(
                        color = theme.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Progress Tag
            Box(
                modifier = GlanceModifier
                    .background(theme.surfaceMuted)
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable(openHomeAction()),
            ) {
                Text(
                    text = "$progressPercent% ($completedCount/$totalCount)",
                    style = TextStyle(
                        color = theme.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Sync Button
            Button(
                text = "🔄",
                onClick = actionRunCallback<RefreshWidgetActionCallback>(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = theme.surfaceMuted,
                    contentColor = theme.textPrimary,
                ),
                modifier = GlanceModifier
                    .width(30.dp)
                    .height(24.dp)
                    .cornerRadius(8.dp),
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
                    .padding(12.dp)
                    .clickable(openHomeAction()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "All daily goals completed! 🎯",
                        style = TextStyle(
                            color = theme.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "Tap to open Promise & manage habits",
                        style = TextStyle(
                            color = theme.textSecondary,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
        } else {
            // Scrollable LazyColumn containing all goals
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize(),
            ) {
                items(items) { item ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(theme.surfaceRaised)
                            .cornerRadius(12.dp)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 1-Tap Check-In / Complete Button
                        Button(
                            text = if (item.isCompleted) "✓ DONE" else "CHECK IN",
                            onClick = actionRunCallback<ToggleCommitmentActionCallback>(
                                actionParametersOf(
                                    ToggleCommitmentActionCallback.ITEM_ID_PARAM to item.id,
                                    ToggleCommitmentActionCallback.ACTION_TYPE_PARAM to "TOGGLE_COMPLETE",
                                ),
                            ),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = if (item.isCompleted) theme.completedBtnBg else theme.pendingBtnBg,
                                contentColor = if (item.isCompleted) theme.completedBtnText else theme.pendingBtnText,
                            ),
                            modifier = GlanceModifier
                                .height(28.dp)
                                .cornerRadius(14.dp),
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        // Goal Content Column: Tapping jumps directly to that Goal Detail in app
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(openGoalAction(item.id)),
                        ) {
                            Text(
                                text = item.title,
                                style = TextStyle(
                                    color = if (item.isCompleted) theme.textSecondary else theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                            )
                            if (item.subtitle.isNotBlank() || item.streakCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.streakCount > 0) {
                                        Text(
                                            text = "🔥 ${item.streakCount}d streak",
                                            style = TextStyle(
                                                color = theme.accent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        )
                                        Spacer(modifier = GlanceModifier.width(4.dp))
                                    }
                                    if (item.subtitle.isNotBlank()) {
                                        Text(
                                            text = "• ${item.subtitle}",
                                            style = TextStyle(
                                                color = theme.textSecondary,
                                                fontSize = 9.sp,
                                            ),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(4.dp))

                        // Quick Arrow to open in app
                        Box(
                            modifier = GlanceModifier
                                .padding(4.dp)
                                .clickable(openGoalAction(item.id)),
                        ) {
                            Text(
                                text = "↗",
                                style = TextStyle(
                                    color = theme.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

