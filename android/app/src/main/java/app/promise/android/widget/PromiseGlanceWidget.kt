package app.promise.android.widget

import android.content.Context
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
import androidx.glance.appwidget.GlanceAppWidget
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

class PromiseGlanceWidget : GlanceAppWidget() {

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
                val size = LocalSize.current

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xB3181F26)))
                        .cornerRadius(24.dp)
                        .padding(12.dp),
                ) {
                    if (size.width >= 240.dp) {
                        ExpandedWidgetContent(data = widgetData)
                    } else {
                        CompactWidgetContent(data = widgetData)
                    }
                }
            }
        }
    }

    companion object {
        private val SMALL_SQUARE = DpSize(120.dp, 120.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(240.dp, 120.dp)

        // Moderate non-flashy colors
        private val SageGreen = Color(0xFF84A98C)
        private val SlateBlue = Color(0xFF52796F)
        private val DarkCardBg = Color(0xFF1E252D)
        private val OffWhite = Color(0xFFF1F5F9)
        private val SoftMuted = Color(0xFF94A3B8)
    }
}

@Composable
private fun CompactWidgetContent(data: PromiseWidgetData) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DAILY COMMITMENT",
            style = TextStyle(
                color = ColorProvider(Color(0xFF84A98C)),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        Box(
            modifier = GlanceModifier
                .padding(4.dp)
                .background(ColorProvider(Color(0xFF1E252D)))
                .cornerRadius(50.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "${data.progressPercent}%",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFF1F5F9)),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "${data.completedCount}/${data.totalCount} Habits",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF94A3B8)),
                        fontSize = 10.sp,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        Text(
            text = "Consistency: ${data.consistencyLevel}",
            style = TextStyle(
                color = ColorProvider(Color(0xFF84A98C)),
                fontSize = 10.sp,
            ),
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        val topItem = data.topPendingItem
        if (topItem != null) {
            Button(
                text = if (topItem.isCompleted) "COMPLETED ✓" else "CHECK IN",
                onClick = actionRunCallback<ToggleCommitmentActionCallback>(
                    actionParametersOf(ToggleCommitmentActionCallback.COMMITMENT_ID_PARAM to topItem.id),
                ),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ColorProvider(if (topItem.isCompleted) Color(0xFF2D3748) else Color(0xFF52796F)),
                    contentColor = ColorProvider(Color(0xFFF1F5F9)),
                ),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .cornerRadius(18.dp),
            )
        }
    }
}

@Composable
private fun ExpandedWidgetContent(data: PromiseWidgetData) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "GOALS & STREAKS",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF84A98C)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "Promise",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF94A3B8)),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(50.dp)
                    .background(ColorProvider(Color(0xFF1E252D)))
                    .cornerRadius(12.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${data.streakCount}",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFF1F5F9)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "Days Streak 🔥",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF94A3B8)),
                            fontSize = 9.sp,
                        ),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(50.dp)
                    .background(ColorProvider(Color(0xFF1E252D)))
                    .cornerRadius(12.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${data.goalCompletionPercent}%",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF84A98C)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "Goal Target",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF94A3B8)),
                            fontSize = 9.sp,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        Text(
            text = "TODAY'S HABITS",
            style = TextStyle(
                color = ColorProvider(Color(0xFF94A3B8)),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )

        Spacer(modifier = GlanceModifier.height(4.dp))

        val displayItems = data.items.take(3)
        displayItems.forEachIndexed { index, item ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}. ${item.title}",
                    style = TextStyle(
                        color = ColorProvider(if (item.isCompleted) Color(0xFF94A3B8) else Color(0xFFF1F5F9)),
                        fontSize = 11.sp,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )

                Button(
                    text = if (item.isCompleted) "DONE ✓" else "TOGGLE",
                    onClick = actionRunCallback<ToggleCommitmentActionCallback>(
                        actionParametersOf(ToggleCommitmentActionCallback.COMMITMENT_ID_PARAM to item.id),
                    ),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = ColorProvider(if (item.isCompleted) Color(0xFF52796F) else Color(0xFF2D3748)),
                        contentColor = ColorProvider(Color(0xFFF1F5F9)),
                    ),
                    modifier = GlanceModifier
                        .height(28.dp)
                        .cornerRadius(14.dp),
                )
            }
        }
    }
}
