package me.habitnudge.takeover

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import me.habitnudge.ui.AppTheme
import me.habitnudge.ui.BrandBlue
import me.habitnudge.ui.BrandBlueDark
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.habitnudge.app
import me.habitnudge.data.ActiveAlert
import me.habitnudge.schedule.Engine

/** Full-screen card over everything, including the lock screen. Only Done closes it. */
class TakeoverActivity : ComponentActivity() {
    private var queue by mutableStateOf<List<ActiveAlert>>(emptyList())
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch {
            app.db.alerts().takeoverQueueFlow().collect {
                queue = it
                loaded = true
                if (it.isEmpty()) {
                    AlarmSound.stop()
                    finish()
                } else {
                    ringIfVisible()
                }
            }
        }

        setContent {
            AppTheme {
                BackHandler(enabled = true) { /* Back doesn't dismiss a Takeover. */ }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BrandBlue, BrandBlueDark)))) {
                    queue.firstOrNull()?.let { alert ->
                        TakeoverCard(alert, more = queue.size - 1) {
                            lifecycleScope.launch { Engine.done(applicationContext, alert.id) }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setTurnScreenOn(true)
    }

    override fun onResume() {
        super.onResume()
        ringIfVisible()
        // Woken once; don't re-wake the screen every time the user presses power while it's up.
        window.decorView.post { setTurnScreenOn(false) }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing || isChangingConfigurations || (loaded && queue.isEmpty())) return
        val ctx = applicationContext
        if (Takeover.inCall(ctx)) {
            AlarmSound.stop()
            ctx.app.scope.launch { Engine.deferTakeovers(ctx) }
        } else if (getSystemService(PowerManager::class.java).isInteractive) {
            // Left via Home/recents without tapping Done: come back.
            Handler(Looper.getMainLooper()).postDelayed({ Takeover.launch(ctx) }, 1000)
        }
    }

    /** Backup only: the Engine starts the tone when the Takeover fires. Ringing is once per alert. */
    private fun ringIfVisible() {
        val top = queue.firstOrNull() ?: return
        // STARTED, not RESUMED: over EMUI's lock screen the card is visible but stays paused.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !Takeover.inCall(this)) {
            AlarmSound.ringFor(this, top.id)
        }
    }
}

@Composable
private fun TakeoverCard(alert: ActiveAlert, more: Int, onDone: () -> Unit) {
    val unlockAt = remember(alert.id) {
        Takeover.shownAt.getOrPut(alert.id) { System.currentTimeMillis() } + alert.style.doneCountdownSec * 1000L
    }
    var remaining by remember(alert.id) { mutableIntStateOf(secondsUntil(unlockAt)) }
    LaunchedEffect(alert.id) {
        while (remaining > 0) {
            delay(250)
            remaining = secondsUntil(unlockAt)
        }
    }

    val soft = Color.White.copy(alpha = 0.75f)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alert.dueAt)),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Light,
            color = soft,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            alert.message,
            color = Color.White,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (alert.escalated) {
            Spacer(Modifier.height(16.dp))
            Text(
                "You ignored this ${alert.timesAlerted} times.",
                style = MaterialTheme.typography.bodyLarge,
                color = soft,
            )
        }
        Spacer(Modifier.height(56.dp))
        Button(
            onClick = onDone,
            enabled = remaining == 0,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = BrandBlue,
                disabledContainerColor = Color.White.copy(alpha = 0.25f),
                disabledContentColor = Color.White.copy(alpha = 0.8f),
            ),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Text(if (remaining > 0) "Done in $remaining" else "Done", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (more > 0) {
            Spacer(Modifier.height(16.dp))
            Text("$more more after this", color = soft)
        }
    }
}
private fun secondsUntil(at: Long): Int =
    ((at - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
