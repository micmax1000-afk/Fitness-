package com.micmax.cibo

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Energy needs, BMI and water goal. Pure functions (unit-tested). */
object Nutrition {
    /** Mifflin–St Jeor. sex = "m" | "f". */
    fun bmr(sex: String, weightKg: Double, heightCm: Double, age: Int): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + (if (sex == "m") 5 else -161)

    val ACTIVITY = listOf(
        "Sedentario (poco movimento)" to 1.2, "Leggero (1-3 allenamenti a settimana)" to 1.375,
        "Moderato (3-5 a settimana)" to 1.55, "Intenso (6-7 a settimana)" to 1.725
    )
    val GOALS = listOf("lose" to "Dimagrire", "keep" to "Mantenere il peso", "gain" to "Mettere massa")

    data class Targets(val kcal: Int, val proteinG: Int, val carbG: Int, val fatG: Int)

    fun targets(sex: String, weightKg: Double, heightCm: Double, age: Int, activity: Double, goal: String): Targets {
        val tdee = bmr(sex, weightKg, heightCm, age) * activity
        val floor = if (sex == "m") 1500.0 else 1200.0                      // never suggest less than a safe minimum
        val kcal = when (goal) { "lose" -> tdee * 0.85; "gain" -> tdee + 300; else -> tdee }.coerceAtLeast(floor).roundToInt()
        val protein = (weightKg * (if (goal == "gain") 1.8 else 1.6)).roundToInt()
        val fat = (kcal * 0.28 / 9).roundToInt()
        val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
        return Targets(kcal, protein, carbs, fat)
    }

    fun bmi(weightKg: Double, heightCm: Double) = if (heightCm <= 0) 0.0 else weightKg / Math.pow(heightCm / 100, 2.0)
    fun bmiLabel(b: Double) = when { b <= 0 -> "—"; b < 18.5 -> "Sottopeso"; b < 25 -> "Normopeso"; b < 30 -> "Sovrappeso"; else -> "Obesità" }
    /** Healthy weight range for a height (BMI 18.5–24.9), in kg. */
    fun healthyRange(heightCm: Double): Pair<Double, Double> = (18.5 * Math.pow(heightCm / 100, 2.0)) to (24.9 * Math.pow(heightCm / 100, 2.0))

    /** About 33 ml per kg of weight, in steps of 50 ml, between 1.5 and 3.5 litres. */
    fun waterGoalMl(weightKg: Double): Int = ((weightKg * 33 / 50.0).roundToInt() * 50).coerceIn(1500, 3500)

    fun ageFrom(birthYear: Int, now: Calendar = Calendar.getInstance()) = (now.get(Calendar.YEAR) - birthYear).coerceAtLeast(0)
}

/** Products from Open Food Facts (free, no account). Parsing is pure and unit-tested. */
object OpenFoodFacts {
    data class Product(
        val barcode: String, val name: String, val brand: String, val quantity: String, val nutriScore: String, val nova: Int,
        val kcal100: Double, val protein100: Double, val carbs100: Double, val sugars100: Double, val fat100: Double,
        val satFat100: Double, val fiber100: Double, val salt100: Double,
        val allergens: List<String>, val additives: List<String>, val ingredients: String, val servingG: Double
    )

    val ALLERGENS = linkedMapOf(
        "gluten" to "Glutine", "milk" to "Latte", "eggs" to "Uova", "peanuts" to "Arachidi", "nuts" to "Frutta a guscio",
        "soybeans" to "Soia", "fish" to "Pesce", "crustaceans" to "Crostacei", "molluscs" to "Molluschi", "celery" to "Sedano",
        "mustard" to "Senape", "sesame-seeds" to "Sesamo", "sulphur-dioxide-and-sulphites" to "Solfiti", "lupin" to "Lupini"
    )

    private fun JsonObject.num(k: String): Double = get(k)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0
    private fun JsonObject.str(k: String): String = get(k)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }.orEmpty().trim()
    private fun JsonElement?.tags(): List<String> {
        val arr = this?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return arr.mapNotNull { runCatching { it.asString }.getOrNull() }
    }

    fun allergenLabel(tag: String): String {
        val key = tag.substringAfter(':').lowercase(Locale.ROOT)
        return ALLERGENS[key] ?: key.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

    /** null if the product is unknown. */
    fun parse(json: String, barcode: String): Product? = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("status")?.asInt != 1) return null
        val p = root.getAsJsonObject("product") ?: return null
        val n = p.getAsJsonObject("nutriments") ?: JsonObject()
        val kcal = n.num("energy-kcal_100g").takeIf { it > 0 } ?: (n.num("energy_100g") / 4.184)
        val name = p.str("product_name_it").ifBlank { p.str("product_name") }
        Product(
            barcode = barcode, name = name.ifBlank { "Prodotto $barcode" }, brand = p.str("brands").substringBefore(',').trim(),
            quantity = p.str("quantity"), nutriScore = p.str("nutriscore_grade").lowercase(Locale.ROOT).takeIf { it.length == 1 && it[0] in 'a'..'e' }.orEmpty(),
            nova = p.num("nova_group").toInt(),
            kcal100 = kcal, protein100 = n.num("proteins_100g"), carbs100 = n.num("carbohydrates_100g"), sugars100 = n.num("sugars_100g"),
            fat100 = n.num("fat_100g"), satFat100 = n.num("saturated-fat_100g"), fiber100 = n.num("fiber_100g"), salt100 = n.num("salt_100g"),
            allergens = p.get("allergens_tags").tags().map { allergenLabel(it) }.distinct(),
            additives = p.get("additives_tags").tags().map { it.substringAfter(':').uppercase(Locale.ROOT) }.distinct(),
            ingredients = p.str("ingredients_text_it").ifBlank { p.str("ingredients_text") },
            servingG = p.num("serving_quantity")
        )
    }.getOrNull()

    enum class Level { LOW, MEDIUM, HIGH }
    // EU «traffic light» thresholds per 100 g (solids)
    fun sugarLevel(g: Double) = if (g > 22.5) Level.HIGH else if (g > 5) Level.MEDIUM else Level.LOW
    fun fatLevel(g: Double) = if (g > 17.5) Level.HIGH else if (g > 3) Level.MEDIUM else Level.LOW
    fun satFatLevel(g: Double) = if (g > 5) Level.HIGH else if (g > 1.5) Level.MEDIUM else Level.LOW
    fun saltLevel(g: Double) = if (g > 1.5) Level.HIGH else if (g > 0.3) Level.MEDIUM else Level.LOW

    /** Warnings for the user: allergens they avoid, and nutrients that are high. */
    fun warnings(p: Product, avoid: Set<String>): List<String> = buildList {
        p.allergens.filter { it in avoid }.forEach { add("⚠️ Contiene $it") }
        if (sugarLevel(p.sugars100) == Level.HIGH) add("🍬 Molto zucchero (${fmt(p.sugars100)} g su 100 g)")
        if (saltLevel(p.salt100) == Level.HIGH) add("🧂 Molto sale (${fmt(p.salt100)} g su 100 g)")
        if (satFatLevel(p.satFat100) == Level.HIGH) add("🧈 Molti grassi saturi (${fmt(p.satFat100)} g su 100 g)")
        if (p.nova == 4) add("🏭 Molto lavorato (NOVA 4)")
    }

    fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.ITALIAN, "%.1f", v)

    // network (call off the main thread). Throws on a network error; returns null for an unknown product.
    fun fetch(barcode: String): Product? {
        val fields = "code,product_name,product_name_it,brands,quantity,nutriscore_grade,nova_group,nutriments,allergens_tags,additives_tags,ingredients_text_it,ingredients_text,serving_quantity"
        val c = java.net.URL("https://world.openfoodfacts.org/api/v2/product/${java.net.URLEncoder.encode(barcode, "UTF-8")}.json?fields=$fields")
            .openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "CiboERicette/1.0 (Android; app personale)")
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return parse(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }, barcode)
        } finally { c.disconnect() }
    }
}

data class FoodEntry(val id: String, val time: Long, val meal: String, val name: String, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val grams: Double = 0.0)

