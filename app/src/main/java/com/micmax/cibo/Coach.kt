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
    /** Attrezzi che hai (null nei programmi salvati prima: nessuno). */
    val equip: List<String>? = null,
    /** Spazio: 0 = una stanza, 1 = un tappetino, 2 = pochissimo. */
    val space: Int = 0,
    /** Niente salti né rumore. */
    val quiet: Boolean = false,
    /** Parti del corpo da non caricare: ginocchia, schiena, spalle, polsi. */
    val avoid: List<String>? = null,
)

/** Quello che l'app sa di te per adattare il programma (null = non si sa). */
data class Body(val age: Int? = null, val sex: String? = null, val bmi: Double? = null, val avgSteps: Double? = null, val restHr: Int? = null)

/** Il test di forma: flessioni in un minuto, squat in un minuto, plank più lungo possibile, battito a riposo. */
data class FitResult(val date: Long, val pushups: Int, val squats: Int, val plankSec: Int, val restHr: Int? = null)

object FitTest {
    /** Le soglie si abbassano con l'età; le flessioni anche per le donne (dove di solito si parte più bassi). */
    private fun ageFactor(age: Int?) = when { age == null -> 1.0; age >= 65 -> 0.6; age >= 50 -> 0.75; age >= 40 -> 0.9; else -> 1.0 }
    private fun lvl(v: Int, lo: Double, hi: Double) = when { v < lo -> 0; v < hi -> 1; else -> 2 }

    /** Livello (0..2) per parte alta, gambe e addominali, e quello generale. */
    fun levels(r: FitResult, b: Body): Map<String, Int> {
        val a = ageFactor(b.age); val f = if (b.sex == "f") 0.6 else 1.0
        val upper = lvl(r.pushups, 10 * a * f, 25 * a * f)
        val legs = lvl(r.squats, 25 * a, 40 * a)
        val core = lvl(r.plankSec, 30 * a, 75 * a)
        return mapOf("upper" to upper, "legs" to legs, "core" to core, "overall" to Math.round((upper + legs + core) / 3.0).toInt())
    }

    /** Confronto con il test precedente: «+5 flessioni», «−3 squat»… */
    fun progress(old: FitResult, new: FitResult): List<String> = listOf(
        "flessioni" to (new.pushups - old.pushups), "squat" to (new.squats - old.squats), "secondi di plank" to (new.plankSec - old.plankSec),
    ).filter { it.second != 0 }.map { (n, d) -> (if (d > 0) "+$d" else "−${-d}") + " $n" }
}

/** Una giornata del programma. [goal] null = riposo; [challenge] = giorno «contro il tempo». */
data class CoachDay(val date: LocalDate, val goal: Moves.Goal?, val minutes: Int, val challenge: ForTime? = null, val note: String = "")

object Coach {
    /** Gli obiettivi tra cui scegliere, con gli allenamenti della settimana a turno. */
    val GOALS = linkedMapOf(
        "forma" to ("Rimettermi in forma" to listOf(Moves.Goal.FULL, Moves.Goal.CARDIO, Moves.Goal.MOBILITY, Moves.Goal.LEGS, Moves.Goal.CORE)),
        "dimagrire" to ("Dimagrire" to listOf(Moves.Goal.CARDIO, Moves.Goal.FULL, Moves.Goal.CARDIO, Moves.Goal.LEGS, Moves.Goal.CORE)),
        "forza" to ("Più forza" to listOf(Moves.Goal.LEGS, Moves.Goal.UPPER, Moves.Goal.CORE, Moves.Goal.FULL)),
        "resistenza" to ("Più fiato e resistenza" to listOf(Moves.Goal.CARDIO, Moves.Goal.FULL, Moves.Goal.CARDIO, Moves.Goal.LEGS)),
        "benessere" to ("Stare bene e sciolto" to listOf(Moves.Goal.MOBILITY, Moves.Goal.BACK, Moves.Goal.BALANCE, Moves.Goal.STRETCH, Moves.Goal.CORE)),
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

    /** Gli adattamenti per te: (livello massimo, niente salti, spiegazioni da mostrare). */
    data class Personal(val maxLevel: Int, val noJumps: Boolean, val notes: List<String>, val balance: Boolean = false)
    fun personal(b: Body): Personal {
        var max = 2; var noJumps = false; val notes = mutableListOf<String>()
        b.age?.let { a ->
            if (a >= 65) { max = minOf(max, 1); noJumps = true; notes += "Età: ogni giorno un esercizio di equilibrio, niente salti" }
            else if (a >= 50) notes += "Età: riscaldamento e allungamenti sempre presenti"
        }
        b.bmi?.let { if (it >= 30) { noJumps = true; notes += "Peso: niente salti per proteggere le ginocchia, più cardio leggero" } else if (it < 18.5) notes += "Peso basso: più forza, poco cardio intenso" }
        b.avgSteps?.let { if (it in 1.0..3999.0) { max = minOf(max, 1); notes += "Ti muovi poco durante il giorno: si parte con calma" } }
        b.restHr?.let { if (it >= 85) { max = minOf(max, 1); notes += "Battito a riposo alto: intensità moderata (sentire il medico se resta così)" } }
        return Personal(max, noJumps, notes, balance = (b.age ?: 0) >= 65)
    }

    /** Le scelte di attrezzi, spazio, silenzio e parti da proteggere, unite agli adattamenti per te. */
    fun opts(p: CoachPlan, pers: Personal? = null) = Moves.Opts(
        noJumps = p.noJumps || p.quiet || pers?.noJumps == true, equip = p.equip.orEmpty().toSet(),
        spaceCap = when (p.space) { 2 -> 0; 1 -> 1; else -> 2 }, avoid = p.avoid.orEmpty().toSet(),
    )

    /** Dopo un allenamento: «Facile» alza il livello, «Difficile» lo abbassa (a piccoli passi). */
    fun feedback(p: CoachPlan, answer: Int): CoachPlan = p.copy(level = (p.level + when (answer) { 0 -> 0.34; 2 -> -0.34; else -> 0.0 }).coerceIn(0.0, 2.0))

    /** Le richieste veloci del giorno («ho poco tempo», «mal di schiena»…). */
    enum class Tweak(val label: String) { SHORT("⏱ Ho poco tempo"), BACK("🌿 Mi fa male la schiena"), NO_JUMP("🔇 Niente salti"), TIRED("😴 Sono stanco"), HARD("🔥 Voglio spingere") }

    /** La scheda di oggi, con le richieste veloci e il sonno della notte (meno di 6 ore = più leggera). */
    fun todayPlan(p: CoachPlan, day: CoachDay, tweaks: Set<Tweak>, sleepHours: Double?, seed: Long, pers: Personal? = null): WorkoutPlan {
        var goal = day.goal ?: Moves.Goal.MOBILITY
        var min = if (day.goal == null) 10 else day.minutes
        var lv = minOf(level(p), pers?.maxLevel ?: 2)
        if (Tweak.SHORT in tweaks) min = minOf(min, 10)
        if (Tweak.BACK in tweaks) { goal = Moves.Goal.BACK; lv = 0 }
        if (Tweak.TIRED in tweaks || (sleepHours != null && sleepHours < 6)) { lv = (lv - 1).coerceAtLeast(0); if (goal == Moves.Goal.CARDIO) goal = Moves.Goal.MOBILITY; min = minOf(min, 15) }
        if (Tweak.HARD in tweaks) { lv = (lv + 1).coerceAtMost(2); min += 5 }
        val base = opts(p, pers)
        val o = base.copy(noJumps = base.noJumps || Tweak.NO_JUMP in tweaks || Tweak.BACK in tweaks, avoid = if (Tweak.BACK in tweaks) base.avoid + "schiena" else base.avoid)
        var plan = Moves.generate(goal, min, lv, seed, opts = o)
        // dai 65 anni: un esercizio di equilibrio dopo il riscaldamento (per prevenire le cadute)
        if (pers?.balance == true && goal != Moves.Goal.BALANCE) {
            Moves.ALL.filter { "balance" in it.tags && Moves.allowed(it, o) }.shuffled(kotlin.random.Random(seed)).firstOrNull()?.let { m ->
                val ex = Moves.toEx(m, 0, 1, 15, holdSecs = if (m.timed) 30 else null)
                plan = plan.copy(exercises = plan.exercises.toMutableList().apply { add(minOf(2, size), ex) })
            }
        }
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
    fun request(model: String, system: String, history: List<Pair<String, String>>, maxTokens: Int = 900): String = JSONObject().apply {
        put("model", model); put("temperature", 0.6); put("max_tokens", maxTokens)
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
        return planFrom(o, "Scheda del coach")
    }

    private fun planFrom(o: JSONObject, fallback: String): WorkoutPlan? {
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
        return WorkoutPlan(Fitness.newPlanId(), "🤖 " + o.optString("nome", fallback).trim().ifBlank { fallback }.take(40), list)
    }

    /** La richiesta per un programma di 4 settimane fatto dall'AI. */
    fun programRequest(p: CoachPlan, extra: String): String = """
Prepara un programma di 4 settimane con ${p.days} allenamenti a settimana da circa ${p.minutes} minuti, per l'obiettivo «${GOALS[p.goal]?.first ?: p.goal}».
Ogni settimana un po' più impegnativa della precedente, la quarta più leggera. Ogni allenamento con riscaldamento, parte centrale e allungamenti.
${if (extra.isNotBlank()) "Richieste della persona: $extra\n" else ""}Rispondi con due o tre righe di spiegazione e poi SOLO questo formato:
```json
{"settimane": [{"allenamenti": [{"nome": "Nome breve", "esercizi": [{"nome": "Squat", "serie": 3, "ripetizioni": 12, "secondi": 0, "riposo": 45}]}]}]}
```
""".trim()

    /** Il programma nella risposta: le schede in ordine (settimana 1 allenamento 1, 2…, poi settimana 2…). */
    fun parseProgram(text: String): List<WorkoutPlan>? {
        val start = text.indexOf('{'); val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() ?: return null
        val weeks = o.optJSONArray("settimane") ?: return null
        val out = mutableListOf<WorkoutPlan>()
        for (w in 0 until weeks.length()) {
            val sessions = weeks.optJSONObject(w)?.optJSONArray("allenamenti") ?: continue
            for (k in 0 until sessions.length()) sessions.optJSONObject(k)?.let { planFrom(it, "Settimana ${w + 1} · allenamento ${k + 1}") }?.let { out += it }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    /** Programma dell'AI salvato: le schede e da quando vale. */
    data class AiProgram(val startDay: Long, val days: Int, val plans: List<WorkoutPlan>)

    /** La scheda di oggi dal programma dell'AI (null se oggi è riposo o il programma è finito). */
    fun fromProgram(prog: AiProgram, date: LocalDate): WorkoutPlan? {
        val k = trainingDays(prog.days).indexOf(date.dayOfWeek.value - 1)
        if (k < 0) return null
        val w = (ChronoUnit.DAYS.between(LocalDate.ofEpochDay(prog.startDay).with(DayOfWeek.MONDAY), date) / 7).toInt()
        return if (w < 0) null else prog.plans.getOrNull(w * prog.days + k)
    }

    /** Il testo da mostrare, senza il blocco della scheda (la scheda si mostra a parte). */
    fun withoutPlan(text: String): String = text.replace(Regex("```(?:json)?\\s*\\{[\\s\\S]*?\\}\\s*```"), "").trim()
}
