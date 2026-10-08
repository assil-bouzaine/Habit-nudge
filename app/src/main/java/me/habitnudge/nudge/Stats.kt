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
        val limit = app.prefs.dailyLimitMin
        app.db.withTransaction {
            dao.ensure(day, pkg, limit)
            dao.add(day, pkg, opens, getOuts, stays, checkIns, ms, limit)
        }
    }

    enum class DayStatus { UNDER, OVER, NOT_TRACKED, TODAY }

    data class DayResult(val day: Long, val minutes: Int, val limitMin: Int, val status: DayStatus)

    data class Summary(
        val currentStreak: Int,
        val bestStreak: Int,
        /** Finished days (before today) since tracking began, and how many stayed under their limit. */
        val successDays: Int,
        val trackedDays: Int,
        /** The last [CALENDAR_DAYS] days ending today, oldest first. */
        val calendar: List<DayResult>,
    )

    const val CALENDAR_DAYS = 35

    /**
     * Each finished day is judged against the limit that applied that day (stored with its stats);
     * a day with no watched-app use at all counts as under. Days before tracking began don't count.
     * Only currently-watched apps count: removing an app takes its history out of the totals
     * (the rows stay in the database, so re-adding it brings them back).
     */
    suspend fun summary(app: HabitApp): Summary {
        val today = LocalDate.now().toEpochDay()
        val start = app.prefs.statsStartDay
        val currentLimit = app.prefs.dailyLimitMin
        val firstShown = today - CALENDAR_DAYS + 1
        val from = if (start < 0) firstShown else minOf(start, firstShown)
        val pkgs = app.db.nudge().watchedPackages()
        // Room rejects an empty IN list; no watched apps means empty totals either way.
        val totals = if (pkgs.isEmpty()) emptyMap() else app.db.stats().totalsSince(from, pkgs).associateBy { it.day }

        fun result(day: Long): DayResult {
            val t = totals[day]
            val minutes = ((t?.totalMs ?: 0L) / 60_000L).toInt()
            val limit = t?.limitMin?.takeIf { it > 0 } ?: currentLimit
            val status = when {
                day == today -> DayStatus.TODAY
                start < 0 || day < start -> DayStatus.NOT_TRACKED
                minutes <= limit -> DayStatus.UNDER
                else -> DayStatus.OVER
            }
            return DayResult(day, minutes, limit, status)
        }

        var success = 0
        var tracked = 0
        var best = 0
        var run = 0
        if (start >= 0) {
            // Older days were pruned, so they can't be judged.
            for (day in maxOf(start, today - KEEP_DAYS) until today) {
                tracked++
                if (result(day).status == DayStatus.UNDER) {
                    success++
                    run++
                    best = maxOf(best, run)
                } else {
                    run = 0
                }
            }
        }
        return Summary(
            currentStreak = run,
            bestStreak = best,
            successDays = success,
            trackedDays = tracked,
            calendar = (firstShown..today).map(::result),
        )
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
