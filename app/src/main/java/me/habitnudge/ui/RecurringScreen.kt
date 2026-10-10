package me.habitnudge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.habitnudge.app
import me.habitnudge.data.RecurringRule
import me.habitnudge.schedule.Engine

@Composable
fun RecurringScreen(modifier: Modifier = Modifier, showHeader: Boolean = true) {
    val context = LocalContext.current
    val app = context.app
    val rules by app.db.rules().all().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<RecurringRule?>(null) }

    // Writes run on the app scope so leaving the screen mid-save can't cancel them.
    fun save(rule: RecurringRule) = app.scope.launch {
        app.db.rules().upsert(rule)
        Engine.reschedule(app)
    }
    fun delete(rule: RecurringRule) = app.scope.launch {
        app.db.rules().delete(rule)
        Engine.reschedule(app)
    }

    Box(modifier) {
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (showHeader) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        val on = rules.count { it.enabled }
                        ScreenHeader("Recurring", if (rules.isEmpty()) "Reminders that repeat every day" else "$on of ${rules.size} on")
                    }
                }
            }
            if (rules.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        EmptyState(Glyphs.Repeat, "Nothing repeating", "Things like \"drink water\" every 90 minutes.")
                    }
                }
            }
            items(rules, key = { it.id }) { rule ->
                ListRow(
                    modifier = Modifier.alpha(if (rule.enabled) 1f else 0.6f),
                    onClick = { editing = rule },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.message, style = MaterialTheme.typography.titleMedium)
                        Text(
                            rule.timeSummary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StrictnessLabel(rule.style.strictness)
                            if (rule.opensPlanner) {
                                Text(
                                    "Opens plan",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = rule.enabled, onCheckedChange = { save(rule.copy(enabled = it)) })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = RecurringRule(message = "", startMinute = 9 * 60, endMinute = 21 * 60, intervalMin = 60) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { rule ->
        RuleEditor(
            initial = rule,
            onSave = { save(it); editing = null },
            onDelete = if (rule.id == 0L) null else ({ delete(rule); editing = null }),
            onDismiss = { editing = null },
        )
    }
}

private fun RecurringRule.timeSummary(): String {
    val time = if (intervalMin == null) formatMinute(startMinute)
    else "Every $intervalMin min · ${formatMinute(startMinute)}–${formatMinute(endMinute)}"
    return "$time · ${daysSummary(daysMask)}"
}

/** "Every day", "Weekdays", "Weekends", or a list like "Mon, Wed, Fri". */
fun daysSummary(mask: Int): String = when (mask) {
    RecurringRule.EVERY_DAY -> "Every day"
    RecurringRule.WEEKDAYS -> "Weekdays"
    RecurringRule.WEEKENDS -> "Weekends"
    else -> DayOfWeek.entries.filter { mask and RecurringRule.dayBit(it) != 0 }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

/** One round toggle per weekday (Monday first), plus quick presets underneath. */
@Composable
private fun DaysPicker(mask: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (day in DayOfWeek.entries) {
                val bit = RecurringRule.dayBit(day)
                val on = mask and bit != 0
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onChange(mask xor bit) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((preset, name) in listOf(
                RecurringRule.EVERY_DAY to "Every day",
                RecurringRule.WEEKDAYS to "Weekdays",
                RecurringRule.WEEKENDS to "Weekends",
            )) {
                FilterChip(selected = mask == preset, onClick = { onChange(preset) }, label = { Text(name) })
            }
        }
    }
}

@Composable
private fun RuleEditor(
    initial: RecurringRule,
    onSave: (RecurringRule) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var rule by remember { mutableStateOf(initial) }
    val repeating = rule.intervalMin != null
    val error = when {
        rule.message.isBlank() -> "Write a message."
        repeating && rule.endMinute <= rule.startMinute -> "End must be after start."
        rule.daysMask == 0 -> "Pick at least one day."
        else -> null
    }

    EditorScreen(
        title = if (initial.id == 0L) "New recurring" else "Edit recurring",
        onDismiss = onDismiss,
        onSave = { onSave(rule.copy(message = rule.message.trim())) },
        saveEnabled = error == null,
        onDelete = onDelete,
    ) {
        MessageField(rule.message, { rule = rule.copy(message = it) }, placeholder = "What should repeat?")
        FormSection("When")
        SegmentedControl(
            options = listOf(false, true),
            selected = repeating,
            onSelect = { rep ->
                rule = if (rep) {
                    if (repeating) rule
                    else rule.copy(intervalMin = 90, endMinute = maxOf(rule.endMinute, (rule.startMinute + 60).coerceAtMost(23 * 60 + 59)))
                } else rule.copy(intervalMin = null, endMinute = rule.startMinute)
            },
            label = { if (it) "Through the day" else "Once a day" },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (repeating) {
            TimeRow("From", rule.startMinute) { rule = rule.copy(startMinute = it) }
            TimeRow("Until", rule.endMinute) { rule = rule.copy(endMinute = it) }
            StepperRow(
                "Every", rule.intervalMin ?: 90, 5, 720, step = 15,
                onValue = { rule = rule.copy(intervalMin = it) }, unit = { "min" },
            )
        } else {
            TimeRow("At", rule.startMinute) { rule = rule.copy(startMinute = it, endMinute = it) }
        }
        FormSection("Days")
        DaysPicker(rule.daysMask) { rule = rule.copy(daysMask = it) }
        SwitchRow(
            "Opens tomorrow's plan",
            checked = rule.opensPlanner,
            onChange = { rule = rule.copy(opensPlanner = it) },
            supporting = "When you tap the reminder",
        )
        StyleEditor(rule.style) { rule = rule.copy(style = it) }
        error?.takeIf { rule.message.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
    }
}
