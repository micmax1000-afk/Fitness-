package com.micmax.cibo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

// ======================= icone =======================

/** Un'icona di [Icons], disegnata a linee nel colore [color]. */
class IconView(c: Context, emoji: String, var color: Int) : View(c) {
    private val key = Icons.key(emoji)
    private val digit = Icons.digit(emoji)
    private val path = Icons.PATHS[key]?.let { buildPath(it) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private val scaled = Path()
    private val m = android.graphics.Matrix()

    override fun onDraw(cv: Canvas) {
        val s = minOf(width, height).toFloat(); if (s <= 0) return
        val k = s / 24f
        if (digit != null) {
            stroke.color = color; stroke.strokeWidth = 1.8f * k
            cv.drawCircle(width / 2f, height / 2f, 9f * k, stroke)
            text.color = color; text.textSize = 11f * k
            cv.drawText(digit, width / 2f, height / 2f + 4f * k, text)
            return
        }
        val p = path ?: return
        m.reset(); m.setScale(k, k); m.postTranslate((width - s) / 2, (height - s) / 2)
        p.transform(m, scaled)
        stroke.color = color; stroke.strokeWidth = 1.8f * k
        cv.drawPath(scaled, stroke)
    }

    companion object {
        private val cache = HashMap<String, Path>()
        /** Trasforma i comandi di [Icons] in un Path di Android. */
        fun buildPath(d: String): Path = cache.getOrPut(d) {
            val p = Path(); var x = 0f; var y = 0f; var sx = 0f; var sy = 0f
            for ((cmd, n) in Icons.commands(d)) {
                val rel = cmd.isLowerCase()
                fun ax(v: Float) = if (rel) x + v else v
                fun ay(v: Float) = if (rel) y + v else v
                when (cmd.uppercaseChar()) {
                    'M' -> { var i = 0; while (i + 1 < n.size) { val nx = ax(n[i]); val ny = ay(n[i + 1]); if (i == 0) { p.moveTo(nx, ny); sx = nx; sy = ny } else p.lineTo(nx, ny); x = nx; y = ny; i += 2 } }
                    'L' -> { var i = 0; while (i + 1 < n.size) { x = ax(n[i]); y = ay(n[i + 1]); p.lineTo(x, y); i += 2 } }
                    'H' -> n.forEach { v -> x = ax(v); p.lineTo(x, y) }
                    'V' -> n.forEach { v -> y = ay(v); p.lineTo(x, y) }
                    'C' -> { var i = 0; while (i + 5 < n.size) { val x1 = ax(n[i]); val y1 = ay(n[i + 1]); val x2 = ax(n[i + 2]); val y2 = ay(n[i + 3]); val ex = ax(n[i + 4]); val ey = ay(n[i + 5]); p.cubicTo(x1, y1, x2, y2, ex, ey); x = ex; y = ey; i += 6 } }
                    'Q' -> { var i = 0; while (i + 3 < n.size) { val x1 = ax(n[i]); val y1 = ay(n[i + 1]); val ex = ax(n[i + 2]); val ey = ay(n[i + 3]); p.quadTo(x1, y1, ex, ey); x = ex; y = ey; i += 4 } }
                    'Z' -> { p.close(); x = sx; y = sy }
                    'O' -> if (n.size >= 3) p.addCircle(n[0], n[1], n[2], Path.Direction.CW)
                    'R' -> if (n.size >= 5) p.addRoundRect(RectF(n[0], n[1], n[0] + n[2], n[1] + n[3]), n[4], n[4], Path.Direction.CW)
                }
            }
            p
        }
    }
}

private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

/**
 * L'icona dentro il suo riquadro tondo: disegnata se è nel set dell'app, altrimenti l'emoji com'è.
 * [boxDp] lato del riquadro, [onAccent] = icona chiara su fondo pieno (pulsanti verdi).
 */
fun Context.iconBox(emoji: String, boxDp: Int, onAccent: Boolean = false): View {
    val color = if (onAccent) col(R.color.onaccent) else Icons.tint(emoji) ?: col(R.color.accent)
    val box = FrameLayout(this).apply {
        background = if (onAccent) null else roundBg(withAlpha(color, 0x24), boxDp * 3 / 10)
        layoutParams = LinearLayout.LayoutParams(dp(boxDp), dp(boxDp))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    val inner = (boxDp * 0.56).toInt()
    if (Icons.has(emoji)) box.addView(IconView(this, emoji, color), FrameLayout.LayoutParams(dp(inner), dp(inner), Gravity.CENTER))
    else box.addView(TextView(this).apply { text = emoji; textSize = boxDp * 0.45f; gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
    return box
}

// ======================= anelli =======================

/** Un anello che si riempie (come negli orologi fitness); oltre il 100% fa un secondo giro più scuro. */
class RingView(c: Context) : View(c) {
    var progress = 0.0; set(v) { field = v; invalidate() }
    var color = c.col(R.color.accent)
    var track = c.col(R.color.track)
    var thicknessDp = 9f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val r = RectF()
    override fun onDraw(cv: Canvas) {
        val t = thicknessDp * resources.displayMetrics.density
        val s = minOf(width, height) - t
        r.set((width - s) / 2, (height - s) / 2, (width + s) / 2, (height + s) / 2)
        p.strokeWidth = t; p.color = track; cv.drawArc(r, 0f, 360f, false, p)
        val f = progress.coerceAtLeast(0.0)
        if (f <= 0.0) return
        p.color = color; cv.drawArc(r, -90f, (360 * minOf(f, 1.0)).toFloat().coerceAtLeast(2f), false, p)
        if (f > 1.0) { p.color = darker(color); cv.drawArc(r, -90f, (360 * minOf(f - 1.0, 1.0)).toFloat(), false, p) }
    }
    private fun darker(c: Int): Int { val h = FloatArray(3); android.graphics.Color.colorToHSV(c, h); h[2] *= 0.72f; return android.graphics.Color.HSVToColor(h) }
}

/** Un anello con al centro un valore grande e una riga piccola; sotto, l'etichetta. */
fun Context.ringStat(label: String, value: String, sub: String?, progress: Double, color: Int, sizeDp: Int = 92, onClick: (() -> Unit)? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
        val frame = FrameLayout(this@ringStat)
        frame.addView(RingView(this@ringStat).apply { this.progress = progress; this.color = color }, FrameLayout.LayoutParams(-1, -1))
        frame.addView(LinearLayout(this@ringStat).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(TextView(this@ringStat).apply { text = value; textSize = if (value.length > 5) 15f else 18f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)); gravity = Gravity.CENTER })
            if (sub != null) addView(TextView(this@ringStat).apply { text = sub; textSize = 10.5f; setTextColor(col(R.color.muted)); gravity = Gravity.CENTER })
        }, FrameLayout.LayoutParams(-1, -1))
        addView(frame, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
        addView(TextView(this@ringStat).apply { text = label; textSize = 13f; setTextColor(col(R.color.text)); setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
    }

// ======================= coriandoli =======================

/** Coriandoli che cadono per qualche secondo sopra tutta la schermata, poi spariscono da soli. */
class ConfettiView(c: Context) : View(c) {
    private class Bit(var x: Float, var y: Float, val vx: Float, val vy: Float, var a: Float, val va: Float, val color: Int, val w: Float, val h: Float)
    private val rnd = Random(System.nanoTime())
    private val colors = intArrayOf(0xFF2EA66A.toInt(), 0xFF38BDF8.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFF8B8CF6.toInt(), 0xFFF472B6.toInt())
    private val bits = ArrayList<Bit>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var start = 0L
    private val life = 3200L

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (bits.isNotEmpty() || w == 0) return
        val d = resources.displayMetrics.density
        repeat(110) {
            bits += Bit(rnd.nextFloat() * w, -rnd.nextFloat() * h * 0.5f, (rnd.nextFloat() - 0.5f) * 120 * d, (170 + rnd.nextFloat() * 230) * d,
                rnd.nextFloat() * 360, (rnd.nextFloat() - 0.5f) * 720, colors[rnd.nextInt(colors.size)], (5 + rnd.nextFloat() * 5) * d, (8 + rnd.nextFloat() * 6) * d)
        }
        start = System.currentTimeMillis()
    }

    override fun onDraw(cv: Canvas) {
        val t = (System.currentTimeMillis() - start).coerceAtLeast(0)
        if (start == 0L) { postInvalidateOnAnimation(); return }
        if (t > life) { (parent as? ViewGroup)?.removeView(this); return }
        val s = t / 1000f
        val alpha = if (t > life - 600) ((life - t) * 255 / 600).toInt() else 255
        bits.forEach { b ->
            val x = b.x + b.vx * s + kotlin.math.sin(s * 3 + b.a) * 12f; val y = b.y + b.vy * s
            p.color = b.color; p.alpha = alpha
            cv.save(); cv.translate(x, y); cv.rotate(b.a + b.va * s); cv.drawRect(-b.w / 2, -b.h / 2, b.w / 2, b.h / 2, p); cv.restore()
        }
        postInvalidateOnAnimation()
    }
}

/** Fa cadere i coriandoli sopra la finestra. */
fun android.app.Activity.confetti() {
    val root = window.decorView as? ViewGroup ?: return
    root.addView(ConfettiView(this).apply { isClickable = false; isFocusable = false }, ViewGroup.LayoutParams(-1, -1))
}

// ======================= schermate vuote =======================

/** Quando non c'è ancora niente: un disegno, due righe che spiegano e (se serve) il pulsante per cominciare. */
fun Context.emptyState(parent: LinearLayout, emoji: String, title: String, text: String, button: String? = null, onClick: (() -> Unit)? = null) {
    parent.addView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(28), dp(20), dp(12))
        addView(iconBox(emoji, 96).apply { (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL })
        addView(TextView(this@emptyState).apply { this.text = title; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(6)) })
        addView(TextView(this@emptyState).apply { this.text = text; textSize = 14f; setTextColor(col(R.color.muted)); gravity = Gravity.CENTER; setLineSpacing(0f, 1.15f) })
        if (button != null && onClick != null) addView(primaryButton(button, onClick).apply { (layoutParams as LinearLayout.LayoutParams).apply { width = -2; topMargin = dp(18); gravity = Gravity.CENTER_HORIZONTAL }; setPadding(dp(28), dp(13), dp(28), dp(13)) })
    }, LinearLayout.LayoutParams(-1, -2))
}

// ======================= muscoli =======================

/** La sagoma davanti e dietro, con i muscoli principali (pieni) e secondari (chiari) dell'esercizio. */
class MuscleMapView(c: Context, private val primary: Set<String>, private val secondary: Set<String>) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = c.col(R.color.muted) }
    private val r = RectF()
    private val accent = c.col(R.color.accent)
    private val base = c.col(R.color.track)
    override fun onDraw(cv: Canvas) {
        val k = minOf(width / 214f, height / 142f); if (k <= 0) return
        cv.save(); cv.translate((width - 214f * k) / 2, 0f); cv.scale(k, k)
        label.textSize = 7f
        cv.drawText("davanti", 50f, 141f, label); cv.drawText("dietro", 164f, 141f, label)
        for (z in MuscleMap.ZONES) {
            val ox = if (z.back) 114f else 0f
            p.color = when { z.key.isNotEmpty() && z.key in primary -> accent; z.key.isNotEmpty() && z.key in secondary -> (accent and 0x00FFFFFF) or 0x66000000; else -> base }
            r.set(ox + z.x, z.y, ox + z.x + z.w, z.y + z.h)
            if (z.oval) cv.drawOval(r, p) else cv.drawRoundRect(r, z.r, z.r, p)
        }
        cv.restore()
    }
}
