package me.habitnudge.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.app.NotificationManager
import me.habitnudge.app
import me.habitnudge.notify.Notifier
import me.habitnudge.nudge.NudgeService

/** One thing the app depends on, with the settings screens that fix it (tried in order). */
data class HealthItem(
    val key: String,
    val title: String,
    val detail: String,
    /** null = the app can't read this setting; the user confirms it by hand. */
    val ok: Boolean?,
    val fixIntents: List<Intent>,
)

object Health {
    fun items(context: Context): List<HealthItem> {
        val pkg = context.packageName
        val pkgUri = Uri.parse("package:$pkg")
        val nm = context.getSystemService(NotificationManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        val appNotifSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)

        fun channelItem(id: String, title: String, detail: String, minImportance: Int) = HealthItem(
            "ch_$id", title, detail,
            (nm.getNotificationChannel(id)?.importance ?: NotificationManager.IMPORTANCE_NONE) >= minImportance,
            listOf(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, id),
                appNotifSettings,
            ),
        )

        return listOf(
            HealthItem(
                "notifications", "Notifications allowed",
                "Without this, no reminder can show.",
                nm.areNotificationsEnabled(),
                listOf(appNotifSettings, appDetails),
            ),
            channelItem(
                Notifier.CH_GENTLE, "Gentle reminders make a sound",
                "The \"Gentle reminders\" category must keep its sound on.",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            channelItem(
                Notifier.CH_STICKY, "Sticky reminders pop up",
                "The \"Sticky reminders\" category must be set to pop up (urgent / banners).",
                NotificationManager.IMPORTANCE_HIGH,
            ),
            channelItem(
                Notifier.nagChannel(context), "Nagging reminders pop up",
                "The \"Nagging reminders\" category must be set to pop up (urgent / banners).",
                NotificationManager.IMPORTANCE_HIGH,
            ),
            channelItem(
                Notifier.CH_TAKEOVER, "Takeover can wake the phone",
                "The \"Takeover reminders\" category must stay urgent, or the card can't open over the lock screen.",
                NotificationManager.IMPORTANCE_HIGH,
            ),
            HealthItem(
                "alarm_volume", "Alarm volume up",
                "Nagging and Takeover play on the alarm volume, which vibrate mode doesn't mute.",
                audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0,
                listOf(Intent(Settings.ACTION_SOUND_SETTINGS)),
            ),
            HealthItem(
                "banners", "Banners and lock screen notifications on",
                "EMUI: in this app's notification settings, turn on Banners and Lock screen notifications. " +
                    "The app can't read these, so tick the box once done.",
                null,
                listOf(appNotifSettings, appDetails),
            ),
            HealthItem(
                "overlay", "Display over other apps",
                "Needed for Takeover cards on top of other apps.",
                Settings.canDrawOverlays(context),
                listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri), appDetails),
            ),
            HealthItem(
                "accessibility", "Nudge service (accessibility) on",
                "Needed for app-open nudges. EMUI sometimes switches it off after the app is killed.",
                NudgeService.isEnabled(context),
                listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            ),
            HealthItem(
                "battery", "Battery optimization off",
                "Stops Android delaying reminders while the phone sleeps.",
                pm.isIgnoringBatteryOptimizations(pkg),
                listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                ),
            ),
            HealthItem(
                "app_launch", "Huawei App launch: Manage manually",
                "Phone Manager > App launch > Habit Nudge: switch off \"Manage automatically\", then turn on " +
                    "Auto-launch, Secondary launch and Run in background. Tick the box once done.",
                null,
                listOf(
                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
                    appDetails,
                ),
            ),
        )
    }

    /** Opens the first fix screen that exists on this phone. */
    fun openFix(context: Context, item: HealthItem): Boolean {
        for (intent in item.fixIntents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    fun isConfirmed(context: Context, item: HealthItem) = context.app.prefs.isConfirmed(item.key)

    /** True if any check is failing (or a manual one isn't ticked). */
    fun hasProblem(context: Context): Boolean =
        items(context).any { !(it.ok ?: isConfirmed(context, it)) }
}
