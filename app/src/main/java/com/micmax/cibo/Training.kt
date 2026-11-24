package com.micmax.cibo

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** Un esercizio di una scheda. I nomi dei campi sono gli stessi di Web Launcher. */
data class Ex(val name: String, val sets: Int, val reps: Int, val restSec: Int, val kg: Double = 0.0, val seconds: Int = 0)
data class WorkoutPlan(val id: String, val name: String, val exercises: List<Ex>)
/** Un'attività fatta (scheda, Tabata o a mano), per l'obiettivo della settimana. */
data class Act(val id: String, val kind: String, val title: String, val start: Long, val durationMin: Int, val distanceM: Double = 0.0, val kcal: Double = 0.0)

/** Allenamento: schede, attività, obiettivo settimanale. Funzioni pure provate con i test. */
object Fitness {
    // ---------- obiettivo settimanale e serie ----------

    /** Numero della settimana (da lunedì) che contiene [t]. */
    fun weekIndex(t: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Math.floorDiv(Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toEpochDay() + 3, 7L)

    fun weekCount(times: List<Long>, now: Long = System.currentTimeMillis()): Int {
        val w = weekIndex(now); return times.count { weekIndex(it) == w }
    }

    /** Settimane di fila in cui hai raggiunto [goal] allenamenti. La settimana in corso conta solo se l'hai già raggiunto. */
    fun streakWeeks(times: List<Long>, goal: Int, now: Long = System.currentTimeMillis()): Int {
        if (goal <= 0) return 0
        val counts = times.groupingBy { weekIndex(it) }.eachCount()
        val cur = weekIndex(now)
        var w = if ((counts[cur] ?: 0) >= goal) cur else cur - 1
        var n = 0
        while ((counts[w] ?: 0) >= goal) { n++; w-- }
        return n
    }

    /** Allenamenti vicini (entro [windowMs]) di fonti diverse contano una volta (li ha registrati sia l'orologio sia il telefono). */
    fun dedupe(times: List<Long>, windowMs: Long = 90 * 60_000L): List<Long> {
        val out = mutableListOf<Long>()
        times.sorted().forEach { t -> if (out.isEmpty() || t - out.last() > windowMs) out += t }
        return out
    }

    // ---------- archivio ----------

    private fun prefs(c: Context) = c.getSharedPreferences("allenamento", Context.MODE_PRIVATE)

    fun acts(c: Context): List<Act> = runCatching {
        Gson().fromJson<List<Act>>(prefs(c).getString("acts", null), object : TypeToken<List<Act>>() {}.type)
    }.getOrNull().orEmpty().filterNotNull().sortedByDescending { it.start }
    private fun saveActs(c: Context, l: List<Act>) = prefs(c).edit().putString("acts", Gson().toJson(l.sortedByDescending { it.start }.take(800))).apply()
    fun addAct(c: Context, a: Act) = saveActs(c, acts(c).filter { it.id != a.id } + a)
    fun removeAct(c: Context, id: String) = saveActs(c, acts(c).filter { it.id != id })

    fun weeklyGoal(c: Context) = prefs(c).getInt("weekly_goal", 3)
    fun setWeeklyGoal(c: Context, n: Int) = prefs(c).edit().putInt("weekly_goal", n.coerceIn(1, 14)).apply()

    /** Quando ti sei allenato: le attività dell'app più quelle dell'orologio (Health Connect), contate una volta. */
    fun allTimes(c: Context): List<Long> = dedupe(acts(c).map { it.start } + (Hc.cached(c)?.exercises?.map { it.start } ?: emptyList()))

    /** Calorie stimate di un allenamento con i pesi o a corpo libero: circa 5 MET. */
    fun kcalForSession(weightKg: Double, minutes: Int): Double = 5.0 * (if (weightKg > 0) weightKg else 70.0) * minutes / 60.0

    // ---------- schede ----------

    val PRESETS = listOf(
        WorkoutPlan("preset-corpo-libero", "Corpo libero (principiante)", listOf(
            Ex("Squat", 3, 12, 45), Ex("Flessioni (anche sulle ginocchia)", 3, 8, 45), Ex("Affondi alternati", 3, 10, 45),
            Ex("Ponte per i glutei", 3, 12, 30), Ex("Plank", 3, 0, 30, seconds = 30))),
        WorkoutPlan("preset-core", "Addominali e schiena", listOf(
            Ex("Crunch", 3, 15, 30), Ex("Bicicletta in aria", 3, 20, 30), Ex("Superman", 3, 12, 30),
            Ex("Plank laterale (per lato)", 2, 0, 30, seconds = 25), Ex("Plank", 3, 0, 30, seconds = 40))),
        WorkoutPlan("preset-mobilita", "Mobilità del mattino (5 minuti)", listOf(
            Ex("Rotazioni delle spalle", 1, 15, 10), Ex("Gatto-cammello", 1, 12, 10), Ex("Affondo con rotazione", 2, 6, 10),
            Ex("Squat profondo tenuto", 1, 0, 10, seconds = 30), Ex("Allungamento dei fianchi", 1, 0, 10, seconds = 30)))
    )
    fun plans(c: Context): List<WorkoutPlan> = runCatching {
        Gson().fromJson<List<WorkoutPlan>>(prefs(c).getString("plans", null), object : TypeToken<List<WorkoutPlan>>() {}.type)
    }.getOrNull().orEmpty().filterNotNull().ifEmpty { PRESETS }
    fun savePlans(c: Context, l: List<WorkoutPlan>) = prefs(c).edit().putString("plans", Gson().toJson(l)).apply()
    fun hasCustomPlans(c: Context) = prefs(c).getString("plans", null) != null
    fun newPlanId() = UUID.randomUUID().toString()

    private val EX_LINE = Regex("^(.+?)\\s+(\\d{1,2})\\s*[x×]\\s*(\\d{1,3})\\s*(s|sec)?(?:\\s+riposo\\s+(\\d{1,3}))?(?:\\s+(\\d+(?:[.,]\\d+)?)\\s*kg)?$", RegexOption.IGNORE_CASE)

    /** "Squat 3x12 riposo 45" / "Plank 3x30s riposo 30" / "Panca 4x8 riposo 90 40kg" → esercizi. Le righe che non tornano si ignorano. */
    fun parseExercises(text: String): List<Ex> = text.lines().mapNotNull { l ->
        val m = EX_LINE.find(l.trim()) ?: return@mapNotNull null
        val timed = m.groupValues[4].isNotEmpty(); val n = m.groupValues[3].toInt()
        Ex(m.groupValues[1].trim(), m.groupValues[2].toInt(), if (timed) 0 else n, m.groupValues[5].ifEmpty { "45" }.toInt(), m.groupValues[6].replace(',', '.').toDoubleOrNull() ?: 0.0, if (timed) n else 0)
    }
    fun exerciseText(e: Ex) = "${e.name} ${e.sets}x" + (if (e.seconds > 0) "${e.seconds}s" else "${e.reps}") + " riposo ${e.restSec}" + (if (e.kg > 0) " ${if (e.kg % 1.0 == 0.0) e.kg.toInt() else e.kg}kg" else "")

    /** Durata approssimativa: 40 secondi per serie (o i secondi dell'esercizio a tempo) più i riposi. */
    fun planMinutes(p: WorkoutPlan): Int = Math.round(p.exercises.sumOf { e -> e.sets * ((if (e.seconds > 0) e.seconds else 40) + e.restSec) } / 60.0).toInt().coerceAtLeast(1)

    // ---------- Tabata / intervalli ----------

    data class Phase(val kind: String, val seconds: Int, val round: Int)

    /** kind: "prepara" | "lavoro" | "riposo". Dopo l'ultimo round non c'è riposo. */
    fun tabata(prepare: Int, work: Int, rest: Int, rounds: Int): List<Phase> {
        val out = mutableListOf<Phase>()
        if (prepare > 0) out += Phase("prepara", prepare, 0)
        for (r in 1..rounds) {
            out += Phase("lavoro", work, r)
            if (r < rounds && rest > 0) out += Phase("riposo", rest, r)
        }
        return out
    }
    fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

    // ---------- opzioni ----------
    /** Conteggio automatico delle ripetizioni (senza toccare) e il suo ritmo (0 lento, 1 normale, 2 veloce). */
    fun auto(c: Context) = prefs(c).getBoolean("auto", true)
    fun setAuto(c: Context, on: Boolean) = prefs(c).edit().putBoolean("auto", on).apply()
    fun tempo(c: Context) = prefs(c).getFloat("tempo", 1f).toDouble()
    fun setTempo(c: Context, v: Double) = prefs(c).edit().putFloat("tempo", v.coerceIn(Moves.TEMPO_MIN, Moves.TEMPO_MAX).toFloat()).apply()
    fun voice(c: Context) = prefs(c).getBoolean("voice", true)
    fun setVoice(c: Context, on: Boolean) = prefs(c).edit().putBoolean("voice", on).apply()
    fun tabataSetting(c: Context, key: String, def: Int) = prefs(c).getInt("tb_$key", def)
    fun setTabataSetting(c: Context, key: String, v: Int) = prefs(c).edit().putInt("tb_$key", v).apply()

    fun dayName(minutes: Int) = "%d min".format(Locale.ITALIAN, minutes)
}
