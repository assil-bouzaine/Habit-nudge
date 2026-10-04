package me.habitnudge.nudge

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalTime
import me.habitnudge.HabitApp
import me.habitnudge.data.Prefs

/** Records nudge outcomes and time spent per watched app per day. */
object Stats {
    const val KEEP_DAYS = 90L

    suspend fun record(
        app: HabitApp,
        pkg: String,
        opens: Int = 0,
        getOuts: Int = 0,
        stays: Int = 0,
        checkIns: Int = 0,
        ms: Long = 0,
    ) {
        val day = LocalDate.now().toEpochDay()
        if (app.prefs.statsStartDay < 0) app.prefs.statsStartDay = day
        val dao = app.db.stats()
        app.db.withTransaction {
            dao.ensure(day, pkg)
            dao.add(day, pkg, opens, getOuts, stays, checkIns, ms)
        }
    }

    /** Consecutive days before today with watched-app time under the limit (since stats began). */
    suspend fun streak(app: HabitApp): Int {
        val start = app.prefs.statsStartDay
        if (start < 0) return 0
        val today = LocalDate.now().toEpochDay()
        val limitMs = app.prefs.dailyLimitMin * 60_000L
        val totals = app.db.stats().totalsSince(start).associate { it.day to it.totalMs }
        var streak = 0
        var day = today - 1
        while (day >= start && (totals[day] ?: 0L) <= limitMs) {
            streak++
            day--
        }
        return streak
    }
}

object Bedtime {
    fun isNow(prefs: Prefs, now: LocalTime = LocalTime.now()): Boolean {
        if (!prefs.bedtimeEnabled) return false
        val m = now.hour * 60 + now.minute
        val start = prefs.bedtimeStartMin
        val end = prefs.bedtimeEndMin
        return if (start <= end) m in start until end else m >= start || m < end
    }

    private val MESSAGES = listOf(
        "It's {time}. {app} can wait. Your sleep can't.",
        "Nothing in {app} at {time} is worth feeling wrecked tomorrow.",
        "You're not bored, you're tired. Close {app} and sleep.",
        "Tomorrow-you is begging: put the phone down.",
        "Every scroll now is stolen from tomorrow's energy.",
    )

    fun message(prefs: Prefs, label: String, time: String): String {
        val i = prefs.nudgeMessageIndex
        prefs.nudgeMessageIndex = i + 1
        return MESSAGES[i.mod(MESSAGES.size)].replace("{app}", label).replace("{time}", time)
    }
}
