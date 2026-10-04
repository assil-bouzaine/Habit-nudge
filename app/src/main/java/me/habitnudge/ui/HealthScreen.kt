package me.habitnudge.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import me.habitnudge.app
import me.habitnudge.data.Strictness
import me.habitnudge.schedule.Engine

@Composable
fun HealthScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Bumped on every resume (coming back from a settings screen) and after actions, to re-read state.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val items = remember(refresh) { Health.items(context) }
    val prefs = context.app.prefs
    val nextAt = remember(refresh) { prefs.nextAlarmAt }
    val nextLabel = remember(refresh) { prefs.nextAlarmLabel }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Setup & health", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(4.dp))
            Text(
                if (nextAt == 0L) "No reminder scheduled."
                else "Next alarm: ${formatTime(nextAt)} - $nextLabel",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(items, key = { it.key }) { item ->
            HealthRow(item, refresh) { refresh++ }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Test reminder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Fires in 1 minute. Lock the phone and wait for it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.padding(4.dp))
                    Button(onClick = {
                        scope.launch {
                            Engine.scheduleTest(context, Strictness.GENTLE)
                            refresh++
                            Toast.makeText(context, "Test set. Lock the phone now.", Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Test Gentle reminder in 1 minute") }
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
    val colors = if (good) CardDefaults.cardColors()
    else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)

    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (good) "OK" else "!", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(12.dp))
                Text(item.title, style = MaterialTheme.typography.titleMedium)
            }
            Text(item.detail, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.padding(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    if (!Health.openFix(context, item)) {
                        Toast.makeText(context, "Couldn't open that settings page.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text(if (good) "Open settings" else "Fix") }
                if (item.ok == null) {
                    Spacer(Modifier.width(8.dp))
                    Checkbox(checked = confirmed, onCheckedChange = {
                        confirmed = it
                        context.app.prefs.setConfirmed(item.key, it)
                        onChanged()
                    })
                    Text("Done")
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
