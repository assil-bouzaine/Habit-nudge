package me.habitnudge.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import me.habitnudge.data.AlertStyle
import me.habitnudge.data.AppDatabase
import me.habitnudge.data.Prefs
import me.habitnudge.data.ReminderConfig

/** One concrete time a reminder is due. [key] is stable, so the same slot is never fired twice. */
data class Occurrence(
    val key: String,
    val dueAt: Long,
    val message: String,
    val style: AlertStyle,
    val opensPlanner: Boolean = false,
)

object Occurrences {
    const val TEST_MESSAGE = "Test reminder: it works!"

    /** All occurrences with fromExclusive < dueAt <= toInclusive, sorted by time. */
    suspend fun between(
        db: AppDatabase,
        prefs: Prefs,
        fromExclusive: Long,
        toInclusive: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Occurrence> {
        val firstDay = Instant.ofEpochMilli(fromExclusive).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(toInclusive).atZone(zone).toLocalDate()
        val out = mutableListOf<Occurrence>()
        fun add(o: Occurrence) {
            if (o.dueAt > fromExclusive && o.dueAt <= toInclusive) out += o
        }

        for (p in db.planned().between(firstDay.toEpochDay(), lastDay.toEpochDay())) {
            add(Occurrence("p:${p.id}", millisAt(LocalDate.ofEpochDay(p.epochDay), p.minuteOfDay, zone), p.message, p.style, p.opensPlanner))
        }

        val rules = db.rules().enabled()
        var day = firstDay
        while (!day.isAfter(lastDay)) {
            for (r in rules) if (r.runsOn(day.dayOfWeek)) for (m in r.slotMinutes()) {
                add(Occurrence("r:${r.id}:${day.toEpochDay()}:$m", millisAt(day, m, zone), r.message, r.style, r.opensPlanner))
            }
            day = day.plusDays(1)
        }

        val testAt = prefs.testDueAt
        if (testAt != 0L) {
            // Short intervals so a test shows repeats, escalation and the Done countdown within minutes.
            val style = AlertStyle(prefs.testStrictness, nagEveryMin = 1, escalateAfterNags = 3, doneCountdownSec = 10)
            add(Occurrence("test:$testAt", testAt, TEST_MESSAGE, style))
        }

        // Note reminders (only regular notes with active reminders)
        val noteReminders = generateNoteReminders(db, firstDay, lastDay, zone)
        noteReminders.forEach { add(it) }

        return out.sortedBy { it.dueAt }
    }

    private suspend fun generateNoteReminders(
        db: AppDatabase,
        firstDay: LocalDate,
        lastDay: LocalDate,
        zone: ZoneId,
    ): List<Occurrence> {
        val notes = db.note().getNotesWithActiveReminders()
        val result = mutableListOf<Occurrence>()

        for (note in notes) {
            val config = note.reminderConfig ?: continue
            // Self-heal: a day stuck in the past (its slot was dropped as late before it
            // could roll forward) would otherwise never fire again. Roll to the next
            // slot on or after today.
            var startDay = LocalDate.ofEpochDay(config.nextReminderEpochDay)
            val today = LocalDate.now()
            if (startDay.isBefore(today)) {
                var d = startDay
                while (d.isBefore(today)) d = d.plusDays(config.intervalDays.toLong().coerceAtLeast(1))
                startDay = d
                db.note().update(note.copy(reminderConfig = config.copy(nextReminderEpochDay = d.toEpochDay())))
            }
            var day = startDay
            val multi = config.timesPerDay > 1

            // Generate occurrences every intervalDays within the range
            while (!day.isAfter(lastDay)) {
                if (!day.isBefore(firstDay)) {
                    val message = note.content.take(200) // First 200 chars
                    if (!multi) {
                        result.add(
                            Occurrence(
                                key = "note:${note.id}:${day.toEpochDay()}",
                                dueAt = millisAt(day, config.timeOfDay, zone),
                                message = message,
                                style = config.style,
                                opensPlanner = false
                            )
                        )
                    } else {
                        for (m in noteSlots(note.id, day.toEpochDay(), config)) {
                            result.add(
                                Occurrence(
                                    key = "note:${note.id}:${day.toEpochDay()}:$m",
                                    dueAt = millisAt(day, m, zone),
                                    message = message,
                                    style = config.style,
                                    opensPlanner = false
                                )
                            )
                        }
                    }
                }
                day = day.plusDays(config.intervalDays.toLong().coerceAtLeast(1))
            }
        }

        return result
    }

    private fun millisAt(day: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        day.atStartOfDay(zone).toLocalDateTime()
            .plusMinutes(minuteOfDay.toLong())
            .atZone(zone).toInstant().toEpochMilli()

    /**
     * The pseudo-random fire times for a multi-times note reminder day. Deterministic per
     * (note, day), so every schedule recomputation yields the same slots without storing them.
     * Collisions merge, so tiny windows may yield fewer than [count] slots.
     */
    fun noteSlots(noteId: Long, epochDay: Long, config: ReminderConfig): List<Int> {
        val count = config.timesPerDay.coerceAtLeast(1)
        if (count <= 1) return listOf(config.timeOfDay)
        return evenSlots(config.windowStartMin, config.windowEndMin, count)
    }

    /**
     * [count] minutes-of-day spread evenly over [start]..[end], both ends included
     * (10:00–22:00 × 5 → 10:00, 13:00, 16:00, 19:00, 22:00). Same every day, so keys stay stable.
     */
    fun evenSlots(start: Int, end: Int, count: Int): List<Int> {
        if (end <= start || count < 1) return emptyList()
        if (count == 1) return listOf(start)
        val span = end - start
        return (0 until count).map { i -> start + (i * span + (count - 1) / 2) / (count - 1) }.distinct()
    }
}
