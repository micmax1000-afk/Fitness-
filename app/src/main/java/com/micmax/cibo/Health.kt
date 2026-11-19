package com.micmax.cibo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Una misurazione. I nomi dei campi sono gli stessi di Web Launcher: i dati si passano da un'app all'altra. */
data class Measure(val id: String, val type: String, val v1: Double, val v2: Double?, val time: Long, val note: String = "")

object Health {
    /** tipo → (etichetta, unità, due valori?) */
    val TYPES = linkedMapOf(
        "peso" to Triple("⚖️ Peso", "kg", false),
        "pressione" to Triple("❤️ Pressione", "mmHg", true),
        "glicemia" to Triple("🩸 Glicemia", "mg/dL", false),
        "battito" to Triple("💓 Battito", "bpm", false),
        "temperatura" to Triple("🌡 Temperatura", "°C", false),
        "saturazione" to Triple("🫁 Saturazione", "%", false),
        "vita" to Triple("📏 Vita", "cm", false),
        "fianchi" to Triple("📏 Fianchi", "cm", false)
    )
    private fun file(c: Context) = File(c.filesDir, "health.json")
    fun load(c: Context): MutableList<Measure> = runCatching {
        Gson().fromJson<MutableList<Measure>>(file(c).readText(), object : TypeToken<MutableList<Measure>>() {}.type)
    }.getOrNull()?.filterNotNull()?.toMutableList() ?: mutableListOf()
    fun save(c: Context, list: List<Measure>) { runCatching { file(c).writeText(Gson().toJson(list)) } }

    fun fmt(m: Measure): String {
        val n = { v: Double -> if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.ITALIAN, "%.1f", v) }
        return n(m.v1) + (m.v2?.let { "/" + n(it) } ?: "") + " " + (if (m.type == "sonno") "h" else TYPES[m.type]?.second ?: "")
    }

    /** Salva una misurazione; il peso aggiorna anche il profilo (da cui dipende il fabbisogno di calorie). */
    fun add(c: Context, m: Measure) {
        save(c, load(c) + m)
        if (m.type == "peso") syncWeight(c)
    }

    /** Il profilo prende l'ultimo peso misurato. */
    fun syncWeight(c: Context) {
        val last = load(c).filter { it.type == "peso" }.maxByOrNull { it.time } ?: return
        val p = Food.profile(c)
        if (p.weightKg != last.v1) Food.saveProfile(c, p.copy(weightKg = last.v1))
    }

    fun last(c: Context, type: String): Measure? = load(c).filter { it.type == type }.maxByOrNull { it.time }
}

data class Med(val id: String, val name: String, val dose: String, val times: List<String>, val stock: Int, val perDose: Int = 1, val reminderIds: List<Int> = emptyList())

/** Farmaci: promemoria agli orari fissi e conteggio di quello che resta. */
object Meds {
    private fun prefs(c: Context) = c.getSharedPreferences("salute", Context.MODE_PRIVATE)
    fun load(c: Context): List<Med> = runCatching {
        Gson().fromJson<List<Med>>(prefs(c).getString("meds", null), object : TypeToken<List<Med>>() {}.type)
    }.getOrNull().orEmpty().filterNotNull()
    private fun save(c: Context, l: List<Med>) = prefs(c).edit().putString("meds", Gson().toJson(l)).apply()

    /** "8, 20:30 , 13.00" -> ["08:00","13:00","20:30"]; gli orari non validi si scartano. */
    fun parseTimes(raw: String): List<String> = raw.split(',', ';', ' ').mapNotNull { t ->
        val m = Regex("^(\\d{1,2})(?:[:.](\\d{2}))?$").find(t.trim()) ?: return@mapNotNull null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (h in 0..23 && min in 0..59) "%02d:%02d".format(h, min) else null
    }.distinct().sorted()

    fun perDay(m: Med) = m.times.size.coerceAtLeast(1) * m.perDose.coerceAtLeast(1)
    fun daysLeft(m: Med) = m.stock / perDay(m)
    fun isLow(m: Med, days: Int = 5) = m.stock > 0 && daysLeft(m) <= days || m.stock == 0 && m.times.isNotEmpty()

    /** La prossima volta (millis) alle HH:MM dopo [now]. */
    fun nextAt(hhmm: String, now: Long = System.currentTimeMillis()): Long {
        val (h, m) = hhmm.split(':').map { it.toInt() }
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Prossima dose del giorno, come testo ("20:30" o "domani 08:00"). */
    fun nextDose(m: Med, now: Calendar = Calendar.getInstance()): String? {
        if (m.times.isEmpty()) return null
        val cur = "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        return m.times.firstOrNull { it > cur } ?: ("domani " + m.times.first())
    }

    fun takenToday(c: Context, id: String) = prefs(c).getInt("taken_${id}_${Food.dayKey(System.currentTimeMillis())}", 0)

    /** «Ho preso la dose»: la conta e toglie le pastiglie dalla scorta. */
    fun markTaken(c: Context, id: String) {
        load(c).firstOrNull { it.id == id } ?: return
        prefs(c).edit().putInt("taken_${id}_${Food.dayKey(System.currentTimeMillis())}", takenToday(c, id) + 1).apply()
        save(c, load(c).map { if (it.id == id) it.copy(stock = (it.stock - it.perDose).coerceAtLeast(0)) else it })
    }

    fun upsert(c: Context, m: Med) {
        load(c).firstOrNull { it.id == m.id }?.reminderIds?.forEach { Reminders.remove(c, it) }
        val ids = m.times.map { t -> Reminders.add(c, "💊 Prendi ${m.name}" + if (m.dose.isNotBlank()) " (${m.dose})" else "", nextAt(t), "day", "meds").id }
        save(c, load(c).filter { it.id != m.id } + m.copy(reminderIds = ids))
    }

    fun remove(c: Context, id: String) {
        load(c).firstOrNull { it.id == id }?.reminderIds?.forEach { Reminders.remove(c, it) }
        save(c, load(c).filter { it.id != id })
    }
    fun newId() = UUID.randomUUID().toString()
}

data class EmergencyCard(
    val name: String = "", val birth: String = "", val blood: String = "", val allergies: String = "", val conditions: String = "",
    val medicines: String = "", val contact1: String = "", val phone1: String = "", val contact2: String = "", val phone2: String = "", val notes: String = ""
)

/** Scheda d'emergenza, a scelta visibile anche a schermo bloccato (la può leggere chiunque: è lo scopo, e lo decidi tu). */
object Emergency {
    private fun prefs(c: Context) = c.getSharedPreferences("salute", Context.MODE_PRIVATE)
    fun load(c: Context): EmergencyCard = runCatching { Gson().fromJson(prefs(c).getString("emergency", null), EmergencyCard::class.java) }.getOrNull() ?: EmergencyCard()
    fun save(c: Context, e: EmergencyCard) { prefs(c).edit().putString("emergency", Gson().toJson(e)).apply(); sync(c) }
    fun onLockScreen(c: Context) = prefs(c).getBoolean("emergency_lock", false)
    fun setOnLockScreen(c: Context, on: Boolean) { prefs(c).edit().putBoolean("emergency_lock", on).apply(); sync(c) }

    fun isEmpty(e: EmergencyCard) = e == EmergencyCard()

    fun lines(e: EmergencyCard): List<String> = buildList {
        if (e.name.isNotBlank()) add(e.name + if (e.birth.isNotBlank()) " · nato/a il ${e.birth}" else "")
        if (e.blood.isNotBlank()) add("Gruppo sanguigno: ${e.blood}")
        if (e.allergies.isNotBlank()) add("⚠️ Allergie: ${e.allergies}")
        if (e.conditions.isNotBlank()) add("Patologie: ${e.conditions}")
        if (e.medicines.isNotBlank()) add("Farmaci: ${e.medicines}")
        if (e.contact1.isNotBlank() || e.phone1.isNotBlank()) add("📞 ${listOf(e.contact1, e.phone1).filter { it.isNotBlank() }.joinToString(": ")}")
        if (e.contact2.isNotBlank() || e.phone2.isNotBlank()) add("📞 ${listOf(e.contact2, e.phone2).filter { it.isNotBlank() }.joinToString(": ")}")
        if (e.notes.isNotBlank()) add(e.notes)
    }

    /** Mostra o toglie la notifica sul blocco schermo secondo la scelta. Si può chiamare sempre (anche dopo un riavvio). */
    fun sync(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val e = load(c)
        if (!onLockScreen(c) || isEmpty(e)) { nm.cancel(7810); return }
        nm.createNotificationChannel(NotificationChannel("emergenza", "Scheda d'emergenza", NotificationManager.IMPORTANCE_LOW).apply { lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC })
        val open = PendingIntent.getActivity(c, 7810, Intent(c, MainActivity::class.java).putExtra("open", "emergency").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, "emergenza").setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("🆘 Scheda d'emergenza").setContentText(lines(e).firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines(e).joinToString("\n")))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build()
        runCatching { nm.notify(7810, n) }
    }
}

data class Visit(val id: String, val title: String, val time: Long, val place: String = "", val note: String = "", val reminderIds: List<Int> = emptyList())

/** Visite mediche, con promemoria la sera prima e due ore prima. */
object Visits {
    private fun prefs(c: Context) = c.getSharedPreferences("salute", Context.MODE_PRIVATE)
    fun load(c: Context): List<Visit> = runCatching {
        Gson().fromJson<List<Visit>>(prefs(c).getString("visits", null), object : TypeToken<List<Visit>>() {}.type)
    }.getOrNull().orEmpty().filterNotNull().sortedBy { it.time }
    private fun save(c: Context, l: List<Visit>) = prefs(c).edit().putString("visits", Gson().toJson(l)).apply()

    /** Quando avvisare: le 18 del giorno prima e 2 ore prima; solo momenti ancora futuri. */
    fun reminderTimes(time: Long, now: Long = System.currentTimeMillis()): List<Long> {
        val evening = Calendar.getInstance().apply { timeInMillis = time; add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        return listOf(evening, time - 2 * 3_600_000L).filter { it > now }.distinct()
    }

    fun upsert(c: Context, v: Visit) {
        load(c).firstOrNull { it.id == v.id }?.reminderIds?.forEach { Reminders.remove(c, it) }
        val f = SimpleDateFormat("EEE d MMM 'alle' HH:mm", Locale.ITALIAN)
        val ids = reminderTimes(v.time).map { at -> Reminders.add(c, "🩺 ${v.title} — ${f.format(Date(v.time))}" + if (v.place.isNotBlank()) " · ${v.place}" else "", at, null, "visits").id }
        save(c, load(c).filter { it.id != v.id } + v.copy(reminderIds = ids))
    }
    fun remove(c: Context, id: String) {
        load(c).firstOrNull { it.id == id }?.reminderIds?.forEach { Reminders.remove(c, it) }
        save(c, load(c).filter { it.id != id })
    }
    fun newId() = UUID.randomUUID().toString()
}

/** Il testo del referto per il medico: sezioni di righe (il PDF si disegna da qui). */
object Report {
    data class Data(
        val name: String, val from: Long, val to: Long,
        val weights: List<Pair<Long, Double>>, val pressures: List<Triple<Long, Int, Int>>, val glucose: List<Pair<Long, Double>>,
        val medicines: List<String>, val allergies: String, val conditions: String
    )

    fun sections(d: Data): List<Pair<String, List<String>>> {
        val day = SimpleDateFormat("d MMM", Locale.ITALIAN); val full = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)
        val out = mutableListOf<Pair<String, List<String>>>()
        out += "Periodo" to listOf("Dal ${full.format(Date(d.from))} al ${full.format(Date(d.to))}")
        if (d.allergies.isNotBlank() || d.conditions.isNotBlank() || d.medicines.isNotEmpty()) out += "Dati clinici" to buildList {
            if (d.allergies.isNotBlank()) add("Allergie: ${d.allergies}")
            if (d.conditions.isNotBlank()) add("Patologie: ${d.conditions}")
            if (d.medicines.isNotEmpty()) add("Farmaci: " + d.medicines.joinToString("; "))
        }
        if (d.pressures.isNotEmpty()) {
            val s = d.pressures.map { it.second }; val di = d.pressures.map { it.third }
            out += "Pressione arteriosa (mmHg)" to (listOf("${d.pressures.size} misurazioni · media ${s.average().toInt()}/${di.average().toInt()} · massima ${s.max()}/${di.max()} · minima ${s.min()}/${di.min()}") +
                d.pressures.takeLast(12).map { "${day.format(Date(it.first))}: ${it.second}/${it.third}" })
        }
        if (d.weights.isNotEmpty()) {
            val w = d.weights.map { it.second }
            out += "Peso (kg)" to listOf("Da %.1f a %.1f (%+.1f)".format(Locale.ITALIAN, w.first(), w.last(), w.last() - w.first()) + " · ${w.size} misurazioni")
        }
        if (d.glucose.isNotEmpty()) {
            val g = d.glucose.map { it.second }
            out += "Glicemia (mg/dL)" to (listOf("${g.size} misurazioni · media ${g.average().toInt()} · massima ${g.max().toInt()} · minima ${g.min().toInt()}") +
                d.glucose.takeLast(12).map { "${day.format(Date(it.first))}: ${it.second.toInt()}" })
        }
        if (out.size == 1) out += "Dati" to listOf("Nessuna misurazione nel periodo.")
        return out
    }
}

/**
 * Grafico a linea: area colorata sotto la linea, una fascia «sana» facoltativa ([bandLo]–[bandHi])
 * e, toccando, il valore del punto più vicino con la sua data ([labels]).
 */
class LineChartView(context: Context) : android.view.View(context) {
    var series1: List<Double> = emptyList()
    var series2: List<Double>? = null
    var labels: List<String> = emptyList()
    var bandLo: Double? = null
    var bandHi: Double? = null
    var bandLabel = "fascia sana"
    var unit = ""
    var color1 = 0xFF2EA66A.toInt()
    var color2 = 0xFF3B6CF5.toInt()
    var gridColor = 0x33888888
    var textColor = 0xFF888888.toInt()
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var sel = -1

    init {
        setOnTouchListener { v, e ->
            if (series1.size < 2) return@setOnTouchListener false
            val d = resources.displayMetrics.density; val pad = 34 * d; val w = width - pad - 8 * d
            sel = Math.round((e.x - pad) / w * (series1.size - 1)).coerceIn(0, series1.size - 1)
            if (e.action == android.view.MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
            invalidate(); true
        }
    }

    private fun fmt(v: Double) = if (v >= 100) v.toInt().toString() else String.format(Locale.ITALIAN, "%.1f", v)

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (series1.size < 2) return
        val all = series1 + (series2 ?: emptyList())
        val d = resources.displayMetrics.density
        val pad = 34 * d
        var min = all.minOrNull()!!; var max = all.maxOrNull()!!
        // la fascia sana si vede se è vicina ai valori (non schiaccia il grafico)
        val lo = bandLo; val hi = bandHi
        if (lo != null && hi != null && hi > lo) { val r = (max - min).coerceAtLeast(1.0); if (lo > min - 3 * r && hi < max + 3 * r) { min = minOf(min, lo); max = maxOf(max, hi) } }
        if (max - min < 1e-6) { min -= 1; max += 1 }
        val range = max - min
        min -= range * 0.1; max += range * 0.1
        val w = width - pad - 8 * d; val h = height - 16 * d
        fun yOf(v: Double) = (8 * d + h * (1 - (v - min) / (max - min))).toFloat()
        fun xOf(i: Int, n: Int) = pad + w * i / (n - 1)
        if (lo != null && hi != null && hi > lo) {
            val top = yOf(hi).coerceIn(8 * d, 8 * d + h); val bot = yOf(lo).coerceIn(8 * d, 8 * d + h)
            if (bot > top) {
                p.style = android.graphics.Paint.Style.FILL; p.color = (color1 and 0x00FFFFFF) or 0x1A000000
                canvas.drawRect(pad, top, pad + w, bot, p)
                p.color = textColor; p.textSize = 10 * d; p.textAlign = android.graphics.Paint.Align.RIGHT
                canvas.drawText(bandLabel, pad + w - 4 * d, top + 12 * d, p); p.textAlign = android.graphics.Paint.Align.LEFT
            }
        }
        p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = d; p.color = gridColor
        p.textSize = 10 * d
        for (i in 0..3) {
            val y = 8 * d + h * i / 3
            canvas.drawLine(pad, y, pad + w, y, p)
            val v = max - (max - min) * i / 3
            p.style = android.graphics.Paint.Style.FILL; p.color = textColor
            canvas.drawText(fmt(v), 0f, y + 4 * d, p)
            p.style = android.graphics.Paint.Style.STROKE; p.color = gridColor
        }
        fun draw(s: List<Double>, col: Int, area: Boolean) {
            val path = android.graphics.Path()
            s.forEachIndexed { i, v -> val x = xOf(i, s.size); val y = yOf(v); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            if (area) {
                val fill = android.graphics.Path(path); fill.lineTo(xOf(s.size - 1, s.size), 8 * d + h); fill.lineTo(pad, 8 * d + h); fill.close()
                p.style = android.graphics.Paint.Style.FILL
                p.shader = android.graphics.LinearGradient(0f, 8 * d, 0f, 8 * d + h, (col and 0x00FFFFFF) or 0x55000000, col and 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
                canvas.drawPath(fill, p); p.shader = null
            }
            p.color = col; p.strokeWidth = 2.5f * d; p.style = android.graphics.Paint.Style.STROKE; p.strokeJoin = android.graphics.Paint.Join.ROUND
            canvas.drawPath(path, p)
            p.style = android.graphics.Paint.Style.FILL
            s.forEachIndexed { i, v -> canvas.drawCircle(xOf(i, s.size), yOf(v), 3 * d, p) }
        }
        draw(series1, color1, series2 == null)
        series2?.takeIf { it.size >= 2 }?.let { draw(it, color2, false) }
        // il punto toccato: riga, cerchio e fumetto con valore e data
        val i = sel.takeIf { it in series1.indices } ?: return
        val x = xOf(i, series1.size); val y = yOf(series1[i])
        p.style = android.graphics.Paint.Style.STROKE; p.color = textColor; p.strokeWidth = d
        canvas.drawLine(x, 8 * d, x, 8 * d + h, p)
        p.style = android.graphics.Paint.Style.FILL; p.color = color1; canvas.drawCircle(x, y, 6 * d, p)
        val txt = fmt(series1[i]) + (series2?.getOrNull(i)?.let { "/" + fmt(it) } ?: "") + (if (unit.isNotBlank()) " $unit" else "") + (labels.getOrNull(i)?.let { " · $it" } ?: "")
        p.textSize = 12 * d; p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val tw = p.measureText(txt) + 16 * d
        val bx = (x - tw / 2).coerceIn(pad, (pad + w - tw).coerceAtLeast(pad)); val by = (y - 34 * d).let { if (it < 2 * d) y + 12 * d else it }
        p.color = 0xEE1D2A44.toInt(); canvas.drawRoundRect(bx, by, bx + tw, by + 22 * d, 11 * d, 11 * d, p)
        p.color = android.graphics.Color.WHITE; canvas.drawText(txt, bx + 8 * d, by + 15.5f * d, p); p.typeface = android.graphics.Typeface.DEFAULT
    }
}
