package me.habitnudge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
            "${dayTitle(day)} · " + when (count.size) {
                0 -> "nothing planned"
                1 -> "1 reminder"
                else -> "${count.size} reminders"
            }
        }
        Segment.REPEAT -> {
            val rules by app.db.rules().all().collectAsState(initial = emptyList())
            if (rules.isEmpty()) "Nothing repeating yet"
            else "${rules.count { it.enabled }} of ${rules.size} on"
        }
    }

    Column(modifier) {
        // Same 8dp top as the LazyColumn tabs, so titles line up across tabs.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            ScreenHeader("Reminders", subtitle)
        }
        SegmentedControl(
            options = Segment.entries,
            selected = seg,
            onSelect = { seg = it },
            label = { it.label },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        when (seg) {
            Segment.DAY -> PlanScreen(day, onDayChange, Modifier.fillMaxSize(), showHeader = false)
            Segment.REPEAT -> RecurringScreen(Modifier.fillMaxSize(), showHeader = false)
        }
    }
}

private enum class Segment(val label: String) { DAY("Day plan"), REPEAT("Recurring") }
