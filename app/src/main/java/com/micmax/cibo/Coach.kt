package com.micmax.cibo

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// ======================= «Contro il tempo» =======================

/** Un esercizio di un allenamento a giri: ripetizioni, oppure secondi se [secs] > 0. */
data class RoundItem(val moveId: String, val reps: Int, val secs: Int = 0) {
    val move get() = Moves.byId(moveId)
    fun label() = (move?.name ?: moveId).let { n -> if (secs > 0) "$secs s · $n" else "$reps · $n" + if (move?.perSide == true) " (per lato)" else "" }
}

/** Un allenamento a giri da finire il prima possibile: il tempo diventa il tuo record. */
data class ForTime(val id: String, val name: String, val level: Int, val rounds: Int, val items: List<RoundItem>, val note: String = "") {
    /** Tempo stimato, per scegliere quello giusto: 2,5 s a ripetizione più i secondi degli esercizi a tempo. */
    fun estMinutes() = ((rounds * items.sumOf { if (it.secs > 0) it.secs.toDouble() else it.reps * 2.5 * (if (it.move?.perSide == true) 2 else 1) }) / 60).roundToInt().coerceAtLeast(1)
    fun steps(): List<Pair<Int, RoundItem>> = (1..rounds).flatMap { r -> items.map { r to it } }
}

object Challenges {
    val ALL = listOf(
        ForTime("primo-passo", "Primo passo", 1, 3, listOf(RoundItem("squat", 10), RoundItem("flessioni-muro", 10), RoundItem("crunch", 10), RoundItem("marcia", 0, 30)),
            "Per cominciare: tre giri tranquilli."),
        ForTime("gambe-ferro", "Gambe d'acciaio", 1, 4, listOf(RoundItem("squat", 15), RoundItem("affondi", 8), RoundItem("ponte", 12)),
            "Quattro giri per gambe e glutei."),
        ForTime("centro", "Centro forte", 2, 4, listOf(RoundItem("situp", 10), RoundItem("bicicletta", 16), RoundItem("plank-su-giu", 8), RoundItem("barchetta", 0, 20)),
            "Addominali e schiena, senza salti."),
        ForTime("fulmine", "Fulmine", 2, 4, listOf(RoundItem("jumping-jack", 20), RoundItem("squat-salto", 10), RoundItem("scalatore", 0, 20), RoundItem("flessioni", 8)),
            "Fiato e gambe: veloce ma pulito."),
        ForTime("scala", "Scala a corpo libero", 2, 3, listOf(RoundItem("flessioni", 10), RoundItem("squat", 20), RoundItem("situp", 15), RoundItem("affondo-laterale", 8)),
            "Tre giri, tutto il corpo."),
        ForTime("tempesta", "Tempesta", 3, 5, listOf(RoundItem("burpee", 10), RoundItem("squat-salto", 15), RoundItem("situp", 20)),
            "Il più duro: cinque giri senza fermarsi troppo."),
        ForTime("vetta", "Vetta", 3, 4, listOf(RoundItem("pike", 8), RoundItem("pistol", 5), RoundItem("sprawl", 10), RoundItem("affondi-salto", 12)),
            "Forza e controllo per chi è già allenato."),
    )
    fun byId(id: String) = ALL.firstOrNull { it.id == id }

    /** «12:34» */
    fun clock(ms: Long): String { val s = (ms / 1000).toInt(); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
}

// ======================= il coach sul telefono =======================

/** Le scelte del programma: obiettivo, giorni a settimana, minuti, livello (0..2, si muove con i «Facile/Difficile»). */
data class CoachPlan(
    val goal: String = "forma", val days: Int = 3, val minutes: Int = 20, val level: Double = 0.0,
    val noJumps: Boolean = false, val startDay: Long = LocalDate.now().toEpochDay(),
)

/** Una giornata del programma. [goal] null = riposo; [challenge] = giorno «contro il tempo». */
data class CoachDay(val date: LocalDate, val goal: Moves.Goal?, val minutes: Int, val challenge: ForTime? = null, val note: String = "")

object Coach {
    /** Gli obiettivi tra cui scegliere, con gli allenamenti della settimana a turno. */
    val GOALS = linkedMapOf(
        "forma" to ("Rimettermi in forma" to listOf(Moves.Goal.FULL, Moves.Goal.CARDIO, Moves.Goal.MOBILITY, Moves.Goal.LEGS, Moves.Goal.CORE)),
        "dimagrire" to ("Dimagrire" to listOf(Moves.Goal.CARDIO, Moves.Goal.FULL, Moves.Goal.CARDIO, Moves.Goal.LEGS, Moves.Goal.CORE)),
        "forza" to ("Più forza" to listOf(Moves.Goal.LEGS, Moves.Goal.UPPER, Moves.Goal.CORE, Moves.Goal.FULL)),
        "resistenza" to ("Più fiato e resistenza" to listOf(Moves.Goal.CARDIO, Moves.Goal.FULL, Moves.Goal.CARDIO, Moves.Goal.LEGS)),
        "benessere" to ("Stare bene e sciolto" to listOf(Moves.Goal.MOBILITY, Moves.Goal.BACK, Moves.Goal.STRETCH, Moves.Goal.CORE)),
    )

    /** In quali giorni della settimana allenarsi (lunedì = 0), ben distanziati. */
    fun trainingDays(n: Int): List<Int> = when (n.coerceIn(1, 7)) {
        1 -> listOf(2); 2 -> listOf(1, 4); 3 -> listOf(0, 2, 4); 4 -> listOf(0, 1, 3, 5)
        5 -> listOf(0, 1, 2, 4, 5); 6 -> listOf(0, 1, 2, 3, 4, 5); else -> (0..6).toList()
    }

    /** Settimana del programma (1 = la prima): ogni settimana un po' più lunga, una ogni quattro più leggera per recuperare. */
    fun week(p: CoachPlan, date: LocalDate): Int = (ChronoUnit.DAYS.between(LocalDate.ofEpochDay(p.startDay).with(DayOfWeek.MONDAY), date) / 7).toInt().coerceAtLeast(0) + 1
    fun minutesFor(p: CoachPlan, week: Int): Int = if (week % 4 == 0) (p.minutes * 0.8).roundToInt() else (p.minutes + (week - 1) % 4 * 2).coerceAtMost(p.minutes + 6)

    /** Il programma di una settimana, da lunedì a domenica. */
    fun weekPlan(p: CoachPlan, anyDay: LocalDate): List<CoachDay> {
        val monday = anyDay.with(DayOfWeek.MONDAY)
        val w = week(p, monday)
        val rotation = GOALS[p.goal]?.second ?: GOALS.values.first().second
        val days = trainingDays(p.days)
        val min = minutesFor(p, w)
        return (0..6).map { d ->
            val date = monday.plusDays(d.toLong())
            val k = days.indexOf(d)
            if (k < 0) CoachDay(date, null, 0, note = "Riposo: cammina un po' o fai stretching")
            else {
                val g = rotation[(k + (w - 1) * days.size) % rotation.size]
                // per fiato e dimagrire, l'ultimo giorno della settimana è «contro il tempo»
                val ch = if (p.goal in setOf("resistenza", "dimagrire", "forma") && k == days.lastIndex && days.size >= 3) challengeFor(p, w) else null
                CoachDay(date, g, min, ch, if (w % 4 == 0) "Settimana leggera: si recupera" else "")
            }
        }
    }

    fun challengeFor(p: CoachPlan, week: Int): ForTime? {
        val lv = level(p) + 1
        val ok = Challenges.ALL.filter { it.level <= lv && (!p.noJumps || it.items.none { i -> "jump" in (i.move?.tags ?: emptySet()) }) }
        return ok.filter { it.level == lv }.ifEmpty { ok }.let { l -> l.getOrNull(week % l.size.coerceAtLeast(1)) }
    }

    fun level(p: CoachPlan) = p.level.roundToInt().coerceIn(0, 2)

    /** Dopo un allenamento: «Facile» alza il livello, «Difficile» lo abbassa (a piccoli passi). */
    fun feedback(p: CoachPlan, answer: Int): CoachPlan = p.copy(level = (p.level + when (answer) { 0 -> 0.34; 2 -> -0.34; else -> 0.0 }).coerceIn(0.0, 2.0))

    /** Le richieste veloci del giorno («ho poco tempo», «mal di schiena»…). */
    enum class Tweak(val label: String) { SHORT("⏱ Ho poco tempo"), BACK("🌿 Mi fa male la schiena"), NO_JUMP("🔇 Niente salti"), TIRED("😴 Sono stanco"), HARD("🔥 Voglio spingere") }

    /** La scheda di oggi, con le richieste veloci e il sonno della notte (meno di 6 ore = più leggera). */
    fun todayPlan(p: CoachPlan, day: CoachDay, tweaks: Set<Tweak>, sleepHours: Double?, seed: Long): WorkoutPlan {
        var goal = day.goal ?: Moves.Goal.MOBILITY
        var min = if (day.goal == null) 10 else day.minutes
        var lv = level(p)
        if (Tweak.SHORT in tweaks) min = minOf(min, 10)
        if (Tweak.BACK in tweaks) { goal = Moves.Goal.BACK; lv = 0 }
        if (Tweak.TIRED in tweaks || (sleepHours != null && sleepHours < 6)) { lv = (lv - 1).coerceAtLeast(0); if (goal == Moves.Goal.CARDIO) goal = Moves.Goal.MOBILITY; min = minOf(min, 15) }
        if (Tweak.HARD in tweaks) { lv = (lv + 1).coerceAtMost(2); min += 5 }
        val plan = Moves.generate(goal, min, lv, seed, noJumps = p.noJumps || Tweak.NO_JUMP in tweaks || Tweak.BACK in tweaks)
        return plan.copy(name = "🤖 Coach · " + plan.name.substringAfter(' '))
    }

    // ======================= la chat con l'AI =======================

    /** I servizi che si possono usare: tutti parlano lo stesso «dialetto» (chat completions). */
    data class Provider(val id: String, val label: String, val url: String, val model: String, val keyHint: String)
    val PROVIDERS = listOf(
        Provider("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", "gsk_…"),
        Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.5-flash", "AIza…"),
        Provider("nvidia", "NVIDIA", "https://integrate.api.nvidia.com/v1/chat/completions", "meta/llama-3.3-70b-instruct", "nvapi-…"),
    )

    /** Le istruzioni per il modello: allenatore prudente, in italiano, e la scheda in un formato che l'app sa leggere. */
    fun systemPrompt(context: String): String = """
Sei il coach di allenamento dell'app «Benessere e cibo». Rispondi in italiano, in modo breve, chiaro e incoraggiante (massimo 8 righe di testo).
Regole di sicurezza: non fare diagnosi; se la persona parla di dolore forte, dolore al petto, svenimenti, gravidanza o malattie, consiglia di sentire il medico e proponi solo esercizi dolci o il riposo.
Quando proponi un allenamento, oltre al testo aggiungi UNA scheda in questo formato esatto, alla fine:
```json
{"nome": "Nome breve", "esercizi": [{"nome": "Squat", "serie": 3, "ripetizioni": 12, "secondi": 0, "riposo": 45}]}
```
Per gli esercizi a tempo metti "ripetizioni": 0 e i "secondi". Usa SOLO esercizi di questo elenco, scritti uguali:
${Moves.ALL.joinToString(", ") { it.name }}.
Quello che sai della persona (dall'app):
$context
""".trim()

    /** Il corpo della richiesta: istruzioni, gli ultimi messaggi, la domanda. */
    fun request(model: String, system: String, history: List<Pair<String, String>>): String = JSONObject().apply {
        put("model", model); put("temperature", 0.6); put("max_tokens", 900)
        put("messages", JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", system))
            history.takeLast(12).forEach { (role, text) -> put(JSONObject().put("role", role).put("content", text)) }
        })
    }.toString()

    /** Il testo della risposta, oppure null se la risposta non è quella attesa. */
    fun replyText(json: String): String? = runCatching {
        JSONObject(json).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Il messaggio d'errore del servizio (per spiegare cosa non va). */
    fun errorText(code: Int, body: String?): String {
        // alcuni servizi rispondono con un oggetto, altri con un elenco di oggetti
        val msg = runCatching {
            val v = org.json.JSONTokener(body ?: "").nextValue()
            ((v as? JSONObject) ?: (v as? JSONArray)?.optJSONObject(0))?.optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            401, 403 -> "la chiave non è valida o non ha i permessi"
            404 -> "il modello non esiste per questo servizio"
            429 -> "limite di richieste raggiunto (riprova più tardi)"
            in 500..599 -> "il servizio ha un problema in questo momento"
            else -> "errore $code"
        } + if (msg.isNotBlank()) " — ${msg.take(160)}" else ""
    }

    /** La scheda dentro la risposta (blocco ```json o un oggetto con "esercizi"), con valori tenuti in limiti sensati. */
    fun parsePlan(text: String): WorkoutPlan? {
        val block = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```").find(text)?.groupValues?.get(1)
            ?: Regex("\\{[\\s\\S]*\"esercizi\"[\\s\\S]*\\}").find(text)?.value ?: return null
        val o = runCatching { JSONObject(block) }.getOrNull() ?: return null
        val arr = o.optJSONArray("esercizi") ?: return null
        val list = (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val raw = e.optString("nome").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = Moves.find(raw)?.let { m -> m.name + if (m.perSide && "lato" in raw.lowercase()) " (per lato)" else "" } ?: raw
            val secs = e.optInt("secondi", 0).coerceIn(0, 600)
            val reps = e.optInt("ripetizioni", 0).coerceIn(0, 100)
            if (secs == 0 && reps == 0) return@mapNotNull null
            Ex(name.take(60), e.optInt("serie", 1).coerceIn(1, 10), if (secs > 0) 0 else reps, e.optInt("riposo", 30).coerceIn(0, 300), seconds = secs)
        }
        if (list.isEmpty()) return null
        return WorkoutPlan(Fitness.newPlanId(), "🤖 " + o.optString("nome", "Scheda del coach").trim().ifBlank { "Scheda del coach" }.take(40), list)
    }

    /** Il testo da mostrare, senza il blocco della scheda (la scheda si mostra a parte). */
    fun withoutPlan(text: String): String = text.replace(Regex("```(?:json)?\\s*\\{[\\s\\S]*?\\}\\s*```"), "").trim()
}
