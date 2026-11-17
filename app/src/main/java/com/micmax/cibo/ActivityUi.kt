package com.micmax.cibo

import android.app.TimePickerDialog
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.health.connect.client.HealthConnectClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.UUID

// ======================= Health Connect: orologio e app fitness =======================

/** Legge Health Connect in background (al massimo ogni 5 minuti, salvo [force]) e poi esegue [then] sul thread principale. */
fun MainActivity.syncHealthConnect(force: Boolean = false, then: (() -> Unit)? = null) {
    if (Hc.status(this) != Hc.SDK_AVAILABLE) { then?.invoke(); return }
    val last = Hc.cached(this)?.time ?: 0L
    if (!force && System.currentTimeMillis() - last < 5 * 60_000L) { then?.invoke(); return }
    io.execute {
        Hc.refreshBlocking(this)
        runOnUiThread { then?.invoke() }
    }
}

fun MainActivity.openHcConnect() {
    openScreen("Orologio e passi") { body, scr ->
        fun render() {
            body.removeAllViews()
            when (Hc.status(this)) {
                Hc.SDK_AVAILABLE -> {}
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    hintLabel(body, "Health Connect va installato o aggiornato dal Play Store per poter leggere i dati.")
                    listRow(body, "⬇️", "Installa o aggiorna Health Connect", null) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${Hc.PACKAGE}&url=healthconnect%3A%2F%2Fonboarding"))) }
                            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=${Hc.PACKAGE}"))) } }
                    }
                    return
                }
                else -> { hintLabel(body, "Questo telefono non supporta Health Connect, quindi i dati dell'orologio non si possono leggere da qui."); return }
            }
            val snap = Hc.cached(this)
            sectionLabel(body, "COLLEGAMENTO")
            listRow(body, "1️⃣", "Consenti la lettura", "Passi, battito, sonno, peso, allenamenti") { hcPermission.launch(Hc.PERMISSIONS) }
            listRow(body, "2️⃣", "Aggiorna ora", if (snap != null) "Ultima lettura ${Store.ago(snap.time)}" else "Mai letto") {
                toast("Leggo i dati…"); syncHealthConnect(true) { render(); toast("Dati aggiornati") }
            }
            listRow(body, "⚙️", "Apri Health Connect", "Qui vedi quali app scrivono i dati e puoi revocare i permessi") {
                runCatching { startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }.onFailure { toast("Non riesco ad aprire Health Connect") }
            }
            openWatchApp(body)
            if (snap != null) {
                sectionLabel(body, "ULTIMA LETTURA")
                val today = Hc.today(snap); val night = Hc.lastNight(snap)
                val rows = buildList {
                    today?.takeIf { it.steps > 0 }?.let { add("👟 Passi oggi" to String.format(Locale.ITALIAN, "%,d", it.steps)) }
                    today?.takeIf { it.distanceM > 0 }?.let { add("📍 Distanza oggi" to "%.2f km".format(Locale.ITALIAN, it.distanceM / 1000)) }
                    today?.takeIf { it.activeKcal > 0 }?.let { add("🔥 Calorie attive oggi" to "${it.activeKcal.toInt()} kcal") }
                    night?.let { add("😴 Sonno (${it.date})" to Hc.duration(it.sleepMin)) }
                    Hc.last(snap.days, 7).lastOrNull { it.restHr > 0 }?.let { add("💓 Battito a riposo" to "${it.restHr} bpm") }
                    snap.exercises.firstOrNull()?.let { add("🏅 Ultimo allenamento" to "${it.type} · ${SimpleDateFormat("EEE d MMM HH:mm", Locale.ITALIAN).format(Date(it.start))} · ${(it.end - it.start) / 60000} min") }
                }
                if (rows.isEmpty()) hintLabel(body, "Ancora nessun dato: tocca «Consenti la lettura» e poi «Aggiorna ora». Se non arriva niente, controlla che nell'app del tuo orologio la condivisione con Health Connect sia attiva.")
                rows.forEach { (l, v) -> listRow(body, "", l, v) {}.also { r -> ((r as LinearLayout).getChildAt(0) as View).visibility = View.GONE } }
            }
            sectionLabel(body, "L'OROLOGIO")
            hintLabel(body, "L'app non si accoppia all'orologio: lo fa la sua app. Apri quella app, cerca «Health Connect» o «Condividi i dati» e attiva passi, battito, sonno e allenamenti. Poi torna qui e tocca «Aggiorna ora». I dati restano sul telefono: l'app li legge soltanto.")
        }
        scr.refresh = { render() }
        render()
        syncHealthConnect { render() }
    }
}

private fun MainActivity.openWatchApp(body: LinearLayout) {
    val apps = listOf("com.sec.android.app.shealth" to "Samsung Health", "com.google.android.apps.fitness" to "Fitbit", "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.huami.watch.hmwatchmanager" to "Zepp", "com.xiaomi.wearable" to "Mi Fitness", "com.huawei.health" to "Huawei Salute", "fi.polar.polarflow" to "Polar Flow")
    val installed = apps.mapNotNull { (pkg, name) -> packageManager.getLaunchIntentForPackage(pkg)?.let { name to it } }
    when (installed.size) {
        0 -> {}
        1 -> listRow(body, "⌚", "Apri ${installed[0].first}", "Qui si attiva la condivisione con Health Connect") { runCatching { startActivity(installed[0].second) } }
        else -> listRow(body, "⌚", "Apri l'app dell'orologio", installed.joinToString { it.first }) {
            MaterialAlertDialogBuilder(this).setTitle("Quale orologio?").setItems(installed.map { it.first }.toTypedArray()) { _, w -> runCatching { startActivity(installed[w].second) } }.show()
        }
    }
}

// ======================= Passi =======================

fun MainActivity.openSteps() {
    val sp = getSharedPreferences("cibo", android.content.Context.MODE_PRIVATE)
    openScreen("Passi") { body, scr ->
        fun render() {
            body.removeAllViews()
            val snap = Hc.cached(this)
            val goal = sp.getInt("steps_goal", 8000)
            val days = Hc.last(snap?.days.orEmpty(), 7)
            val today = Hc.today(snap)
            if (snap == null || days.none { it.steps > 0 }) {
                hintLabel(body, "Nessun dato sui passi.\nSe hai un orologio o un'app che li conta, collega Health Connect e li vedi qui da soli.")
                listRow(body, "⌚", "Orologio e passi", "Collega Health Connect") { openHcConnect() }
                return
            }
            val steps = today?.steps ?: 0L
            body.addView(TextView(this).apply { text = String.format(Locale.ITALIAN, "%,d", steps); textSize = 40f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(if (steps >= goal) GREEN else col(R.color.text)); setPadding(0, dp(10), 0, 0) })
            body.addView(TextView(this).apply { text = "passi oggi · obiettivo ${String.format(Locale.ITALIAN, "%,d", goal)}"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)) })
            body.addView(progressBar(steps.toDouble() / goal, if (steps >= goal) GREEN else col(R.color.accent)))
            body.addView(statRow(
                Triple("Distanza", today?.distanceM?.takeIf { it > 0 }?.let { "%.1f km".format(Locale.ITALIAN, it / 1000) } ?: "—", null),
                Triple("Calorie attive", today?.activeKcal?.takeIf { it > 0 }?.let { "${it.toInt()}" } ?: "—", "kcal"),
                Triple("Media 7 giorni", String.format(Locale.ITALIAN, "%,d", Hc.avg(days) { it.steps.toDouble() }.toLong()), "passi")))
            sectionLabel(body, "ULTIMI 7 GIORNI")
            val max = maxOf(goal.toDouble(), days.maxOf { it.steps }.toDouble())
            days.forEach { d ->
                val label = SimpleDateFormat("EEE d", Locale.ITALIAN).format(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(d.date)!!)
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                line.addView(TextView(this).apply { text = label; textSize = 13f; setTextColor(col(R.color.muted)) }, LinearLayout.LayoutParams(dp(64), -2))
                line.addView(progressBar(d.steps / max, if (d.steps >= goal) GREEN else col(R.color.accent)), LinearLayout.LayoutParams(0, dp(10), 1f))
                line.addView(TextView(this).apply { text = String.format(Locale.ITALIAN, "%,d", d.steps); textSize = 13f; setTextColor(col(R.color.text)); gravity = Gravity.END }, LinearLayout.LayoutParams(dp(64), -2))
                body.addView(line)
            }
            sectionLabel(body, "IMPOSTAZIONI")
            listRow(body, "🎯", "Obiettivo giornaliero", "${String.format(Locale.ITALIAN, "%,d", goal)} passi") {
                val et = field("Passi al giorno", goal.toString(), number = true)
                MaterialAlertDialogBuilder(this).setTitle("Obiettivo").setView(form(et)).setPositiveButton("Salva") { _, _ ->
                    sp.edit().putInt("steps_goal", (et.text.toString().toIntOrNull() ?: goal).coerceIn(1000, 50000)).apply(); render()
                }.setNegativeButton("Annulla", null).show()
            }
            listRow(body, "⌚", "Orologio e passi", "Ultima lettura ${Store.ago(snap.time)}") { openHcConnect() }
            hintLabel(body, "Per un adulto si consiglia di muoversi ogni giorno: 7.000–10.000 passi sono un buon obiettivo.")
        }
        scr.refresh = { render() }
        render()
        syncHealthConnect { render() }
    }
}

// ======================= Sonno =======================

fun MainActivity.openSleep() {
    val sp = getSharedPreferences("cibo", android.content.Context.MODE_PRIVATE)
    openScreen("Sonno", "Aggiungi", {
        val et = field("Ore dormite (es. 7,5)", null, number = true)
        MaterialAlertDialogBuilder(this).setTitle("Com'è andata stanotte?").setView(form(et)).setPositiveButton("Salva") { _, _ ->
            val h = Calc.num(et.text.toString())
            if (h == null || h !in 0.5..16.0) toast("Scrivi le ore, per esempio 7,5")
            else { Health.save(this, Health.load(this) + Measure(UUID.randomUUID().toString(), "sonno", h, null, System.currentTimeMillis())); refreshTop() }
        }.setNegativeButton("Annulla", null).show()
    }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val snap = Hc.cached(this)
            val nights = snap?.days?.filter { it.sleepMin > 0 }.orEmpty().takeLast(14)
            val manual = Health.load(this).filter { it.type == "sonno" }.sortedBy { it.time }
            val hours = if (nights.isNotEmpty()) nights.map { it.sleepMin / 60.0 } else manual.takeLast(14).map { it.v1 }
            if (hours.isEmpty()) hintLabel(body, "Nessun dato sul sonno.\nSe colleghi l'orologio (Orologio e passi) lo vedi qui da solo; altrimenti usa «Aggiungi» in alto.")
            else {
                val avg = hours.average()
                body.addView(statRow(
                    Triple("Media", Hc.duration((avg * 60).toLong()), "ultime ${hours.size} notti"),
                    Triple("Ultima notte", Hc.duration((hours.last() * 60).toLong()), null),
                    Triple("Migliore", Hc.duration((hours.max() * 60).toLong()), null)))
                if (hours.size >= 2) body.addView(LineChartView(this).apply {
                    series1 = hours; color1 = 0xFF6366F1.toInt(); gridColor = col(R.color.stroke); textColor = col(R.color.muted)
                    layoutParams = LinearLayout.LayoutParams(-1, dp(160)).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                })
                hintLabel(body, "Per gli adulti si consigliano 7–9 ore a notte.")
            }
            if (nights.isNotEmpty()) {
                sectionLabel(body, "DALL'OROLOGIO")
                nights.reversed().take(7).forEach { n ->
                    listRow(body, "😴", "${n.date}: ${Hc.duration(n.sleepMin)}", if (n.deepMin > 0 || n.remMin > 0) "profondo ${Hc.duration(n.deepMin)} · REM ${Hc.duration(n.remMin)}" else null) {}
                }
            }
            sectionLabel(body, "PROMEMORIA PER ANDARE A LETTO")
            val rid = sp.getInt("sleep_rem", 0); val time = sp.getString("sleep_time", "22:30") ?: "22:30"
            listRow(body, if (rid != 0) "🔔" else "🔕", if (rid != 0) "Ogni sera alle $time" else "Non attivo", if (rid != 0) "Tocca per cambiare o spegnere" else "Una notifica per ricordarti di prepararti a dormire") {
                MaterialAlertDialogBuilder(this).setItems(arrayOf("Scegli l'orario", if (rid != 0) "Spegni" else "Annulla")) { _, w ->
                    if (w == 1) { if (rid != 0) { Reminders.remove(this, rid); sp.edit().remove("sleep_rem").apply() }; render() }
                    else {
                        val (h, m) = time.split(':').map { it.toInt() }
                        TimePickerDialog(this, { _, hh, mm ->
                            if (rid != 0) Reminders.remove(this, rid)
                            ensureNotificationPermission()
                            val t = "%02d:%02d".format(hh, mm)
                            val r = Reminders.add(this, "😴 È ora di prepararsi per dormire", Meds.nextAt(t), "day", "sleep")
                            sp.edit().putInt("sleep_rem", r.id).putString("sleep_time", t).apply(); render()
                        }, h, m, true).show()
                    }
                }.show()
            }
            if (manual.isNotEmpty()) {
                sectionLabel(body, "INSERITO A MANO")
                manual.reversed().take(7).forEach { m -> listRow(body, "😴", Health.fmt(m), SimpleDateFormat("EEE d MMM", Locale.ITALIAN).format(Date(m.time)),
                    onRemove = { Health.save(this, Health.load(this).filter { it.id != m.id }); render() }) {} }
            }
        }
        scr.refresh = { render() }
        render()
        syncHealthConnect { render() }
    }
}

// ======================= Allenamento =======================

fun MainActivity.openTrain() {
    openScreen("Allenamento") { body, scr ->
        fun render() {
            body.removeAllViews()
            val times = Fitness.allTimes(this); val goal = Fitness.weeklyGoal(this)
            val n = Fitness.weekCount(times); val streak = Fitness.streakWeeks(times, goal)
            sectionLabel(body, "QUESTA SETTIMANA")
            body.addView(TextView(this).apply { text = "$n / $goal"; textSize = 36f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(if (n >= goal) GREEN else col(R.color.text)); setPadding(0, dp(4), 0, 0) })
            body.addView(progressBar(n.toDouble() / goal, if (n >= goal) GREEN else col(R.color.accent)))
            body.addView(TextView(this).apply {
                text = (if (n >= goal) "🎉 Obiettivo raggiunto! " else "Ancora ${goal - n} allenamenti. ") + if (streak > 0) "🔥 $streak ${if (streak == 1) "settimana" else "settimane"} di fila" else ""
                textSize = 14f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted))
            })
            sectionLabel(body, "ALLENATI")
            listRow(body, "🏋️", "Schede", "Serie, ripetizioni, riposo con cronometro e voce") { openPlans() }
            listRow(body, "⏱", "Tabata e intervalli", "Lavoro e riposo a tempo, con bip e voce") { openTabata() }
            listRow(body, "📝", "Ho fatto un allenamento", "Aggiungilo a mano: corsa, nuoto, palestra…") { addActDialog { render() } }
            sectionLabel(body, "STORICO")
            listRow(body, "📅", "Allenamenti fatti", "${Fitness.acts(this).size} registrati · anche quelli dell'orologio") { openActs() }
            sectionLabel(body, "IMPOSTAZIONI")
            listRow(body, "🎯", "Obiettivo settimanale", "$goal allenamenti a settimana") {
                MaterialAlertDialogBuilder(this).setTitle("Allenamenti a settimana").setItems((1..7).map { "$it" }.toTypedArray()) { _, i -> Fitness.setWeeklyGoal(this, i + 1); render() }.show()
            }
        }
        scr.refresh = { render() }
        render()
        syncHealthConnect { render() }
    }
}

fun MainActivity.addActDialog(done: () -> Unit) {
    val kinds = listOf("🏃 Corsa", "🚶 Camminata", "🚴 Bici", "🏊 Nuoto", "🏋️ Palestra", "🧘 Yoga", "⚽ Sport di squadra", "Altro")
    var kind = kinds[0]
    val kindBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { isAllCaps = false; text = "$kind ▾" }
    kindBtn.setOnClickListener { MaterialAlertDialogBuilder(this).setItems(kinds.toTypedArray()) { _, w -> kind = kinds[w]; kindBtn.text = "$kind ▾" }.show() }
    val min = field("Quanti minuti", "30", number = true)
    val km = field("Distanza in km (facoltativa)", null, number = true)
    MaterialAlertDialogBuilder(this).setTitle("Allenamento fatto").setView(form(kindBtn, min, km)).setPositiveButton("Salva") { _, _ ->
        val m = min.text.toString().toIntOrNull()
        if (m == null || m !in 1..600) { toast("Scrivi i minuti"); return@setPositiveButton }
        val w = Food.profile(this).weightKg
        Fitness.addAct(this, Act(UUID.randomUUID().toString(), "manual", kind, System.currentTimeMillis(), m, ((Calc.num(km.text.toString()) ?: 0.0) * 1000), Fitness.kcalForSession(w, m)))
        toast("✓ Registrato"); done()
    }.setNegativeButton("Annulla", null).show()
}

fun MainActivity.openActs() {
    openScreen("Allenamenti fatti", "Aggiungi", { addActDialog { refreshTop() } }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val acts = Fitness.acts(this)
            val watch = Hc.cached(this)?.exercises.orEmpty()
            val f = SimpleDateFormat("EEE d MMM, HH:mm", Locale.ITALIAN)
            if (acts.isEmpty() && watch.isEmpty()) { hintLabel(body, "Ancora nessun allenamento. Fai una scheda, un Tabata, oppure tocca «Aggiungi»."); return }
            if (acts.isNotEmpty()) sectionLabel(body, "REGISTRATI QUI · ${acts.size}")
            acts.take(40).forEach { a ->
                listRow(body, "🏅", a.title, f.format(Date(a.start)).replaceFirstChar { it.uppercase() } + " · ${a.durationMin} min" + (if (a.distanceM > 0) " · %.1f km".format(Locale.ITALIAN, a.distanceM / 1000) else "") + (if (a.kcal > 0) " · ~${a.kcal.toInt()} kcal" else ""),
                    onRemove = { Fitness.removeAct(this, a.id); render() }) {}
            }
            if (watch.isNotEmpty()) {
                sectionLabel(body, "DALL'OROLOGIO · ${watch.size}")
                watch.take(20).forEach { e -> listRow(body, "⌚", e.type + if (e.title.isNotBlank()) " · ${e.title}" else "", f.format(Date(e.start)).replaceFirstChar { it.uppercase() } + " · ${(e.end - e.start) / 60000} min") {} }
            }
        }
        scr.refresh = { render() }
        render()
    }
}

// ---------- schede ----------

fun MainActivity.openPlans() {
    openScreen("Schede di allenamento", "Nuova", { editPlan(null) { refreshTop() } }) { body, scr ->
        fun render() {
            body.removeAllViews()
            Fitness.plans(this).forEach { p ->
                listRow(body, "🏋️", p.name, "${p.exercises.size} esercizi · circa ${Fitness.planMinutes(p)} min", onLong = { editPlan(p) { render() } }) { runPlan(p) }
            }
            hintLabel(body, "Tocca una scheda per iniziare. Tieni premuto per modificarla o eliminarla. Il riposo ha il cronometro, con bip e voce.")
        }
        scr.refresh = { render() }
        render()
    }
}

private fun MainActivity.editPlan(p: WorkoutPlan?, done: () -> Unit) {
    val name = field("Nome della scheda", p?.name)
    val ex = field("Un esercizio per riga", p?.exercises?.joinToString("\n") { Fitness.exerciseText(it) } ?: "Squat 3x12 riposo 45\nPlank 3x30s riposo 30", multiline = true).apply { minLines = 6 }
    val box = form(name, ex)
    box.addView(TextView(this).apply { text = "Scrivi: nome, serie x ripetizioni, riposo in secondi.\nEsempi: «Squat 3x12 riposo 45» · «Plank 3x30s riposo 30» (la s indica secondi) · «Panca 4x8 riposo 90 40kg»"; textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(12), 0, 0) })
    val b = MaterialAlertDialogBuilder(this).setTitle(if (p == null) "Nuova scheda" else "Modifica scheda").setView(android.widget.ScrollView(this).apply { addView(box) })
        .setPositiveButton("Salva") { _, _ ->
            val list = Fitness.parseExercises(ex.text.toString())
            if (name.text.toString().isBlank() || list.isEmpty()) return@setPositiveButton toast("Serve un nome e almeno un esercizio, per esempio «Squat 3x12 riposo 45»")
            val cur = Fitness.plans(this).filter { it.id != p?.id }
            Fitness.savePlans(this, cur + WorkoutPlan(p?.id ?: Fitness.newPlanId(), name.text.toString().trim(), list)); done()
        }.setNegativeButton("Annulla", null)
    if (p != null) b.setNeutralButton("Elimina") { _, _ ->
        MaterialAlertDialogBuilder(this).setTitle("Eliminare «${p.name}»?").setPositiveButton("Elimina") { _, _ -> Fitness.savePlans(this, Fitness.plans(this).filter { it.id != p.id }); done() }.setNegativeButton("Annulla", null).show()
    }
    b.show()
}

private fun MainActivity.beepTone(): ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull()

/** Fa la scheda serie per serie: ripetizioni da toccare o conto alla rovescia, riposo a tempo, bip e (a scelta) voce. */
fun MainActivity.runPlan(plan: WorkoutPlan) {
    var exI = 0; var setI = 0; var reps = 0
    var timer: CountDownTimer? = null
    val tone = beepTone()
    var voice = Fitness.voice(this)
    val started = System.currentTimeMillis()
    var finished = false
    fun beep(long: Boolean = false) { runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, if (long) 500 else 150) } }
    fun say(t: String) { if (voice) speak(t) }
    var scr: Scr? = null
    scr = openScreen(plan.name.take(24)) { body, s ->
        lateinit var render: () -> Unit
        fun big(text: String, size: Float = 40f, color: Int = col(R.color.text)) = TextView(this).apply {
            this.text = text; textSize = size; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(color); setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        fun note(t: String) = TextView(this).apply { text = t; textSize = 15f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)); setPadding(8, 8, 8, 16) }
        fun btn(label: String, onClick: () -> Unit) = MaterialButton(this).apply {
            text = label; isAllCaps = false; textSize = 17f; layoutParams = LinearLayout.LayoutParams(-1, dp(60)).apply { setMargins(dp(12), dp(6), dp(12), dp(6)) }; setOnClickListener { onClick() }
        }
        fun voiceBtn() = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            isAllCaps = false; textSize = 13f
            fun lbl() { text = if (voice) "🔊 Voce: sì" else "🔇 Voce: no" }; lbl()
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) }
            setOnClickListener { voice = !voice; Fitness.setVoice(this@runPlan, voice); if (!voice) stopSpeaking(); lbl() }
        }
        fun nextSet() {
            timer?.cancel()
            val e = plan.exercises[exI]
            if (setI + 1 < e.sets) setI++ else { exI++; setI = 0 }
            reps = 0; render()
        }
        fun needsRest(e: Ex) = e.restSec > 0 && (setI + 1 < e.sets || exI + 1 < plan.exercises.size)
        fun rest(sec: Int) {
            body.removeAllViews()
            val e = plan.exercises.getOrNull(exI)
            val upNext = if (setI + 1 < (e?.sets ?: 0)) "${e!!.name}, serie ${setI + 2} di ${e.sets}" else plan.exercises.getOrNull(exI + 1)?.name?.let { "Prossimo: $it" } ?: "Ultimo esercizio completato"
            val t = big("$sec", 72f, 0xFF2563EB.toInt())
            body.addView(big("Riposo", 22f, col(R.color.muted))); body.addView(t); body.addView(note(upNext))
            body.addView(btn("Salta il riposo") { nextSet() }); body.addView(voiceBtn())
            say("Riposo. $sec secondi. $upNext.")
            timer = object : CountDownTimer(sec * 1000L, 250) {
                override fun onTick(ms: Long) {
                    val left = (ms + 999) / 1000
                    if (left != t.tag) { t.tag = left; if (left in 1..3) { if (voice) say("$left") else beep() } }
                    t.text = left.toString()
                }
                override fun onFinish() { beep(true); say("Via!"); nextSet() }
            }.start()
        }
        render = {
            body.removeAllViews()
            val e = plan.exercises.getOrNull(exI)
            if (e == null) {
                timer?.cancel(); finished = true
                val min = ((System.currentTimeMillis() - started) / 60000).toInt().coerceAtLeast(1)
                val kcal = Fitness.kcalForSession(Food.profile(this).weightKg, min)
                Fitness.addAct(this, Act(UUID.randomUUID().toString(), "plan", "🏋️ ${plan.name}", started, min, 0.0, kcal))
                body.addView(big("🎉", 64f)); body.addView(big("Scheda completata!", 24f))
                body.addView(statRow(Triple("Durata", "$min min", null), Triple("Calorie", "~${kcal.toInt()}", "stima")))
                body.addView(btn("Chiudi") { pop() })
                beep(true); say("Scheda completata. Bravo! $min minuti.")
            } else {
                body.addView(big("Esercizio ${exI + 1} di ${plan.exercises.size}", 14f, col(R.color.muted)))
                body.addView(big(e.name, 26f))
                body.addView(big("Serie ${setI + 1} di ${e.sets}", 18f, col(R.color.muted)))
                if (e.kg > 0) body.addView(big("${if (e.kg % 1.0 == 0.0) e.kg.toInt() else e.kg} kg", 16f, col(R.color.muted)))
                say("${e.name}. Serie ${setI + 1} di ${e.sets}. " + if (e.seconds > 0) "${e.seconds} secondi." else "${e.reps} ripetizioni.")
                if (e.seconds > 0) {
                    val t = big("${e.seconds}", 80f, 0xFF16A34A.toInt()); body.addView(t)
                    body.addView(btn("▶ Avvia il cronometro") {
                        timer?.cancel(); say("Via!")
                        timer = object : CountDownTimer(e.seconds * 1000L, 250) {
                            override fun onTick(ms: Long) { val left = (ms + 999) / 1000; if (left != t.tag) { t.tag = left; if (left in 1..3) { if (voice) say("$left") else beep() } }; t.text = left.toString() }
                            override fun onFinish() { beep(true); say("Finito."); if (needsRest(e)) rest(e.restSec) else nextSet() }
                        }.start()
                    })
                } else {
                    val counter = big("$reps / ${e.reps}", 72f, 0xFF16A34A.toInt()); body.addView(counter)
                    body.addView(btn("＋1 ripetizione (tocca)") {
                        reps++; counter.text = "$reps / ${e.reps}"
                        if (voice) say("$reps") else beep()
                        if (reps >= e.reps) { beep(true); say("Serie fatta."); if (needsRest(e)) rest(e.restSec) else nextSet() }
                    })
                    body.addView(btn("Serie fatta ✓") { if (needsRest(e)) rest(e.restSec) else nextSet() })
                }
                body.addView(btn("Salta esercizio") { timer?.cancel(); exI++; setI = 0; reps = 0; render() })
                body.addView(voiceBtn())
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        s.onClose = { timer?.cancel(); tone?.release(); stopSpeaking(); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        s.back = {
            if (finished) false
            else { MaterialAlertDialogBuilder(this).setTitle("Interrompere l'allenamento?").setPositiveButton("Interrompi") { _, _ -> pop() }.setNegativeButton("Continua", null).show(); true }
        }
        render()
    }
}

// ---------- Tabata ----------

fun MainActivity.openTabata() {
    var timer: CountDownTimer? = null
    val tone = beepTone()
    var running = false
    openScreen("Tabata e intervalli") { body, s ->
        var work = Fitness.tabataSetting(this, "work", 20); var rest = Fitness.tabataSetting(this, "rest", 10)
        var rounds = Fitness.tabataSetting(this, "rounds", 8); var prep = Fitness.tabataSetting(this, "prep", 10)
        var voice = Fitness.voice(this)
        fun big(size: Float) = TextView(this).apply { textSize = size; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(col(R.color.text)); setPadding(0, dp(6), 0, dp(2)) }
        val phase = big(30f); val clock = big(96f)
        val round = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)) }
        body.addView(phase); body.addView(clock); body.addView(round)
        val setup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(setup)
        var startBtn: MaterialButton? = null
        fun idle() {
            running = false; timer?.cancel()
            phase.text = "Pronto"; phase.setTextColor(col(R.color.text)); clock.setTextColor(col(R.color.text))
            clock.text = Fitness.clock(Fitness.tabata(prep, work, rest, rounds).sumOf { it.seconds }); round.text = "$rounds round · lavoro ${work}s · riposo ${rest}s"
            setup.visibility = View.VISIBLE; startBtn?.text = "▶ Inizia"
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        fun stepper(label: String, get: () -> Int, set: (Int) -> Unit, step: Int, min: Int, max: Int) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)) }
            val tv = TextView(this).apply { textSize = 17f; setTextColor(col(R.color.text)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
            fun upd() { tv.text = "$label: ${get()}"; idle() }
            fun b(t: String, d: Int) = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = t; textSize = 18f; minWidth = 0; minimumWidth = 0; layoutParams = LinearLayout.LayoutParams(dp(56), -2).apply { marginStart = dp(12) }
                setOnClickListener { set((get() + d).coerceIn(min, max)); upd() }
            }
            row.addView(tv); row.addView(b("−", -step)); row.addView(b("+", step)); setup.addView(row); upd()
        }
        stepper("Lavoro (s)", { work }, { work = it; Fitness.setTabataSetting(this, "work", it) }, 5, 5, 600)
        stepper("Riposo (s)", { rest }, { rest = it; Fitness.setTabataSetting(this, "rest", it) }, 5, 0, 600)
        stepper("Round", { rounds }, { rounds = it; Fitness.setTabataSetting(this, "rounds", it) }, 1, 1, 50)
        stepper("Preparazione (s)", { prep }, { prep = it; Fitness.setTabataSetting(this, "prep", it) }, 5, 0, 60)
        fun beep(long: Boolean) = runCatching { tone?.startTone(if (long) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD else ToneGenerator.TONE_PROP_BEEP, if (long) 600 else 150) }
        fun say(t: String) { if (voice) speak(t) }
        var startedAt = 0L
        fun run(list: List<Fitness.Phase>, i: Int) {
            if (i >= list.size) {
                beep(true); say("Finito! Bravo, allenamento completato."); idle(); phase.text = "✅ Finito!"
                val min = ((System.currentTimeMillis() - startedAt) / 60000).toInt().coerceAtLeast(1)
                Fitness.addAct(this, Act(UUID.randomUUID().toString(), "tabata", "⏱ Tabata $rounds×${work}s", startedAt, min, 0.0, Fitness.kcalForSession(Food.profile(this).weightKg, min) * 1.6))
                return
            }
            val ph = list[i]
            val color = when (ph.kind) { "lavoro" -> 0xFFDC2626.toInt(); "riposo" -> 0xFF16A34A.toInt(); else -> 0xFFF59E0B.toInt() }
            phase.text = when (ph.kind) { "lavoro" -> "🔥 LAVORO"; "riposo" -> "😮‍💨 RIPOSO"; else -> "Preparati" }
            phase.setTextColor(color); clock.setTextColor(color)
            round.text = if (ph.round > 0) "Round ${ph.round} di $rounds" else "Si parte tra poco"
            beep(true)
            say(when (ph.kind) {
                "lavoro" -> "Lavoro! " + (if (ph.round == rounds) "Ultimo round." else "Round ${ph.round}.")
                "riposo" -> "Riposo. ${ph.seconds} secondi."
                else -> "Preparati. Si parte tra ${ph.seconds} secondi."
            })
            timer = object : CountDownTimer(ph.seconds * 1000L, 200) {
                var lastShown = -1
                override fun onTick(ms: Long) {
                    val sec = ((ms + 999) / 1000).toInt()
                    if (sec != lastShown) { lastShown = sec; clock.text = Fitness.clock(sec); if (sec in 1..3) { if (voice) say("$sec") else beep(false) } }
                }
                override fun onFinish() { run(list, i + 1) }
            }.start()
        }
        startBtn = MaterialButton(this).apply {
            text = "▶ Inizia"; isAllCaps = false; textSize = 18f; layoutParams = LinearLayout.LayoutParams(-1, dp(64)).apply { setMargins(dp(12), dp(14), dp(12), dp(6)) }
            setOnClickListener {
                if (running) { idle(); return@setOnClickListener }
                running = true; setup.visibility = View.GONE; text = "⏹ Ferma"; startedAt = System.currentTimeMillis()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                run(Fitness.tabata(prep, work, rest, rounds), 0)
            }
        }
        body.addView(startBtn)
        body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            isAllCaps = false; text = if (voice) "🔊 Voce: sì" else "🔇 Voce: no"
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) }
            setOnClickListener { voice = !voice; Fitness.setVoice(this@openTabata, voice); if (!voice) stopSpeaking(); text = if (voice) "🔊 Voce: sì" else "🔇 Voce: no" }
        })
        body.addView(TextView(this).apply { text = "Lo schermo resta acceso durante l'allenamento. Tre bip brevi prima di ogni cambio, uno lungo al cambio."; textSize = 13f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)); setPadding(dp(8), dp(12), dp(8), 0) })
        s.onClose = { timer?.cancel(); tone?.release(); stopSpeaking(); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        idle()
    }
}
