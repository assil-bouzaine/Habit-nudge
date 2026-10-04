package me.habitnudge.data

import me.habitnudge.HabitApp

/** Starter recurring reminders, created once on first run; editable or deletable afterwards. */
object Seed {
    suspend fun ifNeeded(app: HabitApp) {
        if (app.prefs.seeded) return
        val rules = app.db.rules()
        rules.upsert(
            RecurringRule(
                message = "Drink water",
                startMinute = 9 * 60,
                endMinute = 22 * 60,
                intervalMin = 90,
            ),
        )
        rules.upsert(
            RecurringRule(
                message = "Plan tomorrow's reminders",
                startMinute = 21 * 60,
                endMinute = 21 * 60,
                intervalMin = null,
                opensPlanner = true,
                style = AlertStyle(Strictness.STICKY),
            ),
        )
        app.prefs.seeded = true
    }
}
