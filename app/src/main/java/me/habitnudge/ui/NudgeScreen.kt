package me.habitnudge.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.habitnudge.app
import me.habitnudge.data.NudgeApp
import me.habitnudge.data.NudgeMessage
import me.habitnudge.data.NudgeStyle
import me.habitnudge.nudge.NudgeService
import me.habitnudge.nudge.Nudges

@Composable
fun NudgeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.app
    val dao = app.db.nudge()
    val apps by dao.appsFlow().collectAsState(initial = emptyList())
    val messages by dao.messagesFlow().collectAsState(initial = emptyList())
    var serviceOn by remember { mutableStateOf(NudgeService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        serviceOn = NudgeService.isEnabled(context)
        onPauseOrDispose {}
    }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NudgeApp?>(null) }
    var newMessage by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(app.prefs.nudgeSeconds) }
    var getMeOut by remember { mutableStateOf(app.prefs.nudgeGetMeOut) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val on = apps.count { it.enabled }
                ScreenHeader(
                    "Nudge",
                    when {
                        !serviceOn -> "Service off · nothing is watched"
                        apps.isEmpty() -> "A reality check when you open an app"
                        else -> "Watching $on of ${apps.size} apps"
                    },
                )
            }
        }
        // Only worth a block when it's off; when on, the header subtitle says so.
        if (!serviceOn) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                            StatusIcon(Icons.Filled.Warning, MaterialTheme.colorScheme.error, description = "Off")
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("The nudge service is off", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Turn it on in Accessibility. It only notices which app is in front; it never reads the screen.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }) { Text("Turn on") }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Watched apps") }
        }
        if (apps.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    EmptyState(Glyphs.Hourglass, "No apps yet", "Choose the apps you open without thinking.")
                }
            }
        }
        items(apps, key = { it.packageName }) { a ->
            ListRow(
                modifier = Modifier.alpha(if (a.enabled) 1f else 0.6f),
                onClick = { editing = a },
            ) {
                AppIconImage(a.packageName, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (a.style == NudgeStyle.CARD) "Card" else "Banner") +
                            (if (a.checkInMin > 0) " · check in every ${a.checkInMin} min" else " · no check-ins"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = a.enabled, onCheckedChange = { on ->
                    app.scope.launch { dao.upsertApp(a.copy(enabled = on)) }
                })
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (apps.isEmpty()) {
                    Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Choose apps") }
                } else {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        Text("Add or remove apps")
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Messages") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SectionCard {
                    Text(
                        "Shown in turn. {app} becomes the app's name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    messages.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\u201C${m.text}\u201D", Modifier.weight(1f).padding(vertical = 10.dp))
                            IconButton(
                                onClick = { app.scope.launch { dao.deleteMessage(m) } },
                                enabled = messages.size > 1,
                            ) { Icon(Icons.Filled.Delete, contentDescription = "Delete message", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    OutlinedTextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        placeholder = { Text("Write a new message") },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    TextButton(
                        onClick = {
                            val text = newMessage.trim()
                            app.scope.launch { dao.insertMessage(NudgeMessage(text = text)) }
                            newMessage = ""
                        },
                        enabled = newMessage.isNotBlank(),
                    ) { Text("Add message") }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Card") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField("Fades after (seconds)", seconds, 2, 60) {
                            seconds = it
                            app.prefs.nudgeSeconds = it
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\"Get me out\" button", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = getMeOut, onCheckedChange = {
                                getMeOut = it
                                app.prefs.nudgeGetMeOut = it
                            })
                        }
                        OutlinedButton(onClick = {
                            if (app.prefs.alertsPaused) {
                                Toast.makeText(context, "Alerts are paused — resume first.", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            val service = NudgeService.instance
                            if (service == null) {
                                Toast.makeText(context, "Turn on the nudge service first.", Toast.LENGTH_SHORT).show()
                            } else {
                                // Preview as the first watched app, so the card shows its real icon.
                                val sample = apps.firstOrNull()
                                val label = sample?.label ?: "Instagram"
                                app.scope.launch {
                                    val msg = Nudges.nextMessage(app, label)
                                    withContext(Dispatchers.Main) { service.show(NudgeStyle.CARD, label, msg, sample?.packageName) }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Preview the card") }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Bedtime") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                BedtimeSettings(previewApp = apps.firstOrNull())
            }
        }
    }

    if (picking) {
        AppPicker(
            selected = apps.map { it.packageName }.toSet(),
            onDone = { chosen ->
                picking = false
                app.scope.launch {
                    val existing = apps.associateBy { it.packageName }
                    chosen.filter { it.packageName !in existing }.let { dao.upsertApps(it) }
                    apps.filter { a -> chosen.none { it.packageName == a.packageName } }.forEach { dao.deleteApp(it) }
                }
            },
            onDismiss = { picking = false },
        )
    }

    editing?.let { a ->
        NudgeAppEditor(
            initial = a,
            onSave = { app.scope.launch { dao.upsertApp(it) }; editing = null },
            onRemove = { app.scope.launch { dao.deleteApp(a) }; editing = null },
            onDismiss = { editing = null },
        )
    }
}

/** Bedtime mode: strictest treatment for watched apps between two times. */
@Composable
private fun BedtimeSettings(previewApp: NudgeApp?) {
    val context = LocalContext.current
    val prefs = context.app.prefs
    var enabled by remember { mutableStateOf(prefs.bedtimeEnabled) }
    var start by remember { mutableIntStateOf(prefs.bedtimeStartMin) }
    var end by remember { mutableIntStateOf(prefs.bedtimeEndMin) }
    var checkIn by remember { mutableIntStateOf(prefs.bedtimeCheckInMin) }
    var lock by remember { mutableIntStateOf(prefs.bedtimeStayLockSec) }

    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bedtime mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Late at night every open gets a full-screen card, and \"Stay anyway\" waits a few seconds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it; prefs.bedtimeEnabled = it })
            }
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeButton("From", start) { start = it; prefs.bedtimeStartMin = it }
                    Spacer(Modifier.width(8.dp))
                    TimeButton("to", end) { end = it; prefs.bedtimeEndMin = it }
                }
                NumberField("Check in every (minutes)", checkIn, 1, 60) { checkIn = it; prefs.bedtimeCheckInMin = it }
                NumberField("\"Stay anyway\" unlocks after (seconds)", lock, 0, 120) { lock = it; prefs.bedtimeStayLockSec = it }
                OutlinedButton(onClick = {
                    if (prefs.alertsPaused) {
                        Toast.makeText(context, "Alerts are paused — resume first.", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    val service = NudgeService.instance
                    if (service == null) {
                        Toast.makeText(context, "Turn on the nudge service first.", Toast.LENGTH_SHORT).show()
                    } else {
                        val label = previewApp?.label ?: "Instagram"
                        service.showBedtime(label, previewApp?.packageName, "It's late. $label can wait. Your sleep can't.")
                    }
                }, modifier = Modifier.fillMaxWidth().height(44.dp)) { Text("Preview bedtime card") }
            }
        }
    }
}

private data class Launchable(val packageName: String, val label: String)

/** Installed apps with a launcher icon, multi-select. */
@Composable
private fun AppPicker(selected: Set<String>, onDone: (List<NudgeApp>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<Launchable>?>(null) }
    val checked = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { Launchable(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apps to nudge") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = all
                if (list == null) {
                    Text("Loading apps...", Modifier.padding(16.dp))
                } else {
                    val shown = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(shown, key = { it.packageName }) { a ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (a.packageName in checked) checked.remove(a.packageName) else checked.add(a.packageName)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = a.packageName in checked, onCheckedChange = null)
                                Text(a.label, Modifier.padding(vertical = 12.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val byPkg = all.orEmpty().associateBy { it.packageName }
                onDone(checked.mapNotNull { pkg -> byPkg[pkg]?.let { NudgeApp(pkg, it.label) } })
            }, enabled = all != null) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NudgeAppEditor(initial: NudgeApp, onSave: (NudgeApp) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(initial.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("When you open it, show a", style = MaterialTheme.typography.titleSmall)
                SegmentedControl(
                    options = listOf(NudgeStyle.CARD, NudgeStyle.NOTIFICATION),
                    selected = a.style,
                    onSelect = { a = a.copy(style = it) },
                    label = { if (it == NudgeStyle.CARD) "Card" else "Banner" },
                )
                NumberField("\"Still here?\" while I stay (minutes, 0 = never)", a.checkInMin, 0, 240) {
                    a = a.copy(checkInMin = it)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(a) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
