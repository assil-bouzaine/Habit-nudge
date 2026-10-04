package me.habitnudge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

private fun dayDate(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
fun PlanScreen(day: Long, onDayChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.app
    val dao = app.db.planned()
    val reminders by remember(day) { dao.forDay(day) }.collectAsState(initial = emptyList())
    val previousCount by remember(day) { dao.countForDay(day - 1) }.collectAsState(initial = 0)
    var editing by remember { mutableStateOf<PlannedReminder?>(null) }
    var confirmCopy by remember { mutableStateOf(false) }
    val isToday = day == today()

    fun save(r: PlannedReminder) = app.scope.launch { dao.upsert(r); Engine.reschedule(app) }
    fun delete(r: PlannedReminder) = app.scope.launch { dao.delete(r); Engine.reschedule(app) }
    fun copy(replace: Boolean) = app.scope.launch { copyDay(app, day - 1, day, replace); Engine.reschedule(app) }

    Box(modifier) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onDayChange(day - 1) }, enabled = day > today()) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dayTitle(day), style = MaterialTheme.typography.headlineSmall)
                        Text(dayDate(day), style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onDayChange(day + 1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
            }
            if (!isToday && previousCount > 0) {
                item {
                    OutlinedButton(
                        onClick = { if (reminders.isEmpty()) copy(replace = false) else confirmCopy = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Copy ${dayRef(day - 1)}'s plan ($previousCount) to this day") }
                }
            }
            if (reminders.isEmpty()) {
                item { Text("Nothing planned. Tap + to add a reminder.", Modifier.padding(top = 8.dp)) }
            }
            items(reminders, key = { it.id }) { r ->
                val past = isToday && r.minuteOfDay <= nowMinute()
                Card(Modifier.fillMaxWidth().alpha(if (past) 0.5f else 1f).clickable { editing = r }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMinute(r.minuteOfDay), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.message, style = MaterialTheme.typography.titleMedium)
                            Text(
                                r.style.strictness.label() + if (past) " - past" else "",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                val nextHour = if (isToday) ((nowMinute() / 60) + 1).coerceAtMost(23) * 60 else 9 * 60
                editing = PlannedReminder(epochDay = day, minuteOfDay = nextHour, message = "")
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add reminder") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New reminder - ${dayTitle(r.epochDay)}" else "Edit reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = r.message,
                    onValueChange = { r = r.copy(message = it) },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TimeButton("At", r.minuteOfDay) { r = r.copy(minuteOfDay = it) }
                StyleEditor(r.style) { r = r.copy(style = it) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(r.copy(message = r.message.trim())) }, enabled = error == null) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
