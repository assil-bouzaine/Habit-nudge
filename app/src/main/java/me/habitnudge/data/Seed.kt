package me.habitnudge.data

import me.habitnudge.HabitApp

/** Starter recurring reminders, created once on first run; editable or deletable afterwards. */
object Seed {
    suspend fun ifNeeded(app: HabitApp) {
        seedRules(app)
        seedNudgeMessages(app)
    }

    /** The original gentle defaults; replaced by [RUTHLESS] (messages you wrote yourself are kept). */
    private val GENTLE = listOf(
        "You opened {app}. Is this what you meant to do right now?",
        "{app} again. What were you about to do before this?",
        "Take a breath. Do you want to be in {app} right now?",
    )

    private val RUTHLESS = listOf(
        "{app} again? You said you'd stop. Prove it.",
        "You opened {app} out of habit, not need. Leave.",
        "Nothing in {app} is worth your next hour. Close it.",
        "Every minute in {app} is a minute you never get back.",
        "Is scrolling {app} getting you closer to your goals? No. Get out.",
        "You're better than mindless scrolling. Act like it.",
        "Your future self is watching you open {app}. Don't let them down.",
        "{app} wins every time you give in. Not today.",
        "Put the phone down. {app} will survive without you.",
        "Discipline is choosing what you want most over what you want now.",
    )

    private suspend fun seedNudgeMessages(app: HabitApp) {
        val dao = app.db.nudge()
        if (!app.prefs.seededNudge) {
            app.prefs.seededNudge = true
            app.prefs.seededRuthless = true
            for (text in RUTHLESS) dao.insertMessage(NudgeMessage(text = text))
            return
        }
        if (!app.prefs.seededRuthless) {
            dao.deleteMessagesWithText(GENTLE)
            for (text in RUTHLESS) dao.insertMessage(NudgeMessage(text = text))
            app.prefs.seededRuthless = true
        }
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
