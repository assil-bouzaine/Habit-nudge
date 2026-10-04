package me.habitnudge

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.habitnudge.data.AppDatabase
import me.habitnudge.data.Prefs
import me.habitnudge.notify.Notifier

class HabitApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val prefs: Prefs by lazy { Prefs(this) }

    /** For work that must outlive the screen that started it. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}

val Context.app: HabitApp get() = applicationContext as HabitApp
