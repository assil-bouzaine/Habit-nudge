package me.habitnudge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import me.habitnudge.app
import me.habitnudge.data.*
import me.habitnudge.schedule.Engine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun NotesScreen() {
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

    // Refresh auth state
    LaunchedEffect(Unit) {
        isSecretUnlocked = NoteAuth.isAuthenticated(app.prefs)
    }

    fun onUnlockClick() {
        if (!NoteAuth.isPinConfigured(app.prefs)) {
            showPinSetupDialog = true
        } else {
            showPinEntryDialog = true
        }
    }

    fun onNewNoteClick(secret: Boolean) {
        if (secret && !NoteAuth.isPinConfigured(app.prefs)) {
            showPinSetupDialog = true
        } else {
            editingNote = Note(
                id = 0,
                content = "",
                isSecret = secret,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            showNoteEditor = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader("Notes")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppCard(onClick = { onNewNoteClick(false) }) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New Note", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            item { SectionLabel("Regular Notes") }

            if (regularNotes.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "📝",
                        title = "No notes yet",
                        body = "Tap 'New Note' to create your first note"
                    )
                }
            } else {
                items(regularNotes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = {
                            editingNote = note
                            showNoteEditor = true
                        },
                        onDelete = { noteToDelete = note }
                    )
                }
            }

            item { SectionLabel("Secret Notes") }

            if (!isSecretUnlocked) {
                item {
                    AppCard(onClick = ::onUnlockClick) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Secret Notes", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = ::onUnlockClick) {
                                Text("Unlock Secret Notes")
                            }
                        }
                    }
                }
            } else {
                if (secretNotes.isEmpty()) {
                    item {
                        EmptyState(
                            emoji = "🔒",
                            title = "No secret notes",
                            body = "Create a secret note to keep private information secure"
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    item {
                        AppCard(onClick = { onNewNoteClick(true) }) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("New Secret Note", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                } else {
                    items(secretNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = {
                                editingNote = note
                                showNoteEditor = true
                            },
                            onDelete = { noteToDelete = note }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onPinSet = { pin ->
                NoteAuth.setPin(pin, app.prefs)
                showPinSetupDialog = false
                isSecretUnlocked = true
                NoteAuth.authenticate(pin, app.prefs)
            },
            onDismiss = { showPinSetupDialog = false }
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
            onDismiss = { showPinEntryDialog = false }
        )
    }

    if (showNoteEditor && editingNote != null) {
        NoteEditorDialog(
            note = editingNote,
            onSave = { note ->
                scope.launch {
                    val hasReminder = note.reminderConfig != null
                    if (note.id == 0L) {
                        app.db.note().insert(note)
                    } else {
                        app.db.note().update(note)
                    }
                    if (hasReminder || editingNote?.reminderConfig != null) {
                        Engine.reschedule(context)
                    }
                    showNoteEditor = false
                    editingNote = null
                }
            },
            onDelete = if (editingNote?.id != 0L) {
                { noteToDelete = editingNote }
            } else null,
            onDismiss = {
                showNoteEditor = false
                editingNote = null
            }
        )
    }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete note?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val hadReminder = noteToDelete?.reminderConfig != null
                            app.db.note().delete(noteToDelete!!)
                            if (hadReminder) {
                                Engine.reschedule(context)
                            }
                            noteToDelete = null
                            showNoteEditor = false
                            editingNote = null
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard(onClick = onClick, onLongClick = onDelete) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (note.isSecret) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Secret",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                note.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            note.reminderConfig?.let { config ->
                if (!config.isPaused) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Every ${config.intervalDays} day${if (config.intervalDays > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StrictnessPill(config.style.strictness)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Pill("Paused", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(
                formatRelativeTime(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
        hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
        else -> {
            val weeks = days / 7
            "$weeks week${if (weeks > 1) "s" else ""} ago"
        }
    }
}

@Composable
fun PinSetupDialog(
    onPinSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Set a PIN to protect your secret notes. You'll need this PIN to view them. There is no recovery if you forget it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        error = null
                    },
                    label = { Text("Enter PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = it
                        error = null
                    },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )

                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN must be at least 4 characters"
                        pin != confirmPin -> error = "PINs don't match"
                        else -> onPinSet(pin)
                    }
                }
            ) {
                Text("Set PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PinEntryDialog(
    onAuthenticate: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter your PIN to unlock secret notes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        error = false
                    },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error
                )

                if (error) {
                    Text(
                        "Incorrect PIN",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!onAuthenticate(pin)) {
                        error = true
                        pin = ""
                    }
                }
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NoteEditorDialog(
    note: Note?,
    onSave: (Note) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var content by remember { mutableStateOf(note?.content ?: "") }
    var isSecret by remember { mutableStateOf(note?.isSecret ?: false) }
    var hasReminder by remember { mutableStateOf(note?.reminderConfig != null) }
    var intervalDays by remember { mutableStateOf(note?.reminderConfig?.intervalDays ?: 1) }
    var timeOfDay by remember { mutableStateOf(note?.reminderConfig?.timeOfDay ?: (9 * 60)) }
    var isPaused by remember { mutableStateOf(note?.reminderConfig?.isPaused ?: false) }
    var strictness by remember { mutableStateOf(note?.reminderConfig?.style?.strictness ?: Strictness.GENTLE) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (note?.id == 0L) "New Note" else "Edit Note",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp),
                    maxLines = 10
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = isSecret,
                        onCheckedChange = {
                            isSecret = it
                            if (it) {
                                hasReminder = false
                            }
                        }
                    )
                    Text("Secret note (no reminders allowed)")
                }

                if (!isSecret) {
                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = hasReminder,
                            onCheckedChange = { hasReminder = it }
                        )
                        Text("Enable reminder")
                    }

                    if (hasReminder) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Every")
                                OutlinedTextField(
                                    value = intervalDays.toString(),
                                    onValueChange = {
                                        intervalDays = it.toIntOrNull()?.coerceIn(1, 365) ?: 1
                                    },
                                    modifier = Modifier.width(80.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Text("day${if (intervalDays > 1) "s" else ""}")
                            }

                            val hours = timeOfDay / 60
                            val minutes = timeOfDay % 60
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("At")
                                OutlinedTextField(
                                    value = String.format("%02d", hours),
                                    onValueChange = {
                                        val h = it.toIntOrNull()?.coerceIn(0, 23) ?: hours
                                        timeOfDay = h * 60 + minutes
                                    },
                                    modifier = Modifier.width(70.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Text(":")
                                OutlinedTextField(
                                    value = String.format("%02d", minutes),
                                    onValueChange = {
                                        val m = it.toIntOrNull()?.coerceIn(0, 59) ?: minutes
                                        timeOfDay = hours * 60 + m
                                    },
                                    modifier = Modifier.width(70.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }

                            Text("Strictness", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Strictness.entries.forEach { s ->
                                    FilterChip(
                                        selected = strictness == s,
                                        onClick = { strictness = s },
                                        label = { Text(s.label()) }
                                    )
                                }
                            }

                            if (note?.reminderConfig != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isPaused,
                                        onCheckedChange = { isPaused = it }
                                    )
                                    Text("Paused")
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onDelete != null) {
                        IconButton(onClick = {
                            onDismiss()
                            onDelete()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val reminderConfig = if (hasReminder && !isSecret) {
                                val nextDay = if (note?.reminderConfig != null) {
                                    note.reminderConfig.nextReminderEpochDay
                                } else {
                                    LocalDate.now().toEpochDay()
                                }
                                ReminderConfig(
                                    intervalDays = intervalDays,
                                    timeOfDay = timeOfDay,
                                    isPaused = isPaused,
                                    nextReminderEpochDay = nextDay,
                                    style = AlertStyle(strictness = strictness)
                                )
                            } else null

                            onSave(
                                Note(
                                    id = note?.id ?: 0,
                                    content = content,
                                    isSecret = isSecret,
                                    createdAt = note?.createdAt ?: System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                    reminderConfig = reminderConfig
                                )
                            )
                        },
                        enabled = content.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
