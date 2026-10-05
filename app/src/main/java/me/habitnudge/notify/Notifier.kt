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
import me.habitnudge.nudge.NudgeActionReceiver
import me.habitnudge.schedule.DoneReceiver
import me.habitnudge.schedule.Occurrence
import me.habitnudge.schedule.RescheduleActivity
import me.habitnudge.takeover.TakeoverActivity

object Notifier {
    // A channel's sound and importance can't be changed after creation; bump the id to change them.
    const val CH_GENTLE = "gentle_v1"
    const val CH_STICKY = "sticky_v1"
    const val CH_NAG = "nag_v1"
    const val CH_TAKEOVER = "takeover_v1"
    const val CH_NUDGE = "nudge_v1"

    const val EXTRA_OPEN_PLANNER = "openPlanner"
    const val EXTRA_ALERT_ID = "alertId"

    /** Accent for the small icon and action buttons; the app's Facebook-style blue. */
    private const val BRAND_BLUE = 0xFF1877F2.toInt()

    private const val TAG_GENTLE = "g"
    private const val TAG_ACTIVE = "a"
    private const val TAG_NUDGE = "n"

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
        nm.createNotificationChannel(
            NotificationChannel(CH_TAKEOVER, "Takeover reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Full-screen card that wakes the phone; the card plays the alarm tone"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_NUDGE, "App-open nudges", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Silent banner when you open a watched app (if set to notification style)"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun showNudge(context: Context, appLabel: String, message: String, seconds: Int, showGetMeOut: Boolean) {
        val n = nudgeBuilder(context, appLabel, message, showGetMeOut)
            .setTimeoutAfter(seconds.coerceAtLeast(1) * 1000L)
            .build()
        nm(context).notify(TAG_NUDGE, 0, n)
    }

    /** Periodic check-in while you stay in a watched app; removed when you leave it. */
    fun showStillHere(context: Context, appLabel: String, minutes: Int, showGetMeOut: Boolean) {
        val n = nudgeBuilder(
            context, "Still in $appLabel?",
            "$minutes minutes gone. That's enough. Get out.",
            showGetMeOut,
        ).build()
        nm(context).notify(TAG_NUDGE, 1, n)
    }

    private fun nudgeBuilder(context: Context, title: String, message: String, showGetMeOut: Boolean) =
        Notification.Builder(context, CH_NUDGE)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(BRAND_BLUE)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .apply {
                if (showGetMeOut) {
                    val out = PendingIntent.getBroadcast(
                        context, 0, Intent(context, NudgeActionReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
                    )
                    addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notif), "Get me out", out).build())
                }
            }

    fun showServiceOff(context: Context) {
        val fix = PendingIntent.getActivity(
            context, 3,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CH_GENTLE)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(BRAND_BLUE)
            .setContentTitle("App-open nudges are off")
            .setContentText("EMUI switched off the nudge service. Tap to turn it back on.")
            .setAutoCancel(true)
            .setContentIntent(fix)
            .build()
        nm(context).notify(TAG_NUDGE, 2, n)
    }

    fun cancelNudge(context: Context) = nm(context).cancel(TAG_NUDGE, 0)

    fun cancelStillHere(context: Context) = nm(context).cancel(TAG_NUDGE, 1)

    fun showGentle(context: Context, o: Occurrence) {
        val n = Notification.Builder(context, CH_GENTLE)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(BRAND_BLUE)
            .setContentTitle(o.message)
            .setWhen(o.dueAt)
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, o.opensPlanner))
            .addAction(rescheduleAction(context, RescheduleActivity.forGentle(context, o)))
            .build()
        nm(context).notify(TAG_GENTLE, o.key.hashCode(), n)
    }

    fun cancelGentle(context: Context, key: String) = nm(context).cancel(TAG_GENTLE, key.hashCode())

    /** Opens the "move it to later" picker; each intent carries a unique data URI so PendingIntents don't collide. */
    private fun rescheduleAction(context: Context, intent: Intent): Notification.Action {
        val pi = PendingIntent.getActivity(
            context, 0, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notif), "Reschedule", pi).build()
    }

    /**
     * Sticky/Nagging/Takeover: can't be swiped away; only Done removes it. Re-posting re-alerts.
     * A Takeover's notification carries the full-screen intent that opens the card over the lock screen,
     * unless it's deferred (during a call), when it's just a reminder in the shade.
     */
    fun showActive(context: Context, alert: ActiveAlert) {
        val takeover = alert.style.strictness == Strictness.TAKEOVER
        val channel = when (alert.style.strictness) {
            Strictness.NAGGING -> CH_NAG
            Strictness.TAKEOVER -> CH_TAKEOVER
            else -> CH_STICKY
        }
        val text = when {
            alert.escalated -> "Ignored ${alert.timesAlerted} times. Tap Done when it's done."
            alert.timesAlerted > 1 -> "Reminder ${alert.timesAlerted}. Tap Done when it's done."
            else -> "Tap Done when it's done."
        }
        val done = PendingIntent.getBroadcast(
            context, alert.id.toInt(),
            Intent(context, DoneReceiver::class.java).putExtra(EXTRA_ALERT_ID, alert.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val content = if (takeover) openTakeover(context) else openApp(context, alert.opensPlanner)
        val n = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(BRAND_BLUE)
            .setContentTitle(alert.message)
            .setContentText(text)
            .setWhen(alert.dueAt)
            .setShowWhen(true)
            .setCategory(if (takeover) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(content)
            .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notif), "Done", done).build())
            .addAction(rescheduleAction(context, RescheduleActivity.forAlert(context, alert.id)))
            .apply { if (takeover && alert.nextNagAt == null) setFullScreenIntent(content, true) }
            .build()
        nm(context).notify(TAG_ACTIVE, alert.id.toInt(), n)
    }

    private fun openTakeover(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 2,
        Intent(context, TakeoverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
