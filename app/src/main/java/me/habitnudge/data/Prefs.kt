package me.habitnudge.data

import android.content.Context
import androidx.core.content.edit

/** Small key-value state. Writes use commit() because receivers may be killed right after. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("habitnudge", Context.MODE_PRIVATE)

    /** Occurrences due at or before this instant have already been handled. */
    var lastProcessedAt: Long
        get() = sp.getLong("lastProcessedAt", 0L)
        set(v) = sp.edit(commit = true) { putLong("lastProcessedAt", v) }

    /** For the health screen: when the next alarm is set for, and what it is. */
    var nextAlarmAt: Long
        get() = sp.getLong("nextAlarmAt", 0L)
        set(v) = sp.edit(commit = true) { putLong("nextAlarmAt", v) }
    var nextAlarmLabel: String
        get() = sp.getString("nextAlarmLabel", "") ?: ""
        set(v) = sp.edit(commit = true) { putString("nextAlarmLabel", v) }

    /** Pending "test reminder in 1 minute"; 0 = none. */
    var testDueAt: Long
        get() = sp.getLong("testDueAt", 0L)
        set(v) = sp.edit(commit = true) { putLong("testDueAt", v) }
    var testStrictness: Strictness
        get() = runCatching { Strictness.valueOf(sp.getString("testStrictness", null)!!) }
            .getOrDefault(Strictness.GENTLE)
        set(v) = sp.edit(commit = true) { putString("testStrictness", v.name) }

    /** Default recurring reminders were created (once, on first run). */
    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) = sp.edit(commit = true) { putBoolean("seeded", v) }

    /** Default nudge messages were created (separate flag: added after the first release). */
    var seededNudge: Boolean
        get() = sp.getBoolean("seededNudge", false)
        set(v) = sp.edit(commit = true) { putBoolean("seededNudge", v) }

    /** The gentle default messages were swapped for the ruthless set. */
    var seededRuthless: Boolean
        get() = sp.getBoolean("seededRuthless", false)
        set(v) = sp.edit(commit = true) { putBoolean("seededRuthless", v) }

    /** How long the nudge card (or notification) stays before fading. */
    var nudgeSeconds: Int
        get() = sp.getInt("nudgeSeconds", 6)
        set(v) = sp.edit { putInt("nudgeSeconds", v) }

    var nudgeGetMeOut: Boolean
        get() = sp.getBoolean("nudgeGetMeOut", true)
        set(v) = sp.edit { putBoolean("nudgeGetMeOut", v) }

    /** Rotates through the nudge messages. */
    var nudgeMessageIndex: Int
        get() = sp.getInt("nudgeMessageIndex", 0)
        set(v) = sp.edit { putInt("nudgeMessageIndex", v) }

    /** Day (epochDay) of the last "nudge service is off" warning, so it's at most once a day. */
    var serviceOffWarnedDay: Long
        get() = sp.getLong("serviceOffWarnedDay", -1L)
        set(v) = sp.edit { putLong("serviceOffWarnedDay", v) }

    /** Settings the app can't read back, confirmed by hand on the health screen. */
    fun isConfirmed(key: String): Boolean = sp.getBoolean("confirmed_$key", false)
    fun setConfirmed(key: String, value: Boolean) = sp.edit { putBoolean("confirmed_$key", value) }
}
