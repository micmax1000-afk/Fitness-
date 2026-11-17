package com.micmax.cibo

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CiboTest {
    @Test fun mealByHour() {
        assertEquals("colazione", Food.mealFor(8)); assertEquals("pranzo", Food.mealFor(13)); assertEquals("spuntino", Food.mealFor(17)); assertEquals("cena", Food.mealFor(21))
    }

    @Test fun totals() {
        val t = Food.totals(listOf(FoodEntry("1", 0, "pranzo", "a", 300.0, 10.0, 40.0, 5.0), FoodEntry("2", 0, "cena", "b", 200.5, 2.0, 30.0, 1.0)))
        assertEquals(500.5, t.kcal, 0.01); assertEquals(12.0, t.protein, 0.01)
    }

    @Test fun nutritionTargets() {
        val t = Nutrition.targets("m", 80.0, 180.0, 40, 1.375, "keep")
        assertTrue(t.kcal in 2300..2450)
        val lose = Nutrition.targets("f", 60.0, 160.0, 30, 1.2, "lose")
        assertTrue(lose.kcal >= 1200)
        assertEquals(2650, Nutrition.waterGoalMl(80.0))
        assertEquals("Normopeso", Nutrition.bmiLabel(Nutrition.bmi(70.0, 175.0)))
    }

    @Test fun openFoodFactsParse() {
        val json = """{"status":1,"product":{"product_name":"Biscotti","brands":"Marca, Altra","quantity":"300 g","nutriscore_grade":"c","nova_group":4,
            "nutriments":{"energy-kcal_100g":450,"proteins_100g":7,"carbohydrates_100g":70,"sugars_100g":25,"fat_100g":16,"saturated-fat_100g":6,"fiber_100g":3,"salt_100g":0.8},
            "allergens_tags":["en:gluten","en:milk"],"serving_quantity":30}}"""
        val p = OpenFoodFacts.parse(json, "123")!!
        assertEquals("Biscotti", p.name); assertEquals("Marca", p.brand); assertEquals(450.0, p.kcal100, 0.1); assertEquals(listOf("Glutine", "Latte"), p.allergens)
        assertTrue(OpenFoodFacts.warnings(p, setOf("Latte")).any { it.contains("Latte") })
        assertTrue(OpenFoodFacts.warnings(p, emptySet()).any { it.contains("zucchero") })
        assertNull(OpenFoodFacts.parse("""{"status":0}""", "1"))
        val e = Food.fromProduct(p, 50.0, "spuntino")
        assertEquals(225.0, e.kcal, 0.1)
    }

    private val ld = """{"@context":"https://schema.org","@graph":[{"@type":"WebSite","name":"Sito"},
        {"@type":["Recipe","Thing"],"name":"Carbonara &amp; co","image":["https://x.it/a.jpg"],"recipeYield":["4","4 porzioni"],"totalTime":"PT1H30M",
         "recipeIngredient":["320 g di spaghetti","<b>4</b> tuorli","100&nbsp;g di guanciale"],
         "recipeInstructions":[{"@type":"HowToSection","itemListElement":[{"@type":"HowToStep","text":"Cuoci la pasta."},{"@type":"HowToStep","text":"Rosola il guanciale."}]},{"@type":"HowToStep","text":"Mantecare."}]}]}"""

    @Test fun recipeFromJsonLd() {
        val r = RecipeLd.parse(listOf("{non valido", ld), "https://ricette.giallozafferano.it/Carbonara.html")!!
        assertEquals("Carbonara & co", r.title); assertEquals("4", r.servings); assertEquals("1 h 30 min", r.time)
        assertEquals("https://x.it/a.jpg", r.image); assertEquals(3, r.ingredients.size); assertEquals("4 tuorli", r.ingredients[1]); assertEquals("100 g di guanciale", r.ingredients[2])
        assertEquals(listOf("Cuoci la pasta.", "Rosola il guanciale.", "Mantecare."), r.steps)
    }

    @Test fun recipeEdgeCases() {
        assertNull(RecipeLd.parse(listOf("""{"@type":"Article","name":"x"}"""), "u"))
        assertNull(RecipeLd.parse(listOf("""{"@type":"Recipe","name":"Vuota"}"""), "u"))
        val s = RecipeLd.parse(listOf("""[{"@type":"Recipe","name":"Uova","recipeIngredient":["2 uova"],"recipeInstructions":"Rompi le uova.\nCuocile."}]"""), "u")!!
        assertEquals(listOf("Rompi le uova.", "Cuocile."), s.steps)
        assertEquals("45 min", RecipeLd.duration("PT45M")); assertEquals("2 h", RecipeLd.duration("PT2H")); assertEquals("", RecipeLd.duration("boh"))
        assertEquals("6", RecipeLd.servings("6 persone")); assertEquals("’", RecipeLd.clean("&rsquo;"))
    }

    @Test fun backupFormatIsCompatibleWithWebLauncher() {
        // i campi di Web Launcher: FoodEntry e Recipe hanno gli stessi nomi
        val d = Backup.Data(1, listOf(FoodEntry("e1", 5, "cena", "Zuppa", 300.0, 10.0, 40.0, 8.0, 350.0)), mapOf("2026-11-01" to 1500), 2200, 250, listOf("Glutine"),
            "f", 1985, 168.0, 1.375, "keep", 62.5, listOf(Recipe("r1", "Zuppa", "http://x", null, "2", "30 min", listOf("a"), listOf("b"), 9)), listOf(ShopItem("s", "Latte")), mapOf("0-pranzo" to "r1"))
        val back = Gson().fromJson(Gson().toJson(d), Backup.Data::class.java)
        assertEquals(d, back)
        val wl = """{"version":1,"entries":[{"id":"a","time":1,"meal":"pranzo","name":"Riso","kcal":200.0,"protein":4.0,"carbs":40.0,"fat":1.0}],"recipes":[]}"""
        val w = Gson().fromJson(wl, Backup.Data::class.java)
        assertEquals(1, w.entries.size); assertEquals(0.0, w.entries[0].grams, 0.0); assertFalse(w.menu.isNotEmpty()); assertNotNull(w.avoid)
    }

    @Test fun todayIndexIsMondayFirst() {
        val mon = java.util.Calendar.getInstance().apply { set(2026, 10, 16) }   // lunedì 16 novembre 2026
        assertEquals(0, Plan.todayIndex(mon)); mon.add(java.util.Calendar.DAY_OF_YEAR, 6); assertEquals(6, Plan.todayIndex(mon))
    }
}

class HealthTest {
    @Test fun medTimesAndStock() {
        assertEquals(listOf("08:00", "13:00", "20:30"), Meds.parseTimes("8, 20:30 , 13.00, 99, boh"))
        val m = Med("1", "X", "", listOf("08:00", "20:00"), 12, 1)
        assertEquals(2, Meds.perDay(m)); assertEquals(6, Meds.daysLeft(m)); assertFalse(Meds.isLow(m)); assertTrue(Meds.isLow(m.copy(stock = 8)))
        assertTrue(Meds.isLow(m.copy(stock = 0)))
        val now = java.util.Calendar.getInstance().apply { set(2026, 10, 17, 9, 0) }
        assertEquals("20:00", Meds.nextDose(m, now)); assertEquals("domani 08:00", Meds.nextDose(m, java.util.Calendar.getInstance().apply { set(2026, 10, 17, 21, 0) }))
    }

    @Test fun visitReminders() {
        val visit = java.util.Calendar.getInstance().apply { set(2026, 10, 20, 15, 30, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        val now = java.util.Calendar.getInstance().apply { set(2026, 10, 17, 9, 0, 0) }.timeInMillis
        val t = Visits.reminderTimes(visit, now)
        assertEquals(2, t.size); assertEquals(visit - 2 * 3_600_000L, t[1])
        assertEquals(0, Visits.reminderTimes(visit, visit - 3_600_000L).size)
    }

    @Test fun reminderNextOccurrence() {
        val day = 86_400_000L
        val now = java.util.Calendar.getInstance().apply { set(2026, 10, 17, 12, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        assertEquals(now + 1000, Reminders.nextOccurrence(now + 1000, null, now))
        assertNull(Reminders.nextOccurrence(now - 1000, null, now))
        val n = Reminders.nextOccurrence(now - 3 * day - 3_600_000, "day", now)!!
        assertTrue(n > now && n - now <= day)
    }

    @Test fun reportAndEmergencyText() {
        val d = Report.Data("Maria", 0, 86_400_000L * 30, listOf(1L to 64.0, 2L to 62.0), listOf(Triple(1L, 130, 85), Triple(2L, 120, 80)), listOf(3L to 100.0), listOf("Vitamina D (1 cp)"), "Penicillina", "")
        val s = Report.sections(d).toMap()
        assertTrue(s.getValue("Peso (kg)")[0].contains("-2,0")); assertTrue(s.getValue("Pressione arteriosa (mmHg)")[0].contains("media 125/82"))
        assertTrue(Report.sections(d.copy(weights = emptyList(), pressures = emptyList(), glucose = emptyList(), medicines = emptyList(), allergies = "")).any { it.first == "Dati" })
        assertEquals(listOf("Maria · nato/a il 1985", "Gruppo sanguigno: A+", "⚠️ Allergie: Penicillina"), Emergency.lines(EmergencyCard("Maria", "1985", "A+", "Penicillina")))
    }

    @Test fun backupWithHealthFields() {
        val d = Backup.Data(1, measures = listOf(Measure("a", "peso", 70.0, null, 5)), meds = listOf(Med("m", "X", "d", listOf("08:00"), 10)),
            visits = listOf(Visit("v", "Visita", 99)), emergency = EmergencyCard("Maria"))
        assertEquals(d, Gson().fromJson(Gson().toJson(d), Backup.Data::class.java))
        val old = Gson().fromJson("""{"version":1,"entries":[]}""", Backup.Data::class.java)
        assertTrue(old.measures.isEmpty()); assertNull(old.emergency)
    }
}
