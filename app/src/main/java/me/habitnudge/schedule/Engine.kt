package me.habitnudge.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.habitnudge.MainActivity
import me.habitnudge.app
import me.habitnudge.data.Strictness
import me.habitnudge.notify.Notifier

/**
 * Keeps exactly one alarm set: the next moment anything is due.
 * When it fires, everything due since the last run is handled, then the next alarm is set.
 */
object Engine {
    /** Reminders more than this late (phone off, app killed) are dropped instead of fired. */
    const val LATE_GRACE_MS = 30 * 60_000L
    private const val LOOKAHEAD_MS = 8 * 24 * 60 * 60_000L

    private val mutex = Mutex()

    /** Alarm fired, phone booted, app updated or clock changed. */
    suspend fun onAlarm(context: Context) = mutex.withLock {
        processDue(context)
        scheduleNext(context)
    }

    /** Reminders were edited: move the alarm without firing anything. */
    suspend fun reschedule(context: Context) = mutex.withLock {
        scheduleNext(context)
    }

    suspend fun scheduleTest(context: Context, strictness: Strictness, inMillis: Long = 60_000L) {
        val prefs = context.app.prefs
        prefs.testStrictness = strictness
        prefs.testDueAt = System.currentTimeMillis() + inMillis
        reschedule(context)
    }

    private suspend fun processDue(context: Context) {
        val app = context.app
        val prefs = app.prefs
        val now = System.currentTimeMillis()
        val last = prefs.lastProcessedAt
        // First run, or the clock moved backwards: start counting from now.
        if (last == 0L || last > now) {
            prefs.lastProcessedAt = now
            return
        }
        val from = maxOf(last, now - LATE_GRACE_MS)
        for (o in Occurrences.between(app.db, prefs, from, now)) fire(context, o)
        if (prefs.testDueAt in 1..now) prefs.testDueAt = 0L
        prefs.lastProcessedAt = now
    }

    private fun fire(context: Context, o: Occurrence) {
        // Sticky, Nagging and Takeover arrive in later steps; until then everything alerts gently.
        Notifier.showGentle(context, o)
    }

    private suspend fun scheduleNext(context: Context) {
        val app = context.app
        val prefs = app.prefs
        val now = System.currentTimeMillis()
        // Anything scheduled from here on must fire, even if this is the very first alarm.
        if (prefs.lastProcessedAt == 0L) prefs.lastProcessedAt = now
        val next = Occurrences.between(app.db, prefs, now, now + LOOKAHEAD_MS).firstOrNull()
        val nextNag = app.db.alerts().nextNagAt()

        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = listOfNotNull(next?.dueAt, nextNag).minOrNull()
        if (at == null) {
            am.cancel(pi)
            prefs.nextAlarmAt = 0L
            prefs.nextAlarmLabel = ""
            return
        }
        val isTakeover = next != null && next.dueAt == at && next.style.strictness == Strictness.TAKEOVER
        if (isTakeover) {
            // Alarm-clock alarms are the hardest for Doze and EMUI to delay.
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        prefs.nextAlarmAt = at
        prefs.nextAlarmLabel = if (next != null && next.dueAt == at) next.message else "Repeat alert"
    }
}
