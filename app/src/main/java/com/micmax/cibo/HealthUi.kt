package com.micmax.cibo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

// ======================= Peso e misure =======================

fun MainActivity.addMeasureDialog(type: String, onSaved: () -> Unit) {
    val (label, unit, two) = Health.TYPES[type] ?: return
    val a = field(if (two) "Massima ($unit)" else "Valore ($unit)", null, number = true)
    val b = field("Minima ($unit)", null, number = true)
    val note = field("Nota (facoltativa)", null)
    val box = if (two) form(a, b, note) else form(a, note)
    MaterialAlertDialogBuilder(this).setTitle(label).setView(box).setPositiveButton("Salva") { _, _ ->
        val v1 = Calc.num(a.text?.toString().orEmpty())
        val v2 = if (two) Calc.num(b.text?.toString().orEmpty()) else null
        if (v1 == null || (two && v2 == null)) { toast("Valore non valido"); return@setPositiveButton }
        Health.add(this, Measure(UUID.randomUUID().toString(), type, v1, v2, System.currentTimeMillis(), note.text?.toString()?.trim().orEmpty()))
        onSaved()
    }.setNegativeButton("Annulla", null).show()
    a.requestFocus()
}

fun MainActivity.openBody(startType: String = "peso") {
    var type = startType
    openScreen("Peso e misure", "Aggiungi", { addMeasureDialog(type) { refreshTop() } }) { body, scr ->
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), 0, dp(4), dp(8)) }
        val tabsScroll = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        (scr.view as LinearLayout).addView(tabsScroll, 1)
        fun render() {
            tabs.removeAllViews()
            Health.TYPES.forEach { (k, v) ->
                val active = k == type
                tabs.addView(TextView(this).apply {
                    text = v.first; textSize = 13f; setTextColor(if (active) android.graphics.Color.WHITE else col(R.color.text)); setPadding(dp(12), dp(7), dp(12), dp(7))
                    background = roundBg(if (active) col(R.color.accent) else col(R.color.card), 16, if (active) col(R.color.accent) else col(R.color.stroke))
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) }
                    setOnClickListener { type = k; render() }
                })
            }
            body.removeAllViews()
            val (label, _, two) = Health.TYPES[type]!!
            val list = Health.load(this).filter { it.type == type }.sortedBy { it.time }
            if (list.isEmpty()) hintLabel(body, "Nessuna misurazione di ${label.substringAfter(' ').lowercase()}.\nTocca «Aggiungi».")
            else {
                val last = list.takeLast(30)
                body.addView(TextView(this).apply { text = Health.fmt(list.last()); textSize = 34f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(col(R.color.text)); setPadding(0, dp(6), 0, 0) })
                body.addView(TextView(this).apply {
                    text = "ultima · " + Store.ago(list.last().time) + if (list.size > 1) " · media ${String.format(Locale.ITALIAN, "%.1f", last.map { it.v1 }.average())}" +
                        (if (two) "/" + String.format(Locale.ITALIAN, "%.1f", last.mapNotNull { it.v2 }.average()) else "") else ""
                    textSize = 13f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted))
                })
                if (type == "peso") {
                    val p = Food.profile(this)
                    if (p.heightCm > 0) {
                        val bmi = Nutrition.bmi(list.last().v1, p.heightCm); val (lo, hi) = Nutrition.healthyRange(p.heightCm)
                        body.addView(statRow(Triple("BMI", "%.1f".format(Locale.ITALIAN, bmi), Nutrition.bmiLabel(bmi)), Triple("Peso sano", "%.0f–%.0f kg".format(Locale.ITALIAN, lo, hi), "per la tua altezza"),
                            Triple("Da inizio", "%+.1f kg".format(Locale.ITALIAN, list.last().v1 - list.first().v1), "${list.size} misure")))
                    } else hintLabel(body, "Aggiungi l'altezza in Fabbisogno per vedere il BMI.")
                }
                if (last.size >= 2) body.addView(LineChartView(this).apply {
                    series1 = last.map { it.v1 }; series2 = if (two) last.mapNotNull { it.v2 } else null
                    color1 = if (two) 0xFFE53935.toInt() else col(R.color.accent); color2 = col(R.color.accent); textColor = col(R.color.muted); gridColor = col(R.color.stroke)
                    layoutParams = LinearLayout.LayoutParams(-1, dp(180)).apply { setMargins(dp(6), dp(12), dp(6), dp(4)) }
                })
                sectionLabel(body, "MISURAZIONI")
                val f = SimpleDateFormat("EEE d MMM, HH:mm", Locale.ITALIAN)
                list.reversed().take(60).forEach { m ->
                    listRow(body, "", Health.fmt(m), f.format(Date(m.time)) + if (m.note.isNotBlank()) " · ${m.note}" else "",
                        onRemove = { Health.save(this, Health.load(this).filter { it.id != m.id }); if (type == "peso") Health.syncWeight(this); render() }) {}
                }
            }
            val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(14), dp(6), 0) }
            btns.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "⏰ Promemoria"; isAllCaps = false; layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
                setOnClickListener {
                    ensureNotificationPermission()
                    TimePickerDialog(this@openBody, { _, h, m ->
                        val t = "%02d:%02d".format(h, m)
                        Reminders.add(this@openBody, "Misurare: ${label.substringAfter(' ').lowercase()}", Meds.nextAt(t), "day", "body")
                        toast("⏰ Ogni giorno alle $t")
                    }, 8, 0, true).show()
                }
            })
            btns.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "📤 Per il medico"; isAllCaps = false; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setOnClickListener { openReport() }
            })
            body.addView(btns)
        }
        scr.refresh = { render() }
        render()
    }
}

// ======================= Farmaci =======================

fun MainActivity.openMeds() {
    openScreen("Farmaci", "Aggiungi", { editMed(null) { refreshTop() } }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val list = Meds.load(this)
            if (list.isEmpty()) { hintLabel(body, "Nessun farmaco.\nAggiungine uno: ti ricordo gli orari e ti avviso quando le scorte stanno finendo."); return }
            list.forEach { m ->
                val low = Meds.isLow(m)
                val sub = buildList {
                    if (m.dose.isNotBlank()) add(m.dose)
                    Meds.nextDose(m)?.let { add("prossima: $it") }
                    if (m.times.isNotEmpty()) add("oggi ${Meds.takenToday(this@openMeds, m.id)}/${m.times.size}")
                    add(if (m.stock <= 0 && m.times.isNotEmpty()) "⚠️ finito" else "${m.stock} rimaste" + if (m.times.isNotEmpty()) " (~${Meds.daysLeft(m)} giorni)" else "")
                }.joinToString(" · ")
                listRow(body, if (low) "⚠️" else "💊", m.name, sub) {
                    MaterialAlertDialogBuilder(this).setTitle(m.name).setItems(arrayOf("✅ Ho preso la dose", "Modifica", "Elimina")) { _, w ->
                        when (w) {
                            0 -> { Meds.markTaken(this, m.id); toast("Segnato. Restano ${(m.stock - m.perDose).coerceAtLeast(0)}"); render() }
                            1 -> editMed(m) { render() }
                            2 -> MaterialAlertDialogBuilder(this).setTitle("Eliminare ${m.name}?").setPositiveButton("Elimina") { _, _ -> Meds.remove(this, m.id); render() }.setNegativeButton("Annulla", null).show()
                        }
                    }.show()
                }
            }
            hintLabel(body, "Le scorte scendono quando tocchi «Ho preso la dose». Non sostituisce le indicazioni del medico.")
        }
        scr.refresh = { render() }
        render()
    }
}

private fun MainActivity.editMed(m: Med?, done: () -> Unit) {
    val name = field("Nome del farmaco", m?.name)
    val dose = field("Dose (es. 1 compressa da 500 mg)", m?.dose)
    val times = field("Orari (es. 8, 14, 20:30)", m?.times?.joinToString(", "))
    val per = field("Pastiglie per dose", (m?.perDose ?: 1).toString(), number = true)
    val stock = field("Quante ne hai nella confezione", m?.stock?.toString(), number = true)
    MaterialAlertDialogBuilder(this).setTitle(if (m == null) "Nuovo farmaco" else "Modifica").setView(form(name, dose, times, per, stock))
        .setPositiveButton("Salva") { _, _ ->
            if (name.text.toString().isBlank()) return@setPositiveButton toast("Scrivi il nome")
            val t = Meds.parseTimes(times.text.toString())
            if (times.text.toString().isNotBlank() && t.isEmpty()) return@setPositiveButton toast("Orari non validi: scrivi per esempio 8, 20:30")
            if (t.isNotEmpty()) ensureNotificationPermission()
            Meds.upsert(this, Med(m?.id ?: Meds.newId(), name.text.toString().trim(), dose.text.toString().trim(), t, stock.text.toString().toIntOrNull() ?: 0, per.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1))
            done()
        }.setNegativeButton("Annulla", null).show()
}

// ======================= Visite =======================

fun MainActivity.openVisits() {
    openScreen("Visite mediche", "Aggiungi", { editVisit(null) { refreshTop() } }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val all = Visits.load(this)
            if (all.isEmpty()) { hintLabel(body, "Nessuna visita.\nAggiungine una: ti avviso la sera prima e due ore prima."); return }
            val now = System.currentTimeMillis(); val f = SimpleDateFormat("EEE d MMM yyyy 'alle' HH:mm", Locale.ITALIAN)
            val next = all.filter { it.time >= now }; val past = all.filter { it.time < now }.reversed()
            if (next.isNotEmpty()) sectionLabel(body, "PROSSIME")
            next.forEach { v -> listRow(body, "🩺", v.title, f.format(Date(v.time)).replaceFirstChar { it.uppercase() } + (if (v.place.isNotBlank()) " · ${v.place}" else ""), onRemove = { Visits.remove(this, v.id); render() }) { editVisit(v) { render() } } }
            if (past.isNotEmpty()) sectionLabel(body, "PASSATE")
            past.take(10).forEach { v -> listRow(body, "📋", v.title, f.format(Date(v.time)), onRemove = { Visits.remove(this, v.id); render() }) { editVisit(v) { render() } } }
        }
        scr.refresh = { render() }
        render()
    }
}

private fun MainActivity.editVisit(v: Visit?, done: () -> Unit) {
    val title = field("Che visita (es. Cardiologo)", v?.title)
    val place = field("Dove (facoltativo)", v?.place)
    val note = field("Nota (facoltativo)", v?.note)
    var at = v?.time ?: Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }.timeInMillis
    val f = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.ITALIAN)
    val whenBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { isAllCaps = false }
    fun show() { whenBtn.text = "📅 " + f.format(Date(at)) }
    show()
    whenBtn.setOnClickListener {
        val c = Calendar.getInstance().apply { timeInMillis = at }
        DatePickerDialog(this, { _, y, mo, d ->
            TimePickerDialog(this, { _, h, m ->
                at = Calendar.getInstance().apply { set(y, mo, d, h, m, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis; show()
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    val box = form(title, place, note).apply { addView(whenBtn) }
    MaterialAlertDialogBuilder(this).setTitle(if (v == null) "Nuova visita" else "Modifica visita").setView(box)
        .setPositiveButton("Salva") { _, _ ->
            if (title.text.toString().isBlank()) return@setPositiveButton toast("Scrivi che visita è")
            ensureNotificationPermission()
            Visits.upsert(this, Visit(v?.id ?: Visits.newId(), title.text.toString().trim(), at, place.text.toString().trim(), note.text.toString().trim()))
            toast("🩺 Salvata, con promemoria"); done()
        }.setNegativeButton("Annulla", null).show()
}

// ======================= Scheda d'emergenza =======================

fun MainActivity.openEmergency() {
    openScreen("Scheda d'emergenza", "Modifica", { editEmergency { refreshTop() } }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val e = Emergency.load(this)
            if (Emergency.isEmpty(e)) { hintLabel(body, "La scheda è vuota.\nTocca «Modifica» e scrivi gruppo sanguigno, allergie, farmaci e chi chiamare: in caso di bisogno serve ai soccorritori."); return }
            Emergency.lines(e).forEach { listRow(body, "", it, null) {} .also { r -> ((r as LinearLayout).getChildAt(0) as View).visibility = View.GONE } }
            sectionLabel(body, "SUL BLOCCO SCHERMO")
            val on = Emergency.onLockScreen(this)
            listRow(body, if (on) "🔓" else "🔒", if (on) "Visibile a schermo bloccato" else "Non visibile a schermo bloccato", if (on) "Chiunque può leggerla: tocca per nasconderla" else "Tocca per mostrarla in una notifica, leggibile anche senza sbloccare") {
                if (on) { Emergency.setOnLockScreen(this, false); render() }
                else MaterialAlertDialogBuilder(this).setTitle("Mostrarla a schermo bloccato?")
                    .setMessage("Chi ha il telefono in mano potrà leggere nome, allergie, patologie e contatti senza sbloccarlo. È utile ai soccorritori, ma è un dato personale visibile a chiunque.")
                    .setPositiveButton("Mostra") { _, _ -> ensureNotificationPermission(); Emergency.setOnLockScreen(this, true); render() }.setNegativeButton("Annulla", null).show()
            }
            listRow(body, "📤", "Condividi", "Come testo") { shareText("Scheda d'emergenza\n" + Emergency.lines(e).joinToString("\n")) }
        }
        scr.refresh = { render() }
        render()
    }
}

private fun MainActivity.editEmergency(done: () -> Unit) {
    val e = Emergency.load(this)
    val f = listOf(
        field("Nome e cognome", e.name), field("Data di nascita", e.birth), field("Gruppo sanguigno (es. A+)", e.blood), field("Allergie", e.allergies),
        field("Patologie", e.conditions), field("Farmaci che prendi", e.medicines), field("Contatto 1 (nome)", e.contact1), field("Telefono 1", e.phone1),
        field("Contatto 2 (nome)", e.contact2), field("Telefono 2", e.phone2), field("Note", e.notes))
    MaterialAlertDialogBuilder(this).setTitle("Scheda d'emergenza").setView(android.widget.ScrollView(this).apply { addView(form(*f.toTypedArray())) }).setPositiveButton("Salva") { _, _ ->
        val v = f.map { it.text.toString().trim() }
        Emergency.save(this, EmergencyCard(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10])); done()
    }.setNegativeButton("Annulla", null).show()
}

// ======================= Referto per il medico =======================

fun reportData(c: android.content.Context, from: Long, to: Long): Report.Data {
    val all = Health.load(c).filter { it.time in from..to }.sortedBy { it.time }
    val e = Emergency.load(c)
    return Report.Data(e.name, from, to, all.filter { it.type == "peso" }.map { it.time to it.v1 },
        all.filter { it.type == "pressione" && it.v2 != null }.map { Triple(it.time, it.v1.toInt(), it.v2!!.toInt()) },
        all.filter { it.type == "glicemia" }.map { it.time to it.v1 },
        Meds.load(c).map { it.name + if (it.dose.isNotBlank()) " (${it.dose})" else "" }, e.allergies, e.conditions)
}

fun MainActivity.openReport() {
    openScreen("Referto per il medico") { body, _ ->
        var days = 30
        fun render() {
            body.removeAllViews()
            val to = System.currentTimeMillis(); val from = to - days * 86_400_000L
            val data = reportData(this, from, to)
            sectionLabel(body, "PERIODO")
            listRow(body, "📅", "Ultimi $days giorni", "Tocca per cambiare") {
                val opts = intArrayOf(14, 30, 90, 180)
                MaterialAlertDialogBuilder(this).setItems(opts.map { "Ultimi $it giorni" }.toTypedArray()) { _, i -> days = opts[i]; render() }.show()
            }
            Report.sections(data).forEach { (title, lines) ->
                sectionLabel(body, title.uppercase(Locale.ITALIAN))
                lines.forEach { body.addView(TextView(this).apply { text = it; textSize = 14f; setTextColor(col(R.color.text)); setPadding(dp(10), dp(4), dp(8), dp(4)) }) }
            }
            body.addView(primaryButton("📄 Crea il PDF e condividilo") { sharePdfReport(data) })
            hintLabel(body, "Il file contiene dati di salute: condividilo solo con il tuo medico.")
        }
        render()
    }
}

/** Il PDF del referto, disegnato riga per riga. */
fun writeReportPdf(data: Report.Data, out: File) {
    val doc = PdfDocument()
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; isFakeBoldText = true }
    val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; isFakeBoldText = true; color = 0xFF2EA66A.toInt() }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
    var n = 1; var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create()); var y = 60f
    fun newPage() { doc.finishPage(page); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create()); y = 50f }
    fun line(s: String, p: Paint, step: Float) { s.chunked(95).ifEmpty { listOf("") }.forEach { part -> if (y > 790f) newPage(); page.canvas.drawText(part, 40f, y, p); y += step } }
    line("Riepilogo di salute" + if (data.name.isNotBlank()) " — ${data.name}" else "", title, 28f)
    line("Creato il " + SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN).format(Date()), text, 20f)
    Report.sections(data).forEach { (t, ls) -> y += 10f; line(t, head, 20f); ls.forEach { line("• $it", text, 15f) } }
    y += 20f; line("Dati inseriti dal paziente: non sono una diagnosi.", text, 14f)
    doc.finishPage(page)
    out.outputStream().use { doc.writeTo(it) }; doc.close()
}

private fun MainActivity.sharePdfReport(data: Report.Data) {
    io.execute {
        val file = runCatching {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            File(dir, "referto_${SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())}.pdf").also { writeReportPdf(data, it) }
        }.getOrNull()
        runOnUiThread {
            if (file == null) { toast("Non sono riuscito a creare il PDF"); return@runOnUiThread }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Condividi il referto"))
        }
    }
}
