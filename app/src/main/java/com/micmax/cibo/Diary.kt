package com.micmax.cibo

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

// ======================= Codice a barre =======================

fun MainActivity.scanFoodBarcode() {
    try {
        val opts = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder().setBarcodeFormats(
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13, com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8,
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A, com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E).build()
        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, opts).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let { lookupProduct(it) } }
            .addOnFailureListener { toast("Scanner non disponibile: servono i Google Play Services aggiornati") }
    } catch (_: Throwable) { toast("Scanner non disponibile su questo telefono") }
}

fun MainActivity.lookupProduct(barcode: String) {
    toast("Cerco il prodotto $barcode…")
    io.execute {
        val res = runCatching { OpenFoodFacts.fetch(barcode) }
        runOnUiThread {
            if (isFinishing) return@runOnUiThread
            val p = res.getOrNull()
            when {
                res.isFailure -> MaterialAlertDialogBuilder(this).setTitle("Non riesco a cercare il prodotto")
                    .setMessage("Controlla la connessione e riprova.\n\nCodice: $barcode")
                    .setPositiveButton("Riprova") { _, _ -> lookupProduct(barcode) }.setNegativeButton("Chiudi", null).show()
                p == null -> MaterialAlertDialogBuilder(this).setTitle("Prodotto non trovato")
                    .setMessage("Il codice $barcode non è nel database Open Food Facts (gratuito e fatto dagli utenti: non ha tutti i prodotti).\n\nPuoi inserire le calorie a mano.")
                    .setPositiveButton("Inserisci a mano") { _, _ -> addFoodDialog(null) }.setNegativeButton("Chiudi", null).show()
                else -> showProductPage(p)
            }
        }
    }
}

private fun nutriColor(g: String) = when (g) { "a" -> 0xFF038141.toInt(); "b" -> 0xFF85BB2F.toInt(); "c" -> 0xFFFECB02.toInt(); "d" -> 0xFFEE8100.toInt(); "e" -> 0xFFE63E11.toInt(); else -> 0xFF9CA3AF.toInt() }
private fun levelColor(l: OpenFoodFacts.Level) = when (l) { OpenFoodFacts.Level.LOW -> GREEN; OpenFoodFacts.Level.MEDIUM -> AMBER; OpenFoodFacts.Level.HIGH -> RED }

fun MainActivity.showProductPage(p: OpenFoodFacts.Product) {
    var scr: Scr? = null
    scr = openScreen(p.name.take(26), "Aggiungi", { addProductDialog(p) { pop() } }) { body, _ ->
        body.addView(TextView(this).apply { text = p.name; textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)); setPadding(dp(8), dp(4), dp(8), 0) })
        body.addView(TextView(this).apply {
            text = listOf(p.brand, p.quantity).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Codice ${p.barcode}" }
            textSize = 13f; setTextColor(col(R.color.muted)); setPadding(dp(8), 0, dp(8), dp(8))
        })
        body.addView(statRow(
            Triple("Nutri-Score", if (p.nutriScore.isEmpty()) "n.d." else p.nutriScore.uppercase(), if (p.nutriScore.isEmpty()) null else "da A (meglio) a E"),
            Triple("Lavorazione", if (p.nova in 1..4) "${p.nova} / 4" else "n.d.", when (p.nova) { 1 -> "poco lavorato"; 2 -> "ingrediente"; 3 -> "lavorato"; 4 -> "ultra-lavorato"; else -> null }),
            Triple("Energia", "${p.kcal100.toInt()} kcal", "per 100 g")))
        val warnings = OpenFoodFacts.warnings(p, Food.avoid(this))
        if (warnings.isNotEmpty()) {
            sectionLabel(body, "ATTENZIONE")
            warnings.forEach { w -> body.addView(TextView(this).apply { text = w; textSize = 15f; setTextColor(col(R.color.text)); setPadding(dp(10), dp(4), dp(10), dp(4)) }) }
        }
        sectionLabel(body, "PER 100 G")
        fun nut(label: String, v: Double, unit: String, lvl: OpenFoodFacts.Level? = null) {
            val row = listRow(body, "", label, null) {} as LinearLayout
            (row.getChildAt(0) as View).visibility = View.GONE
            row.addView(TextView(this).apply {
                text = OpenFoodFacts.fmt(v) + " $unit"; textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(lvl?.let { levelColor(it) } ?: col(R.color.text)); setPadding(0, 0, dp(6), 0)
            })
        }
        nut("Proteine", p.protein100, "g"); nut("Carboidrati", p.carbs100, "g"); nut("di cui zuccheri", p.sugars100, "g", OpenFoodFacts.sugarLevel(p.sugars100))
        nut("Grassi", p.fat100, "g", OpenFoodFacts.fatLevel(p.fat100)); nut("di cui saturi", p.satFat100, "g", OpenFoodFacts.satFatLevel(p.satFat100))
        nut("Fibre", p.fiber100, "g"); nut("Sale", p.salt100, "g", OpenFoodFacts.saltLevel(p.salt100))
        if (p.allergens.isNotEmpty()) { sectionLabel(body, "ALLERGENI"); hintLabel(body, p.allergens.joinToString(" · ")) }
        if (p.additives.isNotEmpty()) { sectionLabel(body, "ADDITIVI (${p.additives.size})"); hintLabel(body, p.additives.joinToString(", ")) }
        if (p.ingredients.isNotBlank()) { sectionLabel(body, "INGREDIENTI"); hintLabel(body, p.ingredients.take(900)) }
        hintLabel(body, "Dati Open Food Facts, inseriti dagli utenti: controlla sempre l'etichetta.")
    }
}

private fun MainActivity.addProductDialog(p: OpenFoodFacts.Product, done: () -> Unit) {
    val grams = field("Quanti grammi?", OpenFoodFacts.fmt(if (p.servingG > 0) p.servingG else 100.0), number = true)
    val live = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, 0); setTextColor(col(R.color.text)) }
    fun update() {
        val g = Calc.num(grams.text.toString()) ?: 0.0
        live.text = "= ${(p.kcal100 * g / 100).toInt()} kcal · P ${OpenFoodFacts.fmt(p.protein100 * g / 100)} g · C ${OpenFoodFacts.fmt(p.carbs100 * g / 100)} g · G ${OpenFoodFacts.fmt(p.fat100 * g / 100)} g"
    }
    grams.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = update()
    })
    update()
    val box = form(grams).apply { addView(live) }
    MaterialAlertDialogBuilder(this).setTitle("Aggiungi al diario").setMessage(p.name).setView(box)
        .setPositiveButton("Aggiungi") { _, _ ->
            val g = Calc.num(grams.text.toString()) ?: return@setPositiveButton toast("Scrivi i grammi")
            Food.add(this, Food.fromProduct(p, g, Food.mealNow())); toast("🍽 Aggiunto: ${(p.kcal100 * g / 100).toInt()} kcal")
            done()
        }.setNegativeButton("Annulla", null).show()
}

// ======================= Diario =======================

/** Aggiunge (o conferma) un cibo a mano: nome, calorie e, se si sa, i macro. */
fun MainActivity.addFoodDialog(prefill: FoodEntry?) {
    val name = field("Cosa hai mangiato", prefill?.name)
    val kcal = field("Calorie (kcal)", prefill?.kcal?.toInt()?.toString(), number = true)
    val prot = field("Proteine (g) — facoltativo", prefill?.protein?.takeIf { it > 0 }?.let { OpenFoodFacts.fmt(it) }, number = true)
    val carb = field("Carboidrati (g) — facoltativo", prefill?.carbs?.takeIf { it > 0 }?.let { OpenFoodFacts.fmt(it) }, number = true)
    val fat = field("Grassi (g) — facoltativo", prefill?.fat?.takeIf { it > 0 }?.let { OpenFoodFacts.fmt(it) }, number = true)
    var meal = prefill?.meal ?: Food.mealNow()
    val mealBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { isAllCaps = false }
    fun showMeal() { mealBtn.text = "Pasto: " + (Food.MEALS.firstOrNull { it.first == meal }?.second ?: meal) + " ▾" }
    showMeal()
    mealBtn.setOnClickListener { MaterialAlertDialogBuilder(this).setItems(Food.MEALS.map { it.second }.toTypedArray()) { _, w -> meal = Food.MEALS[w].first; showMeal() }.show() }
    val box = form(name, kcal, prot, carb, fat).apply { addView(mealBtn) }
    MaterialAlertDialogBuilder(this).setTitle("Aggiungi al diario").setView(box).setPositiveButton("Salva") { _, _ ->
        val k = Calc.num(kcal.text.toString())
        if (name.text.toString().isBlank() || k == null) { toast("Scrivi cosa hai mangiato e le calorie"); return@setPositiveButton }
        Food.add(this, FoodEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), meal, name.text.toString().trim(), k,
            Calc.num(prot.text.toString()) ?: 0.0, Calc.num(carb.text.toString()) ?: 0.0, Calc.num(fat.text.toString()) ?: 0.0, prefill?.grams ?: 0.0))
        toast("🍽 Aggiunto"); refreshTop()
    }.setNegativeButton("Annulla", null).show()
}

fun MainActivity.openDiary() {
    var dayOffset = 0
    openScreen("Diario", "Aggiungi", { addMealMenu() }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val key = Food.dayKey(cal.timeInMillis)
            val entries = Food.day(Food.load(this), key)
            val tot = Food.totals(entries)
            val target = Food.targets(this)
            val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            fun navBtn(t: String, delta: Int) = TextView(this).apply { text = t; textSize = 24f; setTextColor(col(R.color.text)); setPadding(dp(16), dp(4), dp(16), dp(4)); setOnClickListener { dayOffset += delta; render() } }
            nav.addView(navBtn("‹", -1))
            nav.addView(TextView(this).apply {
                text = if (dayOffset == 0) "Oggi" else if (dayOffset == -1) "Ieri" else SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(cal.time).replaceFirstChar { it.uppercase() }
                textSize = 17f; gravity = Gravity.CENTER; setTextColor(col(R.color.text)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            nav.addView(navBtn("›", 1).apply { visibility = if (dayOffset >= 0) View.INVISIBLE else View.VISIBLE })
            body.addView(nav)
            body.addView(TextView(this).apply {
                text = "${tot.kcal.toInt()}" + (target?.let { " / ${it.kcal} kcal" } ?: " kcal"); textSize = 30f; setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (target != null && tot.kcal > target.kcal * 1.1) RED else col(R.color.text)); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0)
            })
            if (target != null) body.addView(progressBar(tot.kcal / target.kcal, if (tot.kcal > target.kcal * 1.1) RED else if (tot.kcal > target.kcal * 0.9) AMBER else GREEN))
            body.addView(statRow(
                Triple("Proteine", "${tot.protein.toInt()} g", target?.let { "di ${it.proteinG}" }),
                Triple("Carboidrati", "${tot.carbs.toInt()} g", target?.let { "di ${it.carbG}" }),
                Triple("Grassi", "${tot.fat.toInt()} g", target?.let { "di ${it.fatG}" })))
            if (target == null) listRow(body, "🧮", "Imposta il tuo obiettivo", "Calcola le calorie giuste per te") { openCalculator() }
            if (entries.isEmpty()) { hintLabel(body, "Niente registrato. Tocca «Aggiungi» in alto: puoi scansionare un prodotto o scrivere a mano."); return }
            Food.MEALS.forEach { (mk, ml) ->
                val list = entries.filter { it.meal == mk }
                if (list.isEmpty()) return@forEach
                sectionLabel(body, ml.uppercase(Locale.ITALIAN) + " · ${list.sumOf { it.kcal }.toInt()} kcal")
                list.forEach { e ->
                    listRow(body, ml.substringBefore(' '), e.name, "${e.kcal.toInt()} kcal" + (if (e.grams > 0) " · ${e.grams.toInt()} g" else "") + (if (e.protein + e.carbs + e.fat > 0) " · P ${e.protein.toInt()} C ${e.carbs.toInt()} G ${e.fat.toInt()}" else ""),
                        onLong = { addFoodDialog(e) }, onRemove = { Food.remove(this, e.id); render() }) {}
                }
            }
        }
        scr.refresh = { render() }
        render()
    }
}

// ======================= Acqua =======================

fun MainActivity.openWater() {
    openScreen("Acqua") { body, scr ->
        fun render() {
            body.removeAllViews()
            val ml = Food.waterMl(this); val goal = Food.waterGoal(this); val glass = Food.glass(this)
            body.addView(TextView(this).apply { text = "$ml / $goal ml"; textSize = 34f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(if (ml >= goal) GREEN else col(R.color.text)); setPadding(0, dp(10), 0, 0) })
            body.addView(progressBar(ml.toDouble() / goal, BLUE))
            body.addView(TextView(this).apply { text = if (ml >= goal) "🎉 Obiettivo raggiunto!" else "Ancora ${goal - ml} ml (${(goal - ml + glass - 1) / glass} bicchieri)"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted)) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(4)) }
            fun btn(label: String, onClick: () -> Unit) = row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label; isAllCaps = false; textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }; setOnClickListener { onClick() }
            })
            btn("💧 +$glass ml") { Food.addWater(this, glass); render() }
            btn("🍼 +500 ml") { Food.addWater(this, 500); render() }
            btn("↩ −$glass") { Food.addWater(this, -glass); render() }
            body.addView(row)
            val days = (6 downTo 0).map { off -> val t = System.currentTimeMillis() - off * 86_400_000L; Food.dayKey(t) to Food.waterMl(this, Food.dayKey(t)) }
            if (days.any { it.second > 0 }) {
                sectionLabel(body, "ULTIMI 7 GIORNI")
                val max = maxOf(goal, days.maxOf { it.second }).toDouble()
                days.forEach { (k, v) ->
                    val label = SimpleDateFormat("EEE d", Locale.ITALIAN).format(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(k)!!)
                    val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    line.addView(TextView(this).apply { text = label; textSize = 13f; setTextColor(col(R.color.muted)) }, LinearLayout.LayoutParams(dp(64), -2))
                    line.addView(progressBar(v / max, if (v >= goal) GREEN else BLUE), LinearLayout.LayoutParams(0, dp(10), 1f))
                    line.addView(TextView(this).apply { text = "$v"; textSize = 13f; setTextColor(col(R.color.text)); gravity = Gravity.END }, LinearLayout.LayoutParams(dp(52), -2))
                    body.addView(line)
                }
            }
            sectionLabel(body, "IMPOSTAZIONI")
            listRow(body, "🎯", "Obiettivo giornaliero", "$goal ml" + (Food.profile(this).weightKg.takeIf { it > 0 }?.let { " · consigliato ${Nutrition.waterGoalMl(it)} ml per il tuo peso" } ?: "")) {
                val et = field("Millilitri", goal.toString(), number = true)
                MaterialAlertDialogBuilder(this).setTitle("Obiettivo giornaliero").setView(form(et))
                    .setPositiveButton("Salva") { _, _ -> Food.setWaterGoal(this, (Calc.num(et.text.toString())?.toInt() ?: goal).coerceIn(500, 6000)); render() }.setNegativeButton("Annulla", null).show()
            }
            val remOn = Food.waterReminders(this)
            listRow(body, if (remOn) "🔔" else "🔕", if (remOn) "Promemoria attivi" else "Promemoria per bere", if (remOn) "Alle 10, 12:30, 15, 17:30 e 20: tocca per spegnerli" else "5 promemoria al giorno, dalle 10 alle 20") {
                if (!remOn) ensureNotificationPermission()
                Food.setWaterReminders(this, !remOn); toast(if (!remOn) "🔔 Promemoria per bere attivi" else "Promemoria spenti"); render()
            }
            listRow(body, "🥛", "Dimensione del bicchiere", "$glass ml") {
                val sizes = intArrayOf(150, 200, 250, 300, 330)
                MaterialAlertDialogBuilder(this).setTitle("Bicchiere").setSingleChoiceItems(sizes.map { "$it ml" }.toTypedArray(), sizes.indexOf(glass).coerceAtLeast(2)) { dl, w -> Food.setGlass(this, sizes[w]); dl.dismiss(); render() }.show()
            }
        }
        scr.refresh = { render() }
        render()
    }
}

// ======================= Fabbisogno =======================

fun MainActivity.openCalculator() {
    openScreen("Fabbisogno di calorie") { body, scr ->
        fun render() {
            body.removeAllViews()
            val p = Food.profile(this); val w = p.weightKg
            sectionLabel(body, "I TUOI DATI")
            listRow(body, "👤", "Sesso", when (p.sex) { "m" -> "Uomo"; "f" -> "Donna"; else -> "Da scegliere" }) {
                MaterialAlertDialogBuilder(this).setTitle("Sesso").setItems(arrayOf("Uomo", "Donna")) { _, i -> Food.saveProfile(this, p.copy(sex = if (i == 0) "m" else "f")); render() }.show()
            }
            listRow(body, "🎂", "Anno di nascita", if (p.birthYear > 0) "${p.birthYear} · ${Nutrition.ageFrom(p.birthYear)} anni" else "Da inserire") {
                val et = field("Anno (es. 1985)", p.birthYear.takeIf { it > 0 }?.toString(), number = true)
                MaterialAlertDialogBuilder(this).setTitle("Anno di nascita").setView(form(et)).setPositiveButton("Salva") { _, _ ->
                    val y = et.text.toString().toIntOrNull(); if (y == null || y !in 1900..2100) toast("Anno non valido") else { Food.saveProfile(this, p.copy(birthYear = y)); render() }
                }.setNegativeButton("Annulla", null).show()
            }
            listRow(body, "📏", "Altezza", if (p.heightCm > 0) "${p.heightCm.toInt()} cm" else "Da inserire") {
                val et = field("Centimetri (es. 172)", p.heightCm.takeIf { it > 0 }?.toInt()?.toString(), number = true)
                MaterialAlertDialogBuilder(this).setTitle("Altezza").setView(form(et)).setPositiveButton("Salva") { _, _ ->
                    val h = Calc.num(et.text.toString()); if (h == null || h !in 100.0..250.0) toast("Altezza non valida") else { Food.saveProfile(this, p.copy(heightCm = h)); render() }
                }.setNegativeButton("Annulla", null).show()
            }
            listRow(body, "⚖️", "Peso", if (w > 0) "%.1f kg".format(Locale.ITALIAN, w) else "Da inserire") {
                val et = field("Chili (es. 72,5)", w.takeIf { it > 0 }?.let { OpenFoodFacts.fmt(it) }, number = true)
                MaterialAlertDialogBuilder(this).setTitle("Peso").setView(form(et)).setPositiveButton("Salva") { _, _ ->
                    val k = Calc.num(et.text.toString()); if (k == null || k !in 20.0..400.0) toast("Peso non valido") else { Health.add(this, Measure(UUID.randomUUID().toString(), "peso", k, null, System.currentTimeMillis())); render() }
                }.setNegativeButton("Annulla", null).show()
            }
            listRow(body, "🏃", "Attività", Nutrition.ACTIVITY.firstOrNull { it.second == p.activity }?.first ?: "Leggero") {
                MaterialAlertDialogBuilder(this).setTitle("Quanto ti muovi?").setItems(Nutrition.ACTIVITY.map { it.first }.toTypedArray()) { _, i -> Food.saveProfile(this, p.copy(activity = Nutrition.ACTIVITY[i].second)); render() }.show()
            }
            listRow(body, "🎯", "Obiettivo", Nutrition.GOALS.firstOrNull { it.first == p.goal }?.second ?: "Mantenere il peso") {
                MaterialAlertDialogBuilder(this).setTitle("Obiettivo").setItems(Nutrition.GOALS.map { it.second }.toTypedArray()) { _, i -> Food.saveProfile(this, p.copy(goal = Nutrition.GOALS[i].first)); render() }.show()
            }
            if (!Food.profileComplete(p)) { hintLabel(body, "Compila sesso, anno di nascita, altezza e peso per vedere il risultato."); return }
            val age = Nutrition.ageFrom(p.birthYear)
            val t = Nutrition.targets(p.sex, w, p.heightCm, age, p.activity, p.goal)
            sectionLabel(body, "IL TUO RISULTATO")
            body.addView(TextView(this).apply { text = "${t.kcal} kcal al giorno"; textSize = 28f; setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER; setTextColor(col(R.color.text)); setPadding(0, dp(8), 0, 0) })
            body.addView(statRow(Triple("Proteine", "${t.proteinG} g", null), Triple("Carboidrati", "${t.carbG} g", null), Triple("Grassi", "${t.fatG} g", null)))
            val bmr = Nutrition.bmr(p.sex, w, p.heightCm, age)
            val bmi = Nutrition.bmi(w, p.heightCm); val (lo, hi) = Nutrition.healthyRange(p.heightCm)
            body.addView(statRow(Triple("Metabolismo basale", "${bmr.toInt()}", "kcal a riposo"), Triple("BMI", "%.1f".format(Locale.ITALIAN, bmi), Nutrition.bmiLabel(bmi)), Triple("Acqua", "${Nutrition.waterGoalMl(w)} ml", "al giorno")))
            hintLabel(body, "Peso sano per la tua altezza: da %.0f a %.0f kg (BMI 18,5–24,9).".format(Locale.ITALIAN, lo, hi))
            listRow(body, "✅", "Usa come obiettivo dell'acqua", "Imposta ${Nutrition.waterGoalMl(w)} ml al giorno") { Food.setWaterGoal(this, Nutrition.waterGoalMl(w)); toast("Obiettivo dell'acqua impostato") }
            hintLabel(body, "Formula di Mifflin-St Jeor: è una stima. Per dimagrire sono previste il 15% di calorie in meno, mai sotto ${if (p.sex == "m") 1500 else 1200} kcal. Per patologie, gravidanza o obiettivi importanti chiedi al medico.")
        }
        scr.refresh = { render() }
        render()
    }
}
