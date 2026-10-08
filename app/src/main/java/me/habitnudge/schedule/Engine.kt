package me.habitnudge.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.habitnudge.MainActivity
import me.habitnudge.app
import me.habitnudge.data.ActiveAlert
import me.habitnudge.data.AlertStyle
import me.habitnudge.data.PlannedReminder
import me.habitnudge.data.DiagLog
import me.habitnudge.nudge.NudgeService
import me.habitnudge.nudge.Stats
import me.habitnudge.data.Strictness
import me.habitnudge.notify.Notifier
import me.habitnudge.takeover.AlarmSound
import me.habitnudge.takeover.Takeover

/**
 * Keeps exactly one alarm set: the next moment anything is due (a reminder or a repeat nag).
 * When it fires, everything due since the last run is handled, then the next alarm is set.
 */
object Engine {
    /** Reminders more than this late (phone off, app killed) are dropped instead of fired. */
    const val LATE_GRACE_MS = 30 * 60_000L
    private const val LOOKAHEAD_MS = 8 * 24 * 60 * 60_000L
    private const val TAKEOVER_RETRY_MS = 60_000L
    /** Past days' planned reminders older than this are deleted. */
    private const val KEEP_PLANNED_DAYS = 30L

    private val mutex = Mutex()

    /** Alarm fired, phone booted, app updated or clock changed. */
    suspend fun onAlarm(context: Context) = mutex.withLock {
        if (context.app.prefs.alertsPaused) {
            // Master pause: swallow the window silently (missed occurrences are dropped,
            // due nags stay due and fire on resume), then re-arm.
            context.app.prefs.lastProcessedAt = System.currentTimeMillis()
            scheduleNext(context)
            return
        }
        processDue(context)
        processNags(context)
        restoreActive(context)
        scheduleNext(context)
        checkNudgeService(context)
    }

    /** EMUI can silently switch the accessibility service off; say so once a day if apps are being watched. */
    private suspend fun checkNudgeService(context: Context) {
        val app = context.app
        val today = LocalDate.now().toEpochDay()
        if (NudgeService.isEnabled(context) || app.prefs.serviceOffWarnedDay == today) return
        if (app.db.nudge().enabledCount() == 0) return
        app.prefs.serviceOffWarnedDay = today
        DiagLog.add(context, "nudge service found off")
        Notifier.showServiceOff(context)
    }

    /** App opened: put back any notifications EMUI cleared, bring back a pending Takeover, and re-arm. */
    suspend fun onAppStart(context: Context) = mutex.withLock {
        context.app.db.planned().deleteBefore(LocalDate.now().toEpochDay() - KEEP_PLANNED_DAYS)
        context.app.db.stats().deleteBefore(LocalDate.now().toEpochDay() - Stats.KEEP_DAYS)
        if (!context.app.prefs.alertsPaused) {
            restoreActive(context)
            if (context.app.db.alerts().takeoverQueue().isNotEmpty() && !Takeover.inCall(context)) {
                Takeover.launch(context)
            }
        }
        scheduleNext(context)
    }

    /** Master pause latch: pausing silences everything now; resuming re-posts open alerts and re-arms. */
    suspend fun setPaused(context: Context, paused: Boolean) = mutex.withLock {
        val app = context.app
        app.prefs.alertsPaused = paused
        if (paused) {
            AlarmSound.stop()
            Notifier.cancelAll(context)
            DiagLog.add(context, "alerts paused")
        } else {
            DiagLog.add(context, "alerts resumed")
            restoreActive(context)
        }
        scheduleNext(context)
    }

    /** A call started while a Takeover was up: hide it and retry every minute until the call ends. */
    suspend fun deferTakeovers(context: Context) = mutex.withLock {
        if (context.app.prefs.alertsPaused) {
            scheduleNext(context)
            return
        }
        val dao = context.app.db.alerts()
        val retryAt = System.currentTimeMillis() + TAKEOVER_RETRY_MS
        for (a in dao.takeoverQueue()) {
            val deferred = a.copy(nextNagAt = retryAt)
            dao.update(deferred)
            AlarmSound.allowRingAgain(a.id)
            Notifier.showActive(context, deferred)
        }
        scheduleNext(context)
    }

    /** Reminders were edited: move the alarm without firing anything. */
    suspend fun reschedule(context: Context) = mutex.withLock {
        scheduleNext(context)
    }

    /** Done tapped on a Sticky/Nagging reminder. */
    suspend fun done(context: Context, alertId: Long) = mutex.withLock {
        val dao = context.app.db.alerts()
        dao.delete(alertId)
        Notifier.cancelActive(context, alertId)
        Takeover.shownAt.remove(alertId)
        if (dao.takeoverQueue().isEmpty()) AlarmSound.stop()
        scheduleNext(context)
    }

    /**
     * "Reschedule" on a reminder: close it now and add the same reminder (message, strictness) to the
     * planner at [at]. [alertId] is set for Sticky/Nagging/Takeover; Gentle ones pass [gentleKey] instead.
     */
    suspend fun rescheduleReminder(
        context: Context,
        at: Long,
        alertId: Long? = null,
        gentleKey: String? = null,
        message: String,
        style: AlertStyle,
        opensPlanner: Boolean,
    ) = mutex.withLock {
        val app = context.app
        if (alertId != null) {
            app.db.alerts().delete(alertId)
            Notifier.cancelActive(context, alertId)
            Takeover.shownAt.remove(alertId)
            if (app.db.alerts().takeoverQueue().isEmpty()) AlarmSound.stop()
        }
        gentleKey?.let { Notifier.cancelGentle(context, it) }
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        app.db.planned().upsert(
            PlannedReminder(
                epochDay = time.toLocalDate().toEpochDay(),
                minuteOfDay = time.hour * 60 + time.minute,
                message = message,
                style = style,
                opensPlanner = opensPlanner,
            ),
        )
        DiagLog.add(context, "rescheduled to ${time.toLocalTime().withSecond(0).withNano(0)} $message")
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
        // Advance note reminder schedule if this is a note occurrence
        if (o.key.startsWith("note:")) {
            advanceNoteReminder(context, o)
        }

        when (o.style.strictness) {
            Strictness.GENTLE -> Notifier.showGentle(context, o)
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
                if (id == -1L) return
                if (o.style.strictness == Strictness.TAKEOVER) presentTakeover(context, alert.copy(id = id))
                else Notifier.showActive(context, alert.copy(id = id))
            }
        }
    }

    /**
     * Advance a note's reminder after it fires. Single-time notes roll to the next reminder day;
     * multi-times notes stay on the day until its last slot fires, so the remaining random
     * times still come. Keys are `note:<id>:<day>` or `note:<id>:<day>:<minute>`.
     */
    private suspend fun advanceNoteReminder(context: Context, o: Occurrence) {
        val parts = o.key.split(":")
        val noteId = parts.getOrNull(1)?.toLongOrNull() ?: return
        val note = context.app.db.note().getById(noteId) ?: return
        val config = note.reminderConfig ?: return
        val interval = config.intervalDays.toLong().coerceAtLeast(1)

        val firedDay = parts.getOrNull(2)?.toLongOrNull()
        val firedMin = parts.getOrNull(3)?.toIntOrNull()
        val nextDay = if (config.timesPerDay > 1 && firedDay != null && firedMin != null) {
            val slots = Occurrences.noteSlots(noteId, firedDay, config)
            // More random times later today: keep the day. Otherwise roll forward.
            if (slots.any { it > firedMin }) return
            maxOf(config.nextReminderEpochDay, firedDay) + interval
        } else {
            config.nextReminderEpochDay + interval
        }

        context.app.db.note().update(
            note.copy(
                reminderConfig = config.copy(nextReminderEpochDay = nextDay),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun processNags(context: Context) {
        val dao = context.app.db.alerts()
        val now = System.currentTimeMillis()
        for (a in dao.nagsDue(now)) {
            if (a.style.strictness == Strictness.TAKEOVER) {
                // A Takeover deferred by a call: try again.
                presentTakeover(context, a.copy(nextNagAt = null))
                continue
            }
            val limit = a.style.escalateAfterNags
            // timesAlerted so far = times ignored, since Done removes the alert.
            if (limit != null && a.timesAlerted >= limit) {
                val escalated = a.copy(
                    style = a.style.copy(strictness = Strictness.TAKEOVER),
                    nextNagAt = null,
                    escalated = true,
                )
                presentTakeover(context, escalated)
                continue
            }
            val updated = a.copy(
                timesAlerted = a.timesAlerted + 1,
                nextNagAt = now + nagMillis(a.style.nagEveryMin),
            )
            dao.update(updated)
            Notifier.showActive(context, updated)
        }
    }

    /** Shows a Takeover now, or, during a call, keeps it in the shade and retries in a minute. */
    private suspend fun presentTakeover(context: Context, alert: ActiveAlert) {
        val toSave = if (Takeover.inCall(context)) {
            alert.copy(nextNagAt = System.currentTimeMillis() + TAKEOVER_RETRY_MS)
        } else {
            alert.copy(nextNagAt = null)
        }
        context.app.db.alerts().update(toSave)
        // The notification's full-screen intent opens the card when the screen is off or locked...
        Notifier.showActive(context, toSave)
        // ...and starting it directly covers the case where another app is in the foreground.
        if (toSave.nextNagAt == null) {
            // Ring from here, not from the card: over EMUI's lock screen the card stays paused.
            AlarmSound.ringFor(context, toSave.id)
            Takeover.launch(context)
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
