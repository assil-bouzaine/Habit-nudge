package me.habitnudge.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedDao {
    @Query("SELECT * FROM planned_reminder WHERE epochDay BETWEEN :fromDay AND :toDay")
    suspend fun between(fromDay: Long, toDay: Long): List<PlannedReminder>

    @Query("SELECT * FROM planned_reminder WHERE epochDay = :day ORDER BY minuteOfDay")
    fun forDay(day: Long): Flow<List<PlannedReminder>>

    @Query("SELECT * FROM planned_reminder WHERE epochDay = :day ORDER BY minuteOfDay")
    suspend fun forDayOnce(day: Long): List<PlannedReminder>

    @Query("SELECT COUNT(*) FROM planned_reminder WHERE epochDay = :day")
    fun countForDay(day: Long): Flow<Int>

    @Upsert
    suspend fun upsert(reminder: PlannedReminder): Long

    @Delete
    suspend fun delete(reminder: PlannedReminder)

    @Query("DELETE FROM planned_reminder WHERE epochDay = :day")
    suspend fun deleteDay(day: Long)

    @Query("DELETE FROM planned_reminder WHERE epochDay < :day")
    suspend fun deleteBefore(day: Long)

    @Query("DELETE FROM planned_reminder WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM recurring_rule WHERE enabled = 1")
    suspend fun enabled(): List<RecurringRule>

    @Query("SELECT * FROM recurring_rule ORDER BY startMinute")
    fun all(): Flow<List<RecurringRule>>

    @Upsert
    suspend fun upsert(rule: RecurringRule): Long

    @Delete
    suspend fun delete(rule: RecurringRule)
}

@Dao
interface NudgeDao {
    @Query("SELECT * FROM nudge_app ORDER BY label COLLATE NOCASE")
    fun appsFlow(): Flow<List<NudgeApp>>

    @Query("SELECT COUNT(*) FROM nudge_app WHERE enabled = 1")
    suspend fun enabledCount(): Int

    @Upsert
    suspend fun upsertApp(app: NudgeApp)

    @Upsert
    suspend fun upsertApps(apps: List<NudgeApp>)

    @Delete
    suspend fun deleteApp(app: NudgeApp)

    @Query("SELECT * FROM nudge_message ORDER BY id")
    fun messagesFlow(): Flow<List<NudgeMessage>>

    @Query("SELECT * FROM nudge_message ORDER BY id")
    suspend fun messages(): List<NudgeMessage>

    @Insert
    suspend fun insertMessage(message: NudgeMessage)

    @Delete
    suspend fun deleteMessage(message: NudgeMessage)

    @Query("DELETE FROM nudge_message WHERE text IN (:texts)")
    suspend fun deleteMessagesWithText(texts: List<String>)
}

@Dao
interface StatsDao {
    @Query("INSERT OR IGNORE INTO app_day_stat (day, packageName, opens, getOuts, stays, checkIns, foregroundMs) VALUES (:day, :pkg, 0, 0, 0, 0, 0)")
    suspend fun ensure(day: Long, pkg: String)

    @Query(
        "UPDATE app_day_stat SET opens = opens + :opens, getOuts = getOuts + :getOuts, stays = stays + :stays, " +
            "checkIns = checkIns + :checkIns, foregroundMs = foregroundMs + :ms WHERE day = :day AND packageName = :pkg",
    )
    suspend fun add(day: Long, pkg: String, opens: Int, getOuts: Int, stays: Int, checkIns: Int, ms: Long)

    @Query("SELECT * FROM app_day_stat WHERE day >= :fromDay")
    fun since(fromDay: Long): Flow<List<AppDayStat>>

    /** Total watched-app time per day, for the streak. */
    @Query("SELECT day, SUM(foregroundMs) AS totalMs FROM app_day_stat WHERE day >= :fromDay GROUP BY day")
    suspend fun totalsSince(fromDay: Long): List<DayTotal>

    @Query("DELETE FROM app_day_stat WHERE day < :day")
    suspend fun deleteBefore(day: Long)
}

data class DayTotal(val day: Long, val totalMs: Long)

@Dao
interface AlertDao {
    @Query("SELECT * FROM active_alert")
    suspend fun all(): List<ActiveAlert>

    /** Takeovers that should be on screen now (deferred ones, e.g. during a call, have nextNagAt set). */
    @Query("SELECT * FROM active_alert WHERE strictness = 'TAKEOVER' AND nextNagAt IS NULL ORDER BY dueAt")
    suspend fun takeoverQueue(): List<ActiveAlert>

    @Query("SELECT * FROM active_alert WHERE strictness = 'TAKEOVER' AND nextNagAt IS NULL ORDER BY dueAt")
    fun takeoverQueueFlow(): Flow<List<ActiveAlert>>

    @Query("SELECT * FROM active_alert WHERE nextNagAt IS NOT NULL ORDER BY nextNagAt LIMIT 1")
    suspend fun nextNag(): ActiveAlert?

    @Query("SELECT * FROM active_alert WHERE nextNagAt <= :now")
    suspend fun nagsDue(now: Long): List<ActiveAlert>

    @Query("SELECT * FROM active_alert WHERE id = :id")
    suspend fun byId(id: Long): ActiveAlert?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alert: ActiveAlert): Long

    @Update
    suspend fun update(alert: ActiveAlert)

    @Query("DELETE FROM active_alert WHERE id = :id")
    suspend fun delete(id: Long)
}
