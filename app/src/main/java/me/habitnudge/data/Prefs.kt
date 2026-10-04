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

    /** Settings the app can't read back, confirmed by hand on the health screen. */
    fun isConfirmed(key: String): Boolean = sp.getBoolean("confirmed_$key", false)
    fun setConfirmed(key: String, value: Boolean) = sp.edit { putBoolean("confirmed_$key", value) }
}
