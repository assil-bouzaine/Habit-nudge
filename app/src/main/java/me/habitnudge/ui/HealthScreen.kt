package me.habitnudge.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.style.TextOverflow
import me.habitnudge.notify.Notifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import me.habitnudge.takeover.TakeoverActivity
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import me.habitnudge.app
import me.habitnudge.data.DiagLog
import me.habitnudge.data.Strictness
import me.habitnudge.schedule.Engine

@Composable
fun HealthScreen(modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bumped on every resume (coming back from a settings screen) and after actions, to re-read state.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val prefs = context.app.prefs
    // Failing checks first, so what needs attention is at the top.
    val items = remember(refresh) {
        Health.items(context).sortedBy { it.ok ?: Health.isConfirmed(context, it) }
    }
    val problems = items.count { !(it.ok ?: Health.isConfirmed(context, it)) }
    val nextAt = remember(refresh) { prefs.nextAlarmAt }
    val nextLabel = remember(refresh) { prefs.nextAlarmLabel }
    // Start from the last level tested, so a restarted app doesn't silently fall back to Gentle.
    var testLevel by remember { mutableStateOf(prefs.testStrictness) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ScreenHeader(
                    "Setup",
                    "Make sure your reminders get through",
                    showGear = false,
                    leading = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusIcon(
                                if (problems == 0) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                if (problems == 0) SuccessGreen else MaterialTheme.colorScheme.error,
                                size = 28.dp,
                                description = if (problems == 0) "OK" else "Attention needed",
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    when (problems) {
                                        0 -> "You're all set"
                                        1 -> "1 thing needs attention"
                                        else -> "$problems things need attention"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    if (problems == 0) "Reminders can reach you." else "Fix the items marked below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusIcon(Icons.Filled.Notifications, MaterialTheme.colorScheme.primary, description = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Next alarm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (nextAt == 0L) "Nothing scheduled" else "${formatTime(nextAt)}  ·  $nextLabel",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Checks") }
        }
        items(items, key = { it.key }) { item ->
            HealthRow(item, refresh) { refresh++ }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Sound") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { AlarmSoundCard(refresh) { refresh++ } }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Try it") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SectionCard {
                    Column {
                        Text("Test reminder", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Fires in 1 minute: lock the phone and wait. A Nagging test repeats every minute and " +
                                "becomes a Takeover after 3 ignored alerts. A Takeover test's Done unlocks after 10 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        StrictnessPicker(testLevel) { testLevel = it }
                        Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (context.app.prefs.alertsPaused) {
                                Toast.makeText(context, "Alerts are paused — resume first.", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    Engine.scheduleTest(context, testLevel)
                                    refresh++
                                    Toast.makeText(context, "Test set. Lock the phone now.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Test ${testLevel.label()} in 1 minute") }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SectionCard {
                    Text("Takeover looks", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "See the full-screen card without waiting for an alert. Silent; Done closes it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { TakeoverActivity.preview(context, note = false) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Reminder") }
                        OutlinedButton(
                            onClick = { TakeoverActivity.preview(context, note = true) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Note") }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Reliability log") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                val log = remember(refresh) { DiagLog.read(context).takeLast(40).asReversed() }
                SectionCard {
                    Column {
                        Text(
                            "When each alarm was due vs. when it fired, newest first. LATE means over a minute late.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (log.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodySmall)
                        for (line in log) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (" LATE" in line) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (log.isNotEmpty()) {
                            TextButton(onClick = { DiagLog.clear(context); refresh++ }) { Text("Clear log") }
                        }
                    }
                }
            }
        }
    }
}

/** The sound Takeover rings with and Nagging alerts with, chosen with the phone's own sound picker. */
@Composable
private fun AlarmSoundCard(refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.app.prefs
    val current = remember(refresh) { prefs.alarmToneUri?.let(Uri::parse) }
    val name = remember(current) {
        current?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
            ?: "Phone's alarm sound"
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION") // the typed overload is API 33+
        val picked: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        // "Default" in the picker means follow the phone's alarm sound, which is what null stores.
        Notifier.setAlarmTone(context, picked.takeUnless { it == Settings.System.DEFAULT_ALARM_ALERT_URI })
        onChanged()
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(Glyphs.MusicNote, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Alarm sound", style = MaterialTheme.typography.titleMedium)
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(onClick = {
                picker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current ?: Settings.System.DEFAULT_ALARM_ALERT_URI),
                )
            }) { Text("Change") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Takeover rings with it, and Nagging alerts use it too (even on vibrate).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthRow(item: HealthItem, refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var confirmed by remember(item.key, refresh) { mutableStateOf(Health.isConfirmed(context, item)) }
    val good = item.ok ?: confirmed

    ListRow {
        StatusIcon(
            if (good) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            if (good) SuccessGreen else MaterialTheme.colorScheme.error,
            size = 24.dp,
            description = if (good) "OK" else "Needs attention",
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val open = {
                    if (!Health.openFix(context, item)) {
                        Toast.makeText(context, "Couldn't open that settings page.", Toast.LENGTH_SHORT).show()
                    }
                }
                if (good) FilledTonalButton(onClick = open) { Text("Settings") }
                else Button(onClick = open) { Text("Fix") }
                if (item.ok == null) {
                    Spacer(Modifier.width(8.dp))
                    Checkbox(checked = confirmed, onCheckedChange = {
                        confirmed = it
                        context.app.prefs.setConfirmed(item.key, it)
                        onChanged()
                    })
                    Text("Done", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}


fun Strictness.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
