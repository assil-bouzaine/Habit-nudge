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
import me.habitnudge.ui.RescheduleDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import me.habitnudge.ui.AppTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
                Box(Modifier.fillMaxSize().background(Scrim)) {
                    queue.firstOrNull()?.let { alert ->
                        TakeoverCard(
                            alert,
                            more = queue.size - 1,
                            onDone = { lifecycleScope.launch { Engine.done(applicationContext, alert.id) } },
                            onReschedule = { at ->
                                lifecycleScope.launch {
                                    Engine.rescheduleReminder(
                                        applicationContext, at, alertId = alert.id,
                                        message = alert.message, style = alert.style, opensPlanner = alert.opensPlanner,
                                    )
                                }
                            },
                        )
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

/** Dark alarm instrument, not an app screen: severity bar, message, countdown, one primary action. */
private val Scrim = Color(0xFF0A0D12)
private val ScrimMuted = Color.White.copy(alpha = 0.66f)
private val TakeoverRed = Color(0xFFE5484D)
/** Notes are the opposite: a full light paper sheet with a teal accent, unmistakably not an alarm. */
private val Paper = Color(0xFFF6F1E7)
private val Ink = Color(0xFF1B1E23)
private val InkMuted = Color(0xFF5C6570)
private val NoteTeal = Color(0xFF0E6B5E)

@Composable
private fun TakeoverCard(alert: ActiveAlert, more: Int, onDone: () -> Unit, onReschedule: (Long) -> Unit) {
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

    var rescheduling by remember(alert.id) { mutableStateOf(false) }
    if (rescheduling) {
        RescheduleDialog(onPick = { rescheduling = false; onReschedule(it) }, onDismiss = { rescheduling = false })
    }

    val totalSec = alert.style.doneCountdownSec.coerceAtLeast(0)
    val fraction =
        if (totalSec <= 0 || remaining <= 0) 1f else (1f - remaining.toFloat() / totalSec).coerceIn(0f, 1f)
    val time = remember(alert.dueAt) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alert.dueAt)) }
    if (alert.occurrenceKey.startsWith("note:")) {
        NoteTakeover(alert, time, remaining, fraction, more, onDone, onReschedule = { rescheduling = true })
    } else {
        AlarmTakeover(alert, time, remaining, fraction, more, onDone, onReschedule = { rescheduling = true })
    }
}

/** Plan and recurring reminders: full-bleed alarm with a clock face, message set like a headline. */
@Composable
private fun AlarmTakeover(
    alert: ActiveAlert,
    time: String,
    remaining: Int,
    fraction: Float,
    more: Int,
    onDone: () -> Unit,
    onReschedule: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF0A0D12), Color(0xFF111722), Color(0xFF191C24)),
            ),
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "⚠ TAKEOVER",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(TakeoverRed.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Text(
                    if (more > 0) "+$more pending" else "active now",
                    color = ScrimMuted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Reminder",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.2.sp,
                color = TakeoverRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                time,
                color = Color.White,
                fontSize = 56.sp,
                lineHeight = 62.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Pause. Breathe. Do this now.",
                color = ScrimMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(horizontal = 20.dp, vertical = 22.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    alert.message,
                    color = Color.White,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (alert.escalated) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "You ignored this ${alert.timesAlerted} times.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TakeoverRed.copy(alpha = 0.92f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            TakeoverActions(
                remaining, fraction, more, onDone, onReschedule,
                button = TakeoverRed, muted = ScrimMuted,
                track = Color.White.copy(alpha = 0.2f), outline = Color.White,
            )
        }
    }
}

/** Note reminders: full paper sheet, ink text, teal actions — a different object from the alarm. */
@Composable
private fun NoteTakeover(
    alert: ActiveAlert,
    time: String,
    remaining: Int,
    fraction: Float,
    more: Int,
    onDone: () -> Unit,
    onReschedule: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text(
                "Note · $time",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.2.sp,
                color = NoteTeal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Read it now and clear your head.",
                color = InkMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.72f))
                    .padding(horizontal = 18.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    alert.message,
                    color = Ink,
                    fontSize = 25.sp,
                    lineHeight = 33.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (alert.escalated) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "You ignored this ${alert.timesAlerted} times.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            TakeoverActions(
                remaining, fraction, more, onDone, onReschedule,
                button = NoteTeal, muted = InkMuted,
                track = Ink.copy(alpha = 0.12f), outline = Ink,
            )
        }
    }
}

/** Countdown bar, one strong Done, quiet Reschedule locked by the same countdown. */
@Composable
private fun TakeoverActions(
    remaining: Int,
    fraction: Float,
    more: Int,
    onDone: () -> Unit,
    onReschedule: () -> Unit,
    button: Color,
    muted: Color,
    track: Color,
    outline: Color,
) {
    val unlocked = remaining == 0
    if (!unlocked) {
        Text(
            "Done unlocks in $remaining s",
            style = MaterialTheme.typography.labelLarge,
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            color = button,
            trackColor = track,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
    Button(
        onClick = onDone,
        enabled = unlocked,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = button,
            contentColor = Color.White,
            disabledContainerColor = button.copy(alpha = 0.25f),
            disabledContentColor = Color.White.copy(alpha = 0.75f),
        ),
        modifier = Modifier.fillMaxWidth().height(60.dp),
    ) {
        Text(if (unlocked) "Done" else "Done in $remaining", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    // Same countdown as Done, so moving it later isn't an instant escape.
    OutlinedButton(
        onClick = onReschedule,
        enabled = unlocked,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, outline.copy(alpha = if (unlocked) 0.6f else 0.25f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = outline,
            disabledContentColor = outline.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Reschedule", fontSize = 17.sp) }
    if (more > 0) {
        Spacer(Modifier.height(12.dp))
        Text(
            "$more more after this",
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
private fun secondsUntil(at: Long): Int =
    ((at - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
