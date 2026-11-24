package com.micmax.cibo

import android.content.Context
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

// ======================= archivio del coach =======================

object CoachStore {
    private fun prefs(c: Context) = c.getSharedPreferences("allenamento", Context.MODE_PRIVATE)
    fun plan(c: Context): CoachPlan? = prefs(c).getString("coach", null)?.let { runCatching { Gson().fromJson(it, CoachPlan::class.java) }.getOrNull() }
    fun save(c: Context, p: CoachPlan) = prefs(c).edit().putString("coach", Gson().toJson(p)).apply()
    fun clear(c: Context) = prefs(c).edit().remove("coach").apply()

    fun best(c: Context, id: String): Long = prefs(c).getLong("pb_$id", 0L)
    /** Salva il tempo; true se è un nuovo record. */
    fun recordTime(c: Context, id: String, ms: Long): Boolean {
        val old = best(c, id); val isNew = old == 0L || ms < old
        prefs(c).edit().putLong("pb_$id", if (isNew) ms else old).putInt("tries_$id", prefs(c).getInt("tries_$id", 0) + 1).apply()
        return isNew
    }
    fun tries(c: Context, id: String) = prefs(c).getInt("tries_$id", 0)

    /** Ha fatto un allenamento in quel giorno (app o orologio)? */
    fun trainedOn(c: Context, d: LocalDate): Boolean = Fitness.allTimes(c).any { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == d }
}

/**
 * Le chiavi dei servizi AI: stanno in un archivio a parte, escluso dal backup di Android (vedi backup_rules)
 * e dal file «Esporta i miei dati». Non escono dal telefono se non verso il servizio scelto.
 */
object AiKeys {
    private fun prefs(c: Context) = c.getSharedPreferences("coach_keys", Context.MODE_PRIVATE)
    fun key(c: Context, id: String) = prefs(c).getString("key_$id", "").orEmpty()
    fun model(c: Context, p: Coach.Provider) = prefs(c).getString("model_${p.id}", null)?.takeIf { it.isNotBlank() } ?: p.model
    fun save(c: Context, id: String, key: String, model: String) = prefs(c).edit().putString("key_$id", key.trim()).putString("model_$id", model.trim()).apply()
    /** Ordine in cui provarli: prima quello scelto, poi gli altri che hanno una chiave. */
    fun first(c: Context) = prefs(c).getString("first", "groq") ?: "groq"
    fun setFirst(c: Context, id: String) = prefs(c).edit().putString("first", id).apply()
    fun ready(c: Context) = Coach.PROVIDERS.filter { key(c, it.id).isNotBlank() }.sortedBy { if (it.id == first(c)) 0 else 1 }
    fun sendProfile(c: Context) = prefs(c).getBoolean("send_profile", false)
    fun setSendProfile(c: Context, on: Boolean) = prefs(c).edit().putBoolean("send_profile", on).apply()
}

object CoachAi {
    /** Quello che il coach sa: solo allenamento, passi e sonno; età, peso e altezza solo se l'hai permesso. Mai farmaci, visite o referti. */
    fun context(c: Context): String {
        val lines = mutableListOf<String>()
        CoachStore.plan(c)?.let { p ->
            lines += "Obiettivo: ${Coach.GOALS[p.goal]?.first}; ${p.days} allenamenti a settimana da circa ${p.minutes} minuti; livello ${Moves.LEVELS[Coach.level(p)].lowercase()}" + if (p.noJumps) "; niente salti" else ""
        }
        val acts = Fitness.acts(c).filter { System.currentTimeMillis() - it.start < 14 * 86_400_000L }
        lines += "Allenamenti negli ultimi 14 giorni: ${acts.size}" + if (acts.isNotEmpty()) " (" + acts.take(6).joinToString("; ") { "${it.title.replace(Regex("^\\S+ "), "")}, ${it.durationMin} min" } + ")" else ""
        val snap = Hc.cached(c)
        Hc.today(snap)?.steps?.takeIf { it > 0 }?.let { lines += "Passi oggi: $it" }
        Hc.lastNight(snap)?.let { lines += "Sonno ultima notte: ${Hc.duration(it.sleepMin)}" }
        if (AiKeys.sendProfile(c)) {
            val p = Food.profile(c)
            if (p.birthYear > 0) lines += "Età: ${LocalDate.now().year - p.birthYear} anni"
            if (p.sex in setOf("m", "f")) lines += "Sesso: ${if (p.sex == "m") "uomo" else "donna"}"
            if (p.heightCm > 0) lines += "Altezza: ${p.heightCm.toInt()} cm"
            if (p.weightKg > 0) lines += "Peso: ${"%.1f".format(Locale.ITALIAN, p.weightKg)} kg"
        }
        return lines.joinToString("\n")
    }

    /** Chiede al primo servizio con la chiave; se non risponde prova il successivo. Da chiamare fuori dal thread principale. */
    fun ask(c: Context, history: List<Pair<String, String>>): Result<String> {
        val providers = AiKeys.ready(c)
        if (providers.isEmpty()) return Result.failure(IllegalStateException("Manca la chiave: aggiungila nelle impostazioni del coach (in alto a destra)."))
        val system = Coach.systemPrompt(context(c))
        val errors = mutableListOf<String>()
        for (p in providers) {
            val r = runCatching {
                val conn = URL(p.url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.connectTimeout = 15000; conn.readTimeout = 60000; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer " + AiKeys.key(c, p.id))
                conn.outputStream.use { it.write(Coach.request(AiKeys.model(c, p), system, history).toByteArray()) }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (code !in 200..299) error(Coach.errorText(code, body))
                Coach.replyText(body.orEmpty()) ?: error("risposta vuota")
            }
            r.onSuccess { return Result.success(it) }
            errors += "${p.label}: ${r.exceptionOrNull()?.message ?: "non risponde"}"
        }
        return Result.failure(IllegalStateException(errors.joinToString("\n")))
    }
}

// ======================= schermata del coach =======================

private var coachTweaks = emptySet<Coach.Tweak>()
private var coachSeed = System.currentTimeMillis()

fun MainActivity.openCoach() {
    val plan = CoachStore.plan(this) ?: return coachSetup(null) { openCoach() }
    openScreen("Il tuo coach", "⚙️", { coachSetup(CoachStore.plan(this)) { refreshTop() } }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val p = CoachStore.plan(this) ?: plan
            val today = LocalDate.now()
            val week = Coach.weekPlan(p, today)
            val day = week.first { it.date == today }
            val sleepH = Hc.lastNight(Hc.cached(this))?.sleepMin?.div(60.0)
            sectionLabel(body, "OGGI · SETTIMANA ${Coach.week(p, today)} · LIVELLO ${Moves.LEVELS[Coach.level(p)].uppercase()}")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = roundBg(col(R.color.card), 18, col(R.color.stroke)); setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            }
            val ch = day.challenge
            val todayPlan = Coach.todayPlan(p, day, coachTweaks, sleepH, coachSeed)
            val title = when {
                ch != null && coachTweaks.isEmpty() -> "⏱ Contro il tempo: ${ch.name}"
                day.goal == null && coachTweaks.isEmpty() -> "Oggi riposo 😌"
                else -> todayPlan.name.substringAfter("· ")
            }
            card.addView(TextView(this).apply { text = title; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)) })
            val notes = buildList {
                if (CoachStore.trainedOn(this@openCoach, today)) add("✓ Oggi ti sei già allenato: bravo!")
                if (sleepH != null && sleepH < 6) add("Hai dormito ${Hc.duration((sleepH * 60).toLong())}: oggi scheda più leggera.")
                if (day.note.isNotBlank()) add(day.note)
                if (ch != null && coachTweaks.isEmpty()) add("${ch.rounds} giri da finire il prima possibile, circa ${ch.estMinutes()} min. " + (CoachStore.best(this@openCoach, ch.id).takeIf { it > 0 }?.let { "Il tuo record: ${Challenges.clock(it)}." } ?: "Sarà il tuo primo record."))
                else if (day.goal != null || coachTweaks.isNotEmpty()) add("${todayPlan.exercises.size} esercizi · circa ${Fitness.planMinutes(todayPlan)} min")
            }
            notes.forEach { n -> card.addView(TextView(this).apply { text = n; textSize = 13.5f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, 0) }) }
            if (ch != null && coachTweaks.isEmpty()) card.addView(primaryButton("▶ Inizia la sfida") { runChallenge(ch) })
            else if (day.goal != null || coachTweaks.isNotEmpty()) {
                todayPlan.exercises.take(4).forEach { e -> card.addView(TextView(this).apply { text = "• ${e.name} — ${exLine(e)}"; textSize = 13f; setTextColor(col(R.color.text)) }) }
                if (todayPlan.exercises.size > 4) card.addView(TextView(this).apply { text = "… e altri ${todayPlan.exercises.size - 4}"; textSize = 13f; setTextColor(col(R.color.muted)) })
                card.addView(primaryButton("▶ Inizia") { runPlan(todayPlan, coach = true) })
                card.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "🔀 Cambia esercizi"; isAllCaps = false; setOnClickListener { coachSeed = System.currentTimeMillis(); render() }
                })
            } else card.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Voglio allenarmi lo stesso"; isAllCaps = false; setOnClickListener { coachTweaks = setOf(Coach.Tweak.SHORT); render() }
            })
            body.addView(card)
            sectionLabel(body, "COME STAI OGGI?")
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                Coach.Tweak.values().toList().chunked(2).forEach { row ->
                    addView(LinearLayout(this@openCoach).apply {
                        orientation = LinearLayout.HORIZONTAL
                        row.forEach { t ->
                            val on = t in coachTweaks
                            addView(TextView(this@openCoach).apply {
                                text = t.label; textSize = 13.5f; gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), dp(10))
                                setTextColor(if (on) col(R.color.onaccent) else col(R.color.text))
                                background = roundBg(if (on) col(R.color.accent) else col(R.color.card), 18, if (on) null else col(R.color.stroke))
                                setOnClickListener { coachTweaks = if (on) coachTweaks - t else coachTweaks + t; coachSeed = System.currentTimeMillis(); render() }
                            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
                        }
                        if (row.size == 1) addView(View(this@openCoach), LinearLayout.LayoutParams(0, 0, 1f))
                    })
                }
            })
            sectionLabel(body, "CHIEDI AL COACH")
            listRow(body, "✨", "Scrivi al coach", if (AiKeys.ready(this).isEmpty()) "Con Gemini, Groq o NVIDIA: aggiungi la tua chiave" else "«Ho 15 minuti e mi fanno male le ginocchia…»") { openCoachChat() }
            sectionLabel(body, "LA TUA SETTIMANA")
            week.forEach { d ->
                val name = d.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
                val done = CoachStore.trainedOn(this, d.date)
                val what = when { d.challenge != null -> "Contro il tempo · ${d.challenge.name}"; d.goal != null -> "${d.goal.label} · ${d.minutes} min"; else -> "Riposo" }
                listRow(body, when { done -> "✅"; d.goal == null -> "😴"; d.challenge != null -> "⏱"; else -> "🏋️" }, name + if (d.date == today) " · oggi" else "", what + if (done) " · fatto" else "") {}
            }
            sectionLabel(body, "ALTRO")
            listRow(body, "⏱", "Contro il tempo", "Allenamenti a giri con il tuo record personale") { openChallenges() }
            hintLabel(body, "Dopo ogni scheda ti chiedo se è stata facile, giusta o difficile: così il livello si regola da solo. Il programma cresce un po' ogni settimana; ogni quarta settimana è più leggera.")
        }
        scr.refresh = { render() }
        render()
        syncHealthConnect { if (scr.view.isAttachedToWindow) render() }
    }
}

/** Le domande per impostare il programma (obiettivo, giorni, minuti, livello, salti). */
fun MainActivity.coachSetup(cur: CoachPlan?, done: () -> Unit) {
    var goal = cur?.goal ?: "forma"; var days = cur?.days ?: 3; var minutes = cur?.minutes ?: 20
    var level = cur?.let { Coach.level(it) } ?: 0; var noJumps = cur?.noJumps ?: false
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), dp(4)) }
    lateinit var render: () -> Unit
    fun label(t: String) = box.addView(TextView(this).apply { text = t; textSize = 12f; letterSpacing = 0.06f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.muted)); setPadding(0, dp(12), 0, dp(4)) })
    fun choices(items: List<String>, sel: Int, pick: (Int) -> Unit) = box.addView(android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@coachSetup).apply {
            items.forEachIndexed { i, t ->
                addView(TextView(this@coachSetup).apply {
                    text = t; textSize = 13.5f; setPadding(dp(12), dp(8), dp(12), dp(8))
                    setTextColor(if (i == sel) col(R.color.onaccent) else col(R.color.text))
                    background = roundBg(if (i == sel) col(R.color.accent) else col(R.color.card), 16, if (i == sel) null else col(R.color.stroke))
                    setOnClickListener { pick(i); render() }
                }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
            }
        })
    })
    val goals = Coach.GOALS.keys.toList(); val dayOpts = (2..6).toList(); val minOpts = listOf(10, 15, 20, 30, 45)
    render = {
        box.removeAllViews()
        label("IL TUO OBIETTIVO")
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            goals.forEach { g ->
                val sel = g == goal
                addView(TextView(this@coachSetup).apply {
                    text = (if (sel) "● " else "○ ") + Coach.GOALS[g]!!.first; textSize = 15f; setPadding(dp(4), dp(7), dp(4), dp(7))
                    setTextColor(if (sel) col(R.color.accent) else col(R.color.text)); setOnClickListener { goal = g; render() }
                })
            }
        })
        label("GIORNI A SETTIMANA"); choices(dayOpts.map { "$it" }, dayOpts.indexOf(days)) { days = dayOpts[it] }
        label("MINUTI PER ALLENAMENTO"); choices(minOpts.map { "$it min" }, minOpts.indexOf(minutes).coerceAtLeast(0)) { minutes = minOpts[it] }
        label("COME TI SENTI ALLENATO"); choices(Moves.LEVELS, level) { level = it }
        box.addView(android.widget.CheckBox(this).apply { text = "Niente salti (ginocchia, vicini di casa…)"; isChecked = noJumps; setOnCheckedChangeListener { _, b -> noJumps = b }; setPadding(0, dp(10), 0, 0) })
    }
    render()
    val b = MaterialAlertDialogBuilder(this).setTitle(if (cur == null) "🤖 Il tuo programma" else "Cambia programma").setView(android.widget.ScrollView(this).apply { addView(box) })
        .setPositiveButton(if (cur == null) "Comincia" else "Salva") { _, _ ->
            // il livello si tiene «fine» se non è cambiato, così non si perdono i Facile/Difficile
            val lv = if (cur != null && Coach.level(cur) == level) cur.level else level.toDouble()
            CoachStore.save(this, CoachPlan(goal, days, minutes, lv, noJumps, cur?.startDay ?: LocalDate.now().toEpochDay())); done()
        }.setNegativeButton("Annulla", null)
    if (cur != null) b.setNeutralButton("Ricomincia") { _, _ -> CoachStore.save(this, CoachPlan(goal, days, minutes, level.toDouble(), noJumps)); done() }
    b.show()
}

/** A fine scheda: «Com'è andata?» Il coach alza o abbassa il livello. */
fun MainActivity.coachFeedbackRow(parent: LinearLayout) {
    val p = CoachStore.plan(this) ?: return
    parent.addView(TextView(this).apply { text = "Com'è andata?"; textSize = 15f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)); setPadding(0, dp(14), 0, dp(4)) })
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), 0, dp(8), 0) }
    listOf("😌 Facile", "👍 Giusta", "🥵 Difficile").forEachIndexed { i, t ->
        row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = t; isAllCaps = false; textSize = 13.5f
            setOnClickListener {
                val np = Coach.feedback(p, i); CoachStore.save(this@coachFeedbackRow, np)
                row.removeAllViews()
                row.addView(TextView(this@coachFeedbackRow).apply {
                    text = when (i) { 0 -> "Bene! La prossima volta un po' più dura."; 2 -> "Ok, la prossima volta un po' più leggera."; else -> "Perfetto, continuiamo così." } +
                        if (Coach.level(np) != Coach.level(p)) " Nuovo livello: ${Moves.LEVELS[Coach.level(np)].lowercase()}." else ""
                    textSize = 14f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted))
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
    }
    parent.addView(row)
}

// ======================= chat con l'AI =======================

private val chat = mutableListOf<Pair<String, String>>()   // ("user"|"assistant", testo): resta finché l'app è aperta

fun MainActivity.openCoachChat() {
    lateinit var send: (String) -> Unit
    openScreen("Scrivi al coach", "⚙️", { aiSettings { refreshTop() } }) { body, scr ->
        val root = scr.view as LinearLayout
        val input = EditText(this).apply {
            hint = "Scrivi una domanda…"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4; background = roundBg(col(R.color.card), 20, col(R.color.stroke)); setPadding(dp(14), dp(10), dp(14), dp(10)); setTextColor(col(R.color.text))
        }
        var busy = false
        fun bubble(role: String, text: String) {
            val mine = role == "user"
            val shown = if (mine) text else Coach.withoutPlan(text).ifBlank { "Ecco la scheda:" }
            body.addView(TextView(this).apply {
                this.text = shown; textSize = 15f; setTextColor(if (mine) col(R.color.onaccent) else col(R.color.text)); setTextIsSelectable(true)
                background = roundBg(if (mine) col(R.color.accent) else col(R.color.card), 16, if (mine) null else col(R.color.stroke)); setPadding(dp(14), dp(10), dp(14), dp(10))
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = if (mine) Gravity.END else Gravity.START; setMargins(if (mine) dp(48) else 0, dp(4), if (mine) 0 else dp(48), dp(4)) }
            })
            if (!mine) Coach.parsePlan(text)?.let { plan ->
                body.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; background = roundBg(col(R.color.icbg), 16); setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(2), 0, dp(8)) }
                    addView(TextView(this@openCoachChat).apply { this.text = plan.name; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)) })
                    val inner = LinearLayout(this@openCoachChat).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, 0) }
                    plan.exercises.forEach { e -> val m = Moves.find(e.name); moveRow(inner, m, e.name, exLine(e), m?.let { mm -> { showMove(mm) } }) }
                    addView(inner)
                    addView(LinearLayout(this@openCoachChat).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(MaterialButton(this@openCoachChat).apply { this.text = "▶ Inizia"; isAllCaps = false; setOnClickListener { runPlan(plan, coach = true) } }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                        addView(MaterialButton(this@openCoachChat, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                            this.text = "💾 Salva"; isAllCaps = false
                            setOnClickListener { Fitness.savePlans(this@openCoachChat, Fitness.plans(this@openCoachChat) + plan.copy(id = Fitness.newPlanId())); toast("✓ Salvata in «Schede»") }
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                    })
                })
            }
        }
        fun scrollDown() { (body.parent as? android.widget.ScrollView)?.let { sv -> sv.post { sv.fullScroll(View.FOCUS_DOWN) } } }
        fun renderAll() {
            body.removeAllViews()
            if (chat.isEmpty()) {
                emptyState(body, "✨", "Chiedi al tuo coach", if (AiKeys.ready(this).isEmpty())
                    "Per la chat serve la chiave di un servizio AI: Groq, Google Gemini o NVIDIA (molti hanno un piano gratuito). La chiave resta solo su questo telefono."
                else "Scrivi come a un allenatore: cosa vuoi ottenere, quanto tempo hai, se qualcosa ti fa male. Se serve ti preparo una scheda da fare subito.",
                    if (AiKeys.ready(this).isEmpty()) "Aggiungi la chiave" else null, if (AiKeys.ready(this).isEmpty()) ({ aiSettings { refreshTop() } }) else null)
                if (AiKeys.ready(this).isNotEmpty()) {
                    sectionLabel(body, "PER ESEMPIO")
                    listOf("Ho 15 minuti, fammi una scheda per le gambe senza salti", "Mi fa male la parte bassa della schiena: cosa posso fare?", "Come miglioro le flessioni se ne faccio solo 3?", "Domani corro 5 km: cosa faccio oggi?")
                        .forEach { q -> listRow(body, "💬", q, null) { send(q) } }
                }
            } else {
                body.addView(TextView(this).apply { text = "Nuova conversazione"; textSize = 13f; setTextColor(col(R.color.accent)); gravity = Gravity.END; setPadding(0, dp(4), dp(6), dp(4)); setOnClickListener { chat.clear(); renderAll() } })
                chat.forEach { (r, t) -> bubble(r, t) }
            }
            scrollDown()
        }
        val thinking = TextView(this).apply { text = "Il coach sta scrivendo…"; textSize = 13f; setTextColor(col(R.color.muted)); setPadding(dp(6), dp(6), dp(6), dp(6)) }
        send = { text ->
            val q = text.trim()
            if (q.isNotEmpty() && !busy) {
                if (AiKeys.ready(this).isEmpty()) aiSettings { refreshTop() }
                else {
                    busy = true; input.setText("")
                    if (chat.isEmpty()) body.removeAllViews()
                    chat += "user" to q; bubble("user", q); body.addView(thinking); scrollDown()
                    io.execute {
                        val r = CoachAi.ask(this, chat.toList())
                        runOnUiThread {
                            busy = false; body.removeView(thinking)
                            r.onSuccess { a -> chat += "assistant" to a; bubble("assistant", a) }
                                .onFailure { e -> chat.removeLastOrNull(); body.addView(TextView(this).apply { this.text = "⚠️ Non sono riuscito a chiedere al coach.\n${e.message}"; textSize = 13.5f; setTextColor(RED); setPadding(dp(6), dp(6), dp(6), dp(6)) }); input.setText(q) }
                            scrollDown()
                        }
                    }
                }
            }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(10)); setBackgroundColor(col(R.color.bg))
            addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            addView(iconBox("📤", 46).apply {
                background = roundBg(col(R.color.accent), 23); (layoutParams as LinearLayout.LayoutParams).marginStart = dp(8); contentDescription = "Invia"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES; isClickable = true; setOnClickListener { send(input.text.toString()) }
                ((this as android.widget.FrameLayout).getChildAt(0) as? IconView)?.color = col(R.color.onaccent)
            })
        }
        root.addView(bar)
        scr.refresh = { renderAll() }
        renderAll()
    }
}

/** Impostazioni della chat: servizio preferito, chiavi (nascoste), modelli, cosa mandare. */
fun MainActivity.aiSettings(done: () -> Unit) {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), dp(4)) }
    box.addView(TextView(this).apply {
        text = "Incolla la chiave di uno o più servizi. Si prova prima quello scelto; se non risponde (o hai finito il limite gratuito) si passa al successivo.\nLe chiavi restano su questo telefono: non vanno nei backup né nel file esportato."
        textSize = 13f; setTextColor(col(R.color.muted)); setPadding(0, 0, 0, dp(8))
    })
    var first = AiKeys.first(this)
    val radios = android.widget.RadioGroup(this)
    val fields = Coach.PROVIDERS.map { p ->
        radios.addView(android.widget.RadioButton(this).apply { text = "Prima ${p.label}"; id = View.generateViewId(); tag = p.id; isChecked = p.id == first })
        box.addView(TextView(this).apply { text = p.label; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)); setPadding(0, dp(12), 0, 0) })
        val key = field("Chiave (${p.keyHint})", AiKeys.key(this, p.id)).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; typeface = android.graphics.Typeface.MONOSPACE }
        val model = field("Modello", AiKeys.model(this, p)).apply { textSize = 13f }
        box.addView(key); box.addView(model)
        Triple(p, key, model)
    }
    box.addView(TextView(this).apply { text = "SERVIZIO DA PROVARE PRIMA"; textSize = 12f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.muted)); setPadding(0, dp(14), 0, dp(2)) })
    box.addView(radios)
    radios.setOnCheckedChangeListener { g, id -> first = g.findViewById<View>(id)?.tag as? String ?: first }
    val profile = android.widget.CheckBox(this).apply { text = "Manda anche età, sesso, altezza e peso (risposte più precise)"; isChecked = AiKeys.sendProfile(this@aiSettings) }
    box.addView(profile)
    box.addView(TextView(this).apply { text = "Al coach vanno solo allenamenti, passi e sonno (e i dati sopra, se lo scegli). Mai farmaci, visite, referti o scheda d'emergenza."; textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(6), 0, 0) })
    MaterialAlertDialogBuilder(this).setTitle("Coach AI").setView(android.widget.ScrollView(this).apply { addView(box) })
        .setPositiveButton("Salva") { _, _ ->
            fields.forEach { (p, k, m) -> AiKeys.save(this, p.id, k.text.toString(), m.text.toString()) }
            AiKeys.setFirst(this, first); AiKeys.setSendProfile(this, profile.isChecked); done()
        }.setNegativeButton("Annulla", null).show()
}

// ======================= contro il tempo =======================

fun MainActivity.openChallenges() {
    openScreen("Contro il tempo") { body, scr ->
        fun render() {
            body.removeAllViews()
            hintLabel(body, "Fai tutti i giri il prima possibile, con riposi brevi quando servono. Il tempo diventa il tuo record: la volta dopo prova a batterlo.")
            Moves.LEVELS.forEachIndexed { i, lv ->
                val l = Challenges.ALL.filter { it.level == i + 1 }
                if (l.isEmpty()) return@forEachIndexed
                sectionLabel(body, lv.uppercase())
                l.forEach { ch ->
                    val pb = CoachStore.best(this, ch.id)
                    listRow(body, "⏱", ch.name, "${ch.rounds} giri: " + ch.items.joinToString(", ") { it.label() } + " · " + (if (pb > 0) "record ${Challenges.clock(pb)}" else "circa ${ch.estMinutes()} min")) { showChallenge(ch) }
                }
            }
        }
        scr.refresh = { render() }
        render()
    }
}

private fun MainActivity.showChallenge(ch: ForTime) {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(6), dp(20), dp(4)) }
    box.addView(TextView(this).apply { text = ch.note + "\n${ch.rounds} giri di:"; textSize = 14f; setTextColor(col(R.color.text)); setPadding(0, 0, 0, dp(6)) })
    ch.items.forEach { it -> moveRow(box, it.move, it.label(), it.move?.let { m -> m.how.substringBefore('.') + "." }, it.move?.let { m -> { showMove(m) } }) }
    val pb = CoachStore.best(this, ch.id)
    if (pb > 0) box.addView(TextView(this).apply { text = "🏅 Il tuo record: ${Challenges.clock(pb)} (${CoachStore.tries(this@showChallenge, ch.id)} volte)"; textSize = 14f; setTextColor(col(R.color.accent)); setPadding(0, dp(8), 0, 0) })
    MaterialAlertDialogBuilder(this).setTitle("⏱ ${ch.name}").setView(android.widget.ScrollView(this).apply { addView(box) })
        .setPositiveButton("▶ Via!") { _, _ -> runChallenge(ch) }.setNegativeButton("Chiudi", null).show()
}

/** Il cronometro sale; tocchi «Fatto» a ogni esercizio; alla fine il tempo e, se è il caso, il nuovo record. */
fun MainActivity.runChallenge(ch: ForTime) {
    val steps = ch.steps()
    var i = 0; var startedAt = 0L; var pausedAt = 0L; var pausedTotal = 0L
    var ticker: CountDownTimer? = null
    var finished = false
    val tone = runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 80) }.getOrNull()
    val voice = Fitness.voice(this)
    fun say(t: String) { if (voice) speak(t) }
    openScreen("⏱ ${ch.name}") { body, s ->
        val clock = TextView(this).apply { textSize = 56f; setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(col(R.color.text)) }
        fun elapsed() = if (startedAt == 0L) 0L else (if (pausedAt > 0) pausedAt else System.currentTimeMillis()) - startedAt - pausedTotal
        lateinit var render: () -> Unit
        fun finish() {
            ticker?.cancel(); finished = true
            val ms = elapsed()
            val isNew = CoachStore.recordTime(this, ch.id, ms)
            val min = (ms / 60000).toInt().coerceAtLeast(1)
            Fitness.addAct(this, Act(UUID.randomUUID().toString(), "fortime", "⏱ ${ch.name} · ${Challenges.clock(ms)}", startedAt, min, 0.0, Fitness.kcalForSession(Food.profile(this).weightKg, min) * 1.4))
            body.removeAllViews()
            body.addView(iconBox("🏅", 110).apply { (layoutParams as LinearLayout.LayoutParams).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(24) } })
            body.addView(TextView(this).apply { text = Challenges.clock(ms); textSize = 52f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(col(R.color.text)) })
            body.addView(TextView(this).apply {
                text = if (isNew) "🎉 Nuovo record personale!" else "Il tuo record resta ${Challenges.clock(CoachStore.best(this@runChallenge, ch.id))}"
                textSize = 17f; gravity = Gravity.CENTER; setTextColor(if (isNew) col(R.color.accent) else col(R.color.muted)); setPadding(0, dp(4), 0, dp(8))
            })
            if (isNew) confetti()
            coachFeedbackRow(body)
            body.addView(primaryButton("Chiudi") { pop() })
            runCatching { tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 500) }
            say(if (isNew) "Finito! Nuovo record: ${ms / 60000} minuti e ${ms / 1000 % 60} secondi." else "Finito! ${ms / 60000} minuti e ${ms / 1000 % 60} secondi.")
        }
        render = {
            body.removeAllViews()
            val (round, item) = steps[i]
            body.addView(progressBar(i.toDouble() / steps.size, col(R.color.accent), 6))
            body.addView(TextView(this).apply { text = "Giro $round di ${ch.rounds} · esercizio ${i % ch.items.size + 1} di ${ch.items.size}"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, 0) })
            body.addView(clock); clock.text = Challenges.clock(elapsed())
            body.addView(TextView(this).apply { text = item.label(); textSize = 26f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(col(R.color.text)); setPadding(dp(8), dp(4), dp(8), dp(4)) })
            item.move?.let { m -> body.addView(moveView(m, 170).apply { setOnClickListener { showMove(m) } }) }
            steps.getOrNull(i + 1)?.let { (r, nx) -> body.addView(TextView(this).apply { text = "Dopo: ${nx.label()}" + if (r != round) " (giro $r)" else ""; textSize = 14f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)); setPadding(0, dp(6), 0, 0) }) }
            body.addView(MaterialButton(this).apply {
                text = if (i == steps.lastIndex) "🏁 Fatto: finito!" else "✓ Fatto"; isAllCaps = false; textSize = 19f
                layoutParams = LinearLayout.LayoutParams(-1, dp(72)).apply { setMargins(dp(12), dp(10), dp(12), dp(6)) }
                setOnClickListener {
                    if (pausedAt > 0) return@setOnClickListener
                    runCatching { tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120) }
                    if (i == steps.lastIndex) finish() else { i++; render(); say(steps[i].second.label().replace(" · ", " ")) }
                }
            })
            body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = if (pausedAt > 0) "▶ Riprendi" else "⏸ Pausa"; isAllCaps = false; setTextColor(col(R.color.muted))
                layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { setMargins(dp(12), dp(6), dp(12), dp(6)) }
                setOnClickListener {
                    if (pausedAt > 0) { pausedTotal += System.currentTimeMillis() - pausedAt; pausedAt = 0 } else pausedAt = System.currentTimeMillis()
                    render()
                }
            })
            CoachStore.best(this, ch.id).takeIf { it > 0 }?.let { pb -> body.addView(TextView(this).apply { text = "Record da battere: ${Challenges.clock(pb)}"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)) }) }
        }
        // 3-2-1 e via: il cronometro parte dopo
        body.addView(TextView(this).apply { text = "${ch.rounds} giri di: " + ch.items.joinToString(", ") { it.label() }; textSize = 15f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)); setPadding(dp(12), dp(24), dp(12), dp(12)) })
        val count = TextView(this).apply { textSize = 96f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(col(R.color.accent)); text = "3" }
        body.addView(count)
        say("${ch.name}. ${ch.rounds} giri. Pronti.")
        ticker = object : CountDownTimer(4000, 250) {
            var last = -1L
            override fun onTick(ms: Long) { val n = ms / 1000; if (n != last && n in 1..3) { last = n; count.text = "$n"; say("$n") } }
            override fun onFinish() {
                startedAt = System.currentTimeMillis(); say("Via! " + steps[0].second.label().replace(" · ", " ")); render()
                ticker = object : CountDownTimer(24 * 3_600_000L, 500) {
                    override fun onTick(ms: Long) { if (!finished) clock.text = Challenges.clock(elapsed()) }
                    override fun onFinish() {}
                }.start()
            }
        }.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        s.onClose = { ticker?.cancel(); tone?.release(); stopSpeaking(); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        s.back = {
            if (finished || startedAt == 0L) false
            else { MaterialAlertDialogBuilder(this).setTitle("Interrompere la sfida?").setMessage("Il tempo non verrà salvato.").setPositiveButton("Interrompi") { _, _ -> pop() }.setNegativeButton("Continua", null).show(); true }
        }
    }
}
