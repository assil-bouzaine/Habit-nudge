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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.habitnudge.app
import me.habitnudge.data.ActiveAlert
import me.habitnudge.schedule.Engine
import me.habitnudge.ui.AppTheme
import me.habitnudge.ui.Glyphs
import me.habitnudge.ui.RescheduleDialog

/** Full-screen card over everything, including the lock screen. Only Done closes it. */
class TakeoverActivity : ComponentActivity() {
    private var queue by mutableStateOf<List<ActiveAlert>>(emptyList())
    private var loaded = false
    /** Opened from Setup to show a look: sample alert, no tone, no database row, Done just closes. */
    private var preview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        preview = intent.getStringExtra(EXTRA_PREVIEW) != null
        if (preview) {
            Takeover.shownAt.remove(-1L) // restart the sample countdown each time
            queue = listOf(previewAlert(intent.getStringExtra(EXTRA_PREVIEW) == PREVIEW_NOTE))
        } else lifecycleScope.launch {
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

        if (!preview) me.habitnudge.data.DiagLog.add(this, "takeover card created")

        setContent {
            AppTheme {
                BackHandler(enabled = true) { /* Back doesn't dismiss a Takeover. */ }
                Box(Modifier.fillMaxSize().background(AlarmDeep)) {
                    queue.firstOrNull()?.let { alert ->
                        TakeoverCard(
                            alert,
                            more = queue.size - 1,
                            onDone = {
                                if (preview) finish()
                                else lifecycleScope.launch { Engine.done(applicationContext, alert.id) }
                            },
                            onReschedule = { at ->
                                if (preview) finish() else lifecycleScope.launch {
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
        // singleInstance: a real Takeover firing while a preview is up must replace the preview.
        if (preview && intent.getStringExtra(EXTRA_PREVIEW) == null) {
            setIntent(intent)
            recreate()
            return
        }
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
        if (preview) {
            finish()
            return
        }
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
        if (preview) return
        val top = queue.firstOrNull() ?: return
        // STARTED, not RESUMED: over EMUI's lock screen the card is visible but stays paused.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !Takeover.inCall(this)) {
            AlarmSound.ringFor(this, top.id)
        }
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
        const val PREVIEW_ALARM = "alarm"
        const val PREVIEW_NOTE = "note"

        fun preview(context: android.content.Context, note: Boolean) {
            context.startActivity(
                Intent(context, TakeoverActivity::class.java)
                    .putExtra(EXTRA_PREVIEW, if (note) PREVIEW_NOTE else PREVIEW_ALARM)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        private fun previewAlert(note: Boolean) = ActiveAlert(
            id = -1L,
            occurrenceKey = if (note) "note:preview" else "preview",
            dueAt = System.currentTimeMillis(),
            message = if (note) "Call grandma back. She asked about the photos from the trip."
            else "Drink a full glass of water",
            style = me.habitnudge.data.AlertStyle(me.habitnudge.data.Strictness.TAKEOVER, doneCountdownSec = 6),
            opensPlanner = false,
            timesAlerted = 3,
            escalated = !note,
        )
    }
}

// Plan/recurring reminders are an alarm: full-bleed red, a ringing glyph, a round Done that pulses.
private val AlarmRed = Color(0xFFD7263D)
private val AlarmDeep = Color(0xFF5C0A16)
private val OnAlarm = Color.White
private val OnAlarmMuted = Color.White.copy(alpha = 0.72f)

// Notes are a sticky note on a desk: cream ground, yellow paper, ink serif, a long pill that fills up.
private val Desk = Color(0xFFEFE8DA)
private val NoteYellow = Color(0xFFFFE57F)
private val Tape = Color(0x99FFFFFF)
private val Ink = Color(0xFF1F2328)
private val InkMuted = Color(0xFF6B655A)
private val Amber = Color(0xFFF2B33D)

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
    // Smooth between the 250ms ticks so the ring / fill glides instead of stepping.
    val target = if (totalSec <= 0 || remaining <= 0) 1f else (1f - remaining.toFloat() / totalSec).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, tween(900, easing = LinearEasing), label = "countdown")
    if (alert.occurrenceKey.startsWith("note:")) {
        NoteTakeover(alert, remaining, fraction, more, onDone, onReschedule = { rescheduling = true })
    } else {
        AlarmTakeover(alert, remaining, fraction, more, onDone, onReschedule = { rescheduling = true })
    }
}

/** Plan and recurring reminders: red alarm, huge time, message as a headline, round pulsing Done. */
@Composable
private fun AlarmTakeover(
    alert: ActiveAlert,
    remaining: Int,
    fraction: Float,
    more: Int,
    onDone: () -> Unit,
    onReschedule: () -> Unit,
) {
    val due = remember(alert.dueAt) { Date(alert.dueAt) }
    val time = remember(due) { SimpleDateFormat("h:mm", Locale.getDefault()).format(due) }
    val amPm = remember(due) { SimpleDateFormat("a", Locale.getDefault()).format(due) }
    val date = remember(due) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(due) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AlarmRed, Color(0xFFA3142A), AlarmDeep)))
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingingGlyph()
            Spacer(Modifier.width(10.dp))
            Text(
                "REMINDER",
                color = OnAlarm,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f),
            )
            if (more > 0) QueueChip(more, OnAlarm, Color.Black.copy(alpha = 0.2f))
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, color = OnAlarm, fontSize = 84.sp, lineHeight = 84.sp, fontWeight = FontWeight.Thin)
            Spacer(Modifier.width(8.dp))
            Text(amPm, color = OnAlarmMuted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 14.dp))
        }
        Text(date, color = OnAlarmMuted, fontSize = 16.sp)

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                alert.message,
                color = OnAlarm,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            if (alert.escalated) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Ignored ${alert.timesAlerted} times",
                    color = OnAlarm,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        PulsingDone(remaining, fraction, onDone, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(4.dp))
        Text(
            if (remaining > 0) "Done unlocks in ${remaining}s" else "Tap when it's done",
            color = OnAlarmMuted,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        LaterButton(remaining == 0, OnAlarm, onReschedule, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** The alarm glyph shaking like a bell, a short burst every second or so. */
@Composable
private fun RingingGlyph() {
    val shake by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1200
                0f at 0
                -16f at 60
                14f at 140
                -12f at 220
                10f at 300
                -6f at 380
                0f at 460
            },
        ),
        label = "shake",
    )
    Icon(Glyphs.Alarm, contentDescription = null, tint = OnAlarm, modifier = Modifier.size(26.dp).rotate(shake))
}

/**
 * Round Done. Locked: a ring around it fills as the countdown runs and the seconds show inside.
 * Unlocked: white button with rings radiating out of it, like an incoming call.
 */
@Composable
private fun PulsingDone(remaining: Int, fraction: Float, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val unlocked = remaining == 0
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "p",
    )
    val buttonSize = 112.dp
    Box(modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = buttonSize.toPx() / 2
            if (unlocked) {
                val reach = size.minDimension / 2 - r
                for (i in 0 until 3) {
                    val p = (pulse + i / 3f) % 1f
                    drawCircle(Color.White.copy(alpha = 0.30f * (1f - p)), radius = r + p * reach)
                }
            } else {
                val stroke = 5.dp.toPx()
                val ringR = r + 12.dp.toPx()
                val topLeft = Offset(center.x - ringR, center.y - ringR)
                val arcSize = Size(ringR * 2, ringR * 2)
                drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(
                    Color.White, -90f, 360f * fraction, false, topLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .size(buttonSize)
                .shadow(if (unlocked) 12.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(if (unlocked) Color.White else Color.White.copy(alpha = 0.14f))
                .clickable(enabled = unlocked, role = Role.Button, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            if (unlocked) {
                Text("Done", color = AlarmRed, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("$remaining", color = OnAlarm, fontSize = 40.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

/** Note reminders: a yellow sticky note taped to a cream desk, dropped in at a slight angle. */
@Composable
private fun NoteTakeover(
    alert: ActiveAlert,
    remaining: Int,
    fraction: Float,
    more: Int,
    onDone: () -> Unit,
    onReschedule: () -> Unit,
) {
    val time = remember(alert.dueAt) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alert.dueAt)) }
    // Drops in from above, slightly more tilted, then settles with a little bounce.
    val drop = remember(alert.id) { Animatable(0f) }
    LaunchedEffect(alert.id) {
        drop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Desk)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyphs.Notes, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("FROM YOUR NOTES", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(time, color = InkMuted, fontSize = 14.sp)
            }
            if (more > 0) QueueChip(more, Ink, Ink.copy(alpha = 0.08f))
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.graphicsLayer {
                    val t = drop.value
                    rotationZ = -2.5f - 7f * (1f - t)
                    translationY = -80.dp.toPx() * (1f - t)
                    alpha = t.coerceIn(0f, 1f)
                },
            ) {
                Column(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .shadow(10.dp, RoundedCornerShape(4.dp))
                        .background(NoteYellow, RoundedCornerShape(4.dp))
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        alert.message,
                        color = Ink,
                        fontSize = 26.sp,
                        lineHeight = 36.sp,
                        fontFamily = FontFamily.Serif,
                    )
                    if (alert.escalated) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "You've let this slide ${alert.timesAlerted} times.",
                            color = InkMuted,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
                // A strip of tape holding the note up.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 2.dp)
                        .rotate(3f)
                        .size(width = 96.dp, height = 26.dp)
                        .background(Tape, RoundedCornerShape(2.dp)),
                )
            }
        }

        FillingDone(remaining, fraction, onDone)
        Spacer(Modifier.height(8.dp))
        LaterButton(remaining == 0, Ink, onReschedule, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Long pill that fills with amber during the countdown, then turns ink-dark and says Done. */
@Composable
private fun FillingDone(remaining: Int, fraction: Float, onDone: () -> Unit) {
    val unlocked = remaining == 0
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(if (unlocked) Ink else Ink.copy(alpha = 0.08f))
            .clickable(enabled = unlocked, role = Role.Button, onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        if (!unlocked) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(fraction).background(Amber))
        }
        Text(
            if (unlocked) "Done" else "Done in ${remaining}s",
            color = if (unlocked) Desk else Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** "Remind me later", locked by the same countdown as Done so it isn't an instant escape. */
@Composable
private fun LaterButton(enabled: Boolean, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(
            "Remind me later",
            color = color.copy(alpha = if (enabled) 0.9f else 0.4f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** "1 of 3" when more Takeovers are waiting behind this one. */
@Composable
private fun QueueChip(more: Int, fg: Color, bg: Color) {
    Text(
        "1 of ${more + 1}",
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private fun secondsUntil(at: Long): Int =
    ((at - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
