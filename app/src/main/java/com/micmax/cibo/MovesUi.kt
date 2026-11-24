package com.micmax.cibo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** L'omino che fa l'esercizio, animato. Disegnato dall'app: niente immagini da scaricare. */
class MoveView(c: Context, var move: Move?, private val animate: Boolean = true) : View(c) {
    private val accent = c.col(R.color.accent)
    private val near = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = accent }
    private val far = Paint(near).apply { color = (accent and 0x00FFFFFF) or 0x66000000 }
    private val body = Paint(near).apply { color = c.col(R.color.text) }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = c.col(R.color.text) }
    private val floor = Paint(near).apply { color = c.col(R.color.stroke) }
    private val wall = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = c.col(R.color.stroke) }
    private val spine = Path()
    private var start = System.currentTimeMillis()
    /** Durata di un fotogramma: con il conteggio automatico va a tempo con le ripetizioni (una ripetizione = due fotogrammi). */
    var frameMs = 1300f
    /** Riparte dalla prima posa (all'inizio di una serie, per andare a tempo con il conteggio). */
    fun restart() { start = System.currentTimeMillis(); invalidate() }

    override fun onDraw(cv: Canvas) {
        val m = move ?: return
        val size = minOf(width, height).toFloat(); if (size <= 0) return
        val k = size / 100f
        cv.save(); cv.translate((width - size) / 2, (height - size) / 2); cv.scale(k, k)
        val t = if (animate) (System.currentTimeMillis() - start) / frameMs else 0f
        val o = Moves.figure(m, t)
        fun x(i: Int) = o[i * 2]
        fun y(i: Int) = o[i * 2 + 1]
        val w = 5.2f * Moves.scale(m)
        near.strokeWidth = w; far.strokeWidth = w; body.strokeWidth = w * 1.25f; floor.strokeWidth = 1.6f
        cv.drawLine(3f, Moves.FLOOR + w / 2 + 1f, 97f, Moves.FLOOR + w / 2 + 1f, floor)
        when (m.prop) {
            Prop.WALL_FRONT -> { val f0 = Moves.figure(m, 0f); val wx = maxOf(f0[Moves.R_HAND * 2], f0[Moves.L_HAND * 2]) + w / 2; cv.drawRect(wx, 8f, wx + 4f, Moves.FLOOR + 2f, wall) }
            Prop.WALL_BACK -> { val f0 = Moves.figure(m, 0f); val wx = minOf(f0[Moves.HIP * 2], f0[Moves.NECK * 2]) - w * 0.6f; cv.drawRect(wx - 4f, 8f, wx, Moves.FLOOR + 2f, wall) }
            Prop.STEP -> { val f0 = Moves.figure(m, 0f); val fx = f0[Moves.R_FOOT * 2]; val fy = f0[Moves.R_FOOT * 2 + 1] + w / 2; cv.drawRoundRect(fx - 10f, fy, fx + 12f, Moves.FLOOR + w / 2 + 1f, 2f, 2f, wall) }
            Prop.NONE -> {}
        }
        fun seg(a: Int, b: Int, p: Paint) = cv.drawLine(x(a), y(a), x(b), y(b), p)
        val back = if (m.front) near else far
        seg(Moves.L_HIP, Moves.L_KNEE, back); seg(Moves.L_KNEE, Moves.L_FOOT, back)
        seg(Moves.L_SHO, Moves.L_ELB, back); seg(Moves.L_ELB, Moves.L_HAND, back)
        spine.reset(); spine.moveTo(x(Moves.HIP), y(Moves.HIP)); spine.quadTo(x(Moves.SPINE), y(Moves.SPINE), x(Moves.NECK), y(Moves.NECK))
        cv.drawPath(spine, body)
        if (m.front) { seg(Moves.L_SHO, Moves.R_SHO, body); seg(Moves.L_HIP, Moves.R_HIP, body) }
        seg(Moves.R_HIP, Moves.R_KNEE, near); seg(Moves.R_KNEE, Moves.R_FOOT, near)
        seg(Moves.R_SHO, Moves.R_ELB, near); seg(Moves.R_ELB, Moves.R_HAND, near)
        cv.drawCircle(x(Moves.HEAD), y(Moves.HEAD), Moves.HEAD_R * Moves.scale(m), head)
        cv.restore()
        if (animate && m.frames.size > 1 && isShown) postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); invalidate() }
}

fun Context.moveView(m: Move?, sizeDp: Int, animate: Boolean = true) = MoveView(this, m, animate).apply {
    background = roundBg(col(R.color.icbg), 18)
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { gravity = Gravity.CENTER_HORIZONTAL }
    contentDescription = m?.name
}

/** Una riga con il disegno dell'esercizio, il nome e una riga piccola. */
fun Context.moveRow(parent: LinearLayout, m: Move?, title: String, sub: String?, onClick: (() -> Unit)?) {
    val r = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = roundBg(col(R.color.card), 16, col(R.color.stroke)); setPadding(dp(10), dp(8), dp(12), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
    }
    if (m != null) r.addView(MoveView(this, m, animate = false).apply { background = roundBg(col(R.color.icbg), 12) }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) })
    else r.addView(TextView(this).apply { text = "🏋️"; textSize = 22f; gravity = Gravity.CENTER; background = roundBg(col(R.color.icbg), 12) }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginEnd = dp(12) })
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    col.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(col(R.color.text)); setTypeface(typeface, android.graphics.Typeface.BOLD) })
    if (!sub.isNullOrBlank()) col.addView(TextView(this).apply { text = sub; textSize = 12.5f; setTextColor(col(R.color.muted)) })
    r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
    parent.addView(r)
}

fun exLine(e: Ex) = "${e.sets} × " + (if (e.seconds > 0) "${e.seconds} s" else "${e.reps}") + (if (e.restSec > 0) " · riposo ${e.restSec} s" else "") + (if (e.kg > 0) " · ${if (e.kg % 1.0 == 0.0) e.kg.toInt() else e.kg} kg" else "")

/** Il disegno grande, come si fa e i muscoli: da un esercizio della scheda o del catalogo. */
fun MainActivity.showMove(m: Move) {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(4)) }
    box.addView(moveView(m, 220))
    box.addView(TextView(this).apply { text = m.how; textSize = 15f; setTextColor(col(R.color.text)); setPadding(0, dp(14), 0, dp(6)) })
    val tips = buildList {
        if (m.timed && m.reps == 0) add("A tempo: circa ${m.secs} secondi")
        if (m.perSide) add("Da fare per ogni lato")
        if ("jump" in m.tags) add("Con salti: se hai dolori alle ginocchia scegli la marcia o la calciata")
    }
    if (tips.isNotEmpty()) box.addView(TextView(this).apply { text = tips.joinToString("\n") { "• $it" }; textSize = 13f; setTextColor(col(R.color.muted)) })
    MaterialAlertDialogBuilder(this).setTitle(m.name).setView(android.widget.ScrollView(this).apply { addView(box) }).setPositiveButton("Chiudi", null).show()
}

/** Tutti gli esercizi con il disegno, divisi per tipo. */
fun MainActivity.openMoves() {
    openScreen("Esercizi") { body, _ ->
        hintLabel(body, "Tocca un esercizio per vedere come si fa. Nelle schede il disegno compare da solo se il nome somiglia a uno di questi.")
        listOf("stretch" to "STRETCHING", "mobility" to "MOBILITÀ E RISCALDAMENTO", "legs" to "GAMBE E GLUTEI", "upper" to "PARTE ALTA", "core" to "ADDOMINALI E SCHIENA", "cardio" to "CARDIO").let { groups ->
            val shown = HashSet<String>()
            groups.forEach { (tag, label) ->
                val l = Moves.ALL.filter { tag in it.tags && it.id !in shown }
                if (l.isEmpty()) return@forEach
                sectionLabel(body, label)
                l.forEach { m -> shown += m.id; moveRow(body, m, m.name, Moves.LEVELS[m.minLevel - 1] + (if (m.maxLevel < 3) " (solo per iniziare)" else if (m.minLevel > 1) " in su" else "") + if (m.perSide) " · per lato" else "") { showMove(m) } }
            }
        }
    }
}

/** «Crea una scheda»: scegli tipo, durata e livello; l'app compone la scheda dal catalogo, e si può rifare finché piace. */
fun MainActivity.openGenerator(start: Moves.Goal? = null) {
    val prefs = getSharedPreferences("allenamento", Context.MODE_PRIVATE)
    var goal = start ?: runCatching { Moves.Goal.valueOf(prefs.getString("gen_goal", "STRETCH")!!) }.getOrDefault(Moves.Goal.STRETCH)
    var minutes = prefs.getInt("gen_min", 15)
    var level = prefs.getInt("gen_level", 0)
    var seed = System.currentTimeMillis()
    openScreen("Crea una scheda") { body, _ ->
        fun save() = prefs.edit().putString("gen_goal", goal.name).putInt("gen_min", minutes).putInt("gen_level", level).apply()
        lateinit var render: () -> Unit
        fun choiceRow(items: List<String>, sel: Int, onPick: (Int) -> Unit): View {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(2), dp(2), dp(2), dp(6)) }
            items.forEachIndexed { i, t ->
                row.addView(TextView(this).apply {
                    text = t; textSize = 13.5f; gravity = Gravity.CENTER; setPadding(dp(12), dp(9), dp(12), dp(9))
                    setTextColor(if (i == sel) col(R.color.onaccent) else col(R.color.text))
                    background = roundBg(if (i == sel) col(R.color.accent) else col(R.color.card), 18, if (i == sel) null else col(R.color.stroke))
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) }
                    setOnClickListener { onPick(i) }
                })
            }
            return android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
        }
        val durations = listOf(5, 10, 15, 20, 30, 45)
        render = {
            body.removeAllViews()
            sectionLabel(body, "COSA VUOI FARE")
            Moves.Goal.values().forEach { g ->
                val sel = g == goal
                body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(9), dp(12), dp(9))
                    background = roundBg(if (sel) col(R.color.icbg) else col(R.color.card), 14, if (sel) col(R.color.accent) else col(R.color.stroke))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
                    addView(TextView(this@openGenerator).apply { text = g.emoji; textSize = 20f }, LinearLayout.LayoutParams(dp(36), -2))
                    addView(LinearLayout(this@openGenerator).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(this@openGenerator).apply { text = g.label; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)) })
                        addView(TextView(this@openGenerator).apply { text = g.sub; textSize = 12.5f; setTextColor(col(R.color.muted)) })
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    if (sel) addView(TextView(this@openGenerator).apply { text = "✓"; textSize = 18f; setTextColor(col(R.color.accent)) })
                    isClickable = true; setOnClickListener { goal = g; seed = System.currentTimeMillis(); save(); render() }
                })
            }
            sectionLabel(body, "QUANTO TEMPO")
            body.addView(choiceRow(durations.map { "$it min" }, durations.indexOf(minutes)) { minutes = durations[it]; save(); render() })
            sectionLabel(body, "LIVELLO")
            body.addView(choiceRow(Moves.LEVELS, level) { level = it; save(); render() })

            val plan = Moves.generate(goal, minutes, level, seed)
            sectionLabel(body, "LA TUA SCHEDA · ${plan.exercises.size} ESERCIZI · CIRCA ${Fitness.planMinutes(plan)} MIN")
            plan.exercises.forEach { e -> val m = Moves.find(e.name); moveRow(body, m, e.name, exLine(e), if (m != null) ({ showMove(m) }) else null) }
            body.addView(primaryButton("▶ Inizia adesso") { runPlan(plan) })
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0)
                fun b(t: String, f: () -> Unit) = MaterialButton(this@openGenerator, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = t; isAllCaps = false; setOnClickListener { f() } }
                addView(b("🔀 Un'altra") { seed = System.currentTimeMillis(); render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                addView(b("💾 Salva tra le schede") {
                    Fitness.savePlans(this@openGenerator, Fitness.plans(this@openGenerator) + plan)
                    toast("✓ Salvata in «Schede»"); seed = System.currentTimeMillis()
                }, LinearLayout.LayoutParams(0, -2, 1f))
            })
            hintLabel(body, "La scheda la compone l'app, sul telefono, dal suo catalogo di esercizi: niente internet. Con «Un'altra» cambia gli esercizi. Se senti dolore, fermati.")
        }
        render()
    }
}
