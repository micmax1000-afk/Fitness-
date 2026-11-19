package com.micmax.cibo

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Small helpers: the whole screen is built in code, with the colours of the theme. */
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.col(id: Int) = ContextCompat.getColor(this, id)

fun Context.roundBg(color: Int, radiusDp: Int = 16, stroke: Int? = null) = GradientDrawable().apply {
    cornerRadius = dp(radiusDp).toFloat(); setColor(color); if (stroke != null) setStroke(dp(1), stroke)
}

fun Context.sectionLabel(parent: LinearLayout, text: String) {
    parent.addView(TextView(this).apply {
        this.text = text; textSize = 12f; letterSpacing = 0.08f; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(col(R.color.muted)); setPadding(dp(6), dp(16), dp(6), dp(6))
    })
}

fun Context.hintLabel(parent: LinearLayout, text: String) {
    parent.addView(TextView(this).apply { this.text = text; textSize = 13.5f; setTextColor(col(R.color.muted)); setPadding(dp(6), dp(4), dp(6), dp(8)) })
}

/** A white card row: round icon, title, small line, tap. */
fun Context.listRow(parent: LinearLayout, icon: String, title: String, sub: String?, onLong: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, onClick: () -> Unit): View {
    val r = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = roundBg(col(R.color.card), 16, col(R.color.stroke)); setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        isClickable = true; setOnClickListener { onClick() }
        if (onLong != null) setOnLongClickListener { onLong(); true }
    }
    // l'icona c'è sempre come primo elemento (alcune schermate la nascondono); se manca resta invisibile
    r.addView(iconBox(icon.ifBlank { " " }, 42).apply {
        (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
        if (icon.isBlank()) visibility = View.GONE
    })
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
    col.addView(TextView(this).apply { text = title; textSize = 15.5f; setTextColor(col(R.color.text)); setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
    if (!sub.isNullOrBlank()) col.addView(TextView(this).apply { text = sub; textSize = 12.5f; setTextColor(col(R.color.muted)) })
    r.addView(col)
    if (onRemove != null) r.addView(TextView(this).apply {
        text = "✕"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)); contentDescription = "Togli"
        setOnClickListener { onRemove() }
    }, LinearLayout.LayoutParams(dp(40), dp(40)))
    parent.addView(r)
    return r
}

/** The big square buttons of the home page. */
fun Context.bigButton(emoji: String, label: String, primary: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
    background = roundBg(if (primary) col(R.color.accent) else col(R.color.card), 18, if (primary) null else col(R.color.stroke))
    setPadding(dp(8), dp(16), dp(8), dp(16)); isClickable = true; setOnClickListener { onClick() }
    addView(iconBox(emoji, 44, onAccent = primary).apply { (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL })
    addView(TextView(this@bigButton).apply {
        text = label; textSize = 14.5f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(if (primary) col(R.color.onaccent) else col(R.color.text))
    })
}

/** A row of rounded chips that scrolls sideways. */
fun Context.chipBar(chips: List<Pair<String, () -> Unit>>): View {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(6), dp(8), dp(6)) }
    chips.forEach { (t, f) ->
        row.addView(TextView(this).apply {
            text = t; textSize = 13.5f; setTextColor(col(R.color.text)); setPadding(dp(13), dp(9), dp(13), dp(9))
            background = roundBg(col(R.color.card), 18, col(R.color.stroke))
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) }
            isClickable = true; setOnClickListener { f() }
        })
    }
    return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
}

fun Context.primaryButton(text: String, onClick: () -> Unit) = TextView(this).apply {
    this.text = text; textSize = 16f; gravity = Gravity.CENTER; setTextColor(col(R.color.onaccent)); setTypeface(typeface, android.graphics.Typeface.BOLD)
    background = roundBg(col(R.color.accent), 16); setPadding(dp(12), dp(14), dp(12), dp(14))
    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    isClickable = true; setOnClickListener { onClick() }
}

object Calc {
    /** «1,5» o «1.5» → 1.5 */
    fun num(s: String?): Double? = s?.trim()?.replace(',', '.')?.toDoubleOrNull()
}

fun Context.field(hint: String, value: String?, number: Boolean = false, multiline: Boolean = false): android.widget.EditText = android.widget.EditText(this).apply {
    this.hint = hint; if (value != null) setText(value)
    inputType = when {
        number -> android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        multiline -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        else -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }
    if (multiline) { minLines = 4; gravity = Gravity.TOP }
}

fun Context.form(vararg views: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4))
    views.forEach { addView(it, LinearLayout.LayoutParams(-1, -2)) }
}

/** Tre riquadri affiancati: titolo, valore, riga piccola. */
fun Context.statRow(vararg tiles: Triple<String, String, String?>): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4))
    tiles.forEach { (label, value, sub) ->
        addView(LinearLayout(this@statRow).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(10), dp(6), dp(10))
            background = roundBg(col(R.color.card), 14, col(R.color.stroke))
            addView(TextView(this@statRow).apply { text = label; textSize = 12f; setTextColor(col(R.color.muted)); gravity = Gravity.CENTER })
            addView(TextView(this@statRow).apply { text = value; textSize = 18f; setTextColor(col(R.color.text)); setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER })
            if (sub != null) addView(TextView(this@statRow).apply { text = sub; textSize = 11.5f; setTextColor(col(R.color.muted)); gravity = Gravity.CENTER })
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
    }
}

/** Una barra che si riempie; il fondo è chiaro, così una barra vuota non sembra piena. */
fun Context.progressBar(frac: Double, color: Int, heightDp: Int = 10): View = android.widget.FrameLayout(this).apply {
    background = roundBg(col(R.color.track), 6)
    val f = frac.coerceIn(0.0, 1.0)
    addView(View(this@progressBar).apply { background = roundBg(color, 6) }, android.widget.FrameLayout.LayoutParams(0, -1).also { it.width = 1 })
    post { (getChildAt(0).layoutParams as android.widget.FrameLayout.LayoutParams).width = (width * f).toInt().coerceAtLeast(if (f > 0) dp(6) else 0); getChildAt(0).requestLayout() }
    layoutParams = LinearLayout.LayoutParams(-1, dp(heightDp)).apply { setMargins(dp(4), dp(6), dp(4), dp(6)) }
}

/** Una foto da internet (si scarica da sola e resta in memoria). */
object Img {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(12) {}
    private val exec = java.util.concurrent.Executors.newFixedThreadPool(2)
    fun load(url: String, into: android.widget.ImageView) {
        into.tag = url
        cache.get(url)?.let { into.setImageBitmap(it); return }
        exec.execute {
            val b = runCatching {
                val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 15000
                c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: return@execute
            cache.put(url, b)
            into.post { if (into.tag == url) into.setImageBitmap(b) }
        }
    }
}
