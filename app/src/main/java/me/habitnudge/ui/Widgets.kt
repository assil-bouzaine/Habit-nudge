package me.habitnudge.ui

import android.app.DatePickerDialog
import java.time.LocalDate
import java.time.ZoneId
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import me.habitnudge.data.AlertStyle
import me.habitnudge.data.Strictness

fun formatMinute(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** Calendar dialog; days before today can't be picked. [initialDay] and the result are epoch days. */
fun pickDate(context: Context, initialDay: Long, onPicked: (Long) -> Unit) {
    val d = LocalDate.ofEpochDay(initialDay)
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth -> onPicked(LocalDate.of(year, month + 1, dayOfMonth).toEpochDay()) },
        d.year, d.monthValue - 1, d.dayOfMonth,
    ).apply {
        datePicker.minDate = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.show()
}

@Composable
fun TimeButton(label: String, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    FilledTonalButton(onClick = { show = true }) {
        Text("$label ${formatMinute(minuteOfDay)}")
    }
    if (show) {
        DigitalTimeDialog(
            initialMinute = minuteOfDay,
            onConfirm = { show = false; onPicked(it) },
            onDismiss = { show = false },
        )
    }
}

/**
 * The one time picker in the app: 12-hour, typed, no analog clock.
 * Hour and minute are numeric fields plus an AM/PM switch.
 */
@Composable
fun DigitalTimeDialog(initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val initH24 = initialMinute / 60
    var hour by remember { mutableStateOf((if (initH24 % 12 == 0) 12 else initH24 % 12).toString()) }
    var minute by remember { mutableStateOf(String.format("%02d", initialMinute % 60)) }
    var isPm by remember { mutableStateOf(initH24 >= 12) }
    val h = hour.toIntOrNull()
    val m = minute.toIntOrNull()
    val valid = h != null && m != null && h in 1..12 && m in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text("Pick a time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") },
                        placeholder = { Text("2") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !valid,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(":", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        placeholder = { Text("30") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = !valid,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.width(96.dp),
                    )
                }
                SegmentedControl(
                    options = listOf(false, true),
                    selected = isPm,
                    onSelect = { isPm = it },
                    label = { if (it) "PM" else "AM" },
                )
                if (!valid) {
                    Text(
                        "Hour 1–12, minute 0–59.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            // 12 AM -> 0, 12 PM -> 12, 1-11 PM -> 13-23.
            TextButton(
                onClick = { onConfirm(((h!! % 12) + if (isPm) 12 else 0) * 60 + m!!) },
                enabled = valid,
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Integer field that only reports values within [min]..[max]; the text may be briefly invalid while typing. */
@Composable
fun NumberField(label: String, value: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val valid = text.toIntOrNull()?.let { it in min..max } == true
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it in min..max }?.let(onValue)
        },
        label = { Text(label) },
        isError = !valid,
        supportingText = { if (!valid) Text("$min to $max") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

fun Strictness.description(): String = when (this) {
    Strictness.GENTLE -> "A normal notification. Swipe it away and it's gone."
    Strictness.STICKY -> "Pops up and stays until you tap Done."
    Strictness.NAGGING -> "Like Sticky, and rings again every few minutes until Done."
    Strictness.TAKEOVER -> "Full-screen card that wakes the phone and rings like an alarm."
}

/** Four-way strictness switch with a one-line explanation of the chosen level. */
@Composable
fun StrictnessPicker(selected: Strictness, onSelect: (Strictness) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SegmentedControl(
            options = Strictness.entries,
            selected = selected,
            onSelect = onSelect,
            label = { it.label() },
            leading = { StrictnessDot(it, 6.dp) },
        )
        Text(
            selected.description(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Strictness picker plus the options that apply to the chosen level. Shared by recurring and planned reminders. */
@Composable
fun StyleEditor(style: AlertStyle, onChange: (AlertStyle) -> Unit) {
    Text("Strictness", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    StrictnessPicker(style.strictness) { onChange(style.copy(strictness = it)) }
    when (style.strictness) {
        Strictness.NAGGING -> {
            NumberField("Alert again every (minutes)", style.nagEveryMin, 1, 120) {
                onChange(style.copy(nagEveryMin = it))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = style.escalateAfterNags != null,
                    onCheckedChange = { onChange(style.copy(escalateAfterNags = if (it) 3 else null)) },
                )
                Text("Become a Takeover if I keep ignoring it")
            }
            style.escalateAfterNags?.let { n ->
                NumberField("...after this many ignored alerts", n, 1, 50) {
                    onChange(style.copy(escalateAfterNags = it))
                }
            }
        }
        Strictness.TAKEOVER -> {
            NumberField("Done unlocks after (seconds, 0 = at once)", style.doneCountdownSec, 0, 600) {
                onChange(style.copy(doneCountdownSec = it))
            }
        }
        else -> {}
    }
}
