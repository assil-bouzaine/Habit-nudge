package me.habitnudge.data

import me.habitnudge.HabitApp

/** Starter recurring reminders, created once on first run; editable or deletable afterwards. */
object Seed {
    suspend fun ifNeeded(app: HabitApp) {
        seedRules(app)
        seedNudgeMessages(app)
    }

    private suspend fun seedNudgeMessages(app: HabitApp) {
        if (app.prefs.seededNudge) return
        val dao = app.db.nudge()
        for (text in listOf(
            "You opened {app}. Is this what you meant to do right now?",
            "{app} again. What were you about to do before this?",
            "Take a breath. Do you want to be in {app} right now?",
        )) dao.insertMessage(NudgeMessage(text = text))
        app.prefs.seededNudge = true
    }

    private suspend fun seedRules(app: HabitApp) {
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
