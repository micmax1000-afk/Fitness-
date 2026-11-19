package com.micmax.cibo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

/** Una schermata dell'app: la vista, cosa fare quando si chiude e come ridisegnarla quando ci si torna. */
class Scr(val view: View) {
    var onClose: (() -> Unit)? = null
    var refresh: (() -> Unit)? = null
    /** Il tasto indietro prima chiede a questa funzione (true = ci ha pensato lei). */
    var back: (() -> Boolean)? = null
}

class MainActivity : AppCompatActivity() {
    val io = Executors.newSingleThreadExecutor()
    private lateinit var host: FrameLayout
    private val stack = ArrayList<Scr>()

    fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    // ---------- Health Connect ----------
    val hcPermission = registerForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { _ -> syncHealthConnect(true) { refreshTop() } }

    // ---------- notifiche ----------
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    /** Da Android 13 le notifiche vanno permesse: serve per i promemoria. */
    fun ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            runCatching { notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }

    // ---------- file ----------
    private var fileCb: ((Uri) -> Unit)? = null
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) fileCb?.invoke(u) }
    fun pickFile(cb: (Uri) -> Unit) { fileCb = cb; runCatching { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) }.onFailure { toast("Non riesco ad aprire i file") } }

    fun shareFile(name: String, mime: String, text: String, chooserTitle: String, pkg: String? = null) {
        runCatching {
            val dir = File(cacheDir, "share").apply { mkdirs() }
            val f = File(dir, name).apply { writeText(text) }
            val u = FileProvider.getUriForFile(this, "$packageName.files", f)
            val i = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (pkg != null) startActivity(i.setPackage(pkg)) else startActivity(Intent.createChooser(i, chooserTitle))
        }.onFailure { toast("Non riesco a condividere") }
    }

    fun shareText(text: String, pkg: String? = null) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        runCatching { if (pkg != null) startActivity(i.setPackage(pkg)) else startActivity(Intent.createChooser(i, "Condividi")) }
            .onFailure { toast(if (pkg != null) "Web Launcher non è installato su questo telefono" else "Non riesco a condividere") }
    }

    // ---------- schermate ----------
    fun push(s: Scr) {
        stack += s
        host.removeAllViews(); host.addView(s.view, FrameLayout.LayoutParams(-1, -1))
    }

    fun pop() {
        if (stack.size <= 1) return
        stack.removeLast().onClose?.invoke()
        keepChosenTheme()
        host.removeAllViews(); host.addView(stack.last().view, FrameLayout.LayoutParams(-1, -1))
        stack.last().refresh?.invoke()
    }

    fun refreshTop() { stack.lastOrNull()?.refresh?.invoke() }

    fun header(title: String, action: String?, onAction: (() -> Unit)?, back: Boolean = true, onBack: (() -> Unit)? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(8), dp(12), dp(8))
        if (back) addView(TextView(this@MainActivity).apply { text = "‹"; textSize = 32f; gravity = Gravity.CENTER; setTextColor(col(R.color.text)); contentDescription = "Indietro"; setOnClickListener { (onBack ?: { pop() })() } }, LinearLayout.LayoutParams(dp(48), dp(44)))
        // titoli lunghi: più piccoli e su due righe, invece di tagliarli
        addView(TextView(this@MainActivity).apply {
            text = title; textSize = if (title.length > 22) 16.5f else 19f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text))
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setLineSpacing(0f, 0.95f)
            if (!back) setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null && Icons.has(action)) addView(iconBox(action, 44).apply {
            background = roundBg(col(R.color.card), 22, col(R.color.stroke)); isClickable = true; contentDescription = "Impostazioni"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES; setOnClickListener { onAction?.invoke() }
        })
        else if (action != null) addView(TextView(this@MainActivity).apply {
            text = action; textSize = 15f; setTextColor(col(R.color.onaccent)); setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = roundBg(col(R.color.accent), 18); setPadding(dp(16), dp(8), dp(16), dp(8)); setOnClickListener { onAction?.invoke() }
        })
    }

    /** Una pagina con titolo e freccia indietro. [build] riempie il contenuto; [Scr.refresh] ridisegna quando ci si torna. */
    fun openScreen(title: String, action: String? = null, onAction: (() -> Unit)? = null, build: (body: LinearLayout, scr: Scr) -> Unit): Scr {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }
        val scr = Scr(root)
        root.addView(header(title, action, onAction))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(24)) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        build(body, scr)
        push(scr)
        return scr
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(themeMode())
        super.onCreate(savedInstanceState)
        host = FrameLayout(this).apply { setBackgroundColor(col(R.color.bg)) }
        setContentView(host)
        push(buildHome())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (stack.lastOrNull()?.back?.invoke() == true) return
                if (stack.size > 1) pop() else finish()
            }
        })
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); handleIntent(intent) }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    /** Tema: 0 come il telefono, 1 chiaro, 2 scuro. */
    fun themeChoice() = getSharedPreferences("cibo", MODE_PRIVATE).getInt("theme", 0)
    private fun themeMode() = when (themeChoice()) { 1 -> AppCompatDelegate.MODE_NIGHT_NO; 2 -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }
    fun setThemeChoice(v: Int) {
        getSharedPreferences("cibo", MODE_PRIVATE).edit().putInt("theme", v).apply()
        AppCompatDelegate.setDefaultNightMode(themeMode())
        recreate()
    }

    /**
     * Android, la prima volta che apre una pagina web (WebView), rimette nelle risorse dell'app il tema del telefono:
     * con «Chiaro» su un telefono scuro i colori diventavano metà chiari e metà scuri. Qui si rimette il tema scelto.
     */
    fun keepChosenTheme() {
        val want = when (themeChoice()) { 1 -> android.content.res.Configuration.UI_MODE_NIGHT_NO; 2 -> android.content.res.Configuration.UI_MODE_NIGHT_YES; else -> return }
        val mask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val cfg = android.content.res.Configuration(resources.configuration)
        if (cfg.uiMode and mask == want) return
        cfg.uiMode = (cfg.uiMode and mask.inv()) or want
        @Suppress("DEPRECATION") resources.updateConfiguration(cfg, resources.displayMetrics)
    }

    override fun onResume() { super.onResume(); keepChosenTheme() }

    var tts: android.speech.tts.TextToSpeech? = null
    fun speak(text: String) {
        if (tts == null) { tts = android.speech.tts.TextToSpeech(this) { st -> if (st == android.speech.tts.TextToSpeech.SUCCESS) { tts?.language = Locale.ITALIAN; tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "step") } }; return }
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "step")
    }
    fun stopSpeaking() { tts?.stop() }

    // ---------- home ----------
    private fun buildHome(): Scr {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }
        val scr = Scr(root)
        root.addView(header("Benessere e cibo", "⚙️", { openSettings() }, back = false))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(24)) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        fun tiles(items: List<Triple<String, String, () -> Unit>>) {
            val grid = android.widget.GridLayout(this).apply { columnCount = 3 }
            items.forEach { (e, t, f) ->
                grid.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = roundBg(col(R.color.card), 16, col(R.color.stroke)); setPadding(dp(4), dp(14), dp(4), dp(12))
                    addView(iconBox(e, 46).apply { (layoutParams as LinearLayout.LayoutParams).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(6) } })
                    addView(TextView(this@MainActivity).apply { text = t; textSize = 13f; gravity = Gravity.CENTER; setTextColor(col(R.color.text)); setTypeface(typeface, android.graphics.Typeface.BOLD) })
                    setOnClickListener { f() }
                }, android.widget.GridLayout.LayoutParams().apply { width = 0; columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f); setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            body.addView(grid)
        }
        fun render() {
            body.removeAllViews()
            val key = Food.dayKey(System.currentTimeMillis())
            val tot = Food.totals(Food.day(Food.load(this), key))
            val target = Food.targets(this)
            val ml = Food.waterMl(this); val goal = Food.waterGoal(this)
            val snap = Hc.cached(this)
            sectionLabel(body, "OGGI")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = roundBg(col(R.color.card), 18, col(R.color.stroke)); setPadding(dp(16), dp(14), dp(16), dp(10))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            }
            // tre anelli: calorie, acqua, passi
            val steps = Hc.today(snap)?.steps ?: 0L
            val stepGoal = getSharedPreferences("cibo", MODE_PRIVATE).getInt("steps_goal", 8000)
            val kcalColor = if (target == null) col(R.color.accent) else if (tot.kcal > target.kcal * 1.1) RED else if (tot.kcal > target.kcal * 0.9) AMBER else col(R.color.accent)
            val rings = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(4)) }
            fun ring(v: View) = rings.addView(v, LinearLayout.LayoutParams(0, -2, 1f))
            ring(ringStat("Calorie", "${tot.kcal.toInt()}", target?.let { "di ${it.kcal}" } ?: "kcal", target?.let { tot.kcal / it.kcal } ?: 0.0, kcalColor) { openDiary() })
            ring(ringStat("Acqua", if (ml >= 1000) String.format(Locale.ITALIAN, "%.1f l", ml / 1000.0) else "$ml ml", "di ${String.format(Locale.ITALIAN, "%.1f", goal / 1000.0)} l", ml.toDouble() / goal, BLUE) { openWater() })
            ring(ringStat("Passi", if (steps > 0) String.format(Locale.ITALIAN, "%,d", steps) else "—", "di ${String.format(Locale.ITALIAN, "%,d", stepGoal)}", steps.toDouble() / stepGoal, 0xFFF59E0B.toInt()) { openSteps() })
            card.addView(rings)
            val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun small(t: String, f: () -> Unit) = btns.addView(TextView(this).apply {
                text = t; textSize = 14f; gravity = Gravity.CENTER; setTextColor(col(R.color.text)); background = roundBg(col(R.color.bg), 14, col(R.color.stroke))
                setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { f() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(6), dp(3), 0) })
            small("💧 +${Food.glass(this)} ml") { Food.addWater(this, Food.glass(this)); render() }
            small("＋ Pasto") { addMealMenu() }
            card.addView(btns)
            body.addView(card)
            // sonno e allenamenti della settimana
            val night = Hc.lastNight(snap)
            val manualSleep = Health.load(this).filter { it.type == "sonno" }.maxByOrNull { it.time }
            val sleepText = night?.let { Hc.duration(it.sleepMin) } ?: manualSleep?.takeIf { System.currentTimeMillis() - it.time < 36 * 3_600_000L }?.let { Hc.duration((it.v1 * 60).toLong()) } ?: "—"
            val n = Fitness.weekCount(Fitness.allTimes(this)); val wgoal = Fitness.weeklyGoal(this)
            body.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                fun tile(icon: String, label: String, value: String, sub: String, onClick: () -> Unit) = addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = roundBg(col(R.color.card), 16, col(R.color.stroke)); isClickable = true; setOnClickListener { onClick() }
                    addView(iconBox(icon, 40).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(10) })
                    addView(LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(col(R.color.muted)) })
                        addView(TextView(this@MainActivity).apply { text = value; textSize = 17f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)) })
                        addView(TextView(this@MainActivity).apply { text = sub; textSize = 11.5f; setTextColor(col(R.color.muted)) })
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
                tile("😴", "Sonno", sleepText, "ultima notte") { openSleep() }
                tile("🏋️", "Allenamenti", "$n / $wgoal", "questa settimana") { openTrain() }
            })
            // farmaci di oggi
            val meds = Meds.load(this).filter { it.times.isNotEmpty() }
            if (meds.isNotEmpty()) {
                sectionLabel(body, "FARMACI DI OGGI")
                meds.forEach { m ->
                    val taken = Meds.takenToday(this, m.id)
                    listRow(body, if (Meds.isLow(m)) "⚠️" else "💊", m.name + if (m.dose.isNotBlank()) " · ${m.dose}" else "",
                        "oggi $taken/${m.times.size} · prossima ${Meds.nextDose(m)}" + if (Meds.isLow(m)) " · scorta quasi finita" else "") {
                        MaterialAlertDialogBuilder(this).setTitle(m.name).setItems(arrayOf("✅ Ho preso la dose", "Apri i farmaci")) { _, w -> if (w == 0) { Meds.markTaken(this, m.id); render() } else openMeds() }.show()
                    }
                }
            }
            // menu di oggi
            val menu = Plan.menu(this); val recipes = Recipes.load(this)
            val today = Plan.MENU_MEALS.mapNotNull { (mk, ml2) -> menu[Plan.menuKey(Plan.todayIndex(), mk)]?.let { id -> recipes.firstOrNull { it.id == id }?.let { ml2 to it } } }
            if (today.isNotEmpty()) {
                sectionLabel(body, "OGGI IN TAVOLA")
                today.forEach { (mm, r) -> listRow(body, "🍳", r.title, mm + (r.time.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")) { showRecipe(r) } }
            }
            sectionLabel(body, "CIBO")
            tiles(listOf(Triple("📒", "Diario") { openDiary() }, Triple("💧", "Acqua") { openWater() }, Triple("🍳", "Ricette") { openRecipes() },
                Triple("📅", "Menu") { openMenu() }, Triple("🛒", "Spesa") { openShopping() }, Triple("🧮", "Fabbisogno") { openCalculator() }))
            sectionLabel(body, "MOVIMENTO")
            tiles(listOf(Triple("👟", "Passi") { openSteps() }, Triple("😴", "Sonno") { openSleep() }, Triple("🏋️", "Allenamento") { openTrain() },
                Triple("🏋️‍♀️", "Schede") { openPlans() }, Triple("⏱", "Tabata") { openTabata() }, Triple("⌚", "Orologio") { openHcConnect() }))
            sectionLabel(body, "SALUTE")
            tiles(listOf(Triple("⚖️", "Peso e misure") { openBody() }, Triple("💊", "Farmaci") { openMeds() }, Triple("🩺", "Visite") { openVisits() },
                Triple("🆘", "Emergenza") { openEmergency() }, Triple("📄", "Referto") { openReport() }))
            if (Food.load(this).isEmpty() && recipes.isEmpty() && Health.load(this).isEmpty()) {
                hintLabel(body, "Hai già dati in Web Launcher (diario, ricette, peso, farmaci, allenamenti)? In ⚙️ → «Importa da Web Launcher» li porti qui.")
            }
        }
        scr.refresh = { render() }
        render()
        syncHealthConnect { if (stack.lastOrNull() === scr) render() }
        return scr
    }

    fun addMealMenu() {
        MaterialAlertDialogBuilder(this).setTitle("Aggiungi al diario")
            .setItems(arrayOf("🔍 Scansiona un prodotto", "✏️ A mano", "🕘 Mangiati di recente")) { _, w ->
                when (w) {
                    0 -> scanFoodBarcode()
                    1 -> addFoodDialog(null)
                    else -> {
                        val r = Food.recents(Food.load(this), 12)
                        if (r.isEmpty()) toast("Ancora niente di recente")
                        else MaterialAlertDialogBuilder(this).setTitle("Mangiati di recente").setItems(r.map { "${it.name} · ${it.kcal.toInt()} kcal" }.toTypedArray()) { _, i ->
                            Food.add(this, r[i].copy(id = java.util.UUID.randomUUID().toString(), time = System.currentTimeMillis(), meal = Food.mealNow()))
                            refreshTop(); toast("Aggiunto")
                        }.show()
                    }
                }
            }.show()
    }

    // ---------- impostazioni ----------
    private fun openSettings() {
        openScreen("Impostazioni") { body, scr ->
            fun render() {
                body.removeAllViews()
                sectionLabel(body, "ASPETTO")
                listRow(body, "🎨", "Tema", listOf("Come il telefono", "Chiaro", "Scuro")[themeChoice()]) {
                    MaterialAlertDialogBuilder(this).setTitle("Tema").setSingleChoiceItems(arrayOf("Come il telefono", "Chiaro", "Scuro"), themeChoice()) { dl, w -> dl.dismiss(); setThemeChoice(w) }.show()
                }
                sectionLabel(body, "IL MIO CORPO")
                listRow(body, "🧮", "Fabbisogno di calorie", Food.targets(this)?.let { "${it.kcal} kcal al giorno" } ?: "Inserisci sesso, età, altezza e peso") { openCalculator() }
                listRow(body, "🔔", "Promemoria attivi", Reminders.load(this).size.toString() + " (farmaci, visite, misure)") { ensureNotificationPermission(); toast("Le notifiche sono permesse se vedi i promemoria arrivare") }
                listRow(body, "🛡", "Allergeni da evitare", Food.avoid(this).takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Li segnalo quando scansioni un prodotto") { avoidDialog { render() } }
                sectionLabel(body, "I MIEI DATI")
                listRow(body, "📥", "Importa da Web Launcher o da un file", "Diario, ricette, peso, farmaci, visite, allenamenti: niente si cancella, i dati si uniscono") { importData() }
                listRow(body, "📤", "Esporta i miei dati", "Un file con tutto: per una copia o per cambiare telefono") {
                    shareFile("cibo-e-ricette.json", "application/json", Backup.export(this), "Esporta i dati")
                }
                sectionLabel(body, "INFO")
                hintLabel(body, "I prodotti vengono da Open Food Facts (gratuito, fatto dagli utenti): controlla sempre l'etichetta. Le calorie e i fabbisogni sono stime: per patologie, gravidanza o obiettivi importanti chiedi al medico.")
            }
            scr.refresh = { render() }
            render()
        }
    }

    fun importData() {
        pickFile { uri ->
            io.execute {
                val text = runCatching { contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
                val r = text?.let { Backup.import(this, it) }
                runOnUiThread {
                    if (r == null) toast("Questo file non contiene dati di Cibo e ricette")
                    else { MaterialAlertDialogBuilder(this).setTitle("✓ Dati importati").setMessage("${r.entries} cibi nel diario, ${r.recipes} ricette, ${r.days} giorni di acqua, ${r.measures} misure, ${r.meds} farmaci, ${r.visits} visite, ${r.acts} allenamenti.").setPositiveButton("Ok", null).show(); refreshTop() }
                }
            }
        }
    }

    fun avoidDialog(done: () -> Unit) {
        val names = OpenFoodFacts.ALLERGENS.values.toList()
        val cur = Food.avoid(this)
        val checked = BooleanArray(names.size) { names[it] in cur }
        MaterialAlertDialogBuilder(this).setTitle("Allergeni da evitare")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Salva") { _, _ -> Food.setAvoid(this, names.filterIndexed { i, _ -> checked[i] }.toSet()); done() }
            .setNegativeButton("Annulla", null).show()
    }

    // ---------- da altre app e prove automatiche ----------
    private fun handleIntent(i: Intent?) {
        i ?: return
        if (i.action == Intent.ACTION_SEND && i.type == "text/plain") {
            val t = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            i.action = null
            val lines = t.lines().map { it.trim().removePrefix("☐").removePrefix("-").removePrefix("•").trim() }.filter { it.isNotEmpty() }
            if (lines.isNotEmpty()) {
                MaterialAlertDialogBuilder(this).setTitle("🛒 Aggiungere alla spesa?").setMessage(lines.take(15).joinToString("\n") + if (lines.size > 15) "\n…" else "")
                    .setPositiveButton("Aggiungi ${lines.size}") { _, _ -> val n = Plan.addToShopping(this, lines); toast("Aggiunte $n voci"); openShopping() }.setNegativeButton("No", null).show()
            }
            return
        }
        i.getStringExtra("open")?.let { o ->
            i.removeExtra("open")
            when (o) { "meds" -> openMeds(); "visits" -> openVisits(); "emergency" -> openEmergency(); "body" -> openBody(); "sleep" -> openSleep(); "water" -> openWater() }
            return
        }
        val action = i.getStringExtra("action") ?: return
        i.removeExtra("action")
        Selftest.run(this, action)
    }

    companion object {
        const val GREEN = 0xFF16A34A.toInt()
        const val AMBER = 0xFFF59E0B.toInt()
        const val RED = 0xFFDC2626.toInt()
        const val BLUE = 0xFF38BDF8.toInt()
    }
}

val GREEN get() = MainActivity.GREEN
val AMBER get() = MainActivity.AMBER
val RED get() = MainActivity.RED
val BLUE get() = MainActivity.BLUE
