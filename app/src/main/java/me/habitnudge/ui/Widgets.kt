package me.habitnudge.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.time.LocalDate
import java.time.ZoneId
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import me.habitnudge.data.AlertStyle
import me.habitnudge.data.Strictness

fun formatMinute(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

fun pickTime(context: Context, initialMinute: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        initialMinute / 60, initialMinute % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}

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
    val context = LocalContext.current
    FilledTonalButton(onClick = { pickTime(context, minuteOfDay, onPicked) }) {
        Text("$label ${formatMinute(minuteOfDay)}")
    }
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
    Strictness.GENTLE -> "Normal notification with sound"
    Strictness.STICKY -> "Pops up and stays until you tap Done"
    Strictness.NAGGING -> "Like Sticky, and alerts again until Done"
    Strictness.TAKEOVER -> "Full-screen card, wakes the phone like an alarm"
}

/** Strictness picker plus the options that apply to the chosen level. Shared by recurring and planned reminders. */
@Composable
fun StyleEditor(style: AlertStyle, onChange: (AlertStyle) -> Unit) {
    Text("Strictness", style = MaterialTheme.typography.titleSmall)
    for (level in Strictness.entries) {
        Row(
            Modifier.fillMaxWidth().selectable(
                selected = style.strictness == level,
                onClick = { onChange(style.copy(strictness = level)) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = style.strictness == level,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = level.color()),
            )
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(level.label(), style = MaterialTheme.typography.titleSmall, color = level.color())
                Text(level.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
