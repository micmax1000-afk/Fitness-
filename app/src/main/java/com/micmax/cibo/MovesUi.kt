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
    private val gear = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = 0xFF64748B.toInt() }
    private val rope = Path()
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
        // muro, gradino e sedia dietro all'omino; gli attrezzi in mano vanno sopra (più sotto)
        val props = Moves.propShapes(m, t, o)
        fun drawProp(sh: Moves.Shape) {
            when (sh) {
                is Moves.Shape.Box -> cv.drawRect(sh.l, sh.t, sh.r, sh.b, wall)
                is Moves.Shape.Dot -> { gear.style = Paint.Style.FILL; cv.drawCircle(sh.x, sh.y, sh.r, gear) }
                is Moves.Shape.Line -> { gear.style = Paint.Style.STROKE; gear.strokeWidth = sh.w; cv.drawLine(sh.x1, sh.y1, sh.x2, sh.y2, gear) }
                is Moves.Shape.Curve -> { gear.style = Paint.Style.STROKE; gear.strokeWidth = sh.w; rope.reset(); rope.moveTo(sh.x1, sh.y1); rope.quadTo(2 * sh.cx - (sh.x1 + sh.x2) / 2, 2 * sh.cy - (sh.y1 + sh.y2) / 2, sh.x2, sh.y2); cv.drawPath(rope, gear) }
            }
        }
        props.filterIsInstance<Moves.Shape.Box>().forEach { drawProp(it) }
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
        props.filter { it !is Moves.Shape.Box }.forEach { drawProp(it) }
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

/** La scheda completa dell'esercizio: disegno, muscoli, come si fa, errori, respirazione, varianti, foto e video. */
fun MainActivity.showMove(m: Move) {
    val info = MoveInfos.of(m.id)
    openScreen(m.name) { body, _ ->
        body.addView(moveView(m, 220).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(4) })
        // etichette: livello, attrezzi, spazio, per lato
        val tags = buildList {
            add(Moves.LEVELS[m.minLevel - 1] + if (m.minLevel > 1) " in su" else "")
            if (m.equip.isEmpty()) add("Corpo libero") else m.equip.forEach { add(MoveInfos.EQUIPMENT[it]?.substringBefore(" (") ?: it) }
            add(when (info.space) { 0 -> "Basta 1 metro"; 1 -> "Spazio di un tappetino"; else -> "Serve spazio" })
            if (m.perSide) add("Per lato")
            if ("jump" in m.tags) add("Con salti")
        }
        body.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(2))
            tags.take(4).forEach { t -> addView(TextView(this@showMove).apply { text = t; textSize = 12f; setTextColor(col(R.color.text)); setPadding(dp(9), dp(4), dp(9), dp(4)); background = roundBg(col(R.color.card), 12, col(R.color.stroke)) },
                LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(3), 0, dp(3), 0) }) }
        })
        body.addView(TextView(this).apply { text = m.how; textSize = 15f; setTextColor(col(R.color.text)); setPadding(dp(6), dp(10), dp(6), dp(4)) })
        if (info.primary.isNotEmpty()) {
            sectionLabel(body, "MUSCOLI")
            body.addView(MuscleMapView(this, info.primary, info.secondary), LinearLayout.LayoutParams(-1, dp(190)))
            body.addView(TextView(this).apply {
                text = "Lavorano: " + info.primary.joinToString(", ") { MoveInfos.MUSCLES[it] ?: it } + if (info.secondary.isNotEmpty()) "\nAiutano: " + info.secondary.joinToString(", ") { MoveInfos.MUSCLES[it] ?: it } else ""
                textSize = 13.5f; setTextColor(col(R.color.muted)); setPadding(dp(6), dp(6), dp(6), 0)
            })
        }
        fun list(title: String, items: List<String>, numbered: Boolean) {
            if (items.isEmpty()) return
            sectionLabel(body, title)
            items.forEachIndexed { k, t -> body.addView(TextView(this).apply { text = (if (numbered) "${k + 1}. " else "✕  ") + t; textSize = 15f; setTextColor(col(R.color.text)); setPadding(dp(6), dp(3), dp(6), dp(3)) }) }
        }
        list("COME SI FA", info.steps, true)
        list("ERRORI DA EVITARE", info.mistakes, false)
        if (info.cue.isNotBlank() || info.breath.isNotBlank()) {
            sectionLabel(body, "RESPIRO E CONSIGLIO")
            body.addView(TextView(this).apply { text = "🫁 ${info.breath}" + if (info.cue.isNotBlank()) "\n💡 ${info.cue}" else ""; textSize = 15f; setTextColor(col(R.color.text)); setPadding(dp(6), dp(3), dp(6), dp(3)) })
        }
        if (info.easier != null || info.harder != null) {
            sectionLabel(body, "VARIANTI")
            info.easier?.let { Moves.byId(it) }?.let { e -> moveRow(body, e, "Più facile: ${e.name}", "Tocca per vederlo") { showMove(e) } }
            info.harder?.let { Moves.byId(it) }?.let { h -> moveRow(body, h, "Più difficile: ${h.name}", "Tocca per vederlo") { showMove(h) } }
        }
        if (info.caution.isNotBlank()) {
            sectionLabel(body, "ATTENZIONE")
            body.addView(TextView(this).apply { text = "⚠️ ${info.caution}\nSe senti dolore fermati."; textSize = 14.5f; setTextColor(col(R.color.text)); setPadding(dp(12), dp(10), dp(12), dp(10)); background = roundBg(0x22F59E0B, 12) })
        }
        info.photo?.let { ph ->
            sectionLabel(body, "FOTO")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val note = TextView(this).apply { text = "Foto: free-exercise-db (pubblico dominio). Si scaricano la prima volta, poi restano sul telefono."; textSize = 11.5f; setTextColor(col(R.color.muted)); setPadding(dp(6), dp(4), dp(6), 0) }
            (0..1).forEach { n ->
                row.addView(android.widget.ImageView(this).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; background = roundBg(col(R.color.track), 12); clipToOutline = true
                    contentDescription = if (n == 0) "Posizione di partenza" else "Posizione di arrivo"
                    Img.load(MoveInfos.photoUrl(ph, n), this) { note.text = "Le foto non si sono scaricate (serve internet la prima volta)." }
                }, LinearLayout.LayoutParams(0, dp(150), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
            body.addView(row); body.addView(note)
        }
        body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "▶ Cerca il video su MuscleWiki"; isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            setOnClickListener { runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(MoveInfos.muscleWikiUrl(info.en)))) }.onFailure { toast("Non trovo un browser") } }
        })
        hintLabel(body, "Il video si apre nel browser, sul sito di MuscleWiki.")
    }
}

/** Tutti gli esercizi con il disegno, divisi per tipo. */
fun MainActivity.openMoves() {
    openScreen("Esercizi") { body, _ ->
        hintLabel(body, "Tocca un esercizio per vedere come si fa. Nelle schede il disegno compare da solo se il nome somiglia a uno di questi.")
        listOf("attrezzi" to "CON GLI ATTREZZI", "balance" to "EQUILIBRIO", "stretch" to "STRETCHING", "mobility" to "MOBILITÀ E RISCALDAMENTO", "legs" to "GAMBE E GLUTEI", "upper" to "PARTE ALTA", "core" to "ADDOMINALI E SCHIENA", "cardio" to "CARDIO").let { groups ->
            val shown = HashSet<String>()
            groups.forEach { (tag, label) ->
                val l = Moves.ALL.filter { (if (tag == "attrezzi") it.equip.isNotEmpty() else tag in it.tags) && it.id !in shown }
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

            val cp = CoachStore.plan(this)
            val plan = Moves.generate(goal, minutes, level, seed, opts = cp?.let { Coach.opts(it, Coach.personal(CoachStore.body(this))) } ?: Moves.Opts())
            listRow(body, "⚙️", "Attrezzi, spazio e limiti", cp?.let { p -> (p.equip.orEmpty().takeIf { it.isNotEmpty() }?.joinToString(", ") { MoveInfos.EQUIPMENT[it]?.substringBefore(" (")?.lowercase() ?: it } ?: "corpo libero") + " · " + MoveInfos.SPACES[p.space.coerceIn(0, 2)].lowercase() + if (p.quiet) " · silenzioso" else "" }
                ?: "Corpo libero, una stanza: tocca per cambiare") { coachSetup(CoachStore.plan(this)) { seed = System.currentTimeMillis(); render() } }
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
