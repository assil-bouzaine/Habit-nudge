package me.habitnudge.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Small rolling log of when alarms were due vs. when they actually fired, for checking how much
 * Doze/EMUI delays reminders. Kept in the app's external files dir so it can be pulled over adb:
 * adb pull /sdcard/Android/data/me.habitnudge/files/reliability.log
 */
object DiagLog {
    private const val MAX_LINES = 400
    /** About twice MAX_LINES of typical ~60-byte lines, so trimming happens roughly every 400 appends. */
    private const val TRIM_AT_BYTES = 48_000L
    /** Firing later than this is marked LATE. */
    private const val LATE_MS = 60_000L

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    private fun file(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "reliability.log")

    private fun fmt(f: DateTimeFormatter, millis: Long) =
        f.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** Appends one line; only when the file has grown well past [MAX_LINES] is it rewritten to the newest ones. */
    @Synchronized
    fun add(context: Context, line: String) {
        runCatching {
            val f = file(context)
            f.appendText("${fmt(stamp, System.currentTimeMillis())} $line\n")
            if (f.length() > TRIM_AT_BYTES) {
                f.writeText(f.readLines().takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** Records an alarm firing against the time it was scheduled for. */
    fun alarm(context: Context, scheduledAt: Long, label: String) {
        if (scheduledAt == 0L) {
            add(context, "alarm (nothing was scheduled)")
            return
        }
        val delay = System.currentTimeMillis() - scheduledAt
        val late = if (delay > LATE_MS) " LATE" else ""
        add(context, "alarm due ${fmt(clock, scheduledAt)} (+${delay / 1000}s)$late $label")
    }

    @Synchronized
    fun read(context: Context): List<String> =
        runCatching { file(context).takeIf { it.exists() }?.readLines() }.getOrNull().orEmpty()

    @Synchronized
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
