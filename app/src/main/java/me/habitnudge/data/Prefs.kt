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

    // Bedtime mode: strictest nudges between start and end (window may cross midnight).
    var bedtimeEnabled: Boolean
        get() = sp.getBoolean("bedtimeEnabled", true)
        set(v) = sp.edit { putBoolean("bedtimeEnabled", v) }
    var bedtimeStartMin: Int
        get() = sp.getInt("bedtimeStartMin", 23 * 60)
        set(v) = sp.edit { putInt("bedtimeStartMin", v) }
    var bedtimeEndMin: Int
        get() = sp.getInt("bedtimeEndMin", 6 * 60)
        set(v) = sp.edit { putInt("bedtimeEndMin", v) }
    var bedtimeCheckInMin: Int
        get() = sp.getInt("bedtimeCheckInMin", 5)
        set(v) = sp.edit { putInt("bedtimeCheckInMin", v) }
    var bedtimeStayLockSec: Int
        get() = sp.getInt("bedtimeStayLockSec", 15)
        set(v) = sp.edit { putInt("bedtimeStayLockSec", v) }

    /** Daily limit for total time in watched apps; the streak counts days under it. */
    var dailyLimitMin: Int
        get() = sp.getInt("dailyLimitMin", 60)
        set(v) = sp.edit { putInt("dailyLimitMin", v) }

    /** First day stats were recorded; days before it don't count toward the streak. */
    var statsStartDay: Long
        get() = sp.getLong("statsStartDay", -1L)
        set(v) = sp.edit { putLong("statsStartDay", v) }

    /** Day (epochDay) of the last "nudge service is off" warning, so it's at most once a day. */
    var serviceOffWarnedDay: Long
        get() = sp.getLong("serviceOffWarnedDay", -1L)
        set(v) = sp.edit { putLong("serviceOffWarnedDay", v) }

    /** SHA-256 hashed PIN for secret notes; null = not set up. */
    var notesAuthPin: String?
        get() = sp.getString("notesAuthPin", null)
        set(v) = sp.edit(commit = true) { putString("notesAuthPin", v) }

    /** Secret notes are unlocked until this timestamp; 0 = locked. */
    var notesAuthenticatedUntil: Long
        get() = sp.getLong("notesAuthenticatedUntil", 0L)
        set(v) = sp.edit(commit = true) { putLong("notesAuthenticatedUntil", v) }

    /** Settings the app can't read back, confirmed by hand on the health screen. */
    fun isConfirmed(key: String): Boolean = sp.getBoolean("confirmed_$key", false)
    fun setConfirmed(key: String, value: Boolean) = sp.edit { putBoolean("confirmed_$key", value) }
}
