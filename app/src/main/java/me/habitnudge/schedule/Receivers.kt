package me.habitnudge.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.habitnudge.notify.Notifier

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        runAsync { Engine.onAlarm(context.applicationContext) }
}

/** Boot, app update and clock/time-zone changes: catch up on anything missed and re-arm. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) =
        runAsync { Engine.onAlarm(context.applicationContext) }
}

/** The Done action on a Sticky/Nagging notification. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Notifier.EXTRA_ALERT_ID, -1L)
        if (id == -1L) return
        runAsync { Engine.done(context.applicationContext, id) }
    }
}

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
