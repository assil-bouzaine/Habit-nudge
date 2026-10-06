package me.habitnudge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.habitnudge.app

/** Plan + Recurring under one tab, switched by a segmented control under the header. */
@Composable
fun RemindersScreen(day: Long, onDayChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.app
    var seg by remember { mutableStateOf(Segment.DAY) }

    // The header lives here (both halves render headerless below), so it reads the same data.
    val subtitle = when (seg) {
        Segment.DAY -> {
            val count by remember(day) { app.db.planned().forDay(day) }.collectAsState(initial = emptyList())
            when (count.size) {
                0 -> "Nothing planned yet"
                1 -> "1 reminder - long-press to select"
                else -> "${count.size} reminders - long-press to select"
            }
        }
        Segment.REPEAT -> {
            val rules by app.db.rules().all().collectAsState(initial = emptyList())
            if (rules.isEmpty()) "Reminders that repeat every day"
            else "${rules.count { it.enabled }} of ${rules.size} on"
        }
    }

    Column(modifier) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ScreenHeader("Reminders", subtitle)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SegmentedButton(
                selected = seg == Segment.DAY,
                onClick = { seg = Segment.DAY },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Day plan") }
            SegmentedButton(
                selected = seg == Segment.REPEAT,
                onClick = { seg = Segment.REPEAT },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Recurring") }
        }
        Spacer(Modifier.height(4.dp))
        when (seg) {
            Segment.DAY -> PlanScreen(day, onDayChange, Modifier.fillMaxSize(), showHeader = false)
            Segment.REPEAT -> RecurringScreen(Modifier.fillMaxSize(), showHeader = false)
        }
    }
}

private enum class Segment { DAY, REPEAT }
