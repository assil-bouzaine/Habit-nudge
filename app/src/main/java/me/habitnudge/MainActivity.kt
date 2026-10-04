package me.habitnudge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.habitnudge.schedule.Engine
import me.habitnudge.ui.HealthScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Re-arm on every launch, in case EMUI dropped the alarm while the app was killed.
        lifecycleScope.launch { Engine.reschedule(applicationContext) }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    HealthScreen(Modifier.fillMaxSize())
                }
            }
        }
    }
}
