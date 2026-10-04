package me.habitnudge.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.provider.Settings
import me.habitnudge.MainActivity
import me.habitnudge.R
import me.habitnudge.data.ActiveAlert
import me.habitnudge.data.Strictness
import me.habitnudge.schedule.DoneReceiver
import me.habitnudge.schedule.Occurrence

object Notifier {
    // A channel's sound and importance can't be changed after creation; bump the id to change them.
    const val CH_GENTLE = "gentle_v1"
    const val CH_STICKY = "sticky_v1"
    const val CH_NAG = "nag_v1"

    const val EXTRA_OPEN_PLANNER = "openPlanner"
    const val EXTRA_ALERT_ID = "alertId"

    private const val TAG_GENTLE = "g"
    private const val TAG_ACTIVE = "a"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_GENTLE, "Gentle reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Normal notification with sound"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_STICKY, "Sticky reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pops up and stays until you tap Done"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_NAG, "Nagging reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pops up and alerts again until you tap Done; sounds even on vibrate"
                // Notification sound on the alarm stream, so ringer/vibrate mode doesn't mute it.
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
        nm(context).notify(TAG_GENTLE, o.key.hashCode(), n)
    }

    /** Sticky/Nagging: can't be swiped away; only the Done action removes it. Re-posting re-alerts. */
    fun showActive(context: Context, alert: ActiveAlert) {
        val channel = if (alert.style.strictness == Strictness.NAGGING) CH_NAG else CH_STICKY
        val ignored = alert.timesAlerted - 1
        val text = when {
            alert.escalated -> "Ignored $ignored times. Tap Done when it's done."
            ignored > 0 -> "Reminder ${alert.timesAlerted}. Tap Done when it's done."
            else -> "Tap Done when it's done."
        }
        val done = PendingIntent.getBroadcast(
            context, alert.id.toInt(),
            Intent(context, DoneReceiver::class.java).putExtra(EXTRA_ALERT_ID, alert.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(alert.message)
            .setContentText(text)
            .setWhen(alert.dueAt)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp(context, alert.opensPlanner))
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notif), "Done", done).build())
            .build()
        nm(context).notify(TAG_ACTIVE, alert.id.toInt(), n)
    }

    fun cancelActive(context: Context, alertId: Long) = nm(context).cancel(TAG_ACTIVE, alertId.toInt())

    fun isActiveShowing(context: Context, alertId: Long): Boolean =
        nm(context).activeNotifications.any { it.tag == TAG_ACTIVE && it.id == alertId.toInt() }

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

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
