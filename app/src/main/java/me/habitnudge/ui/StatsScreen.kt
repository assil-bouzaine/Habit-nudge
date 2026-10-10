package me.habitnudge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import me.habitnudge.app
import me.habitnudge.data.AppDayStat
import me.habitnudge.nudge.NudgeService
import me.habitnudge.nudge.Stats
import me.habitnudge.nudge.Stats.DayStatus

private fun minutes(ms: Long): Int = (ms / 60_000L).toInt()

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.app
    val today = LocalDate.now().toEpochDay()
    val weekAll by remember(today) { app.db.stats().since(today - 6) }.collectAsState(initial = emptyList())
    val apps by app.db.nudge().appsFlow().collectAsState(initial = emptyList())
    val labels = apps.associate { it.packageName to it.label }
    // Removed apps keep their rows but drop out of every total, streak and list below.
    val watched = remember(apps) { apps.map { it.packageName }.toSet() }
    val week = remember(weekAll, watched) { weekAll.filter { it.packageName in watched } }
    var limit by remember { mutableIntStateOf(app.prefs.dailyLimitMin) }
    var summary by remember { mutableStateOf<Stats.Summary?>(null) }
    var editingLimit by remember { mutableStateOf(false) }
    var selectedDay by remember(today) { mutableLongStateOf(today) }
    var calendarDay by remember(today) { mutableLongStateOf(today) }

    // Count time in the app you're in right now, so "today" is up to date.
    LifecycleResumeEffect(Unit) {
        NudgeService.instance?.flushForeground()
        onPauseOrDispose {}
    }
    LaunchedEffect(week, limit) { summary = Stats.summary(app) }

    val byDay = week.groupBy { it.day }
    val todayRows = byDay[today].orEmpty().sortedByDescending { it.foregroundMs }
    val todayMin = minutes(todayRows.sumOf { it.foregroundMs })
    val weekOpens = week.sumOf { it.opens }
    val weekOuts = week.sumOf { it.getOuts }
    val s = summary

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ScreenHeader("Stats", "Time in your watched apps")
            }
        }

        // Headline numbers in one quiet strip — no dashboard cards.
        item {
            val tracked = s?.trackedDays ?: 0
            val success = s?.successDays ?: 0
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                HeadlineStat("${s?.currentStreak ?: 0}", "day streak", Modifier.weight(1f))
                HeadlineStat("${s?.bestStreak ?: 0}", "best", Modifier.weight(1f))
                HeadlineStat(
                    "$success/$tracked",
                    if (tracked == 0) "days under" else "under · ${success * 100 / tracked}%",
                    Modifier.weight(1f),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        // Today.
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val over = todayMin > limit
                SectionCard {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Today", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        over -> "Over by ${todayMin - limit} min"
                                        else -> "${limit - todayMin} min left"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "$todayMin",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                " / $limit min",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (todayMin.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        TextButton(
                            onClick = { editingLimit = true },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) { Text("Change daily limit") }
                    }
                }
            }
        }

        // Success calendar.
        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Last ${Stats.CALENDAR_DAYS} days") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SectionCard {
                    Column {
                        val cal = s?.calendar.orEmpty()
                        val picked = cal.firstOrNull { it.day == calendarDay }
                        Text(
                            picked?.let { dayDetail(it) } ?: "Tap a day",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (cal.isNotEmpty()) SuccessCalendar(cal, calendarDay) { calendarDay = it }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendItem("✓", SuccessGreen, "Under limit")
                            LegendItem("✕", MaterialTheme.colorScheme.error, "Over limit")
                            LegendItem("", MaterialTheme.colorScheme.outline, "Not tracked")
                        }
                    }
                }
            }
        }

        // Last 7 days.
        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Last 7 days") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                val days = (today - 6..today).toList()
                val totals = remember(week) { days.associateWith { d -> minutes(byDay[d].orEmpty().sumOf { it.foregroundMs }) } }
                val sel = byDay[selectedDay].orEmpty()
                SectionCard {
                    Column {
                        Text(
                            "${dayName(selectedDay, today)}  ·  ${totals[selectedDay] ?: 0} min  ·  ${sel.sumOf { it.opens }} opens",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "Tap a bar for that day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        WeekBars(days, totals, limit, selectedDay, today) { selectedDay = it }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LegendSwatch(MaterialTheme.colorScheme.primary, "Under limit")
                            LegendSwatch(MaterialTheme.colorScheme.error, "Over limit")
                        }
                    }
                }
            }
        }

        item {
            val weekSummary = if (weekOpens == 0) "No opens of watched apps yet."
            else "You opened watched apps $weekOpens times and chose \"Get me out\" $weekOuts times" +
                " (${weekOuts * 100 / weekOpens}%)."
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("This week", style = MaterialTheme.typography.titleSmall)
                Text(
                    weekSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Today by app") }
        }
        if (todayRows.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    EmptyState(Icons.Filled.CheckCircle, "Clean so far", "You haven't opened a watched app today.")
                }
            }
        }
        items(todayRows, key = { it.packageName }) { row ->
            AppStatRow(row, labels[row.packageName])
        }
    }

    if (editingLimit) {
        var draft by remember { mutableIntStateOf(limit) }
        AlertDialog(
            onDismissRequest = { editingLimit = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = { Text("Daily limit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total minutes per day across all watched apps. Past days keep the limit they had.")
                    NumberField("Minutes", draft, 5, 600) { draft = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    limit = draft
                    app.prefs.dailyLimitMin = draft
                    editingLimit = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingLimit = false }) { Text("Cancel") } },
        )
    }
}

private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")

private fun dayDetail(r: Stats.DayResult): String {
    val date = LocalDate.ofEpochDay(r.day).format(shortDate)
    return when (r.status) {
        DayStatus.UNDER -> "$date · ${r.minutes} of ${r.limitMin} min · under limit"
        DayStatus.OVER -> "$date · ${r.minutes} of ${r.limitMin} min · over by ${r.minutes - r.limitMin}"
        DayStatus.TODAY -> "Today · ${r.minutes} of ${r.limitMin} min so far"
        DayStatus.NOT_TRACKED -> "$date · not tracked"
    }
}

private fun dayName(day: Long, today: Long): String = when (day) {
    today -> "Today"
    today - 1 -> "Yesterday"
    else -> LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

@Composable
private fun HeadlineStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/**
 * Month-style grid, Monday first. Status is shown by colour AND a glyph (✓ / ✕), never colour alone;
 * today has a blue ring. Tap a day for its numbers.
 */
@Composable
private fun SuccessCalendar(days: List<Stats.DayResult>, selected: Long, onSelect: (Long) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val leading = LocalDate.ofEpochDay(days.first().day).dayOfWeek.value - 1
    val cells: List<Stats.DayResult?> = List(leading) { null } + days

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in DayOfWeek.entries) {
                Text(
                    d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (week in cells.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val r = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (r != null) CalendarCell(r, r.day == selected) { onSelect(r.day) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(r: Stats.DayResult, selected: Boolean, onClick: () -> Unit) {
    val (bg, fg, glyph) = when (r.status) {
        DayStatus.UNDER -> Triple(SuccessGreen.copy(alpha = 0.16f), SuccessGreen, "✓")
        DayStatus.OVER -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.16f), MaterialTheme.colorScheme.error, "✕")
        DayStatus.TODAY -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, "")
        DayStatus.NOT_TRACKED -> Triple(Color.Transparent, MaterialTheme.colorScheme.outline, "")
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(bg)
            .then(
                when {
                    selected -> Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape)
                    else -> Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${LocalDate.ofEpochDay(r.day).dayOfMonth}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (r.status == DayStatus.TODAY) FontWeight.Bold else FontWeight.Normal,
            color = if (r.status == DayStatus.NOT_TRACKED) MaterialTheme.colorScheme.outline.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
        )
        if (glyph.isNotEmpty()) Text(glyph, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
    }
}

@Composable
private fun LegendItem(glyph: String, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.16f))
                .border(BorderStroke(1.dp, color.copy(alpha = 0.5f)), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) { if (glyph.isNotEmpty()) Text(glyph, color = color, fontSize = 10.sp, lineHeight = 10.sp) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One series (minutes per day). Bars over the limit turn red, explained by the legend under the chart;
 * the dashed line is the limit. The selected day is full strength, the others lighter.
 */
@Composable
private fun WeekBars(
    days: List<Long>,
    totals: Map<Long, Int>,
    limit: Int,
    selected: Long,
    today: Long,
    onSelect: (Long) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = (maxOf(limit, totals.values.maxOrNull() ?: 0) * 1.15f).coerceAtLeast(1f)

    Box(Modifier.fillMaxWidth().height(150.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * (1f - limit / maxValue)
            drawLine(
                color = muted.copy(alpha = 0.6f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
        }
        Text(
            "limit $limit",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            for (d in days) {
                val v = totals[d] ?: 0
                val base = if (v > limit) error else primary
                // The tap target is the whole column, wider and taller than the bar itself.
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(d) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .width(20.dp)
                            .fillMaxHeight((v / maxValue).coerceAtLeast(if (v > 0) 0.02f else 0f))
                            .background(
                                if (d == selected) base else base.copy(alpha = 0.45f),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (d in days) {
            Text(
                if (d == today) "Today" else LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = if (d == selected) MaterialTheme.colorScheme.onSurface else muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AppStatRow(row: AppDayStat, knownLabel: String?) {
    val context = LocalContext.current
    val pm = context.packageManager
    val label = knownLabel ?: remember(row.packageName) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(row.packageName, 0)).toString() }.getOrDefault(row.packageName)
    }
    val icon = remember(row.packageName) {
        runCatching { pm.getApplicationIcon(row.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    ListRow {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                "Left ${row.getOuts}× · stayed ${row.stays}×",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${minutes(row.foregroundMs)} min", style = MaterialTheme.typography.titleMedium)
            Text(
                "${row.opens} opens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
