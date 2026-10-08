package me.habitnudge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

private const val MINUTE_MS = 60_000L

/** Rounds up to the next whole minute; planned reminders have minute precision. */
private fun ceilToMinute(millis: Long): Long = ((millis + MINUTE_MS - 1) / MINUTE_MS) * MINUTE_MS

/** The next time the clock shows [minuteOfDay]: today if still ahead, otherwise tomorrow. */
private fun nextAt(minuteOfDay: Int): Long {
    val zone = ZoneId.systemDefault()
    val now = LocalTime.now()
    val day = if (minuteOfDay > now.hour * 60 + now.minute) LocalDate.now() else LocalDate.now().plusDays(1)
    return day.atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()
}

/** "Remind me later": quick offsets or a picked time. [onPick] gets the new time in epoch millis. */
@Composable
fun RescheduleDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val options = listOf(15 to "In 15 minutes", 30 to "In 30 minutes", 60 to "In 1 hour", 120 to "In 2 hours")
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    var pickCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text("Remind me later") },
        text = {
            Column {
                Text(
                    "It comes back at the new time with the same strictness, and shows up in that day's plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                for ((minutes, label) in options) {
                    val at = ceilToMinute(now + minutes * MINUTE_MS)
                    OptionRow(label, fmt.format(Date(at))) { onPick(at) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                OptionRow("Pick a time…", "") { pickCustom = true }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickCustom) {
        val inAnHour = LocalTime.now().plusHours(1)
        DigitalTimeDialog(
            initialMinute = inAnHour.hour * 60 + inAnHour.minute,
            onConfirm = { onPick(nextAt(it)) },
            onDismiss = { pickCustom = false },
        )
    }
}

@Composable
private fun OptionRow(label: String, time: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(time, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
