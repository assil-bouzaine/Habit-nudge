package me.habitnudge.nudge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import me.habitnudge.HabitApp
import me.habitnudge.notify.Notifier

object Nudges {
    const val DEFAULT_MESSAGE = "{app} again? You said you'd stop. Prove it."

    /** The next message in rotation, with {app} filled in. */
    suspend fun nextMessage(app: HabitApp, label: String): String {
        val messages = app.db.nudge().messages().map { it.text }.ifEmpty { listOf(DEFAULT_MESSAGE) }
        val i = app.prefs.nudgeMessageIndex
        app.prefs.nudgeMessageIndex = i + 1
        return messages[i.mod(messages.size)].replace("{app}", label)
    }
}

/** "Get me out" on a nudge or "Still here?" notification. */
class NudgeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.cancelNudge(context)
        Notifier.cancelStillHere(context)
        NudgeService.instance?.goHome()
    }
}
