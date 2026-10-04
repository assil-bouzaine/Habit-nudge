package me.habitnudge.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import me.habitnudge.data.AlertStyle
import me.habitnudge.data.AppDatabase
import me.habitnudge.data.Prefs

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
            add(Occurrence("p:${p.id}", millisAt(LocalDate.ofEpochDay(p.epochDay), p.minuteOfDay, zone), p.message, p.style))
        }

        val rules = db.rules().enabled()
        var day = firstDay
        while (!day.isAfter(lastDay)) {
            for (r in rules) for (m in r.slotMinutes()) {
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

        return out.sortedBy { it.dueAt }
    }

    private fun millisAt(day: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        day.atStartOfDay(zone).toLocalDateTime()
            .plusMinutes(minuteOfDay.toLong())
            .atZone(zone).toInstant().toEpochMilli()
}
