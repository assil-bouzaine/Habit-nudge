package me.habitnudge.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlannedReminder::class, RecurringRule::class, ActiveAlert::class,
        NudgeApp::class, NudgeMessage::class, AppDayStat::class,
        Note::class,
    ],
    version = 8,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = AppDatabase.CooldownToCheckIn::class),
        AutoMigration(from = 3, to = 4),
        // v5: recurring rules get days of the week; daily stats remember that day's limit.
        AutoMigration(from = 4, to = 5),
        // v6: add notes table with optional recurring reminders.
        AutoMigration(from = 5, to = 6),
        // v7: note reminders can fire several times a day inside a time window.
        AutoMigration(from = 6, to = 7),
        // v8: no schema change. v7's new columns were backfilled into reminder-less notes,
        // breaking Room's all-null check for the optional reminder — clear them again.
        AutoMigration(from = 7, to = 8, spec = AppDatabase.ClearBackfilledReminderColumns::class),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planned(): PlannedDao
    abstract fun rules(): RuleDao
    abstract fun alerts(): AlertDao
    abstract fun nudge(): NudgeDao
    abstract fun stats(): StatsDao
    abstract fun note(): NoteDao

    /** v3: the per-app nudge cooldown became the "Still here?" check-in interval (default 15 min). */
    @RenameColumn(tableName = "nudge_app", fromColumnName = "cooldownMin", toColumnName = "checkInMin")
    class CooldownToCheckIn : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE nudge_app SET checkInMin = 15")
        }
    }

    /** v8: data-only fix, see the autoMigrations comment. */
    class ClearBackfilledReminderColumns : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE note SET timesPerDay = NULL, windowStartMin = NULL, windowEndMin = NULL " +
                    "WHERE strictness IS NULL",
            )
        }
    }

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "habitnudge.db").build()
    }
}
