package me.habitnudge.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.RadioButton
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
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Nudge", "A reality check when you open apps you want to use less") }
        item {
            AppCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    IconBadge(
                        if (serviceOn) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        if (serviceOn) SuccessGreen else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (serviceOn) "Nudge service is on" else "Nudge service is off",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "It only notices which app is in front; it never reads what's on screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!serviceOn) {
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }) { Text("Turn on") }
                        }
                    }
                }
            }
        }

        item { SectionLabel("Watched apps") }
        if (apps.isEmpty()) {
            item { EmptyState("\uD83D\uDCF1", "No apps yet", "Choose the apps you open without thinking.") }
        }
        items(apps, key = { it.packageName }) { a ->
            AppCard(Modifier.alpha(if (a.enabled) 1f else 0.6f), onClick = { editing = a }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.packageName)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.label, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill(if (a.style == NudgeStyle.CARD) "Card" else "Banner", MaterialTheme.colorScheme.primary)
                            Pill(
                                if (a.checkInMin > 0) "Check-in ${a.checkInMin} min" else "No check-ins",
                                MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    Switch(checked = a.enabled, onCheckedChange = { on ->
                        app.scope.launch { dao.upsertApp(a.copy(enabled = on)) }
                    })
                }
            }
        }
        item {
            FilledTonalButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (apps.isEmpty()) "Choose apps" else "Add or remove apps")
            }
        }

        item { SectionLabel("Messages") }
        item {
            AppCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Shown in turn. {app} becomes the app's name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
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
                        shape = MaterialTheme.shapes.medium,
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

        item { SectionLabel("Card") }
        item {
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Button(onClick = {
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
                    }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Preview the card") }
                }
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

/** The installed app's real launcher icon. */
@Composable
private fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val icon = remember(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(44.dp))
    } else {
        IconBadge(Icons.Filled.Face, MaterialTheme.colorScheme.primary, 44.dp)
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
                Text("Style when you open it", style = MaterialTheme.typography.titleSmall)
                for ((style, label) in listOf(NudgeStyle.CARD to "Card over the app", NudgeStyle.NOTIFICATION to "Notification banner")) {
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = a.style == style, onClick = { a = a.copy(style = style) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = a.style == style, onClick = null)
                        Text(label)
                    }
                }
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
