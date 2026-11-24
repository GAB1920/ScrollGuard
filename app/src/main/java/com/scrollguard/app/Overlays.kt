package com.scrollguard.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.scrollguard.app.Ui.dp

/**
 * Things drawn on top of other apps: the counter pill, the tap panel, the
 * limit panel and short messages. Accessibility overlays don't need the
 * separate "Display over other apps" permission.
 */
class Overlays(private val ctx: Context, private val actions: Actions) {

    interface Actions {
        /** "Lock it": block reels for a while and leave. */
        fun lockNow()
        fun openApp()
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var pill: LinearLayout? = null
    private var pillFace: FaceView? = null
    private var pillCount: TextView? = null
    private var panel: View? = null
    private var message: TextView? = null

    private var prefs: Prefs? = null
    private var appName = ""

    val isPromptShowing get() = panel != null

    private fun dp(v: Int) = dp(ctx, v)

    private fun params(touchable: Boolean, fullScreen: Boolean) = WindowManager.LayoutParams(
        if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
        if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    )

    private fun remove(v: View?) {
        if (v != null) runCatching { wm.removeView(v) }
    }

    fun removeAll() {
        remove(pill); pill = null
        remove(panel); panel = null
        remove(message); message = null
    }

    // ---- Counter pill: face + number --------------------------------------

    fun updateBadge(visible: Boolean, prefs: Prefs, appName: String = this.appName) {
        this.prefs = prefs
        this.appName = appName
        if (!visible) {
            remove(pill); pill = null
            return
        }
        val d = Mood.dullness(prefs)

        val view = pill ?: LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(16), dp(5))
            val face = FaceView(ctx)
            addView(face, LinearLayout.LayoutParams(dp(34), dp(34)))
            val count = Ui.text(ctx, "0", 20f, Ui.TEXT, Ui.BOLD)
            addView(count, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
            pillFace = face
            pillCount = count
            setOnClickListener { showInfoPanel() }
            wm.addView(this, params(touchable = true, fullScreen = false).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(34)
            })
            pill = this
        }
        pillFace?.dullness = d
        pillCount?.text = prefs.countToday().toString()
        view.background = Ui.rounded(ctx, 0xE6121218.toInt(), 24, Mood.accent(d))
    }

    // ---- Panels ------------------------------------------------------------

    /** Opened by tapping the pill. */
    fun showInfoPanel() {
        val p = prefs ?: return
        val d = Mood.dullness(p)
        showPanel(p, title = null, dismissOnOutsideTap = true, buttons = listOf(
            Ui.button(ctx, "Keep going", "primary", Mood.accent(d)) { closePanel() },
            Ui.button(ctx, "🔒  Lock reels for ${Prefs.QUICK_LOCK_MINUTES} min", "danger") {
                closePanel(); actions.lockNow()
            },
            Ui.button(ctx, "Open ScrollGuard", "ghost") { closePanel(); actions.openApp() },
        ))
    }

    /** Shown when a daily or hourly limit is reached. */
    fun showLimitPanel(prefs: Prefs, title: String, appName: String, onKeep: () -> Unit, onClose: () -> Unit) {
        if (panel != null) return
        showPanel(prefs, title = title, dismissOnOutsideTap = false, buttons = listOf(
            Ui.button(ctx, "Close $appName", "primary", Ui.LIME) { closePanel(); onClose() },
            Ui.button(ctx, "Keep going (+${Prefs.SNOOZE_REELS} reels)", "ghost") { closePanel(); onKeep() },
        ))
    }

    private fun closePanel() {
        remove(panel); panel = null
    }

    private fun showPanel(p: Prefs, title: String?, dismissOnOutsideTap: Boolean, buttons: List<View>) {
        closePanel()
        val d = Mood.dullness(p)
        val accent = Mood.accent(d)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
            background = Ui.rounded(ctx, Ui.CARD, 32)
            isClickable = true // taps on the card don't close it
        }
        fun centered(v: View, top: Int = 0) = card.addView(v, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(top) })
        fun full(v: View, top: Int = 0) = card.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })

        if (title != null) centered(Ui.text(ctx, title, 22f, Ui.TEXT, Ui.BOLD))
        card.addView(FaceView(ctx).apply { dullness = d },
            LinearLayout.LayoutParams(dp(120), dp(120)).apply { topMargin = dp(if (title != null) 12 else 0) })
        centered(Ui.text(ctx, Mood.label(d).uppercase(), 13f, accent, Ui.BOLD).apply { letterSpacing = 0.15f }, 8)
        centered(Ui.text(ctx, p.countToday().toString(), 64f, Ui.TEXT, Ui.BOLD))
        centered(Ui.text(ctx, "reels today", 15f, Ui.MUTED))
        centered(Ui.text(ctx, Mood.quip(d), 15f, Ui.TEXT).apply { gravity = Gravity.CENTER }, 12)
        full(Ui.progressRow(ctx, "Today", p.countToday(), p.dailyLimit, accent), 20)
        full(Ui.progressRow(ctx, "This hour", p.countLastHour(), p.hourlyLimit, accent), 12)
        card.addView(View(ctx), LinearLayout.LayoutParams(1, dp(8)))
        buttons.forEach { card.addView(it) }

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(0xB3000000.toInt())
            if (dismissOnOutsideTap) setOnClickListener { closePanel() }
        }
        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(28))
        })
        wm.addView(root, params(touchable = true, fullScreen = true))
        panel = root
    }

    // ---- Short message (locks) ---------------------------------------------

    private val hideMessage = Runnable { remove(message); message = null }

    fun flashMessage(text: String) {
        handler.removeCallbacks(hideMessage)
        val tv = message ?: Ui.text(ctx, "", 17f, Ui.TEXT, Ui.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(18), dp(24), dp(18))
            background = Ui.rounded(ctx, Ui.CARD, 24, Ui.LIME)
            wm.addView(this, params(touchable = false, fullScreen = false).apply { gravity = Gravity.CENTER })
            message = this
        }
        tv.text = text
        handler.postDelayed(hideMessage, 2500)
    }
}
