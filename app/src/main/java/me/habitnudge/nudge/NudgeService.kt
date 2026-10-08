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
import java.text.DateFormat
import java.util.Date
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
 * Watches which app is in front, using only the package name on accessibility events; no screen
 * content is read. Window-state events alone aren't enough: EMUI sends none when you return to an
 * app from recents, so content/scroll/click events from the app count as "it's in front" too.
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

    /** Watched app currently in front, and since when, for time-spent stats. */
    private var fgPkg: String? = null
    private var fgSince = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Locking the phone ends the session; unlocking back into the app counts as opening it.
            current = null
            trackForeground(null)
            endSession()
        }
    }

    override fun onServiceConnected() {
        // Apply the event list at runtime too: Android can keep a stale copy of the XML config across app updates.
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED
            notificationTimeout = 200
        }
        instance = this
        card = NudgeCard(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        scope.launch {
            app.db.nudge().appsFlow().collect { list -> watched = list.associateBy { it.packageName } }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Any event tells us which app is in front. Most are from the current app: return fast.
        val pkg = event.packageName?.toString() ?: return
        if (pkg == current || isTransient(pkg)) return
        current = pkg
        trackForeground(pkg)
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

    /** Adds time spent in the previous watched app (if any) and starts timing [pkg] if it's watched. */
    private fun trackForeground(pkg: String?) {
        val now = SystemClock.elapsedRealtime()
        fgPkg?.let { prev -> recordStat(prev, ms = now - fgSince) }
        fgPkg = pkg?.takeIf { it in watched }
        fgSince = now
    }

    /** Count time so far without waiting for you to leave (the Stats screen calls this so "today" is current). */
    fun flushForeground() = trackForeground(fgPkg)

    private fun recordStat(pkg: String, opens: Int = 0, getOuts: Int = 0, stays: Int = 0, checkIns: Int = 0, ms: Long = 0) {
        app.scope.launch { Stats.record(app, pkg, opens, getOuts, stays, checkIns, ms) }
    }

    /** Next check-in at the next multiple of the interval since the session started; tighter at bedtime. */
    private fun scheduleCheckIn(s: Session, target: NudgeApp) {
        handler.removeCallbacksAndMessages(null)
        val bedtime = Bedtime.isNow(app.prefs)
        val interval = (if (bedtime) app.prefs.bedtimeCheckInMin else target.checkInMin) * 60_000L
        if (interval <= 0) return
        val elapsed = SystemClock.elapsedRealtime() - s.startedAt
        val delay = interval - (elapsed % interval)
        handler.postDelayed({
            if (session === s && s.leftAt == null && current == s.pkg) {
                val minutes = ((SystemClock.elapsedRealtime() - s.startedAt) / 60_000L).toInt()
                recordStat(s.pkg, checkIns = 1)
                if (app.prefs.alertsPaused) {
                    watched[s.pkg]?.let { scheduleCheckIn(s, it) }
                    return@postDelayed
                }
                if (Bedtime.isNow(app.prefs)) {
                    showBedtime(target.label, target.packageName, "$minutes minutes in ${target.label}, at ${nowText()}. Go to sleep.")
                } else {
                    Notifier.showStillHere(this, target.label, minutes, app.prefs.nudgeGetMeOut)
                }
                watched[s.pkg]?.let { scheduleCheckIn(s, it) }
            }
        }, delay)
    }

    private fun nudge(target: NudgeApp) {
        val s = session
        recordStat(target.packageName, opens = 1)
        if (app.prefs.alertsPaused) return
        if (Bedtime.isNow(app.prefs)) {
            showBedtime(target.label, target.packageName, Bedtime.message(app.prefs, target.label, nowText()))
            return
        }
        scope.launch {
            val message = Nudges.nextMessage(app, target.label)
            // Skip if you already left while the message was being picked.
            if (session === s && current == target.packageName) show(target.style, target.label, message, target.packageName)
        }
    }

    /** Full-screen bedtime card: no auto-fade; "Stay anyway" unlocks after a few seconds. */
    fun showBedtime(label: String, pkg: String?, message: String) {
        if (app.prefs.alertsPaused) return
        val icon = pkg?.let { runCatching { packageManager.getApplicationIcon(it) }.getOrNull() }
        card.showBedtime(nowText(), message, app.prefs.bedtimeStayLockSec, icon)
    }

    private fun nowText(): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())

    /** "Stay anyway" tapped on a card. */
    fun onStay() {
        (session?.pkg ?: current)?.takeIf { it in watched }?.let { recordStat(it, stays = 1) }
    }

    /** [pkg] supplies the icon on the card; null shows a generic one. */
    fun show(style: NudgeStyle, label: String, message: String, pkg: String? = null) {
        val prefs = app.prefs
        if (prefs.alertsPaused) return
        when (style) {
            NudgeStyle.CARD -> {
                val icon = pkg?.let { runCatching { packageManager.getApplicationIcon(it) }.getOrNull() }
                card.show(message, prefs.nudgeSeconds, prefs.nudgeGetMeOut, icon)
            }
            NudgeStyle.NOTIFICATION -> Notifier.showNudge(this, label, message, prefs.nudgeSeconds, prefs.nudgeGetMeOut)
        }
    }

    fun goHome() {
        (session?.pkg ?: current)?.takeIf { it in watched }?.let { recordStat(it, getOuts = 1) }
        card.dismiss()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Surfaces that sit on top of an app without leaving it: our own windows, the shade, keyboards, share sheets. */
    private fun isTransient(pkg: String): Boolean =
        pkg == packageName || pkg in SYSTEM_SURFACES || pkg in cachedPackages().inputMethods

    private fun isHome(pkg: String): Boolean = pkg in cachedPackages().homes

    private class KnownPackages(val inputMethods: Set<String>, val homes: Set<String>, val at: Long)
    private var known: KnownPackages? = null

    /** Keyboards and launchers, refreshed every few minutes: these checks now run on many more events. */
    private fun cachedPackages(): KnownPackages {
        val now = SystemClock.elapsedRealtime()
        known?.takeIf { now - it.at < 5 * 60_000L }?.let { return it }
        val imes = getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
        val homes = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
        ).map { it.activityInfo.packageName }.toSet()
        return KnownPackages(imes, homes, now).also { known = it }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        runCatching { unregisterReceiver(screenOff) }
        trackForeground(null)
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
