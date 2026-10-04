package me.habitnudge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlannedReminder::class, RecurringRule::class, ActiveAlert::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planned(): PlannedDao
    abstract fun rules(): RuleDao
    abstract fun alerts(): AlertDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "habitnudge.db").build()
    }
}
