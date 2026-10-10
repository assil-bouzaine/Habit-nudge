package me.habitnudge.ui

import android.app.DatePickerDialog
import java.time.LocalDate
import java.time.ZoneId
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
 * The one time picker in the app: three scroll wheels (hour, minute, AM/PM) that snap to the middle row.
 * Hour and minute wrap around; AM/PM is just two rows.
 */
@Composable
fun DigitalTimeDialog(initialMinute: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val initH24 = initialMinute / 60
    var hour12 by remember { mutableIntStateOf(if (initH24 % 12 == 0) 12 else initH24 % 12) }
    var minute by remember { mutableIntStateOf(initialMinute % 60) }
    var isPm by remember { mutableStateOf(initH24 >= 12) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text("Pick a time") },
        text = {
            Box(Modifier.fillMaxWidth().height(WHEEL_ROW * WHEEL_VISIBLE), contentAlignment = Alignment.Center) {
                // The band marking the chosen row, behind the wheels.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ROW)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Wheel(
                        count = 12, initial = hour12 - 1, looping = true,
                        label = { "${it + 1}" }, onSelected = { hour12 = it + 1 },
                        modifier = Modifier.width(64.dp),
                    )
                    Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp))
                    Wheel(
                        count = 60, initial = minute, looping = true,
                        label = { String.format("%02d", it) }, onSelected = { minute = it },
                        modifier = Modifier.width(64.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Wheel(
                        count = 2, initial = if (isPm) 1 else 0, looping = false,
                        label = { if (it == 1) "PM" else "AM" }, onSelected = { isPm = it == 1 },
                        modifier = Modifier.width(64.dp),
                    )
                }
            }
        },
        confirmButton = {
            // 12 AM -> 0, 12 PM -> 12, 1-11 PM -> 13-23.
            TextButton(onClick = { onConfirm(((hour12 % 12) + if (isPm) 12 else 0) * 60 + minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val WHEEL_ROW = 44.dp
private const val WHEEL_VISIBLE = 5
/** Rows a looping wheel pretends to have, so it never visibly runs out in either direction. */
private const val LOOP_ROWS = 10_000

/**
 * A vertical wheel of [count] values. The row nearest the middle is the selection; flings snap to it,
 * and tapping a row scrolls it to the middle.
 */
@Composable
private fun Wheel(
    count: Int,
    initial: Int,
    looping: Boolean,
    label: (Int) -> String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = if (looping) LOOP_ROWS * count else count
    // Start a looping wheel in the middle of its fake length, on the right value.
    val start = if (looping) (LOOP_ROWS / 2) * count + initial else initial
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { WHEEL_ROW.toPx() }

    // Index of the row whose centre is closest to the wheel's centre.
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - mid) }?.index ?: start
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centered }.collect { onSelected(it % count) }
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        contentPadding = PaddingValues(vertical = WHEEL_ROW * (WHEEL_VISIBLE / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.height(WHEEL_ROW * WHEEL_VISIBLE),
    ) {
        items(total) { i ->
            // Fade and shrink rows by their distance from the middle, like a drum.
            val distance by remember {
                derivedStateOf {
                    val info = state.layoutInfo
                    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val item = info.visibleItemsInfo.firstOrNull { it.index == i }
                    if (item == null) 2f else (kotlin.math.abs(item.offset + item.size / 2f - mid) / rowPx).coerceAtMost(2f)
                }
            }
            Box(
                Modifier
                    .height(WHEEL_ROW)
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = null) {
                        scope.launch { state.animateScrollToItem(i) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(i % count),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (distance < 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - distance * 0.38f
                        val scale = 1f - distance * 0.12f
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }
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

/** Strictness picker plus the options that apply to the chosen level, as editor rows. */
@Composable
fun StyleEditor(style: AlertStyle, onChange: (AlertStyle) -> Unit) {
    FormSection("How hard to remind")
    StrictnessPicker(style.strictness) { onChange(style.copy(strictness = it)) }
    Spacer(Modifier.height(8.dp))
    when (style.strictness) {
        Strictness.NAGGING -> {
            StepperRow(
                "Alert again every", style.nagEveryMin, 1, 120,
                onValue = { onChange(style.copy(nagEveryMin = it)) }, unit = { "min" },
            )
            SwitchRow(
                "Escalate to Takeover",
                checked = style.escalateAfterNags != null,
                onChange = { onChange(style.copy(escalateAfterNags = if (it) 3 else null)) },
                supporting = "If I keep ignoring it",
            )
            style.escalateAfterNags?.let { n ->
                StepperRow(
                    "After ignoring it", n, 1, 50,
                    onValue = { onChange(style.copy(escalateAfterNags = it)) }, unit = { "×" },
                )
            }
        }
        Strictness.TAKEOVER -> {
            StepperRow(
                "Done unlocks after", style.doneCountdownSec, 0, 600, step = 5,
                onValue = { onChange(style.copy(doneCountdownSec = it)) }, unit = { "s" },
                supporting = "0 = at once",
            )
        }
        else -> {}
    }
}

/**
 * Launcher icons decoded off the main thread and kept for the process's life (a handful of watched apps,
 * ~36 KB each), so switching tabs doesn't decode them again.
 */
private val appIconCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

/** The app's launcher icon, or null while it loads (or if the app is gone). */
@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
    val context = LocalContext.current
    val icon by produceState(appIconCache[pkg], pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }
                    .getOrNull()
                    ?.also { appIconCache[pkg] = it }
            }
        }
    }
    return icon
}

/** An app's icon at [size], with a quiet rounded placeholder until it's ready. */
@Composable
fun AppIconImage(pkg: String, size: Dp) {
    val icon = rememberAppIcon(pkg)
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium))
    }
}
