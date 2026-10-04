package me.habitnudge.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import me.habitnudge.MainActivity
import me.habitnudge.R
import me.habitnudge.schedule.Occurrence

object Notifier {
    // A channel's sound and importance can't be changed after creation; bump the id to change them.
    const val CH_GENTLE = "gentle_v1"

    const val EXTRA_OPEN_PLANNER = "openPlanner"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_GENTLE, "Gentle reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Normal notification with sound"
            },
        )
    }

    fun showGentle(context: Context, o: Occurrence) {
        val n = Notification.Builder(context, CH_GENTLE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(o.message)
            .setWhen(o.dueAt)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, o.opensPlanner))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(o.key.hashCode(), n)
    }

    private fun openApp(context: Context, openPlanner: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_PLANNER, openPlanner)
        return PendingIntent.getActivity(
            context, if (openPlanner) 1 else 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
