package com.scrollguard.app

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.floor
import kotlin.math.min

/**
 * "Blobby", the brain buddy. dullness 0 = bright, happy, wide awake;
 * 1 = grey, droopy eyelids, unfocused eyes, frown, melting and drooling.
 */
class FaceView(context: Context) : View(context) {

    var dullness = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val clip = Path()
    private val argb = ArgbEvaluator()

    // Fresh lime -> sunny yellow -> tired tan -> zombie grey
    private val stops = intArrayOf(
        0xFF8BFF5A.toInt(), 0xFFFFE14D.toInt(), 0xFFD9A85B.toInt(), 0xFF8F948A.toInt(),
    )

    private fun bodyColor(d: Float): Int {
        val seg = d * (stops.size - 1)
        val i = floor(seg).toInt().coerceAtMost(stops.size - 2)
        return argb.evaluate(seg - i, stops[i], stops[i + 1]) as Int
    }

    private fun darker(c: Int, f: Float = 0.55f) =
        Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = s * 0.40f
        val d = dullness
        val body = bodyColor(d)
        val line = darker(body)

        paint.style = Paint.Style.FILL

        // Melting drips under the face
        if (d > 0.55f) {
            val k = (d - 0.55f) / 0.45f
            paint.color = body
            c.drawRoundRect(cx - s * 0.20f, cy, cx - s * 0.12f, cy + r + s * 0.08f * k, s, s, paint)
            c.drawRoundRect(cx + s * 0.08f, cy, cx + s * 0.15f, cy + r + s * 0.05f * k, s, s, paint)
        }

        // Head
        paint.color = body
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.035f
        paint.color = line
        c.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.FILL

        // Rosy cheeks while fresh
        if (d < 0.45f) {
            paint.color = Color.argb(((0.45f - d) / 0.45f * 140).toInt(), 255, 105, 140)
            c.drawCircle(cx - s * 0.23f, cy + s * 0.07f, s * 0.055f, paint)
            c.drawCircle(cx + s * 0.23f, cy + s * 0.07f, s * 0.055f, paint)
        }

        // Eyes
        val ey = cy - s * 0.07f
        val er = s * 0.085f
        val lid = (d * 1.15f - 0.15f).coerceIn(0f, 0.8f)
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * s * 0.15f
            paint.color = Color.WHITE
            c.drawCircle(ex, ey, er, paint)

            // Pupils drift apart and down as focus goes
            paint.color = 0xFF16161C.toInt()
            c.drawCircle(
                ex + side * d * er * 0.4f,
                ey + d * er * 0.35f,
                er * (0.55f - 0.15f * d),
                paint,
            )
            // Sparkle while fresh
            if (d < 0.3f) {
                paint.color = Color.WHITE
                c.drawCircle(ex - er * 0.18f, ey - er * 0.2f, er * 0.16f, paint)
            }

            // Heavy eyelids
            if (lid > 0f) {
                val lidY = ey - er + 2 * er * lid
                c.save()
                clip.reset()
                clip.addCircle(ex, ey, er, Path.Direction.CW)
                c.clipPath(clip)
                paint.color = body
                c.drawRect(ex - er, ey - er, ex + er, lidY, paint)
                c.restore()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.025f
                paint.color = line
                c.drawLine(ex - er * 0.95f, lidY, ex + er * 0.95f, lidY, paint)
                paint.style = Paint.Style.FILL
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.02f
            paint.color = line
            c.drawCircle(ex, ey, er, paint)
            paint.style = Paint.Style.FILL
        }

        // Mouth: big smile -> flat -> frown
        val my = cy + s * 0.17f
        val mw = s * 0.12f
        val bend = s * (0.10f - 0.16f * d)
        path.reset()
        path.moveTo(cx - mw, my)
        path.quadTo(cx, my + bend, cx + mw, my)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.035f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = line
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL

        // Drool
        if (d > 0.8f) {
            val k = (d - 0.8f) / 0.2f
            paint.color = 0xCC8FD8FF.toInt()
            c.drawRoundRect(
                cx + mw * 0.5f - s * 0.022f, my - s * 0.01f,
                cx + mw * 0.5f + s * 0.022f, my + s * 0.11f * k,
                s, s, paint,
            )
        }
    }
}

/** How "cooked" your brain is, from your counts and limits. */
object Mood {
    fun dullness(p: Prefs): Float {
        val day = p.countToday()
        val hour = p.countLastHour()
        val a = if (p.dailyLimit > 0) day.toFloat() / p.dailyLimit else day / 100f
        val b = if (p.hourlyLimit > 0) hour.toFloat() / p.hourlyLimit else 0f
        return maxOf(a, b).coerceIn(0f, 1f)
    }

    fun label(d: Float) = when {
        d < 0.25f -> "Fresh brain"
        d < 0.5f -> "Still sharp"
        d < 0.75f -> "Getting foggy"
        d < 1f -> "Brain melting"
        else -> "Fully cooked"
    }

    fun quip(d: Float) = when {
        d < 0.25f -> "Blobby is wide awake."
        d < 0.5f -> "A few reels in. All good."
        d < 0.75f -> "Blobby's eyes are getting heavy…"
        d < 1f -> "Blobby is turning to mush. Maybe stop?"
        else -> "Blobby has left the chat."
    }

    /** Lime when fresh, amber in the middle, red when cooked. */
    fun accent(d: Float) = when {
        d < 0.5f -> 0xFFB8FF5A.toInt()
        d < 0.85f -> 0xFFFFB84D.toInt()
        else -> 0xFFFF5A5A.toInt()
    }
}
