package me.habitnudge.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.habitnudge.MainActivity
import me.habitnudge.app
import me.habitnudge.data.ActiveAlert
import me.habitnudge.data.Strictness
import me.habitnudge.notify.Notifier

/**
 * Keeps exactly one alarm set: the next moment anything is due (a reminder or a repeat nag).
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
        processNags(context)
        restoreActive(context)
        scheduleNext(context)
    }

    /** App opened: put back any Sticky/Nagging notifications EMUI cleared, and re-arm. */
    suspend fun onAppStart(context: Context) = mutex.withLock {
        restoreActive(context)
        scheduleNext(context)
    }

    /** Reminders were edited: move the alarm without firing anything. */
    suspend fun reschedule(context: Context) = mutex.withLock {
        scheduleNext(context)
    }

    /** Done tapped on a Sticky/Nagging reminder. */
    suspend fun done(context: Context, alertId: Long) = mutex.withLock {
        context.app.db.alerts().delete(alertId)
        Notifier.cancelActive(context, alertId)
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
        for (o in Occurrences.between(app.db, prefs, from, now)) fire(context, o, now)
        if (prefs.testDueAt in 1..now) prefs.testDueAt = 0L
        prefs.lastProcessedAt = now
    }

    private suspend fun fire(context: Context, o: Occurrence, now: Long) {
        when (o.style.strictness) {
            Strictness.GENTLE -> Notifier.showGentle(context, o)
            // Takeover arrives in step 3; until then it behaves like Sticky.
            Strictness.STICKY, Strictness.NAGGING, Strictness.TAKEOVER -> {
                val alert = ActiveAlert(
                    occurrenceKey = o.key,
                    dueAt = o.dueAt,
                    message = o.message,
                    style = o.style,
                    opensPlanner = o.opensPlanner,
                    nextNagAt = if (o.style.strictness == Strictness.NAGGING) now + nagMillis(o.style.nagEveryMin) else null,
                )
                val id = context.app.db.alerts().insert(alert)
                if (id != -1L) Notifier.showActive(context, alert.copy(id = id))
            }
        }
    }

    private suspend fun processNags(context: Context) {
        val dao = context.app.db.alerts()
        val now = System.currentTimeMillis()
        for (a in dao.nagsDue(now)) {
            val limit = a.style.escalateAfterNags
            // timesAlerted so far = times ignored, since Done removes the alert.
            val escalate = !a.escalated && limit != null && a.timesAlerted >= limit
            // Escalation to Takeover arrives in step 3; until then an escalated alert keeps nagging.
            val updated = a.copy(
                timesAlerted = a.timesAlerted + 1,
                nextNagAt = now + nagMillis(a.style.nagEveryMin),
                escalated = a.escalated || escalate,
            )
            dao.update(updated)
            Notifier.showActive(context, updated)
        }
    }

    /** Ongoing notifications vanish on reboot or force-stop; the alerts in the database don't. */
    private suspend fun restoreActive(context: Context) {
        for (a in context.app.db.alerts().all()) {
            if (!Notifier.isActiveShowing(context, a.id)) Notifier.showActive(context, a)
        }
    }

    private fun nagMillis(minutes: Int) = minutes.coerceAtLeast(1) * 60_000L

    private suspend fun scheduleNext(context: Context) {
        val app = context.app
        val prefs = app.prefs
        val now = System.currentTimeMillis()
        // Anything scheduled from here on must fire, even if this is the very first alarm.
        if (prefs.lastProcessedAt == 0L) prefs.lastProcessedAt = now
        val next = Occurrences.between(app.db, prefs, now, now + LOOKAHEAD_MS).firstOrNull()
        val nag = app.db.alerts().nextNag()

        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nagAt = nag?.nextNagAt
        val useNag = nagAt != null && (next == null || nagAt <= next.dueAt)
        val at = if (useNag) nagAt else next?.dueAt
        if (at == null) {
            am.cancel(pi)
            prefs.nextAlarmAt = 0L
            prefs.nextAlarmLabel = ""
            return
        }
        val strictness = if (useNag) Strictness.NAGGING else next!!.style.strictness
        if (strictness == Strictness.NAGGING || strictness == Strictness.TAKEOVER) {
            // Doze may delay ordinary exact alarms by up to ~9 minutes; alarm-clock alarms aren't delayed.
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        prefs.nextAlarmAt = at
        prefs.nextAlarmLabel = if (useNag) "Repeat: ${nag!!.message}" else next!!.message
    }
}
