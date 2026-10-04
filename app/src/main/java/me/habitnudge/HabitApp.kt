package me.habitnudge

import android.app.Application
import android.content.Context
import me.habitnudge.data.AppDatabase
import me.habitnudge.data.Prefs
import me.habitnudge.notify.Notifier

class HabitApp : Application() {
    val db: AppDatabase by lazy { AppDatabase.build(this) }
    val prefs: Prefs by lazy { Prefs(this) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}

val Context.app: HabitApp get() = applicationContext as HabitApp
