package me.habitnudge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import me.habitnudge.app
import me.habitnudge.data.AppDayStat
import me.habitnudge.nudge.NudgeService
import me.habitnudge.nudge.Stats

private fun minutes(ms: Long): Int = (ms / 60_000L).toInt()

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.app
    val today = LocalDate.now().toEpochDay()
    val week by remember(today) { app.db.stats().since(today - 6) }.collectAsState(initial = emptyList())
    val apps by app.db.nudge().appsFlow().collectAsState(initial = emptyList())
    val labels = apps.associate { it.packageName to it.label }
    var limit by remember { mutableIntStateOf(app.prefs.dailyLimitMin) }
    var streak by remember { mutableIntStateOf(0) }
    var editingLimit by remember { mutableStateOf(false) }
    var selectedDay by remember(today) { mutableLongStateOf(today) }

    // Count time in the app you're in right now, so "today" is up to date.
    LifecycleResumeEffect(Unit) {
        NudgeService.instance?.flushForeground()
        onPauseOrDispose {}
    }
    LaunchedEffect(week, limit) { streak = Stats.streak(app) }

    val byDay = week.groupBy { it.day }
    val todayRows = byDay[today].orEmpty().sortedByDescending { it.foregroundMs }
    val todayMin = minutes(todayRows.sumOf { it.foregroundMs })
    val weekOpens = week.sumOf { it.opens }
    val weekOuts = week.sumOf { it.getOuts }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Stats", "Time in your watched apps, honestly") }

        item {
            AppCard {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥", fontSize = 40.sp) // fire
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (streak == 1) "1 day" else "$streak days",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Text(
                                "in a row under $limit min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    val over = todayMin > limit
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            if (over) "$todayMin of $limit min - over by ${todayMin - limit}" else "$todayMin of $limit min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (todayMin.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                    TextButton(onClick = { editingLimit = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Change daily limit")
                    }
                }
            }
        }

        item { SectionLabel("Last 7 days") }
        item {
            val days = (today - 6..today).toList()
            val totals = days.associateWith { d -> minutes(byDay[d].orEmpty().sumOf { it.foregroundMs }) }
            val sel = byDay[selectedDay].orEmpty()
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${dayName(selectedDay, today)}  ·  ${totals[selectedDay] ?: 0} min  ·  ${sel.sumOf { it.opens }} opens",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Tap a day for details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    WeekBars(days, totals, limit, selectedDay, today) { selectedDay = it }
                }
            }
        }

        item {
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Text("This week", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (weekOpens == 0) "No opens of watched apps yet."
                        else "You opened watched apps $weekOpens times and chose \"Get me out\" $weekOuts times.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionLabel("Today by app") }
        if (todayRows.isEmpty()) {
            item { EmptyState("🌱", "Clean so far", "You haven't opened a watched app today.") }
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
                    Text("Total minutes per day across all watched apps. Days under it build your streak.")
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

private fun dayName(day: Long, today: Long): String = when (day) {
    today -> "Today"
    today - 1 -> "Yesterday"
    else -> LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

/** One series (minutes per day), one colour; dashed line at the limit. The selected day is full strength. */
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = (maxOf(limit, totals.values.maxOrNull() ?: 0) * 1.15f).coerceAtLeast(1f)
    val chartHeight = 140.dp

    Box(Modifier.fillMaxWidth().height(chartHeight)) {
        // Limit line.
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
                // Tap target is the whole column, wider and taller than the bar itself.
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(d) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .fillMaxHeight((v / maxValue).coerceAtLeast(if (v > 0) 0.02f else 0f))
                            .background(
                                if (d == selected) primary else primary.copy(alpha = 0.45f),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                            ),
                    )
                }
            }
        }
    }
    // Baseline labels.
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
    AppCard {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${row.opens} opens  ·  ${minutes(row.foregroundMs)} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Left ${row.getOuts}×", MaterialTheme.colorScheme.primary)
                    Pill("Stayed ${row.stays}×", MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
