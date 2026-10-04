package me.habitnudge.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Strictness { GENTLE, STICKY, NAGGING, TAKEOVER }

/** How a reminder alerts. Shared by planned reminders, recurring rules and active alerts. */
data class AlertStyle(
    val strictness: Strictness = Strictness.GENTLE,
    /** NAGGING: minutes between repeat alerts. */
    val nagEveryMin: Int = 5,
    /** NAGGING: escalate to TAKEOVER after this many ignored alerts; null = never. */
    val escalateAfterNags: Int? = null,
    /** TAKEOVER: seconds before Done unlocks; 0 = immediately. */
    val doneCountdownSec: Int = 0,
)

/** A one-off reminder on a specific day, planned the evening before. */
@Entity(tableName = "planned_reminder", indices = [Index("epochDay")])
data class PlannedReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val minuteOfDay: Int,
    val message: String,
    @Embedded val style: AlertStyle = AlertStyle(),
)

/** A daily reminder, either once at [startMinute] or every [intervalMin] within the window. */
@Entity(tableName = "recurring_rule")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val startMinute: Int,
    val endMinute: Int,
    /** null = once a day at [startMinute]. */
    val intervalMin: Int?,
    val enabled: Boolean = true,
    /** Tapping the reminder opens tomorrow's plan (the evening "plan tomorrow" reminder). */
    val opensPlanner: Boolean = false,
    @Embedded val style: AlertStyle = AlertStyle(),
) {
    fun slotMinutes(): List<Int> =
        if (intervalMin == null || intervalMin <= 0) listOf(startMinute)
        else (startMinute..endMinute step intervalMin).toList()
}

enum class NudgeStyle { CARD, NOTIFICATION }

/** An app that gets a gentle nudge when opened. */
@Entity(tableName = "nudge_app")
data class NudgeApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val enabled: Boolean = true,
    val style: NudgeStyle = NudgeStyle.CARD,
    /** While you stay in the app, a "Still here?" check-in every this many minutes; 0 = never. */
    val checkInMin: Int = 15,
)

/** A nudge message; "{app}" is replaced with the app's name. Shown in rotation. */
@Entity(tableName = "nudge_message")
data class NudgeMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
)

/** A fired Sticky/Nagging/Takeover reminder that is waiting for Done. */
@Entity(tableName = "active_alert", indices = [Index("occurrenceKey", unique = true)])
data class ActiveAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurrenceKey: String,
    val dueAt: Long,
    val message: String,
    @Embedded val style: AlertStyle,
    val opensPlanner: Boolean,
    val timesAlerted: Int = 1,
    val nextNagAt: Long? = null,
    val escalated: Boolean = false,
)
