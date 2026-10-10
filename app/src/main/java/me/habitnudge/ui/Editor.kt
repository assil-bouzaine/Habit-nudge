package me.habitnudge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full-screen editor used for new/edit reminder, recurring rule and note: close on the left,
 * title, Save on the right; the form scrolls underneath. Delete, when allowed, sits at the very bottom.
 */
@Composable
fun EditorScreen(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    onDelete: (() -> Unit)? = null,
    deleteLabel: String = "Delete",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    Button(onClick = onSave, enabled = saveEnabled) { Text("Save") }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    content()
                    if (onDelete != null) {
                        Spacer(Modifier.height(24.dp))
                        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/** The main text of the editor: large, borderless, focused on open when empty. */
@Composable
fun MessageField(value: String, onValueChange: (String) -> Unit, placeholder: String, minLines: Int = 1) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (value.isEmpty()) runCatching { focus.requestFocus() } }
    val style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        minLines = minLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).focusRequester(focus),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = style, color = MaterialTheme.colorScheme.outline)
                }
                inner()
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** One setting line: label on the left, whatever control on the right, a hairline below. */
@Composable
fun SettingRow(
    label: String,
    onClick: (() -> Unit)? = null,
    supporting: String? = null,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                supporting?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "Time   9:00 AM ›" — opens the wheel time picker. */
@Composable
fun TimeRow(label: String, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    SettingRow(label, onClick = { show = true }) {
        Text(formatMinute(minuteOfDay), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (show) {
        DigitalTimeDialog(minuteOfDay, onConfirm = { show = false; onPicked(it) }, onDismiss = { show = false })
    }
}

/** A number changed with − / + in [step]s, clamped to [min]..[max]; [unit] reads after the value. */
@Composable
fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValue: (Int) -> Unit,
    step: Int = 1,
    unit: (Int) -> String = { "" },
    supporting: String? = null,
) {
    SettingRow(label, supporting = supporting) {
        IconButton(onClick = { onValue((value - step).coerceAtLeast(min)) }, enabled = value > min) {
            Icon(Glyphs.Minus, contentDescription = "Less", modifier = Modifier.size(20.dp))
        }
        Text(
            "$value${unit(value).let { if (it.isEmpty()) "" else " $it" }}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        IconButton(onClick = { onValue((value + step).coerceAtMost(max)) }, enabled = value < max) {
            Icon(Icons.Filled.Add, contentDescription = "More", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null) {
    SettingRow(label, onClick = { onChange(!checked) }, supporting = supporting) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Small uppercase heading between groups of rows in an editor. */
@Composable
fun FormSection(text: String) {
    Spacer(Modifier.height(12.dp))
    SectionLabel(text)
}
