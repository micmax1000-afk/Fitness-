package com.micmax.cibo

import android.util.Log
import java.util.UUID

/** Prove automatiche: riempiono l'app con dati finti e aprono le schermate. */
object Selftest {
    private const val TAG = "CIBO_SELFTEST"

    fun seed(a: MainActivity) {
        val now = System.currentTimeMillis()
        Food.save(a, listOf(
            FoodEntry(UUID.randomUUID().toString(), now - 4 * 3_600_000, "colazione", "Cappuccino e 2 biscotti", 320.0, 8.0, 45.0, 11.0),
            FoodEntry(UUID.randomUUID().toString(), now - 3_600_000, "pranzo", "Pasta al pomodoro", 640.0, 20.0, 100.0, 14.0, 250.0)))
        Food.saveProfile(a, Food.Profile("f", 1985, 168.0, 1.375, "keep", 62.0))
        Food.addWater(a, 500)
        val r = Recipes.new("Pasta al pomodoro", "2", "20 min", listOf("200 g di pasta", "400 g di pomodori pelati", "basilico", "olio extravergine"),
            listOf("Cuoci la pasta in acqua salata.", "Scalda il pomodoro con un filo d'olio.", "Condisci e servi con il basilico."))
        Recipes.save(a, listOf(r))
        Plan.saveMenu(a, mapOf(Plan.menuKey(Plan.todayIndex(), "pranzo") to r.id))
        Plan.saveShopping(a, listOf(ShopItem("1", "Latte"), ShopItem("2", "Pane", true)))
    }

    fun run(a: MainActivity, action: String) {
        if (action == "backup") {
            // esporta e reimporta: i numeri devono tornare
            seed(a)
            val json = Backup.export(a)
            val r = Backup.import(a, json)
            Log.i(TAG, "BACKUP bytes=${json.length} importati=${r?.entries}/${r?.recipes} (0/0 = niente di nuovo, come previsto)")
            return
        }
        seed(a)
        a.runOnUiThread {
            a.refreshTop()
            when (action) {
                "diary" -> a.openDiary()
                "water" -> a.openWater()
                "calc" -> a.openCalculator()
                "recipes" -> a.openRecipes()
                "recipe" -> Recipes.load(a).firstOrNull()?.let { a.showRecipe(it) }
                "cooking" -> Recipes.load(a).firstOrNull()?.let { a.cookingMode(it) }
                "menu" -> a.openMenu()
                "shopping" -> a.openShopping()
                "web" -> a.openRecipeWeb("https://www.giallozafferano.it/")
                "settings" -> a.addMealMenu()
                else -> {}
            }
        }
    }
}
