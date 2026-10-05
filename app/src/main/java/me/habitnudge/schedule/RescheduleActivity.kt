package me.habitnudge.schedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.habitnudge.app
import me.habitnudge.data.AlertStyle
import me.habitnudge.data.Strictness
import me.habitnudge.ui.AppTheme
import me.habitnudge.ui.RescheduleDialog

/** Transparent screen behind a notification's "Reschedule" action; shows the "remind me later" picker. */
class RescheduleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                RescheduleDialog(onPick = ::apply, onDismiss = ::finish)
            }
        }
    }

    private fun apply(at: Long) {
        val app = applicationContext.app
        val i = intent
        val alertId = i.getLongExtra(EXTRA_ALERT_ID, -1L)
        app.scope.launch {
            val moved = if (alertId >= 0) {
                val alert = app.db.alerts().byId(alertId)
                if (alert != null) {
                    Engine.rescheduleReminder(
                        app, at, alertId = alert.id,
                        message = alert.message, style = alert.style, opensPlanner = alert.opensPlanner,
                    )
                }
                alert != null
            } else {
                Engine.rescheduleReminder(
                    app, at, gentleKey = i.getStringExtra(EXTRA_KEY),
                    message = i.getStringExtra(EXTRA_MESSAGE).orEmpty(),
                    style = AlertStyle(
                        strictness = runCatching { Strictness.valueOf(i.getStringExtra(EXTRA_STRICTNESS)!!) }
                            .getOrDefault(Strictness.GENTLE),
                        nagEveryMin = i.getIntExtra(EXTRA_NAG_EVERY, 5),
                        escalateAfterNags = i.getIntExtra(EXTRA_ESCALATE, -1).takeIf { it > 0 },
                        doneCountdownSec = i.getIntExtra(EXTRA_COUNTDOWN, 0),
                    ),
                    opensPlanner = i.getBooleanExtra(EXTRA_OPENS_PLANNER, false),
                )
                true
            }
            withContext(Dispatchers.Main) {
                val text = if (moved) "Moved to ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))}"
                else "That reminder is already done."
                Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    companion object {
        private const val EXTRA_ALERT_ID = "alertId"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_STRICTNESS = "strictness"
        private const val EXTRA_NAG_EVERY = "nagEvery"
        private const val EXTRA_ESCALATE = "escalate"
        private const val EXTRA_COUNTDOWN = "countdown"
        private const val EXTRA_OPENS_PLANNER = "opensPlanner"

        /** Sticky/Nagging/Takeover: everything else is read from the stored alert. */
        fun forAlert(context: Context, alertId: Long): Intent =
            Intent(context, RescheduleActivity::class.java)
                .setData(Uri.parse("habitnudge://reschedule/alert/$alertId"))
                .putExtra(EXTRA_ALERT_ID, alertId)

        /** Gentle reminders aren't stored, so the intent carries the whole reminder. */
        fun forGentle(context: Context, o: Occurrence): Intent =
            Intent(context, RescheduleActivity::class.java)
                .setData(Uri.parse("habitnudge://reschedule/gentle/" + Uri.encode(o.key)))
                .putExtra(EXTRA_KEY, o.key)
                .putExtra(EXTRA_MESSAGE, o.message)
                .putExtra(EXTRA_STRICTNESS, o.style.strictness.name)
                .putExtra(EXTRA_NAG_EVERY, o.style.nagEveryMin)
                .putExtra(EXTRA_ESCALATE, o.style.escalateAfterNags ?: -1)
                .putExtra(EXTRA_COUNTDOWN, o.style.doneCountdownSec)
                .putExtra(EXTRA_OPENS_PLANNER, o.opensPlanner)
    }
}
