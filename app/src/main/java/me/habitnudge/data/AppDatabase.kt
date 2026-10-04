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
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = AppDatabase.CooldownToCheckIn::class),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planned(): PlannedDao
    abstract fun rules(): RuleDao
    abstract fun alerts(): AlertDao
    abstract fun nudge(): NudgeDao
    abstract fun stats(): StatsDao

    /** v3: the per-app nudge cooldown became the "Still here?" check-in interval (default 15 min). */
    @RenameColumn(tableName = "nudge_app", fromColumnName = "cooldownMin", toColumnName = "checkInMin")
    class CooldownToCheckIn : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE nudge_app SET checkInMin = 15")
        }
    }

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "habitnudge.db").build()
    }
}
