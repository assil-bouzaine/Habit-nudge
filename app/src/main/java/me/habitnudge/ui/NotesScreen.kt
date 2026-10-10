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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun NotesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()

    val regularNotes by app.db.note().observeRegularNotes().collectAsState(initial = emptyList())
    val secretNotes by app.db.note().observeSecretNotes().collectAsState(initial = emptyList())

    var isSecretUnlocked by remember { mutableStateOf(NoteAuth.isAuthenticated(app.prefs)) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinEntryDialog by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    // The secret session is short (60s): once it lapses, re-lock even if the screen is still up.
    LaunchedEffect(isSecretUnlocked) {
        if (isSecretUnlocked) {
            val left = app.prefs.notesAuthenticatedUntil - System.currentTimeMillis()
            if (left > 0) {
                delay(left)
                isSecretUnlocked = NoteAuth.isAuthenticated(app.prefs)
            }
        }
    }

    fun onLockClick() {
        if (!NoteAuth.isPinConfigured(app.prefs)) {
            showPinSetupDialog = true
        } else if (isSecretUnlocked) {
            NoteAuth.clearSession(app.prefs)
            isSecretUnlocked = false
        } else {
            showPinEntryDialog = true
        }
    }

    fun openNewNote() {
        // In the private view a new note defaults to secret so it shows where you are.
        editingNote = Note(
            id = 0, content = "", isSecret = isSecretUnlocked,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        showNoteEditor = true
    }

    fun togglePause(note: Note) {
        val config = note.reminderConfig ?: return
        scope.launch {
            app.db.note().update(note.copy(reminderConfig = config.copy(isPaused = !config.isPaused)))
            Engine.reschedule(context)
        }
    }

    // Locked = regular notes only; unlocked = secret notes alone (never mixed).
    val visibleNotes = if (isSecretUnlocked) secretNotes else regularNotes

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Header: title + a discreet lock, then the Setup gear (from ScreenHeader).
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ScreenHeader("Notes", if (isSecretUnlocked) "Private" else null) {
                        IconButton(onClick = ::onLockClick) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Private notes",
                                modifier = Modifier.size(20.dp),
                                tint = if (isSecretUnlocked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            if (visibleNotes.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        if (isSecretUnlocked) {
                            EmptyState(
                                icon = Icons.Default.Lock,
                                title = "No private notes yet",
                                body = "Tap New to write one. It stays behind your PIN.",
                            )
                        } else {
                            EmptyState(
                                icon = Glyphs.Notes,
                                title = "No notes yet",
                                body = "Tap New to write one. Add a reminder to see it again later.",
                            )
                        }
                    }
                }
            } else {
                items(visibleNotes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { editingNote = note; showNoteEditor = true },
                        onDelete = { noteToDelete = note },
                        onTogglePause = { togglePause(note) },
                    )
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }

        // New note FAB: tap it, write, then choose regular/secret and reminders inside the editor.
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
                isSecretUnlocked = true
            },
            onDismiss = { showPinSetupDialog = false },
        )
    }

    if (showPinEntryDialog) {
        PinEntryDialog(
            onAuthenticate = { pin ->
                if (NoteAuth.authenticate(pin, app.prefs)) {
                    isSecretUnlocked = true
                    showPinEntryDialog = false
                    true
                } else {
                    false
                }
            },
            onDismiss = { showPinEntryDialog = false },
        )
    }

    if (showNoteEditor && editingNote != null) {
        NoteEditorDialog(
            note = editingNote,
            onNeedPin = {
                if (!NoteAuth.isPinConfigured(app.prefs)) showPinSetupDialog = true
            },
            onSave = { note ->
                scope.launch {
                    var willReschedule = note.reminderConfig != null
                    if (note.id == 0L) {
                        app.db.note().insert(note)
                    } else {
                        willReschedule = willReschedule || editingNote?.reminderConfig != null
                        app.db.note().update(note)
                    }
                    if (willReschedule) Engine.reschedule(context)
                    showNoteEditor = false
                    editingNote = null
                }
            },
            onDelete = if (editingNote?.id != 0L) ({ noteToDelete = editingNote }) else null,
            onDismiss = {
                showNoteEditor = false
                editingNote = null
            },
        )
    }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete note?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val hadReminder = noteToDelete?.reminderConfig != null
                            app.db.note().delete(noteToDelete!!)
                            if (hadReminder) Engine.reschedule(context)
                            noteToDelete = null
                            showNoteEditor = false
                            editingNote = null
                        }
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(note: Note, onClick: () -> Unit, onDelete: () -> Unit, onTogglePause: () -> Unit) {
    val config = note.reminderConfig
    ListRow(onClick = onClick, onLongClick = onDelete) {
        Column(Modifier.weight(1f)) {
            // One line only: the list is a list, the editor shows the full text.
            Text(
                note.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(formatRelativeTime(note.updatedAt))
                    when {
                        config == null -> {}
                        config.isPaused -> append(" · Reminder paused")
                        else -> {
                            append(" · ")
                            append(if (config.intervalDays == 1) "Daily" else "Every ${config.intervalDays} days")
                            if (config.timesPerDay > 1) {
                                append(", ${config.timesPerDay}× ${formatMinute(config.windowStartMin)}–${formatMinute(config.windowEndMin)}")
                            } else {
                                append(" at ${formatMinute(config.timeOfDay)}")
                            }
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (config != null) {
            TextButton(
                onClick = onTogglePause,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(if (config.isPaused) "Resume" else "Pause", style = MaterialTheme.typography.labelMedium)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete note",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
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
fun PinEntryDialog(onAuthenticate: (String) -> Boolean, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; error = false },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error,
                )
                if (error) {
                    Text(
                        "Incorrect PIN",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!onAuthenticate(pin)) { error = true; pin = "" }
                },
            ) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NoteEditorDialog(
    note: Note?,
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
        Spacer(Modifier.height(16.dp))
        SegmentedControl(
            options = listOf(false, true),
            selected = secretToggle,
            onSelect = { secret ->
                secretToggle = secret
                if (secret) onNeedPin()
            },
            label = { if (it) "Secret" else "Regular" },
        )
        if (secretToggle) {
            Text(
                "Hidden behind your PIN. Secret notes can't have reminders.",
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
