package com.micmax.cibo

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/** Diario del cibo, acqua e dati personali. I nomi dei campi sono gli stessi di Web Launcher: i dati si passano da un'app all'altra. */
object Food {
    val MEALS = listOf("colazione" to "🥐 Colazione", "pranzo" to "🍝 Pranzo", "cena" to "🍽 Cena", "spuntino" to "🍎 Spuntino")
    data class Totals(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

    private fun prefs(c: Context) = c.getSharedPreferences("cibo", Context.MODE_PRIVATE)
    fun load(c: Context): MutableList<FoodEntry> = runCatching {
        Gson().fromJson<MutableList<FoodEntry>>(prefs(c).getString("entries", null), object : TypeToken<MutableList<FoodEntry>>() {}.type)
    }.getOrNull()?.filterNotNull()?.toMutableList() ?: mutableListOf()
    fun save(c: Context, l: List<FoodEntry>) {
        val cutoff = System.currentTimeMillis() - 400L * 86_400_000L
        prefs(c).edit().putString("entries", Gson().toJson(l.filter { it.time >= cutoff })).apply()
    }
    fun add(c: Context, e: FoodEntry) = save(c, load(c) + e)
    fun remove(c: Context, id: String) = save(c, load(c).filter { it.id != id })

    fun dayKey(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { "%04d-%02d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
    fun day(entries: List<FoodEntry>, key: String) = entries.filter { dayKey(it.time) == key }.sortedBy { it.time }
    fun totals(entries: List<FoodEntry>) = Totals(entries.sumOf { it.kcal }, entries.sumOf { it.protein }, entries.sumOf { it.carbs }, entries.sumOf { it.fat })

    /** Colazione fino alle 10:30, pranzo fino alle 15:30, spuntino fino alle 18:30, poi cena. */
    fun mealFor(hour: Int, minute: Int = 0): String { val t = hour * 60 + minute; return when { t < 630 -> "colazione"; t < 930 -> "pranzo"; t < 1110 -> "spuntino"; else -> "cena" } }
    fun mealNow(): String = Calendar.getInstance().let { mealFor(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }

    fun fromProduct(p: OpenFoodFacts.Product, grams: Double, meal: String, time: Long = System.currentTimeMillis()): FoodEntry {
        val k = grams / 100.0
        return FoodEntry(UUID.randomUUID().toString(), time, meal, listOf(p.brand, p.name).filter { it.isNotBlank() }.joinToString(" · "),
            p.kcal100 * k, p.protein100 * k, p.carbs100 * k, p.fat100 * k, grams)
    }

    /** Cibi diversi mangiati di recente, il più nuovo per primo. */
    fun recents(entries: List<FoodEntry>, n: Int = 8) = entries.sortedByDescending { it.time }.distinctBy { it.name.lowercase(Locale.ITALIAN) }.take(n)

    // ----- acqua -----
    fun waterMl(c: Context, key: String = dayKey(System.currentTimeMillis())) = prefs(c).getInt("water_$key", 0)
    fun addWater(c: Context, ml: Int, key: String = dayKey(System.currentTimeMillis())) = prefs(c).edit().putInt("water_$key", (waterMl(c, key) + ml).coerceAtLeast(0)).apply()
    fun setWater(c: Context, ml: Int, key: String) = prefs(c).edit().putInt("water_$key", ml.coerceAtLeast(0)).apply()
    fun waterDays(c: Context): Map<String, Int> = prefs(c).all.filterKeys { it.startsWith("water_2") }.mapKeys { it.key.removePrefix("water_") }.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    fun waterGoal(c: Context) = prefs(c).getInt("water_goal", 2000)
    fun setWaterGoal(c: Context, ml: Int) = prefs(c).edit().putInt("water_goal", ml).apply()
    fun glass(c: Context) = prefs(c).getInt("glass", 250)
    fun setGlass(c: Context, ml: Int) = prefs(c).edit().putInt("glass", ml).apply()

    // ----- promemoria per bere -----
    val WATER_TIMES = listOf("10:00", "12:30", "15:00", "17:30", "20:00")
    fun waterReminders(c: Context) = prefs(c).getBoolean("water_rem", false)
    /** Accende (5 promemoria al giorno) o spegne i promemoria per bere. */
    fun setWaterReminders(c: Context, on: Boolean) {
        prefs(c).getString("water_rem_ids", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.forEach { Reminders.remove(c, it) }
        val ids = if (on) WATER_TIMES.map { Reminders.add(c, "💧 Bevi un bicchiere d'acqua", Meds.nextAt(it), "day", "water").id } else emptyList()
        prefs(c).edit().putBoolean("water_rem", on).putString("water_rem_ids", ids.joinToString(",")).apply()
    }

    // ----- allergeni da evitare e profilo -----
    fun avoid(c: Context): Set<String> = prefs(c).getStringSet("avoid", emptySet()).orEmpty()
    fun setAvoid(c: Context, s: Set<String>) = prefs(c).edit().putStringSet("avoid", s).apply()

    data class Profile(val sex: String = "", val birthYear: Int = 0, val heightCm: Double = 0.0, val activity: Double = 1.375, val goal: String = "keep", val weightKg: Double = 0.0)
    fun profile(c: Context) = Profile(prefs(c).getString("sex", "") ?: "", prefs(c).getInt("birthYear", 0), prefs(c).getFloat("height", 0f).toDouble(),
        prefs(c).getFloat("activity", 1.375f).toDouble(), prefs(c).getString("goal", "keep") ?: "keep", prefs(c).getFloat("weight", 0f).toDouble())
    fun saveProfile(c: Context, p: Profile) = prefs(c).edit().putString("sex", p.sex).putInt("birthYear", p.birthYear).putFloat("height", p.heightCm.toFloat())
        .putFloat("activity", p.activity.toFloat()).putString("goal", p.goal).putFloat("weight", p.weightKg.toFloat()).apply()
    fun profileComplete(p: Profile) = p.sex in setOf("m", "f") && p.birthYear in 1900..2100 && p.heightCm in 100.0..250.0 && p.weightKg in 20.0..400.0
    fun targets(c: Context): Nutrition.Targets? {
        val p = profile(c)
        if (!profileComplete(p)) return null
        return Nutrition.targets(p.sex, p.weightKg, p.heightCm, Nutrition.ageFrom(p.birthYear), p.activity, p.goal)
    }
}

data class Recipe(
    val id: String, val title: String, val url: String, val image: String?, val servings: String,
    val time: String, val ingredients: List<String>, val steps: List<String>, val saved: Long
)

object Recipes {
    private fun file(c: Context) = File(c.filesDir, "recipes.json")
    fun load(c: Context): MutableList<Recipe> = runCatching {
        Gson().fromJson<MutableList<Recipe>>(file(c).readText(), object : TypeToken<MutableList<Recipe>>() {}.type)
    }.getOrNull()?.filterNotNull()?.toMutableList() ?: mutableListOf()
    fun save(c: Context, list: List<Recipe>) { runCatching { file(c).writeText(Gson().toJson(list)) } }
    fun add(c: Context, r: Recipe) = save(c, listOf(r) + load(c).filter { it.id != r.id })
    fun remove(c: Context, id: String) = save(c, load(c).filter { it.id != id })
    fun new(title: String, servings: String, time: String, ingredients: List<String>, steps: List<String>, url: String = "", image: String? = null) =
        Recipe(UUID.randomUUID().toString(), title.trim(), url, image, servings.trim(), time.trim(), ingredients.map { it.trim() }.filter { it.isNotEmpty() },
            steps.map { it.trim() }.filter { it.isNotEmpty() }, System.currentTimeMillis())
}

data class ShopItem(val id: String, val text: String, val done: Boolean = false)

/** Lista della spesa e menu della settimana. */
object Plan {
    private fun prefs(c: Context) = c.getSharedPreferences("piano", Context.MODE_PRIVATE)
    fun shopping(c: Context): MutableList<ShopItem> = runCatching {
        Gson().fromJson<MutableList<ShopItem>>(prefs(c).getString("shopping", null), object : TypeToken<MutableList<ShopItem>>() {}.type)
    }.getOrNull()?.filterNotNull()?.toMutableList() ?: mutableListOf()
    fun saveShopping(c: Context, l: List<ShopItem>) = prefs(c).edit().putString("shopping", Gson().toJson(l)).apply()

    /** Aggiunge le voci che non ci sono già (senza badare a maiuscole); restituisce quante ne ha aggiunte. */
    fun addToShopping(c: Context, items: List<String>): Int {
        val cur = shopping(c)
        val have = cur.map { it.text.trim().lowercase(Locale.ITALIAN) }.toMutableSet()
        var n = 0
        items.map { it.trim() }.filter { it.isNotEmpty() }.forEach { t ->
            if (have.add(t.lowercase(Locale.ITALIAN))) { cur += ShopItem(UUID.randomUUID().toString(), t.replaceFirstChar { it.uppercase() }); n++ }
        }
        saveShopping(c, cur)
        return n
    }

    val DAYS = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
    val MENU_MEALS = listOf("pranzo" to "Pranzo", "cena" to "Cena")
    fun menu(c: Context): Map<String, String> = runCatching {
        Gson().fromJson<Map<String, String>>(prefs(c).getString("menu", null), object : TypeToken<Map<String, String>>() {}.type)
    }.getOrNull() ?: emptyMap()
    fun saveMenu(c: Context, m: Map<String, String>) = prefs(c).edit().putString("menu", Gson().toJson(m)).apply()
    fun menuKey(day: Int, meal: String) = "$day-$meal"
    /** 0 = lunedì … 6 = domenica */
    fun todayIndex(cal: Calendar = Calendar.getInstance()) = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

object Store {
    fun ago(time: Long, now: Long = System.currentTimeMillis()): String {
        val m = (now - time) / 60_000
        return when {
            m < 1 -> "adesso"
            m < 60 -> "$m min fa"
            m < 60 * 24 -> "${m / 60} ore fa"
            m < 60 * 24 * 2 -> "ieri"
            m < 60 * 24 * 14 -> "${m / (60 * 24)} giorni fa"
            else -> java.text.SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).format(java.util.Date(time))
        }
    }
}

/** Tutti i dati in un file solo (per passarli da Web Launcher o per fare una copia). */
object Backup {
    data class Data(
        val version: Int = 1,
        val entries: List<FoodEntry> = emptyList(), val water: Map<String, Int> = emptyMap(), val waterGoal: Int = 0, val glass: Int = 0,
        val avoid: List<String> = emptyList(),
        val sex: String = "", val birthYear: Int = 0, val heightCm: Double = 0.0, val activity: Double = 0.0, val goal: String = "", val weightKg: Double = 0.0,
        val recipes: List<Recipe> = emptyList(), val shopping: List<ShopItem> = emptyList(), val menu: Map<String, String> = emptyMap(),
        val measures: List<Measure> = emptyList(), val meds: List<Med> = emptyList(), val visits: List<Visit> = emptyList(), val emergency: EmergencyCard? = null,
        val plans: List<WorkoutPlan> = emptyList(), val acts: List<Act> = emptyList(), val weeklyGoal: Int = 0
    )

    fun export(c: Context): String {
        val p = Food.profile(c)
        return Gson().toJson(Data(1, Food.load(c), Food.waterDays(c), Food.waterGoal(c), Food.glass(c), Food.avoid(c).toList(),
            p.sex, p.birthYear, p.heightCm, p.activity, p.goal, p.weightKg, Recipes.load(c), Plan.shopping(c), Plan.menu(c),
            Health.load(c), Meds.load(c), Visits.load(c), Emergency.load(c).takeIf { !Emergency.isEmpty(it) },
            if (Fitness.hasCustomPlans(c)) Fitness.plans(c) else emptyList(), Fitness.acts(c), Fitness.weeklyGoal(c)))
    }

    data class Result(val entries: Int, val recipes: Int, val days: Int, val measures: Int = 0, val meds: Int = 0, val visits: Int = 0, val acts: Int = 0)

    /** Unisce i dati del file a quelli che ci sono già (niente si cancella). null se il file non è valido. */
    fun import(c: Context, json: String): Result? {
        val d = runCatching { Gson().fromJson(json, Data::class.java) }.getOrNull() ?: return null
        if (d.version < 1 || (d.entries.isEmpty() && d.recipes.isEmpty() && d.water.isEmpty() && d.birthYear == 0 && d.shopping.isEmpty() && d.menu.isEmpty() &&
                d.measures.isEmpty() && d.meds.isEmpty() && d.visits.isEmpty() && d.emergency == null && d.plans.isEmpty() && d.acts.isEmpty())) return null
        val ids = Food.load(c).map { it.id }.toSet()
        val newEntries = d.entries.filter { it.id !in ids }
        Food.save(c, Food.load(c) + newEntries)
        var days = 0
        d.water.forEach { (k, v) -> if (v > Food.waterMl(c, k)) { Food.setWater(c, v, k); days++ } }
        val rids = Recipes.load(c).map { it.id }.toSet()
        val newRecipes = d.recipes.filter { it.id !in rids }
        Recipes.save(c, Recipes.load(c) + newRecipes)
        val cur = Food.profile(c)
        if (!Food.profileComplete(cur) && d.birthYear != 0) Food.saveProfile(c, Food.Profile(d.sex, d.birthYear, d.heightCm, d.activity.takeIf { it > 0 } ?: 1.375, d.goal.ifBlank { "keep" }, d.weightKg))
        if (d.avoid.isNotEmpty()) Food.setAvoid(c, Food.avoid(c) + d.avoid)
        if (d.waterGoal > 0 && Food.waterGoal(c) == 2000) Food.setWaterGoal(c, d.waterGoal)
        if (d.glass > 0) Food.setGlass(c, d.glass)
        Plan.addToShopping(c, d.shopping.filter { !it.done }.map { it.text })
        if (Plan.menu(c).isEmpty() && d.menu.isNotEmpty()) Plan.saveMenu(c, d.menu)
        // salute: le misure si uniscono; farmaci e visite si rimettono con promemoria nuovi (quelli vecchi sono dell'altra app)
        val mids = Health.load(c).map { it.id }.toSet()
        val newMeasures = d.measures.filter { it.id !in mids }
        if (newMeasures.isNotEmpty()) { Health.save(c, Health.load(c) + newMeasures); Health.syncWeight(c) }
        val medIds = Meds.load(c).map { it.id }.toSet()
        val newMeds = d.meds.filter { it.id !in medIds }
        newMeds.forEach { Meds.upsert(c, it.copy(reminderIds = emptyList())) }
        val visitIds = Visits.load(c).map { it.id }.toSet()
        val newVisits = d.visits.filter { it.id !in visitIds }
        newVisits.forEach { Visits.upsert(c, it.copy(reminderIds = emptyList())) }
        if (Emergency.isEmpty(Emergency.load(c)) && d.emergency != null) Emergency.save(c, d.emergency)
        // allenamenti: schede e attività si uniscono; l'obiettivo della settimana solo se qui non c'è ancora niente
        if (d.plans.isNotEmpty()) { val have = Fitness.plans(c).map { it.id }.toSet(); Fitness.savePlans(c, Fitness.plans(c) + d.plans.filter { it.id !in have }) }
        val actIds = Fitness.acts(c).map { it.id }.toSet()
        val newActs = d.acts.filter { it.id !in actIds }
        if (d.weeklyGoal > 0 && Fitness.acts(c).isEmpty()) Fitness.setWeeklyGoal(c, d.weeklyGoal)
        newActs.forEach { Fitness.addAct(c, it) }
        return Result(newEntries.size, newRecipes.size, days, newMeasures.size, newMeds.size, newVisits.size, newActs.size)
    }
}
