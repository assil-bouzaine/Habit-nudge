package me.habitnudge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import me.habitnudge.HabitApp
import me.habitnudge.app
import me.habitnudge.data.PlannedReminder
import me.habitnudge.schedule.Engine

fun today(): Long = LocalDate.now().toEpochDay()

/** Before 17:00 you're probably checking today; after that, planning tomorrow. */
fun defaultPlanDay(): Long = if (LocalTime.now().hour >= 17) today() + 1 else today()

private fun nowMinute(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

fun dayTitle(day: Long): String = when (day - today()) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE"))
}

/** For use mid-sentence: "today", "tomorrow", or a weekday name. */
private fun dayRef(day: Long): String = if (day - today() in 0L..1L) dayTitle(day).lowercase() else dayTitle(day)

@Composable
fun PlanScreen(day: Long, onDayChange: (Long) -> Unit, modifier: Modifier = Modifier, showHeader: Boolean = true) {
    val context = LocalContext.current
    val app = context.app
    val dao = app.db.planned()
    val reminders by remember(day) { dao.forDay(day) }.collectAsState(initial = emptyList())
    val previousCount by remember(day) { dao.countForDay(day - 1) }.collectAsState(initial = 0)
    var editing by remember { mutableStateOf<PlannedReminder?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    // Multi-select: long-press a reminder to start; selection is per day.
    var selected by remember(day) { mutableStateOf(emptySet<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val isToday = day == today()

    fun save(r: PlannedReminder) = app.scope.launch { dao.upsert(r); Engine.reschedule(app) }
    fun delete(r: PlannedReminder) = app.scope.launch { dao.delete(r); Engine.reschedule(app) }
    fun deleteSelected(ids: Set<Long>) = app.scope.launch { dao.deleteIds(ids.toList()); Engine.reschedule(app) }
    fun copy(replace: Boolean) = app.scope.launch { copyDay(app, day - 1, day, replace); Engine.reschedule(app) }
    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    BackHandler(enabled = selecting) { selected = emptySet() }

    Box(modifier) {
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (selecting || showHeader) {
                item {
                    if (selecting) {
                        SelectionBar(
                            count = selected.size,
                            allSelected = selected.size == reminders.size,
                            onClose = { selected = emptySet() },
                            onSelectAll = { selected = reminders.map { it.id }.toSet() },
                            onDelete = { confirmDelete = true },
                        )
                    } else {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            ScreenHeader(
                                "Your plan",
                                when (reminders.size) {
                                    0 -> "Nothing planned yet"
                                    1 -> "1 reminder"
                                    else -> "${reminders.size} reminders"
                                },
                            )
                        }
                    }
                }
            }
            item { DayStrip(day, onDayChange) }
            if (!isToday && previousCount > 0) {
                item {
                    OutlinedButton(
                        onClick = { if (reminders.isEmpty()) copy(replace = false) else confirmCopy = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(44.dp),
                    ) { Text("Copy ${dayRef(day - 1)}'s plan · $previousCount") }
                }
            }
            if (reminders.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        EmptyState(
                            Icons.Filled.DateRange,
                            "Nothing planned",
                            "Tap New to plan a reminder for ${dayRef(day)}.",
                        )
                    }
                }
            }
            items(reminders, key = { it.id }) { r ->
                val past = isToday && r.minuteOfDay <= nowMinute()
                val isSelected = r.id in selected
                ListRow(
                    modifier = Modifier.alpha(if (past) 0.55f else 1f),
                    onClick = { if (selecting) toggle(r.id) else editing = r },
                    onLongClick = { toggle(r.id) },
                ) {
                    Text(
                        formatMinute(r.minuteOfDay),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(64.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(r.message, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StrictnessLabel(r.style.strictness)
                            if (past) {
                                Text(
                                    "Past",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (selecting) {
                        Checkbox(checked = isSelected, onCheckedChange = { toggle(r.id) })
                    }
                }
            }
        }
        if (!selecting) {
            ExtendedFloatingActionButton(
                onClick = {
                    val nextHour = if (isToday) ((nowMinute() / 60) + 1).coerceAtMost(23) * 60 else 9 * 60
                    editing = PlannedReminder(epochDay = day, minuteOfDay = nextHour, message = "")
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (confirmDelete) {
        val ids = selected
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = { Text(if (ids.size == 1) "Delete 1 reminder?" else "Delete ${ids.size} reminders?") },
            text = { Text("They won't fire, and this can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteSelected(ids)
                    selected = emptySet()
                    confirmDelete = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    editing?.let { r ->
        PlannedEditor(
            initial = r,
            onSave = { save(it); editing = null },
            onDelete = if (r.id == 0L) null else ({ delete(r); editing = null }),
            onDismiss = { editing = null },
        )
    }

    if (confirmCopy) {
        AlertDialog(
            onDismissRequest = { confirmCopy = false },
            title = { Text("This day already has ${reminders.size} reminders") },
            text = { Text("Add ${dayRef(day - 1)}'s $previousCount reminders to them, or replace them?") },
            confirmButton = {
                TextButton(onClick = { copy(replace = false); confirmCopy = false }) { Text("Add") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { copy(replace = true); confirmCopy = false }) { Text("Replace") }
                    TextButton(onClick = { confirmCopy = false }) { Text("Cancel") }
                }
            },
        )
    }
}

/**
 * Two weeks of days from today as a scrollable strip (further out via the calendar button).
 * The chosen day is filled; today is marked in blue when it isn't the chosen one.
 */
@Composable
private fun DayStrip(day: Long, onDayChange: (Long) -> Unit) {
    val context = LocalContext.current
    val first = today()
    val days = remember(first, day) { (first..maxOf(first + 13, day)).toList() }
    val listState = rememberLazyListState()
    // Bring the chosen day into view (e.g. after picking one from the calendar), with a little left context.
    LaunchedEffect(day) {
        val index = (day - first).toInt()
        val info = listState.layoutInfo
        val fullyVisible = info.visibleItemsInfo.any {
            it.index == index && it.offset >= 0 && it.offset + it.size <= info.viewportEndOffset
        }
        if (!fullyVisible) listState.animateScrollToItem((index - 2).coerceAtLeast(0))
    }
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(days, key = { it }) { d -> DayChip(d, selected = d == day, isToday = d == first) { onDayChange(d) } }
        }
        IconButton(onClick = { pickDate(context, day, onDayChange) }, modifier = Modifier.padding(end = 4.dp)) {
            Icon(Icons.Filled.DateRange, contentDescription = "Pick a date", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayChip(day: Long, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val date = LocalDate.ofEpochDay(day)
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier
            .width(46.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isToday) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected || isToday) fg else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
            color = fg,
        )
    }
}

/** Replaces the header while selecting: close, count, select all, delete. */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Stop selecting") }
        Text("$count selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (!allSelected) TextButton(onClick = onSelectAll) { Text("Select all") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private suspend fun copyDay(app: HabitApp, from: Long, to: Long, replace: Boolean) {
    val dao = app.db.planned()
    app.db.withTransaction {
        if (replace) dao.deleteDay(to)
        for (r in dao.forDayOnce(from)) dao.upsert(r.copy(id = 0, epochDay = to))
    }
}

@Composable
private fun PlannedEditor(
    initial: PlannedReminder,
    onSave: (PlannedReminder) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var r by remember { mutableStateOf(initial) }
    val timeChanged = initial.id == 0L || r.minuteOfDay != initial.minuteOfDay
    val error = when {
        r.message.isBlank() -> "Write a message."
        r.epochDay == today() && timeChanged && r.minuteOfDay <= nowMinute() -> "That time has passed. Pick a later one."
        else -> null
    }

    EditorScreen(
        title = if (initial.id == 0L) "New reminder" else "Edit reminder",
        onDismiss = onDismiss,
        onSave = { onSave(r.copy(message = r.message.trim())) },
        saveEnabled = error == null,
        onDelete = onDelete,
    ) {
        MessageField(r.message, { r = r.copy(message = it) }, placeholder = "What do you need to do?")
        FormSection("When")
        SettingRow("Day") {
            Text(
                "${dayTitle(r.epochDay)} · ${LocalDate.ofEpochDay(r.epochDay).format(DateTimeFormatter.ofPattern("d MMM"))}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TimeRow("Time", r.minuteOfDay) { r = r.copy(minuteOfDay = it) }
        StyleEditor(r.style) { r = r.copy(style = it) }
        // Only shout once there's a message; an empty new form isn't an error yet.
        error?.takeIf { r.message.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
