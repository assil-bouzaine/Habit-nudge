package me.habitnudge.nudge

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.habitnudge.app
import me.habitnudge.data.NudgeApp
import me.habitnudge.data.NudgeStyle
import me.habitnudge.notify.Notifier

/**
 * Watches which app is in front (window-state changes only; no screen content is read).
 *
 * Opening a watched app nudges every time, where "opening" means arriving from the home screen,
 * from a locked screen, or from another app you'd been in for more than [HANDOFF_GRACE_MS].
 * System surfaces (shade, keyboard, share sheet) don't count as leaving. While you stay,
 * a "Still here?" notification checks in every [NudgeApp.checkInMin] minutes.
 */
class NudgeService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var watched: Map<String, NudgeApp> = emptyMap()
    private lateinit var card: NudgeCard

    /** The app in front, ignoring transient system surfaces. */
    private var current: String? = null

    /** The watched app you're in (or briefly stepped out of to another app). */
    private var session: Session? = null

    private class Session(val pkg: String, val startedAt: Long) {
        /** Set while you're in another app; null while you're in [pkg]. */
        var leftAt: Long? = null
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Locking the phone ends the session; unlocking back into the app counts as opening it.
            current = null
            endSession()
        }
    }

    override fun onServiceConnected() {
        instance = this
        card = NudgeCard(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        scope.launch {
            app.db.nudge().appsFlow().collect { list -> watched = list.associateBy { it.packageName } }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == current || isTransient(pkg)) return
        current = pkg
        val now = SystemClock.elapsedRealtime()

        val s = session
        if (s != null && s.pkg != pkg) {
            if (isHome(pkg)) endSession() else if (s.leftAt == null) leaveSession(s, now)
        }

        val target = watched[pkg]?.takeIf { it.enabled } ?: return
        val resumed = s != null && s.pkg == pkg && s.leftAt?.let { now - it < HANDOFF_GRACE_MS } == true
        if (resumed) {
            s!!.leftAt = null
            scheduleCheckIn(s, target)
        } else {
            endSession()
            val fresh = Session(pkg, now)
            session = fresh
            nudge(target)
            scheduleCheckIn(fresh, target)
        }
    }

    private fun leaveSession(s: Session, now: Long) {
        s.leftAt = now
        clearNudges()
    }

    private fun endSession() {
        session = null
        clearNudges()
    }

    /** You left the app: take down the card and nudge notifications right away instead of letting them time out. */
    private fun clearNudges() {
        handler.removeCallbacksAndMessages(null)
        if (::card.isInitialized) card.dismiss(fast = true)
        Notifier.cancelNudge(this)
        Notifier.cancelStillHere(this)
    }

    /** Next check-in at the next multiple of the interval since the session started. */
    private fun scheduleCheckIn(s: Session, target: NudgeApp) {
        handler.removeCallbacksAndMessages(null)
        val interval = target.checkInMin * 60_000L
        if (interval <= 0) return
        val elapsed = SystemClock.elapsedRealtime() - s.startedAt
        val delay = interval - (elapsed % interval)
        handler.postDelayed({
            if (session === s && s.leftAt == null && current == s.pkg) {
                val minutes = ((SystemClock.elapsedRealtime() - s.startedAt) / 60_000L).toInt()
                Notifier.showStillHere(this, target.label, minutes, app.prefs.nudgeGetMeOut)
                watched[s.pkg]?.let { scheduleCheckIn(s, it) }
            }
        }, delay)
    }

    private fun nudge(target: NudgeApp) {
        val s = session
        scope.launch {
            val message = Nudges.nextMessage(app, target.label)
            // Skip if you already left while the message was being picked.
            if (session === s && current == target.packageName) show(target.style, target.label, message, target.packageName)
        }
    }

    /** [pkg] supplies the icon on the card; null shows a generic one. */
    fun show(style: NudgeStyle, label: String, message: String, pkg: String? = null) {
        val prefs = app.prefs
        when (style) {
            NudgeStyle.CARD -> {
                val icon = pkg?.let { runCatching { packageManager.getApplicationIcon(it) }.getOrNull() }
                card.show(message, prefs.nudgeSeconds, prefs.nudgeGetMeOut, icon)
            }
            NudgeStyle.NOTIFICATION -> Notifier.showNudge(this, label, message, prefs.nudgeSeconds, prefs.nudgeGetMeOut)
        }
    }

    fun goHome() {
        card.dismiss()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Surfaces that sit on top of an app without leaving it: our own windows, the shade, keyboards, share sheets. */
    private fun isTransient(pkg: String): Boolean =
        pkg == packageName || pkg in SYSTEM_SURFACES || pkg in inputMethods()

    private fun inputMethods(): Set<String> =
        getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()

    private fun isHome(pkg: String): Boolean =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        ).any { it.activityInfo.packageName == pkg }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        runCatching { unregisterReceiver(screenOff) }
        endSession()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Popping out to another app and back within this time (e.g. sharing a post) isn't a new open. */
        private const val HANDOFF_GRACE_MS = 30_000L

        private val SYSTEM_SURFACES = setOf(
            "android", // system dialogs and the stock share sheet
            "com.android.systemui", // notification shade, volume, recents
            "com.huawei.android.internal.app", // EMUI share sheet
        )

        /** The running service, if the user has it switched on. */
        @Volatile
        var instance: NudgeService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, NudgeService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
