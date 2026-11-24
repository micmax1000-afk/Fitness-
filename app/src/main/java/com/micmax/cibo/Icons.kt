package com.micmax.cibo

/**
 * Le icone dell'app, disegnate a linee (griglia 24×24), al posto delle emoji che cambiano da un telefono all'altro.
 * Comandi come negli SVG (M L H V C Q Z, maiuscoli = assoluti, minuscoli = relativi) più due comodi:
 * «O cx cy r» = cerchio, «R x y w h r» = rettangolo con gli angoli arrotondati.
 */
object Icons {
    private const val DROP = "M12 3C12 3 5 10.5 5 14.5C5 18.4 8.1 21 12 21C15.9 21 19 18.4 19 14.5C19 10.5 12 3 12 3Z"
    private const val HEART = "M12 20C7 16.5 3.5 13.5 3.5 9.2C3.5 6.5 5.6 4.5 8.1 4.5C9.7 4.5 11.1 5.3 12 6.6C12.9 5.3 14.3 4.5 15.9 4.5C18.4 4.5 20.5 6.5 20.5 9.2C20.5 13.5 17 16.5 12 20Z"
    private const val BELL = "M6 16.5V11C6 7.7 8.7 5 12 5C15.3 5 18 7.7 18 11V16.5L19.5 18H4.5ZM10 20.5H14M12 3V5"
    private const val CLIP = "R5 4 14 17.5 2M9 2.5H15V5.5H9ZM8.5 10H15.5M8.5 13.5H15.5M8.5 17H13"
    private const val IMPORT = "M12 3V15M7.5 10.5L12 15L16.5 10.5M4 15V20H20V15"
    private const val LOCK_BODY = "R5 10.5 14 10.5 2M8 10.5V7.5C8 5.3 9.8 3.5 12 3.5"

    val PATHS: Map<String, String> = mapOf(
        // cibo
        "🍽" to "O12 12 6.5O12 12 3M3 4V9.5M3 9.5V20M1.5 4V8C1.5 9 2 9.5 3 9.5C4 9.5 4.5 9 4.5 8V4M21.5 4C19.5 5 19.5 10 21.5 11V20",
        "🍝" to "M3 12H21C21 17 17 20 12 20C7 20 3 17 3 12ZM9 9C9 7 11 6 11 4M13 9C13 7 15 6 15 4",
        "🥐" to "M4 9H16V14C16 17 14 19 10 19C6 19 4 17 4 14ZM16 11H18C20.3 11 20.3 15 18 15H16M8 2.5C8 4.5 10 4.5 10 6.5",
        "🍎" to "M12 7C9 5 4 6 4 12C4 17 8 21.5 12 19.5C16 21.5 20 17 20 12C20 6 15 5 12 7ZM12 7C12 5 13 3 15 2.5",
        "📒" to "R5 3 14 18 2M9 3V21M12.5 8H16M12.5 11.5H16",
        "💧" to DROP, "🩸" to DROP,
        "🥛" to "M6 3H18L16.5 21H7.5ZM6.7 10H17.3",
        "🍼" to "R7.5 8.5 9 13 2.5M10 8.5V5.5H14V8.5M9.5 4H14.5M7.5 13H11M7.5 16.5H11",
        "🍳" to "O9.5 12.5 6.5O9.5 12.5 2.2M16 12.5H22.5",
        "🛒" to "M2 4H5L7.5 15H18L20.5 7H6O9 19.5 1.5O17 19.5 1.5",
        "🧮" to "R5 2.5 14 19 2R8 5.5 8 4 1M9 14H9.01M12 14H12.01M15 14H15.01M9 17.5H9.01M12 17.5H12.01M15 17.5H15.01",
        "📅" to "R3 5 18 16 2M3 10H21M8 3V7M16 3V7M7.5 14H7.51M12 14H12.01M16.5 14H16.51M7.5 17.5H7.51M12 17.5H12.01",
        "🔎" to "O10.5 10.5 6.5M15.5 15.5L20.5 20.5", "🔍" to "O10.5 10.5 6.5M15.5 15.5L20.5 20.5",
        "🌐" to "O12 12 8.5M3.5 12H20.5M12 3.5C9.5 6 8.5 9 8.5 12C8.5 15 9.5 18 12 20.5C14.5 18 15.5 15 15.5 12C15.5 9 14.5 6 12 3.5",
        "✏️" to "M15 4.5L19.5 9L8.5 20H4V15.5ZM13 6.5L17.5 11",
        "📝" to "M13 4H5.5C4.7 4 4 4.7 4 5.5V18.5C4 19.3 4.7 20 5.5 20H18.5C19.3 20 20 19.3 20 18.5V11M18.5 2.8L21.2 5.5L13 13.7L9.8 14.2L10.3 11Z",
        "📖" to "M12 6C10 4.5 7 4 3 4.5V19C7 18.5 10 19 12 20.5C14 19 17 18.5 21 19V4.5C17 4 14 4.5 12 6ZM12 6V20.5",
        // movimento
        "👟" to "M7.5 3C9.6 3 10.5 5.8 10.5 8.6C10.5 11 9.3 12.5 7.8 12.5C6.2 12.5 5 11 5 9C5 5.8 5.8 3 7.5 3ZM5.4 15H10.1C10.1 17.6 9.2 19.5 7.7 19.5C6.3 19.5 5.4 17.6 5.4 15ZM16.5 5C18.2 5 19 7.8 19 11C19 13 17.8 14.5 16.2 14.5C14.7 14.5 13.5 13 13.5 10.6C13.5 7.8 14.4 5 16.5 5ZM13.9 17H18.6C18.6 19.6 17.7 21.5 16.3 21.5C14.8 21.5 13.9 19.6 13.9 17Z",
        "😴" to "M20 14.5C18.8 15.1 17.4 15.5 16 15.5C11.5 15.5 8.5 12.2 8.5 8C8.5 6.6 8.9 5.2 9.5 4C6.2 5 4 8.2 4 12C4 16.4 7.6 20 12 20C15.8 20 19 17.8 20 14.5ZM14.5 3.5H18.5L14.5 8H18.5",
        "🏋" to "M6.5 7V17M3.5 9.5V14.5M17.5 7V17M20.5 9.5V14.5M6.5 12H17.5",
        "🏋\u200D♀" to CLIP, "📋" to CLIP,
        "⏱" to "O12 13.5 7.5M12 13.5V9.5M10 2.5H14M12 2.5V6M18.3 6.8L19.8 5.3",
        "⌚" to "R6.5 6.5 11 11 3M9 6.5L9.5 2.5H14.5L15 6.5M9 17.5L9.5 21.5H14.5L15 17.5M12 9.5V12L13.5 13.5",
        "🏅" to "O12 15 5.5M8.5 3L10.5 9.8M15.5 3L13.5 9.8M12 12.3L12.9 14.1L14.8 14.4L13.4 15.7L13.8 17.6L12 16.7L10.2 17.6L10.6 15.7L9.2 14.4L11.1 14.1Z",
        "🏃" to "O15 4.5 2M5.5 11L8.5 8.5H13L15.5 12H19M13 8.5L10.5 15L6 17M10.5 15L14 17.5L13 21.5",
        "✨" to "M11 3C11.6 7.6 13.4 9.4 18 10C13.4 10.6 11.6 12.4 11 17C10.4 12.4 8.6 10.6 4 10C8.6 9.4 10.4 7.6 11 3ZM18.5 15V21M15.5 18H21.5",
        "💬" to "M4 5.5C4 4.7 4.7 4 5.5 4H18.5C19.3 4 20 4.7 20 5.5V15C20 15.8 19.3 16.5 18.5 16.5H10L6 20V16.5H5.5C4.7 16.5 4 15.8 4 15ZM8 9H16M8 12.5H13",
        "🎯" to "O12 12 8.5O12 12 4.5M12 12H12.01",
        // salute
        "⚖" to "R3.5 3.5 17 17 4M8 9.5C9.5 7.3 14.5 7.3 16 9.5M12 9.5L13.6 7.7",
        "💊" to "M4.9 12.1L12.1 4.9C14.1 2.9 17.1 2.9 19.1 4.9C21.1 6.9 21.1 9.9 19.1 11.9L11.9 19.1C9.9 21.1 6.9 21.1 4.9 19.1C2.9 17.1 2.9 14.1 4.9 12.1ZM8.5 8.5L15.5 15.5",
        "🩺" to "M6 3V9C6 12 8 14 10 14C12 14 14 12 14 9V3M10 14V16C10 19 12 21 15 21C18 21 19 19 19 16V14O19 12 2",
        "🆘" to "O12 12 8.5O12 12 3.5M6 6L9.5 9.5M18 6L14.5 9.5M6 18L9.5 14.5M18 18L14.5 14.5",
        "📄" to "M6 2.5H14L19 7.5V21.5H6ZM14 2.5V7.5H19M9 12H16M9 15.5H16",
        "❤" to HEART,
        "💓" to "$HEART M7 11.5H9.5L10.8 9L12.8 14L14.2 11.5H17",
        "🌡" to "M10 14V5C10 3.9 10.9 3 12 3C13.1 3 14 3.9 14 5V14C15.3 14.8 16 16 16 17.5C16 19.7 14.2 21.5 12 21.5C9.8 21.5 8 19.7 8 17.5C8 16 8.7 14.8 10 14ZM12 17.5V9",
        "📏" to "R2.5 8 19 8 1.5M6.5 8V11M10 8V12.5M13.5 8V11M17 8V12.5",
        "⚠" to "M12 3.5L21.5 20H2.5ZM12 10V14M12 17V17.01",
        // impostazioni e altro
        "⚙" to "M4 7H12.5M17.5 7H20O15 7 2.2M4 17H6.5M11.5 17H20O9 17 2.2",
        "🎨" to "M12 3C7 3 3 6.8 3 11.5C3 16.5 7 20.5 12 20.5C13.2 20.5 13.8 19.6 13.5 18.5C13.1 17.2 14 16 15.3 16H17C19.4 16 21 14.2 21 11.8C21 6.9 17 3 12 3ZM7.5 11.5H7.51M10 7.5H10.01M14.5 7.5H14.51M17 11.5H17.01",
        "🔔" to BELL, "🔕" to "$BELL M3.5 3.5L20.5 20.5",
        "⏰" to "O12 13 7.5M12 9V13L14.5 15M3.8 5.5L6.5 2.8M20.2 5.5L17.5 2.8",
        "🛡" to "M12 2.5L19.5 5.5V11C19.5 15.5 16.5 19.5 12 21.5C7.5 19.5 4.5 15.5 4.5 11V5.5ZM8.5 12L11 14.5L15.5 9.5",
        "📥" to IMPORT, "⬇" to IMPORT,
        "📤" to "M12 15V3M7.5 7.5L12 3L16.5 7.5M4 15V20H20V15",
        "👤" to "O12 8 4M4.5 20.5C4.5 16.5 7.8 14 12 14C16.2 14 19.5 16.5 19.5 20.5",
        "🎂" to "R4 11 16 9.5 2M4 15C6 16.5 8 16.5 10 15C12 13.5 14 13.5 16 15C18 16.5 19 16 20 15M8 11V8M12 11V8M16 11V8M8 4.5V5.5M12 4.5V5.5M16 4.5V5.5",
        "✅" to "O12 12 8.5M8 12.5L11 15.5L16.5 9",
        "🔒" to "$LOCK_BODY C14.2 3.5 16 5.3 16 7.5V10.5",
        "🔓" to "$LOCK_BODY C13.8 3.5 15.3 4.7 15.8 6.3",
        "🔥" to "M12 21C8.1 21 5.5 18.4 5.5 15C5.5 10.5 10 9 10 3.5C13.5 5.5 15 8.5 14.5 11.5C15.5 11 16.3 10 16.5 8.5C18 10 18.5 12.5 18.5 15C18.5 18.4 15.9 21 12 21Z",
    )

    /** Numeri dentro un cerchio (1️⃣, 2️⃣…): si scrivono, non si disegnano. */
    fun digit(e: String): String? = e.firstOrNull()?.takeIf { it.isDigit() && e.contains('⃣') }?.toString()

    /** La chiave per un'emoji, senza i caratteri invisibili che la colorano. */
    fun key(e: String): String = e.replace("️", "").trim()
    fun has(e: String) = PATHS.containsKey(key(e)) || digit(e) != null

    /** Il colore che l'icona prende da sola (null = quello dell'app). */
    fun tint(e: String): Int? = when (key(e)) {
        "💧", "🥛", "🍼" -> 0xFF38BDF8.toInt()
        "😴" -> 0xFF8B8CF6.toInt()
        "⚠", "🔥", "🎂" -> 0xFFF59E0B.toInt()
        "🆘", "❤", "💓", "🩸" -> 0xFFEF4444.toInt()
        else -> null
    }

    /** Legge i comandi del disegno: per ogni pezzo (lettera, numeri). Senza Android, per provarlo nei test. */
    fun commands(d: String): List<Pair<Char, FloatArray>> {
        val out = ArrayList<Pair<Char, FloatArray>>()
        val re = Regex("([MLHVCQZOR])([^MLHVCQZOR]*)", RegexOption.IGNORE_CASE)
        re.findAll(d).forEach { m ->
            val nums = Regex("-?\\d*\\.?\\d+").findAll(m.groupValues[2]).map { it.value.toFloat() }.toList().toFloatArray()
            out += m.groupValues[1][0] to nums
        }
        return out
    }
}
