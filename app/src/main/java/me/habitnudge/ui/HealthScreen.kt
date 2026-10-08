package me.habitnudge.ui

import android.widget.Toast
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
            Column(Modifier.padding(horizontal = 16.dp)) { SectionLabel("Try it") }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SectionCard {
                    Column {
                        Text("Test reminder", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Fires in 1 minute; lock the phone and wait. A Nagging test repeats every minute and " +
                                "becomes a Takeover after 3 ignored alerts. A Takeover test's Done unlocks after 10 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        for (level in TESTABLE) {
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = testLevel == level, onClick = { testLevel = level }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = testLevel == level,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = level.color()),
                                )
                                Text(level.label(), Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp), color = level.color())
                            }
                        }
                        Spacer(Modifier.height(8.dp))
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

private val TESTABLE = Strictness.entries

fun Strictness.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
