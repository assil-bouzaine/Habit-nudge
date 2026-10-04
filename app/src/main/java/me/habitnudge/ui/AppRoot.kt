package me.habitnudge.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

enum class Tab(val label: String, val icon: ImageVector) {
    PLAN("Plan", Icons.Filled.DateRange),
    RECURRING("Recurring", Icons.Filled.Refresh),
    NUDGE("Nudge", Icons.Filled.Face),
    SETUP("Setup", Icons.Filled.Settings),
}

@Composable
fun AppRoot(tab: Tab, onTab: (Tab) -> Unit, planDay: Long, onPlanDay: (Long) -> Unit) {
    val context = LocalContext.current
    // Red dot on Setup when something the reminders depend on is off (e.g. EMUI disabled the nudge service).
    var setupProblem by remember { mutableStateOf(false) }
    LifecycleResumeEffect(tab) {
        setupProblem = Health.hasProblem(context)
        onPauseOrDispose {}
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTab(t) },
                        icon = {
                            if (t == Tab.SETUP && setupProblem) {
                                BadgedBox(badge = { Badge() }) { Icon(t.icon, contentDescription = null) }
                            } else {
                                Icon(t.icon, contentDescription = null)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding)
        when (tab) {
            Tab.PLAN -> PlanScreen(planDay, onPlanDay, content)
            Tab.RECURRING -> RecurringScreen(content)
            Tab.NUDGE -> NudgeScreen(content)
            Tab.SETUP -> HealthScreen(content)
        }
    }
}
