package me.habitnudge.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

enum class Tab(val label: String, val icon: ImageVector) {
    REMINDERS("Reminders", Icons.Filled.Notifications),
    NUDGE("Nudge", Icons.Filled.Face),
    STATS("Stats", Icons.Filled.Star),
    NOTES("Notes", Icons.Filled.Edit),
}

/** How to reach Setup from any screen header, plus whether it has something to warn about. */
class SetupNav(val open: () -> Unit, val problem: Boolean)

val LocalSetup = staticCompositionLocalOf<SetupNav?> { null }

@Composable
fun AppRoot(tab: Tab, onTab: (Tab) -> Unit, planDay: Long, onPlanDay: (Long) -> Unit) {
    val context = LocalContext.current
    // Red dot on the gear when something the reminders depend on is off (e.g. EMUI disabled the nudge service).
    var setupProblem by remember { mutableStateOf(false) }
    // Setup is a pushed screen now, not a tab.
    var showSetup by remember { mutableStateOf(false) }
    LifecycleResumeEffect(tab, showSetup) {
        setupProblem = Health.hasProblem(context)
        onPauseOrDispose {}
    }

    BackHandler(enabled = showSetup) { showSetup = false }

    CompositionLocalProvider(LocalSetup provides SetupNav(open = { showSetup = true }, problem = setupProblem)) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    for (t in Tab.entries) {
                        NavigationBarItem(
                            selected = tab == t && !showSetup,
                            onClick = { showSetup = false; onTab(t) },
                            icon = {
                                Icon(t.icon, contentDescription = null)
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val content = Modifier.fillMaxSize().padding(padding)
            when {
                showSetup -> HealthScreen(content, onClose = { showSetup = false })
                else -> when (tab) {
                    Tab.REMINDERS -> RemindersScreen(planDay, onPlanDay, content)
                    Tab.NUDGE -> NudgeScreen(content)
                    Tab.STATS -> StatsScreen(content)
                    Tab.NOTES -> NotesScreen(content)
                }
            }
        }
    }
}
