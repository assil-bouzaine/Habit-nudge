package me.habitnudge.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.habitnudge.app
import me.habitnudge.data.AlertStyle
import me.habitnudge.data.Note
import me.habitnudge.data.NoteAuth
import me.habitnudge.data.ReminderConfig
import me.habitnudge.data.Strictness
import me.habitnudge.schedule.Engine
import java.time.LocalDate

private const val PRIVATE_OPEN_MS = 60_000L

private enum class NoteFilter(val label: String) { ALL("All"), REMINDERS("Reminders"), PRIVATE("Private") }

@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()

    val regularNotes by app.db.note().observeRegularNotes().collectAsState(initial = emptyList())
    val secretNotes by app.db.note().observeSecretNotes().collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(NoteFilter.ALL) }
    // Private notes are invisible until a *new* note whose whole text is the PIN is saved: that opens the
    // Private filter instead of saving anything. It closes on going back to All/Reminders, after a minute,
    // or when the app goes to the background.
    var unlocked by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    fun lock() {
        unlocked = false
        NoteAuth.clearSession(app.prefs)
        if (filter == NoteFilter.PRIVATE) filter = NoteFilter.ALL
    }
    fun unlock() {
        unlocked = true
        filter = NoteFilter.PRIVATE
    }
    LifecycleResumeEffect(Unit) { onPauseOrDispose { lock() } }
    LaunchedEffect(unlocked) {
        if (unlocked) {
            delay(PRIVATE_OPEN_MS)
            lock()
        }
    }

    fun select(f: NoteFilter) {
        if (f != NoteFilter.PRIVATE) lock()
        filter = f
    }

    fun openNewNote() {
        val now = System.currentTimeMillis()
        editingNote = Note(id = 0, content = "", isSecret = filter == NoteFilter.PRIVATE, createdAt = now, updatedAt = now)
    }

    fun togglePause(note: Note) {
        val config = note.reminderConfig ?: return
        scope.launch {
            app.db.note().update(note.copy(reminderConfig = config.copy(isPaused = !config.isPaused)))
            Engine.reschedule(context)
        }
    }

    val visible = when (filter) {
        NoteFilter.ALL -> regularNotes
        NoteFilter.REMINDERS -> regularNotes.filter { it.reminderConfig != null }
        NoteFilter.PRIVATE -> if (unlocked) secretNotes else emptyList()
    }
    val withReminders = regularNotes.count { it.reminderConfig?.isPaused == false }

    Box(modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp,
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                ScreenHeader(
                    "Notes",
                    when {
                        regularNotes.isEmpty() -> null
                        withReminders == 0 -> "${regularNotes.size} notes"
                        else -> "${regularNotes.size} notes · $withReminders with reminders"
                    },
                    trailing = {
                        if (unlocked) TextButton(onClick = ::lock) { Text("Lock") }
                    },
                )
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                FilterPills(filter, showPrivate = unlocked, onSelect = ::select)
            }

            if (visible.isEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    when (filter) {
                        NoteFilter.ALL -> EmptyState(Glyphs.Notes, "No notes yet", "Tap New to write one.")
                        NoteFilter.REMINDERS -> EmptyState(
                            Icons.Default.Notifications, "No reminders on notes",
                            "Open a note and turn on \"Remind me about this\".",
                        )
                        NoteFilter.PRIVATE -> EmptyState(Icons.Default.Lock, "No private notes", "Tap New to write one.")
                    }
                }
            } else {
                items(visible, key = { it.id }) { note ->
                    NoteTile(
                        note = note,
                        onClick = { editingNote = note },
                        onDelete = { noteToDelete = note },
                        onTogglePause = { togglePause(note) },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = ::openNewNote,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("New") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onPinSet = { pin ->
                NoteAuth.setPin(pin, app.prefs)
                NoteAuth.authenticate(pin, app.prefs)
                showPinSetupDialog = false
            },
            onDismiss = { showPinSetupDialog = false },
        )
    }

    editingNote?.let { editing ->
        NoteEditorDialog(
            note = editing,
            // The Regular/Private switch only exists while Private is open, or before there's any PIN to set.
            allowPrivate = unlocked || !NoteAuth.isPinConfigured(app.prefs),
            onNeedPin = {
                if (!NoteAuth.isPinConfigured(app.prefs)) showPinSetupDialog = true
            },
            onSave = { note ->
                // The secret knock: a brand-new regular note that is exactly the PIN opens Private and isn't saved.
                if (note.id == 0L && !note.isSecret && NoteAuth.isPinConfigured(app.prefs) &&
                    NoteAuth.authenticate(note.content.trim(), app.prefs)
                ) {
                    editingNote = null
                    unlock()
                    return@NoteEditorDialog
                }
                scope.launch {
                    val reschedule = note.reminderConfig != null || editing.reminderConfig != null
                    if (note.id == 0L) app.db.note().insert(note) else app.db.note().update(note)
                    if (reschedule) Engine.reschedule(context)
                    editingNote = null
                }
            },
            onDelete = if (editing.id != 0L) ({ noteToDelete = editing }) else null,
            onDismiss = { editingNote = null },
        )
    }

    noteToDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            title = { Text("Delete this note?") },
            text = { Text("It can't be brought back.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.db.note().delete(doomed)
                        if (doomed.reminderConfig != null) Engine.reschedule(context)
                        noteToDelete = null
                        editingNote = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { noteToDelete = null }) { Text("Cancel") } },
        )
    }
}

/** All / Reminders / Private as pills; the chosen one is filled with the text colour, like a tab. */
@Composable
private fun FilterPills(selected: NoteFilter, showPrivate: Boolean, onSelect: (NoteFilter) -> Unit) {
    Row(
        Modifier.padding(bottom = 6.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (f in NoteFilter.entries) {
            if (f == NoteFilter.PRIVATE && !showPrivate) continue
            val on = f == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(f) })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                if (f == NoteFilter.PRIVATE) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(f.label, style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

/**
 * A note as a card: first line as its title, a few lines of the rest, then a quiet footer with the
 * reminder (if any) and when it last changed. Long-press for pause/resume and delete.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteTile(note: Note, onClick: () -> Unit, onDelete: () -> Unit, onTogglePause: () -> Unit) {
    val config = note.reminderConfig
    val lines = note.content.trim().lines()
    val title = lines.firstOrNull().orEmpty()
    val body = lines.drop(1).joinToString("\n").trim()
    var menu by remember { mutableStateOf(false) }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(14.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (body.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (config != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (config.isPaused) Glyphs.BellOff else Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (config.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (config.isPaused) "Paused" else reminderSummary(config),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (config.isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(
                formatRelativeTime(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (config != null) {
                DropdownMenuItem(
                    text = { Text(if (config.isPaused) "Resume reminder" else "Pause reminder") },
                    onClick = { menu = false; onTogglePause() },
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/** "Daily · 9:00 AM", "Every 3 days · 9:00 AM", "5× daily · 7:00 AM–10:00 PM". */
private fun reminderSummary(config: ReminderConfig): String {
    val every = if (config.intervalDays == 1) "daily" else "every ${config.intervalDays} days"
    return if (config.timesPerDay > 1) {
        "${config.timesPerDay}× $every · ${formatMinute(config.windowStartMin)}–${formatMinute(config.windowEndMin)}"
    } else {
        "${every.replaceFirstChar { it.uppercase() }} · ${formatMinute(config.timeOfDay)}"
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes} min ago"
        hours < 24 -> "${hours} h ago"
        days < 2 -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
    }
}

@Composable
fun PinSetupDialog(onPinSet: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Secret notes stay on this phone under a PIN. There is no way to recover it if you forget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN must be at least 4 characters"
                        pin != confirm -> error = "PINs don't match"
                        else -> onPinSet(pin)
                    }
                },
            ) { Text("Set PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NoteEditorDialog(
    note: Note?,
    allowPrivate: Boolean,
    onNeedPin: () -> Unit,
    onSave: (Note) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf(note?.content ?: "") }
    var secretToggle by remember { mutableStateOf(note?.isSecret ?: false) }
    var hasReminder by remember { mutableStateOf(note?.reminderConfig != null) }
    var intervalDays by remember { mutableStateOf(note?.reminderConfig?.intervalDays ?: 1) }
    var timeOfDay by remember { mutableStateOf(note?.reminderConfig?.timeOfDay ?: (9 * 60)) }
    var timesPerDay by remember { mutableStateOf(note?.reminderConfig?.timesPerDay ?: 5) }
    var windowStart by remember { mutableStateOf(note?.reminderConfig?.windowStartMin ?: (7 * 60)) }
    var windowEnd by remember { mutableStateOf(note?.reminderConfig?.windowEndMin ?: (22 * 60)) }
    var isPaused by remember { mutableStateOf(note?.reminderConfig?.isPaused ?: false) }
    var style by remember { mutableStateOf(note?.reminderConfig?.style ?: AlertStyle(strictness = Strictness.GENTLE)) }
    // Several evenly spaced times a day inside a window, or once at a fixed time.
    val multi = timesPerDay > 1

    val windowOk = !multi || windowEnd > windowStart
    fun save() {
        val cfg = if (hasReminder && !secretToggle && (!multi || windowEnd > windowStart)) {
            val interval = intervalDays.coerceAtLeast(1)
            val today = LocalDate.now().toEpochDay()
            var startDay = note?.reminderConfig?.nextReminderEpochDay ?: today
            // First slot must be in the future: a past slot would be dropped
            // as late and the reminder would never start.
            val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            val tmp = ReminderConfig(
                intervalDays = interval,
                timeOfDay = timeOfDay,
                isPaused = isPaused,
                nextReminderEpochDay = startDay,
                style = style,
                timesPerDay = timesPerDay,
                windowStartMin = windowStart,
                windowEndMin = windowEnd,
            )
            while (startDay <= today) {
                if (startDay < today) {
                    startDay += interval
                    continue
                }
                val ahead = if (!multi) timeOfDay > nowMin
                else me.habitnudge.schedule.Occurrences
                    .noteSlots(note?.id ?: 0L, today, tmp).any { it > nowMin }
                if (ahead) break else startDay += interval
            }
            tmp.copy(nextReminderEpochDay = startDay)
        } else null
        onSave(
            Note(
                id = note?.id ?: 0,
                content = content,
                isSecret = secretToggle,
                createdAt = note?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                reminderConfig = cfg,
            ),
        )
    }

    EditorScreen(
        title = if (note?.id == 0L) "New note" else "Edit note",
        onDismiss = onDismiss,
        onSave = ::save,
        saveEnabled = content.isNotBlank() && (!hasReminder || secretToggle || windowOk),
        onDelete = onDelete?.let { del -> { onDismiss(); del() } },
    ) {
        MessageField(content, { content = it }, placeholder = "Write something worth remembering", minLines = 4)
        if (allowPrivate || secretToggle) Spacer(Modifier.height(16.dp))
        if (allowPrivate || secretToggle) SegmentedControl(
            options = listOf(false, true),
            selected = secretToggle,
            onSelect = { secret ->
                secretToggle = secret
                if (secret) onNeedPin()
            },
            label = { if (it) "Private" else "Regular" },
        )
        if (secretToggle) {
            Text(
                "Only opens with your PIN. Private notes can't have reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        } else {
            FormSection("Reminder")
            SwitchRow("Remind me about this", checked = hasReminder, onChange = { hasReminder = it })
            if (hasReminder) {
                StepperRow(
                    "Every", intervalDays, 1, 365,
                    onValue = { intervalDays = it }, unit = { if (it == 1) "day" else "days" },
                )
                SegmentedControl(
                    options = listOf(false, true),
                    selected = multi,
                    onSelect = { several -> if (!several) timesPerDay = 1 else if (!multi) timesPerDay = 5 },
                    label = { if (it) "Several times" else "Once a day" },
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (!multi) {
                    TimeRow("At", timeOfDay) { timeOfDay = it }
                } else {
                    TimeRow("From", windowStart) { windowStart = it }
                    TimeRow("Until", windowEnd) { windowEnd = it }
                    StepperRow(
                        "Times a day", timesPerDay, 2, 24,
                        onValue = { timesPerDay = it }, unit = { "×" },
                    )
                    // Show exactly when it will ring: the slots are evenly spaced, both ends included.
                    val slots = if (windowOk) me.habitnudge.schedule.Occurrences.evenSlots(windowStart, windowEnd, timesPerDay)
                    else emptyList()
                    Text(
                        if (!windowOk) "\"Until\" must be after \"From\"."
                        else "Spread evenly: " + slots.joinToString(" · ") { formatMinute(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (windowOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                StyleEditor(style) { style = it }
                if (note?.reminderConfig != null) {
                    SwitchRow("Pause reminders", checked = isPaused, onChange = { isPaused = it })
                }
            }
        }
    }
}
