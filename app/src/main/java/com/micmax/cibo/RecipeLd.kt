package com.micmax.cibo

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/** Legge la ricetta che i siti mettono nella pagina in formato schema.org (JSON-LD). Funzioni pure, provate con i test. */
object RecipeLd {
    /** [blocks] = il testo di ogni <script type="application/ld+json"> della pagina. */
    fun parse(blocks: List<String>, url: String): Recipe? {
        for (b in blocks) {
            val v = runCatching { JSONTokener(b.trim()).nextValue() }.getOrNull() ?: continue
            val node = findRecipe(v) ?: continue
            val r = toRecipe(node, url)
            if (r != null) return r
        }
        return null
    }

    private fun isRecipe(o: JSONObject): Boolean {
        val t = o.opt("@type")
        return when (t) { is String -> t.equals("Recipe", true); is JSONArray -> (0 until t.length()).any { t.optString(it).equals("Recipe", true) }; else -> false }
    }

    private fun findRecipe(v: Any?): JSONObject? {
        when (v) {
            is JSONObject -> {
                if (isRecipe(v)) return v
                findRecipe(v.opt("@graph"))?.let { return it }
                for (k in v.keys()) { if (k != "@graph") { val x = v.opt(k); if (x is JSONObject || x is JSONArray) findRecipe(x)?.let { return it } } }
            }
            is JSONArray -> for (i in 0 until v.length()) findRecipe(v.opt(i))?.let { return it }
        }
        return null
    }

    private fun toRecipe(o: JSONObject, url: String): Recipe? {
        val title = clean(o.optString("name"))
        val ingredients = strings(o.opt("recipeIngredient") ?: o.opt("ingredients")).map { clean(it) }.filter { it.isNotEmpty() }
        val steps = steps(o.opt("recipeInstructions")).map { clean(it) }.filter { it.isNotEmpty() }
        if (title.isEmpty() || (ingredients.isEmpty() && steps.isEmpty())) return null
        return Recipe(UUID.randomUUID().toString(), title, url, image(o.opt("image")), servings(o.opt("recipeYield")),
            duration(o.optString("totalTime").ifBlank { o.optString("cookTime") }), ingredients, steps, System.currentTimeMillis())
    }

    private fun strings(v: Any?): List<String> = when (v) {
        is String -> listOf(v)
        is JSONArray -> (0 until v.length()).mapNotNull { (v.opt(it) as? String) }
        else -> emptyList()
    }

    private fun steps(v: Any?): List<String> = when (v) {
        is String -> v.split(Regex("\\r?\\n+")).filter { it.isNotBlank() }
        is JSONArray -> (0 until v.length()).flatMap { steps(v.opt(it)) }
        is JSONObject -> when {
            v.has("itemListElement") -> steps(v.opt("itemListElement"))
            v.has("text") -> listOf(v.optString("text"))
            v.has("name") -> listOf(v.optString("name"))
            else -> emptyList()
        }
        else -> emptyList()
    }

    private fun image(v: Any?): String? = when (v) {
        is String -> v
        is JSONArray -> if (v.length() > 0) image(v.opt(0)) else null
        is JSONObject -> v.optString("url").ifBlank { null }
        else -> null
    }?.takeIf { it.startsWith("http") }

    /** «4», «4 persone», ["4","4 porzioni"] → «4» */
    fun servings(v: Any?): String {
        val raw = when (v) { is JSONArray -> if (v.length() > 0) v.opt(0).toString() else ""; null -> ""; else -> v.toString() }
        return Regex("\\d+").find(raw)?.value ?: clean(raw)
    }

    /** «PT1H30M» → «1 h 30 min» */
    fun duration(iso: String): String {
        val m = Regex("^P(?:(\\d+)D)?T?(?:(\\d+)H)?(?:(\\d+)M)?", RegexOption.IGNORE_CASE).find(iso.trim()) ?: return ""
        val d = m.groupValues[1].toIntOrNull() ?: 0; val h = (m.groupValues[2].toIntOrNull() ?: 0) + d * 24; val min = m.groupValues[3].toIntOrNull() ?: 0
        return when { h > 0 && min > 0 -> "$h h $min min"; h > 0 -> "$h h"; min > 0 -> "$min min"; else -> "" }
    }

    private val NAMED = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
        "egrave" to "è", "eacute" to "é", "agrave" to "à", "ograve" to "ò", "ugrave" to "ù", "igrave" to "ì", "deg" to "°", "hellip" to "…")

    /** Toglie i tag HTML, decodifica i caratteri speciali e le spaziature doppie. */
    fun clean(s: String): String {
        var t = s.replace(Regex("<[^>]*>"), " ")
        t = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);").replace(t) { m ->
            val g = m.groupValues[1]
            when {
                g.startsWith("#x") -> g.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                g.startsWith("#") -> g.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> NAMED[g] ?: m.value
            }
        }
        return t.replace(Regex("[\\s\\u00A0]+"), " ").trim()
    }
}
