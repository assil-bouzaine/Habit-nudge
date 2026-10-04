package me.habitnudge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.habitnudge.data.Seed
import me.habitnudge.notify.Notifier
import me.habitnudge.schedule.Engine
import me.habitnudge.ui.AppRoot
import me.habitnudge.ui.Tab
import me.habitnudge.ui.defaultPlanDay
import me.habitnudge.ui.today

class MainActivity : ComponentActivity() {
    private var tab by mutableStateOf(Tab.PLAN)
    private var planDay by mutableLongStateOf(defaultPlanDay())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        lifecycleScope.launch {
            Seed.ifNeeded(app)
            // Re-arm on every launch, in case EMUI dropped the alarm or notifications while the app was killed.
            Engine.onAppStart(applicationContext)
        }
        setContent {
            MaterialTheme {
                AppRoot(tab = tab, onTab = { tab = it }, planDay = planDay, onPlanDay = { planDay = it })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** The evening "plan tomorrow" reminder opens straight into the planner. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(Notifier.EXTRA_OPEN_PLANNER, false) == true) {
            tab = Tab.PLAN
            planDay = today() + 1
        }
    }
}
