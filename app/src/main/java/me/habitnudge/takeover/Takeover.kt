package me.habitnudge.takeover

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import me.habitnudge.data.Prefs

object Takeover {
    /** Cellular or VoIP call in progress or ringing: Takeover waits until it ends. */
    fun inCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }

    /** Brings up the card. Allowed from the background on Android 10 because of "display over other apps". */
    fun launch(context: Context) {
        context.startActivity(
            Intent(context, TakeoverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** When each alert's card was first shown, so the Done countdown survives the card being relaunched. */
    val shownAt = mutableMapOf<Long, Long>()
}

/** Looping alarm tone + vibration for a Takeover, capped so a forgotten phone doesn't ring all day. */
object AlarmSound {
    private const val MAX_RING_MS = 2 * 60_000L

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    /** Alerts that already rang, so relaunching the card doesn't restart the tone. */
    private val rang = mutableSetOf<Long>()

    private val alarmAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    @Synchronized
    fun ringFor(context: Context, alertId: Long) {
        if (alertId in rang) return
        rang += alertId
        stop()
        val ctx = context.applicationContext
        val default = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        val chosen = Prefs(ctx).alarmToneUri?.let(Uri::parse)
        // The chosen sound may have been deleted since; fall back to the phone's alarm rather than ring nothing.
        player = (if (chosen != null) play(ctx, chosen) else null) ?: play(ctx, default)
        vibrator = ctx.getSystemService(Vibrator::class.java)?.also {
            // Deprecated on Android 13+, but it's the call that routes vibration as an alarm on Android 10.
            @Suppress("DEPRECATION")
            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0), alarmAttrs)
        }
        handler.postDelayed(::stop, MAX_RING_MS)
    }

    private fun play(ctx: Context, uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(alarmAttrs)
            setDataSource(ctx, uri)
            isLooping = true
            prepare()
            start()
        }
    } catch (e: Exception) {
        null
    }

    /** Let this alert ring again next time it's shown (it was deferred, e.g. by a call). */
    @Synchronized
    fun allowRingAgain(alertId: Long) {
        rang -= alertId
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.run {
            runCatching { stop() }
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }
}
