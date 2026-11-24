package com.micmax.cibo

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// ======================= Ricette =======================

object GialloZafferano {
    fun searchUrl(q: String) = "https://www.giallozafferano.it/ricerca-ricette/" + java.net.URLEncoder.encode(q.trim(), "UTF-8").replace("+", "%20") + "/"
}

fun MainActivity.openRecipes() {
    openScreen("Ricette") { body, scr ->
        fun render() {
            body.removeAllViews()
            val list = Recipes.load(this)
            sectionLabel(body, "NUOVA RICETTA")
            listRow(body, "🔎", "Cerca su Giallo Zafferano", "Si apre qui: con un tocco la salvi nelle tue ricette") {
                val et = field("Cosa vuoi cucinare? (es. carbonara)", null)
                MaterialAlertDialogBuilder(this).setTitle("Giallo Zafferano").setView(form(et)).setPositiveButton("Cerca") { _, _ ->
                    val q = et.text.toString().trim()
                    openRecipeWeb(if (q.isBlank()) "https://www.giallozafferano.it/" else GialloZafferano.searchUrl(q))
                }.setNegativeButton("Annulla", null).show()
            }
            listRow(body, "🌐", "Salva da un altro sito", "Apri la pagina di una ricetta e tocca «Salva ricetta»") { openRecipeWeb("https://www.google.com/search?q=" + java.net.URLEncoder.encode("ricetta", "UTF-8")) }
            listRow(body, "✏️", "Scrivila tu", "Titolo, ingredienti e passaggi") { editRecipe(null) }
            if (list.isEmpty()) { emptyState(body, "🍳", "Ancora nessuna ricetta", "Salvale da Giallo Zafferano o da altri siti, oppure scrivile tu (in alto). Quelle di Web Launcher si portano qui dalle impostazioni → «Importa»."); return }
            sectionLabel(body, "LE MIE RICETTE · ${list.size}")
            list.forEach { r ->
                listRow(body, "🍳", r.title, listOf(r.time, r.servings.takeIf { it.isNotBlank() }?.let { "$it porzioni" }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "${r.ingredients.size} ingredienti" },
                    onLong = { recipeMenu(r) }) { showRecipe(r) }
            }
        }
        scr.refresh = { render() }
        render()
    }
}

private fun MainActivity.recipeMenu(r: Recipe) {
    MaterialAlertDialogBuilder(this).setTitle(r.title).setItems(arrayOf("Apri", "Modifica", "Elimina")) { _, w ->
        when (w) {
            0 -> showRecipe(r)
            1 -> editRecipe(r)
            2 -> MaterialAlertDialogBuilder(this).setTitle("Eliminare «${r.title}»?").setPositiveButton("Elimina") { _, _ -> Recipes.remove(this, r.id); refreshTop() }.setNegativeButton("Annulla", null).show()
        }
    }.show()
}

/** Scrivere o modificare una ricetta: un ingrediente o un passaggio per riga. */
fun MainActivity.editRecipe(r: Recipe?) {
    var save: () -> Unit = {}
    openScreen(if (r == null) "Nuova ricetta" else "Modifica ricetta", "Salva", { save() }) { body, _ ->
        val title = field("Titolo", r?.title)
        val servings = field("Per quante persone", r?.servings, number = true)
        val time = field("Tempo (es. 30 min)", r?.time)
        val ing = field("Ingredienti: uno per riga", r?.ingredients?.joinToString("\n"), multiline = true)
        val steps = field("Procedimento: un passaggio per riga", r?.steps?.joinToString("\n"), multiline = true)
        listOf(title, servings, time, ing, steps).forEach { body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }) }
        save = {
            if (title.text.toString().isBlank()) toast("Scrivi il titolo")
            else {
                val n = Recipes.new(title.text.toString(), servings.text.toString(), time.text.toString(), ing.text.toString().lines(), steps.text.toString().lines(), r?.url.orEmpty(), r?.image)
                Recipes.add(this, if (r != null) n.copy(id = r.id, saved = r.saved) else n)
                pop(); refreshTop(); toast("✓ Ricetta salvata")
            }
        }
    }
}

fun MainActivity.showRecipe(r: Recipe) {
    openScreen(r.title.take(30)) { body, _ ->
        r.image?.let { img ->
            body.addView(ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = roundBg(col(R.color.stroke), 14); clipToOutline = true; Img.load(img, this) },
                LinearLayout.LayoutParams(-1, dp(190)).apply { setMargins(dp(6), 0, dp(6), dp(8)) })
        }
        body.addView(TextView(this).apply { text = r.title; textSize = 22f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(col(R.color.text)); setPadding(dp(8), 0, dp(8), dp(4)) })
        body.addView(TextView(this).apply {
            text = listOf(r.time, r.servings.takeIf { it.isNotBlank() }?.let { "$it porzioni" } ?: "").filter { it.isNotBlank() }.joinToString(" · ")
            textSize = 14f; setTextColor(col(R.color.muted)); setPadding(dp(8), 0, dp(8), dp(8))
        })
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), 0, dp(6), 0) }
        btns.addView(MaterialButton(this).apply { text = "👨‍🍳 Modalità cucina"; isAllCaps = false; layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }; setOnClickListener { cookingMode(r) } })
        btns.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "🛒 Alla lista"; isAllCaps = false; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setOnClickListener {
                val checked = BooleanArray(r.ingredients.size) { true }
                MaterialAlertDialogBuilder(this@showRecipe).setTitle("Cosa ti manca?").setMultiChoiceItems(r.ingredients.toTypedArray(), checked) { _, i, c -> checked[i] = c }
                    .setPositiveButton("Aggiungi") { _, _ -> toast("🛒 Aggiunte ${Plan.addToShopping(this@showRecipe, r.ingredients.filterIndexed { i, _ -> checked[i] })} voci alla spesa") }.setNegativeButton("Annulla", null).show()
            }
        })
        body.addView(btns)
        sectionLabel(body, "INGREDIENTI · ${r.ingredients.size}")
        r.ingredients.forEach { ing -> body.addView(CheckBox(this).apply { text = ing; textSize = 16f; setTextColor(col(R.color.text)); setPadding(dp(4), dp(2), 0, dp(2)) }) }
        sectionLabel(body, "PROCEDIMENTO")
        r.steps.forEachIndexed { i, st -> body.addView(TextView(this).apply { text = "${i + 1}.  $st"; textSize = 16f; setTextColor(col(R.color.text)); setLineSpacing(0f, 1.2f); setPadding(dp(10), dp(6), dp(8), dp(6)) }) }
        if (r.url.isNotBlank()) body.addView(TextView(this).apply {
            text = "Fonte: ${r.url.substringAfter("://").substringBefore('/')} ›"; textSize = 13f; setPadding(dp(10), dp(14), 0, 0); setTextColor(col(R.color.accent))
            setOnClickListener { openRecipeWeb(r.url) }
        })
    }
}

/** Passo dopo passo, con testo grande e lo schermo sempre acceso; il 🔊 legge il passaggio ad alta voce. */
fun MainActivity.cookingMode(r: Recipe) {
    if (r.steps.isEmpty()) { toast("Questa ricetta non ha passaggi"); return }
    var step = 0
    val dialog = androidx.appcompat.app.AppCompatDialog(this, R.style.Theme_Cibo)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF111317.toInt()); setPadding(dp(22), dp(20), dp(22), dp(20)) }
    val counter = TextView(this).apply { textSize = 16f; setTextColor(0xFF9AA0A8.toInt()) }
    val stepText = TextView(this).apply { textSize = 28f; setTextColor(android.graphics.Color.WHITE); setLineSpacing(0f, 1.25f) }
    val scroll = android.widget.ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f); addView(stepText) }
    fun show() { counter.text = "Passaggio ${step + 1} di ${r.steps.size} · ${r.title}"; stepText.text = r.steps[step] }
    val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    fun big(label: String, on: () -> Unit) = nav.addView(MaterialButton(this).apply {
        text = label; textSize = 20f; isAllCaps = false; layoutParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply { setMargins(dp(4), 0, dp(4), 0) }; setOnClickListener { on() }
    })
    big("‹ Indietro") { if (step > 0) { step--; show() } }
    big("🔊") { speak(r.steps[step]) }
    big("Avanti ›") { if (step < r.steps.lastIndex) { step++; show() } else { stopSpeaking(); dialog.dismiss(); toast("Buon appetito! 🍽") } }
    root.addView(counter); root.addView(scroll); root.addView(nav)
    dialog.setContentView(root)
    dialog.window?.apply { setLayout(-1, -1); addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    dialog.setOnDismissListener { stopSpeaking() }
    dialog.show()
    show()
}

/** Naviga un sito di ricette; «Salva ricetta» legge la ricetta dalla pagina (formato schema.org, senza AI). */
@SuppressLint("SetJavaScriptEnabled")
fun MainActivity.openRecipeWeb(startUrl: String) {
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }
    val scr = Scr(root)
    val web = WebView(this)
    keepChosenTheme()   // la prima pagina web rimette il tema del telefono: si torna a quello scelto
    web.settings.javaScriptEnabled = true; web.settings.domStorageEnabled = true
    web.webViewClient = WebViewClient()
    fun save() {
        val url = web.url.orEmpty()
        web.evaluateJavascript("JSON.stringify(Array.from(document.querySelectorAll('script[type=\"application/ld+json\"]')).map(function(s){return s.textContent}))") { raw ->
            val blocks = runCatching { org.json.JSONArray(org.json.JSONTokener(raw).nextValue() as String) }.getOrNull()?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val r = RecipeLd.parse(blocks, url)
            if (r == null) toast("In questa pagina non trovo una ricetta: aprine una e riprova")
            else if (Recipes.load(this).any { it.url == url }) toast("L'hai già salvata")
            else { Recipes.add(this, r); toast("✓ Salvata: ${r.title.take(40)}"); MaterialAlertDialogBuilder(this).setTitle("Ricetta salvata").setMessage(r.title).setPositiveButton("Aprila") { _, _ -> pop(); showRecipe(r) }.setNegativeButton("Continua a cercare", null).show() }
        }
    }
    root.addView(header("Ricette dal web", "💾 Salva ricetta", { save() }, onBack = { if (web.canGoBack()) web.goBack() else pop() }))
    root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
    scr.back = { if (web.canGoBack()) { web.goBack(); true } else false }
    scr.onClose = { runCatching { web.stopLoading(); web.destroy() } }
    push(scr)
    web.loadUrl(startUrl)
}

// ======================= Menu della settimana =======================

fun MainActivity.openMenu() {
    openScreen("Menu della settimana", "🛒 Spesa", { menuToShopping() }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val menu = Plan.menu(this); val recipes = Recipes.load(this)
            hintLabel(body, "Scegli cosa cucinare ogni giorno: il menu resta uguale ogni settimana finché non lo cambi. «Spesa» mette gli ingredienti nella lista.")
            if (recipes.isEmpty()) { hintLabel(body, "Prima servono delle ricette: aggiungile da Ricette."); return }
            Plan.DAYS.forEachIndexed { d, day ->
                sectionLabel(body, day.uppercase() + if (d == Plan.todayIndex()) " · OGGI" else "")
                Plan.MENU_MEALS.forEach { (mk, ml) ->
                    val r = menu[Plan.menuKey(d, mk)]?.let { id -> recipes.firstOrNull { it.id == id } }
                    listRow(body, if (mk == "pranzo") "🍝" else "🍽", ml, r?.title ?: "Scegli una ricetta", onLong = { if (r != null) showRecipe(r) }) {
                        val names = listOf("— Niente —") + recipes.map { it.title }
                        MaterialAlertDialogBuilder(this).setTitle("$day · $ml").setItems(names.toTypedArray()) { _, w ->
                            val m = Plan.menu(this).toMutableMap()
                            if (w == 0) m.remove(Plan.menuKey(d, mk)) else m[Plan.menuKey(d, mk)] = recipes[w - 1].id
                            Plan.saveMenu(this, m); render()
                        }.show()
                    }
                }
            }
        }
        scr.refresh = { render() }
        render()
    }
}

private fun MainActivity.menuToShopping() {
    val menu = Plan.menu(this); val recipes = Recipes.load(this)
    val used = menu.values.distinct().mapNotNull { id -> recipes.firstOrNull { it.id == id } }
    if (used.isEmpty()) { toast("Il menu è vuoto"); return }
    val n = Plan.addToShopping(this, used.flatMap { it.ingredients })
    toast("🛒 Aggiunte $n voci alla spesa"); openShopping()
}

// ======================= Spesa =======================

fun MainActivity.openShopping() {
    openScreen("Lista della spesa", "Condividi", { shareList() }) { body, scr ->
        fun render() {
            body.removeAllViews()
            val items = Plan.shopping(this)
            val et = field("Aggiungi: latte, pane…", null)
            et.setSingleLine(); et.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            fun add() { val t = et.text.toString().trim(); if (t.isNotEmpty()) { Plan.addToShopping(this, listOf(t)); render() } }
            et.setOnEditorActionListener { _, _, _ -> add(); true }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(et, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(MaterialButton(this).apply { text = "＋"; setOnClickListener { add() } })
            body.addView(row)
            if (items.isEmpty()) { hintLabel(body, "La lista è vuota. Dalle ricette con «Alla lista», dal Menu con «Spesa», oppure scrivi qui sopra."); return }
            items.sortedBy { it.done }.forEach { it0 ->
                body.addView(CheckBox(this).apply {
                    text = it0.text; textSize = 17f; isChecked = it0.done; setTextColor(col(if (it0.done) R.color.muted else R.color.text))
                    paintFlags = if (it0.done) paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    setPadding(dp(4), dp(8), 0, dp(8))
                    setOnCheckedChangeListener { _, on -> Plan.saveShopping(this@openShopping, Plan.shopping(this@openShopping).map { if (it.id == it0.id) it.copy(done = on) else it }); render() }
                    setOnLongClickListener { Plan.saveShopping(this@openShopping, Plan.shopping(this@openShopping).filter { it.id != it0.id }); render(); true }
                })
            }
            if (items.any { it.done }) body.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Togli quelli presi"; isAllCaps = false; setOnClickListener { Plan.saveShopping(this@openShopping, Plan.shopping(this@openShopping).filter { !it.done }); render() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            hintLabel(body, "Tieni premuta una voce per toglierla.")
        }
        scr.refresh = { render() }
        render()
    }
}

private fun MainActivity.shareList() {
    val items = Plan.shopping(this).filter { !it.done }
    if (items.isEmpty()) { toast("Non c'è niente da condividere"); return }
    val text = "🛒 Lista della spesa\n" + items.joinToString("\n") { "☐ " + it.text }
    MaterialAlertDialogBuilder(this).setTitle("Condividi la lista").setItems(arrayOf("📤 Con un'altra app (WhatsApp, note…)", "🌐 Con Web Launcher")) { _, w ->
        if (w == 0) shareText(text) else shareText(text, "com.weblauncher.app")
    }.show()
}
