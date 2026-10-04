package me.habitnudge.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val label: String, val icon: ImageVector) {
    PLAN("Plan", Icons.Filled.DateRange),
    RECURRING("Recurring", Icons.Filled.Refresh),
    SETUP("Setup", Icons.Filled.Settings),
}

@Composable
fun AppRoot(tab: Tab, onTab: (Tab) -> Unit, planDay: Long, onPlanDay: (Long) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTab(t) },
                        icon = { Icon(t.icon, contentDescription = null) },
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
            Tab.SETUP -> HealthScreen(content)
        }
    }
}
