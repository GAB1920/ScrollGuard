package com.scrollguard.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Shared look for the app screen and the overlays. */
object Ui {
    const val BG = 0xFF0E0E13.toInt()
    const val CARD = 0xFF1A1A23.toInt()
    const val CARD_2 = 0xFF252531.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF9A9AAB.toInt()
    const val LIME = 0xFFB8FF5A.toInt()
    const val RED = 0xFFFF5A5A.toInt()

    val BOLD: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(ctx: Context, color: Int, radiusDp: Int, strokeColor: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(ctx, 2), strokeColor)
        }

    fun text(ctx: Context, s: String, sizeSp: Float, color: Int = TEXT, face: Typeface = MEDIUM) =
        TextView(ctx).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            typeface = face
        }

    /** Big pill button. style: "primary" (filled), "danger" (outlined red), "ghost" (text only). */
    fun button(ctx: Context, label: String, style: String, accent: Int = LIME, onClick: () -> Unit) =
        TextView(ctx).apply {
            text = label
            textSize = 16f
            typeface = BOLD
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 16), dp(ctx, 15), dp(ctx, 16), dp(ctx, 15))
            when (style) {
                "primary" -> { setTextColor(0xFF000000.toInt()); background = rounded(ctx, accent, 28) }
                "danger" -> { setTextColor(RED); background = rounded(ctx, 0x00000000, 28, RED) }
                else -> { setTextColor(MUTED); background = null }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(ctx, 10) }
            setOnClickListener { onClick() }
        }

    /** "Today  11 / 100" label with a rounded progress bar under it. */
    fun progressRow(ctx: Context, label: String, value: Int, limit: Int, accent: Int): View {
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(text(ctx, label, 14f, MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text(ctx, if (limit > 0) "$value / $limit" else "$value · no limit", 14f, TEXT, BOLD))
        box.addView(top)

        val p = if (limit > 0) (value.toFloat() / limit).coerceIn(0f, 1f) else 0f
        val track = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(ctx, CARD_2, 6)
            clipToOutline = true
        }
        track.addView(View(ctx).apply { background = rounded(ctx, accent, 6) },
            LinearLayout.LayoutParams(0, -1, p))
        track.addView(View(ctx), LinearLayout.LayoutParams(0, -1, 1f - p))
        box.addView(track, LinearLayout.LayoutParams(-1, dp(ctx, 10)).apply { topMargin = dp(ctx, 6) })
        return box
    }
}
