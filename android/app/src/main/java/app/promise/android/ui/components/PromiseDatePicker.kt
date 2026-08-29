package app.promise.android.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val WheelItemHeight = 44.dp
private const val VisibleWheelItems = 5

object DateWheelBounds {
    fun daysInMonth(year: Int, month: Int): Int = YearMonth.of(year, month).lengthOfMonth()

    fun clampDay(year: Int, month: Int, day: Int): Int =
        day.coerceIn(1, daysInMonth(year, month))

    fun resolveDate(year: Int, month: Int, day: Int): LocalDate =
        LocalDate.of(year, month, clampDay(year, month, day))
}

@Composable
fun PromiseDateField(
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Choose date",
    allowClear: Boolean = false,
) {
    val colors = PromiseThemeColors.current
    var expanded by remember { mutableStateOf(date == null) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min)
                .clip(RoundedCornerShape(Radius.sm))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.sm))
                .background(colors.surfaceMuted)
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = date?.let { DateTimeFormat.formatDate(it) } ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (date != null) colors.textPrimary else colors.textSecondary,
            )
            Text(
                text = if (expanded) "Hide" else "Change",
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            PromiseDatePicker(
                value = date ?: LocalDate.now(),
                onDateChange = {
                    onDateChange(it)
                },
                enabled = enabled,
            )
            if (allowClear && date != null) {
                Text(
                    text = "Clear date",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .heightIn(min = TouchTarget.min)
                        .clickable(enabled = enabled) {
                            onDateChange(null)
                            expanded = false
                        },
                )
            }
        }
    }
}

@SuppressLint("NonObservableLocale")
@Composable
fun PromiseDatePicker(
    value: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    yearRange: IntRange = (LocalDate.now().year - 5)..(LocalDate.now().year + 15),
) {
    var year by remember(value) { mutableStateOf(value.year.coerceIn(yearRange)) }
    var month by remember(value) { mutableStateOf(value.monthValue) }
    var day by remember(value) { mutableStateOf(value.dayOfMonth) }

    fun emit(y: Int = year, m: Int = month, d: Int = day) {
        val next = DateWheelBounds.resolveDate(y, m, d)
        year = next.year
        month = next.monthValue
        day = next.dayOfMonth
        onDateChange(next)
    }

    val days = (1..DateWheelBounds.daysInMonth(year, month)).toList()
    val months = (1..12).toList()
    val years = yearRange.toList()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        WheelColumn(
            label = "Day",
            items = days.map { it.toString() },
            selectedIndex = (day - 1).coerceIn(0, days.lastIndex),
            onSelectedIndex = { emit(d = days[it]) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        WheelColumn(
            label = "Month",
            items = months.map {
                Month.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            },
            selectedIndex = month - 1,
            onSelectedIndex = { emit(m = months[it]) },
            enabled = enabled,
            modifier = Modifier.weight(1.2f),
        )
        WheelColumn(
            label = "Year",
            items = years.map { it.toString() },
            selectedIndex = years.indexOf(year).coerceAtLeast(0),
            onSelectedIndex = { emit(y = years[it]) },
            enabled = enabled,
            modifier = Modifier.weight(1.1f),
        )
    }
}

@Composable
fun PromiseTimeField(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PromiseThemeColors.current
    var expanded by remember { mutableStateOf(true) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min)
                .clip(RoundedCornerShape(Radius.sm))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.sm))
                .background(colors.surfaceMuted)
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = DateTimeFormat.formatTime(time),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (expanded) "Hide" else "Change",
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            PromiseClockPicker(
                value = time,
                onTimeChange = onTimeChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun PromiseClockPicker(
    value: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var hour12 by remember(value) { mutableStateOf(DateTimeFormat.toHour12(value)) }
    var minute by remember(value) { mutableStateOf(value.minute) }
    var isPm by remember(value) { mutableStateOf(DateTimeFormat.isPm(value)) }

    fun emit(h: Int = hour12, m: Int = minute, pm: Boolean = isPm) {
        hour12 = h
        minute = m
        isPm = pm
        onTimeChange(DateTimeFormat.toLocalTime(h, m, pm))
    }

    val hours = (1..12).toList()
    val minutes = (0..59).toList()
    val meridiems = listOf("AM", "PM")

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        WheelColumn(
            label = "Hour",
            items = hours.map { it.toString() },
            selectedIndex = hours.indexOf(hour12).coerceAtLeast(0),
            onSelectedIndex = { emit(h = hours[it]) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        WheelColumn(
            label = "Min",
            items = minutes.map { "%02d".format(it) },
            selectedIndex = minute,
            onSelectedIndex = { emit(m = minutes[it]) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        WheelColumn(
            label = "",
            items = meridiems,
            selectedIndex = if (isPm) 1 else 0,
            onSelectedIndex = { emit(pm = it == 1) },
            enabled = enabled,
            modifier = Modifier.weight(0.9f),
        )
    }
}

@Composable
private fun WheelColumn(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndex: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    itemHeight: Dp = WheelItemHeight,
) {
    val colors = PromiseThemeColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val wheelHeight = itemHeight * VisibleWheelItems
    val sidePad = itemHeight * ((VisibleWheelItems - 1) / 2)

    LaunchedEffect(items, selectedIndex) {
        val target = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        if (items.isNotEmpty()) {
            listState.scrollToItem(target)
        }
    }

    LaunchedEffect(listState, items) {
        snapshotFlow {
            val info = listState.layoutInfo
            val viewportCenter = info.viewportStartOffset +
                (info.viewportEndOffset - info.viewportStartOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                val itemCenter = item.offset + item.size / 2
                kotlin.math.abs(itemCenter - viewportCenter)
            }?.index
        }
            .distinctUntilChanged()
            .collect { index ->
                if (index != null && index in items.indices && index != selectedIndex && enabled) {
                    onSelectedIndex(index)
                }
            }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(wheelHeight)
                .clip(RoundedCornerShape(Radius.sm))
                .background(colors.surfaceMuted)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.sm)),
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = fling,
                userScrollEnabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = sidePad),
            ) {
                itemsIndexed(items) { index, labelText ->
                    val selected = index == selectedIndex
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) colors.accent else colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clickable(enabled = enabled) {
                                scope.launch { listState.animateScrollToItem(index) }
                                onSelectedIndex(index)
                            }
                            .padding(vertical = Spacing.xs)
                            .semantics { contentDescription = labelText },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(itemHeight)
                    .border(
                        width = 1.dp,
                        color = colors.accent.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(Radius.sm),
                    ),
            )
        }
    }
}
