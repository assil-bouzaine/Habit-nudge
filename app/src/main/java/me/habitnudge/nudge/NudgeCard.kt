package me.habitnudge.nudge

import android.accessibilityservice.AccessibilityService
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A card in the middle of the screen over whatever app is open. Drawn as an accessibility overlay,
 * so it needs no "display over other apps" permission. It never takes focus, and touches outside it
 * still reach the app underneath; the dim behind it is only visual.
 */
class NudgeCard(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null

    private class Palette(
        val surface: Int, val text: Int, val muted: Int, val accent: Int, val onAccent: Int, val tonal: Int,
    )

    private fun palette(): Palette {
        val night = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // Same blues as the app theme (BrandBlue in Theme.kt).
        return if (night) {
            Palette(0xFF161B22.toInt(), 0xFFE6EAF0.toInt(), 0xFFA3AEBD.toInt(), 0xFF6AA6FF.toInt(), 0xFF002A66.toInt(), 0x336AA6FF)
        } else {
            Palette(0xFFFFFFFF.toInt(), 0xFF101828.toInt(), 0xFF5A6474.toInt(), 0xFF1877F2.toInt(), 0xFFFFFFFF.toInt(), 0x1F1877F2)
        }
    }

    fun show(message: String, seconds: Int, showGetMeOut: Boolean, icon: Drawable?) {
        removeNow()
        val p = palette()
        val durationMs = seconds.coerceAtLeast(1) * 1000L
        val bar = View(service).apply {
            setBackgroundColor(p.accent)
            pivotX = 0f
        }
        val card = buildCard(p, message, showGetMeOut, icon, bar)
        val width = minOf(service.resources.displayMetrics.widthPixels - dp(48), dp(360))
        val root = FrameLayout(service).apply {
            setPadding(dp(16), dp(16), dp(16), dp(16)) // room for the shadow
            addView(card, FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.25f
        }
        wm.addView(root, params)
        view = root

        card.alpha = 0f
        card.scaleX = 0.9f
        card.scaleY = 0.9f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setInterpolator(OvershootInterpolator(1.2f)).setDuration(260).start()
        bar.animate().scaleX(0f).setInterpolator(LinearInterpolator()).setDuration(durationMs).start()
        handler.postDelayed({ dismiss() }, durationMs)
    }

    /** Fades out, then removes the card. [fast] is for when you've left the app. */
    fun dismiss(fast: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        val root = view ?: return
        val card = (root as FrameLayout).getChildAt(0)
        card.animate().cancel()
        card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f)
            .setDuration(if (fast) 100 else 220)
            .withEndAction { if (view === root) removeNow() }
            .start()
    }

    private fun removeNow() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun buildCard(p: Palette, message: String, showGetMeOut: Boolean, icon: Drawable?, bar: View): View =
        LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(p.surface, dp(28))
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            elevation = dp(12).toFloat()

            addView(LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(24), dp(24), dp(24), dp(20))

                if (icon != null) {
                    addView(ImageView(service).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(56), dp(56)))
                } else {
                    addView(TextView(service).apply {
                        text = "🌿" // herb emoji
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
                    })
                }
                addView(TextView(service).apply {
                    text = "REALITY CHECK"
                    setTextColor(p.muted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    letterSpacing = 0.12f
                    typeface = Typeface.DEFAULT_BOLD
                }, marginTop(dp(14)))
                addView(TextView(service).apply {
                    text = message
                    gravity = Gravity.CENTER
                    setTextColor(p.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                    setLineSpacing(0f, 1.2f)
                }, marginTop(dp(8)))

                addView(LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    // Leaving is the bold, obvious choice; staying is the quiet one you have to mean.
                    if (showGetMeOut) {
                        addView(pill("Stay anyway", p.tonal, p.accent, p) { dismiss() },
                            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(12) })
                        addView(pill("Get me out", p.accent, p.onAccent, p) {
                            (service as? NudgeService)?.goHome() ?: dismiss()
                        }, LinearLayout.LayoutParams(0, dp(48), 1f))
                    } else {
                        addView(pill("OK", p.accent, p.onAccent, p) { dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f))
                    }
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(22)
                })
            })
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)))
        }

    private fun pill(label: String, bg: Int, fg: Int, p: Palette, onClick: () -> Unit) = TextView(service).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(fg)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        background = RippleDrawable(ColorStateList.valueOf(p.muted and 0x40FFFFFF), rounded(bg, dp(24)), null)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun marginTop(px: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = px }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()
}
