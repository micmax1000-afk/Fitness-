package com.micmax.cibo

import java.text.Normalizer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Una posa dell'omino che mostra l'esercizio. Angoli in gradi, assoluti: 0 = verso il basso, 90 = in avanti (a destra), 180 = in alto, -90 = indietro.
 * r = braccio e gamba dal lato di chi guarda (colore pieno), l = quelli dall'altro lato (più chiari).
 * [arch]: quanto si curva la schiena (+ = gobba, − = schiena inarcata). [head]: la testa rispetto al busto.
 */
data class Pose(
    val torso: Float = 180f, val head: Float = 0f, val arch: Float = 0f,
    val rUa: Float = 0f, val rFa: Float = 0f, val lUa: Float = 0f, val lFa: Float = 0f,
    val rTh: Float = 0f, val rSh: Float = 0f, val lTh: Float = 0f, val lSh: Float = 0f,
)

/** Un esercizio del catalogo: come si chiama, come si fa, a cosa serve e i fotogrammi del disegno. */
data class Move(
    val id: String, val name: String, val keys: List<String>, val tags: Set<String>, val how: String,
    val frames: List<Pose>,
    val reps: Int = 0, val secs: Int = 0, val perSide: Boolean = false,
    val minLevel: Int = 1, val maxLevel: Int = 3,
    /** Vista di fronte (per jumping jack, inclinazioni laterali…); altrimenti di profilo, rivolto a destra. */
    val front: Boolean = false,
    /** Braccia che seguono il busto (gli angoli delle braccia si sommano alla sua inclinazione). */
    val armsFollow: Boolean = false,
    val anchor: Anchor = Anchor.FEET, val prop: Prop = Prop.NONE,
) {
    val timed get() = secs > 0
}

enum class Anchor { FEET, HANDS, CENTER }
enum class Prop { NONE, WALL_FRONT, WALL_BACK }

/** Catalogo degli esercizi con i disegni, e il generatore di schede. Niente Android qui: si prova con i test. */
object Moves {
    // ---------- l'omino ----------
    const val TORSO = 28f; const val UA = 15f; const val FA = 14f; const val TH = 20f; const val SH = 20f; const val HEAD_R = 6.5f
    const val FLOOR = 90f

    // Indici dei punti (x, y) restituiti da [figure].
    const val HEAD = 0; const val NECK = 1; const val HIP = 2
    const val R_SHO = 3; const val R_ELB = 4; const val R_HAND = 5; const val L_SHO = 6; const val L_ELB = 7; const val L_HAND = 8
    const val R_HIP = 9; const val R_KNEE = 10; const val R_FOOT = 11; const val L_HIP = 12; const val L_KNEE = 13; const val L_FOOT = 14
    const val SPINE = 15   // punto di controllo della curva della schiena
    const val POINTS = 16

    private fun rad(a: Float) = Math.toRadians(a.toDouble())
    private fun dx(a: Float) = sin(rad(a)).toFloat()
    private fun dy(a: Float) = cos(rad(a)).toFloat()

    /** Differenza d'angolo più corta, così una rotazione completa (0 → 90 → 180 → 270 → 0) gira sempre nello stesso verso. */
    private fun lerpAngle(a: Float, b: Float, t: Float): Float { val d = ((b - a + 540f) % 360f) - 180f; return a + d * t }
    fun lerp(a: Pose, b: Pose, t: Float) = Pose(
        lerpAngle(a.torso, b.torso, t), lerpAngle(a.head, b.head, t), a.arch + (b.arch - a.arch) * t,
        lerpAngle(a.rUa, b.rUa, t), lerpAngle(a.rFa, b.rFa, t), lerpAngle(a.lUa, b.lUa, t), lerpAngle(a.lFa, b.lFa, t),
        lerpAngle(a.rTh, b.rTh, t), lerpAngle(a.rSh, b.rSh, t), lerpAngle(a.lTh, b.lTh, t), lerpAngle(a.lSh, b.lSh, t),
    )

    /** Punti dell'omino nella posa [p], con il bacino in (0, 0). */
    fun raw(p: Pose, front: Boolean, armsFollow: Boolean): FloatArray {
        val o = FloatArray(POINTS * 2)
        fun set(i: Int, x: Float, y: Float) { o[i * 2] = x; o[i * 2 + 1] = y }
        fun x(i: Int) = o[i * 2]
        fun y(i: Int) = o[i * 2 + 1]
        set(HIP, 0f, 0f)
        set(NECK, TORSO * dx(p.torso), TORSO * dy(p.torso))
        // lato della pancia: perpendicolare al busto
        val bx = dx(p.torso - 90f); val by = dy(p.torso - 90f)
        set(SPINE, x(NECK) / 2 - bx * p.arch * 2, y(NECK) / 2 - by * p.arch * 2)
        set(HEAD, x(NECK) + 9f * dx(p.torso + p.head), y(NECK) + 9f * dy(p.torso + p.head))
        // di fronte spalle e anche sono larghe; di profilo stanno una sopra l'altra
        val sw = if (front) 8f else 0f; val hw = if (front) 5f else 0f
        val px = dx(p.torso - 90f); val py = dy(p.torso - 90f)
        set(R_SHO, x(NECK) + px * sw, y(NECK) + py * sw); set(L_SHO, x(NECK) - px * sw, y(NECK) - py * sw)
        set(R_HIP, px * hw, py * hw); set(L_HIP, -px * hw, -py * hw)
        val ar = if (armsFollow) p.torso - 180f else 0f
        fun limb(from: Int, mid: Int, end: Int, a1: Float, l1: Float, a2: Float, l2: Float) {
            set(mid, x(from) + l1 * dx(a1), y(from) + l1 * dy(a1)); set(end, x(mid) + l2 * dx(a2), y(mid) + l2 * dy(a2))
        }
        limb(R_SHO, R_ELB, R_HAND, p.rUa + ar, UA, p.rFa + ar, FA); limb(L_SHO, L_ELB, L_HAND, p.lUa + ar, UA, p.lFa + ar, FA)
        limb(R_HIP, R_KNEE, R_FOOT, p.rTh, TH, p.rSh, SH); limb(L_HIP, L_KNEE, L_FOOT, p.lTh, TH, p.lSh, SH)
        return o
    }

    private fun lowest(o: FloatArray): Float {
        var m = o[HEAD * 2 + 1] + HEAD_R
        for (i in 1 until SPINE) m = max(m, o[i * 2 + 1])
        return m
    }
    private fun anchorX(m: Move, o: FloatArray): Float = when (m.anchor) {
        Anchor.FEET -> (o[R_FOOT * 2] + o[L_FOOT * 2]) / 2
        Anchor.HANDS -> (o[R_HAND * 2] + o[L_HAND * 2]) / 2
        Anchor.CENTER -> { var a = Float.MAX_VALUE; var b = -Float.MAX_VALUE; for (i in 0 until SPINE) { a = min(a, o[i * 2]); b = max(b, o[i * 2]) }; (a + b) / 2 }
    }

    /** Dove sta il punto d'appoggio nel riquadro 100×100: si sceglie una volta (con tutti i fotogrammi) così l'omino non esce dal riquadro. */
    private class Fit(val x: Float, val scale: Float)
    private val fitCache = HashMap<String, Fit>()
    private fun fit(m: Move): Fit = synchronized(fitCache) { fitCache.getOrPut(m.id) {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var top = 0f
        for (k in 0 until m.frames.size * 8) {
            val o = raw(pose(m, k / 8f), m.front, m.armsFollow); val ax = anchorX(m, o); val low = lowest(o)
            for (i in 0 until SPINE) { lo = min(lo, o[i * 2] - ax); hi = max(hi, o[i * 2] - ax); top = max(top, low - o[i * 2 + 1]) }
            lo = min(lo, o[HEAD * 2] - HEAD_R - ax); hi = max(hi, o[HEAD * 2] + HEAD_R - ax); top = max(top, low - o[HEAD * 2 + 1] + HEAD_R)
        }
        // tutto dentro il riquadro: al massimo 92 di larghezza e 84 di altezza
        Fit(-(lo + hi) / 2, min(1f, min(92f / (hi - lo), 84f / top)))
    } }

    /** La posa al tempo [t] (in fotogrammi, ciclico): si ferma un attimo su ogni fotogramma e si muove dolcemente tra uno e l'altro. */
    fun pose(m: Move, t: Float): Pose {
        val n = m.frames.size
        if (n == 1) return m.frames[0]
        val tt = ((t % n) + n) % n
        val i = tt.toInt() % n; val u = tt - tt.toInt()
        val k = ((u - 0.2f) / 0.6f).coerceIn(0f, 1f)
        return lerp(m.frames[i], m.frames[(i + 1) % n], k * k * (3 - 2 * k))
    }

    /** I punti da disegnare nel riquadro 100×100, col pavimento a y = [FLOOR]. */
    fun scale(m: Move) = fit(m).scale
    fun figure(m: Move, t: Float): FloatArray {
        val o = raw(pose(m, t), m.front, m.armsFollow); val f = fit(m)
        val ax = anchorX(m, o); val low = lowest(o)
        for (i in 0 until POINTS) { o[i * 2] = 50f + (o[i * 2] - ax + f.x) * f.scale; o[i * 2 + 1] = FLOOR - (low - o[i * 2 + 1]) * f.scale }
        return o
    }

    // ---------- il catalogo ----------

    private val HANDS_HIPS = Pose(rUa = -35f, rFa = 32f, lUa = -30f, lFa = 35f)
    private val LUNGE = Pose(rUa = -35f, rFa = 32f, lUa = -30f, lFa = 35f, rTh = 85f, rSh = 0f, lTh = -30f, lSh = -82f)
    private val QUAD = Pose(torso = 109f, rUa = 0f, rFa = 0f, lUa = 4f, lFa = 4f, rTh = 0f, rSh = -90f, lTh = 4f, lSh = -88f)
    private val PLANK_HIGH = Pose(torso = 115f, rUa = 0f, rFa = 0f, lUa = 5f, lFa = 5f, rTh = -65f, rSh = -65f, lTh = -63f, lSh = -63f)
    private val SUPINE_KNEES = Pose(torso = -90f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 135f, rSh = 35f, lTh = 138f, lSh = 38f)

    val ALL: List<Move> = listOf(
        // ----- stretching -----
        Move("polpacci", "Allungamento dei polpacci", listOf("polpacc"), setOf("stretch"),
            "Mani al muro, una gamba avanti piegata e l'altra indietro tesa, col tallone a terra. Spingi i fianchi verso il muro finché senti tirare il polpaccio.",
            listOf(Pose(torso = 158f, rUa = 108f, rFa = 95f, lUa = 104f, lFa = 92f, rTh = 25f, rSh = -5f, lTh = -20f, lSh = -20f),
                Pose(torso = 152f, rUa = 104f, rFa = 85f, lUa = 100f, lFa = 82f, rTh = 32f, rSh = -8f, lTh = -24f, lSh = -24f)),
            secs = 30, perSide = true, prop = Prop.WALL_FRONT),
        Move("quadricipite", "Allungamento del quadricipite", listOf("quadricip", "coscia davanti"), setOf("stretch"),
            "In piedi, piega una gamba e porta il tallone verso il gluteo tenendo la caviglia con la mano. Ginocchia vicine, schiena dritta; se serve appoggiati.",
            listOf(Pose(rUa = -30f, rFa = -10f, lUa = 80f, lFa = 80f, rTh = 0f, rSh = -160f),
                Pose(rUa = -32f, rFa = -12f, lUa = 85f, lFa = 85f, rTh = -6f, rSh = -165f)),
            secs = 30, perSide = true),
        Move("femorali", "Allungamento dei femorali da seduto", listOf("femoral", "seduto in avanti", "allungamento delle gambe"), setOf("stretch", "back"),
            "Seduto con le gambe tese davanti. Allunga la schiena e scendi in avanti dalle anche, con le mani verso i piedi, senza forzare.",
            listOf(Pose(torso = 150f, rUa = 120f, rFa = 120f, lUa = 122f, lFa = 122f, rTh = 90f, rSh = 90f, lTh = 90f, lSh = 90f),
                Pose(torso = 118f, arch = 3f, rUa = 95f, rFa = 95f, lUa = 97f, lFa = 97f, rTh = 90f, rSh = 90f, lTh = 90f, lSh = 90f)),
            secs = 30, anchor = Anchor.CENTER),
        Move("flessori", "Allungamento dei flessori dell'anca", listOf("flessori", "fianchi", "affondo basso", "psoas"), setOf("stretch", "back"),
            "Un ginocchio a terra e l'altro piede avanti. Busto dritto, spingi piano il bacino in avanti: tira davanti all'anca della gamba dietro.",
            listOf(Pose(rUa = 40f, rFa = 60f, lUa = 45f, lFa = 65f, rTh = 80f, rSh = 0f, lTh = -35f, lSh = -90f),
                Pose(rUa = 45f, rFa = 65f, lUa = 50f, lFa = 70f, rTh = 72f, rSh = -12f, lTh = -50f, lSh = -90f)),
            secs = 30, perSide = true),
        Move("bambino", "Posizione del bambino", listOf("bambino", "child"), setOf("stretch", "back"),
            "In ginocchio, siediti sui talloni e porta il busto sulle cosce, braccia lunghe davanti. Respira lento e lascia andare la schiena.",
            listOf(Pose(torso = 66f, head = -10f, arch = 2f, rUa = 92f, rFa = 90f, lUa = 93f, lFa = 91f, rTh = 45f, rSh = -90f, lTh = 46f, lSh = -90f),
                Pose(torso = 69f, head = -10f, arch = 4f, rUa = 92f, rFa = 90f, lUa = 93f, lFa = 91f, rTh = 45f, rSh = -90f, lTh = 46f, lSh = -90f)),
            secs = 40, anchor = Anchor.CENTER),
        Move("cobra", "Cobra", listOf("cobra"), setOf("stretch", "back"),
            "A pancia in giù, mani sotto le spalle. Spingi piano e solleva il petto tenendo il bacino a terra e le spalle lontane dalle orecchie.",
            listOf(Pose(torso = 108f, head = 10f, rUa = -80f, rFa = 40f, lUa = -78f, lFa = 42f, rTh = -90f, rSh = -90f, lTh = -90f, lSh = -90f),
                Pose(torso = 130f, head = 20f, arch = -4f, rUa = -55f, rFa = 25f, lUa = -53f, lFa = 27f, rTh = -90f, rSh = -90f, lTh = -90f, lSh = -90f)),
            secs = 25, reps = 8, anchor = Anchor.CENTER),
        Move("cane", "Cane a testa in giù", listOf("cane a testa", "downward"), setOf("stretch", "mobility"),
            "Mani e piedi a terra, bacino in alto: il corpo fa una V rovesciata. Spingi il pavimento con le mani e porta i talloni verso terra.",
            listOf(Pose(torso = 45f, rUa = 45f, rFa = 45f, lUa = 46f, lFa = 46f, rTh = -20f, rSh = -20f, lTh = -21f, lSh = -21f),
                Pose(torso = 45f, rUa = 45f, rFa = 45f, lUa = 46f, lFa = 46f, rTh = -24f, rSh = -12f, lTh = -21f, lSh = -21f)),
            secs = 30, minLevel = 1, anchor = Anchor.CENTER),
        Move("avanti", "Piegamento in avanti in piedi", listOf("piegamento in avanti", "toccare le punte", "punte dei piedi"), setOf("stretch", "back"),
            "In piedi, ginocchia morbide. Scendi in avanti lentamente lasciando pendere testa e braccia. Per risalire srotola la schiena piano.",
            listOf(Pose(torso = 176f, rUa = 170f, rFa = 175f, lUa = 172f, lFa = 177f),
                Pose(torso = 18f, head = 0f, arch = 4f, rUa = 5f, rFa = 0f, lUa = 6f, lFa = 1f, rTh = -4f, rSh = 4f, lTh = -4f, lSh = 4f)),
            secs = 30),
        Move("ginocchia-petto", "Ginocchia al petto", listOf("ginocchia al petto", "ginocchio al petto"), setOf("stretch", "back"),
            "Sdraiato sulla schiena, abbraccia le ginocchia e portale al petto. Dondola piano da un lato all'altro: massaggia la parte bassa della schiena.",
            listOf(Pose(torso = -90f, rUa = 120f, rFa = 150f, lUa = 122f, lFa = 152f, rTh = -150f, rSh = 100f, lTh = -152f, lSh = 98f),
                Pose(torso = -90f, rUa = 130f, rFa = 160f, lUa = 132f, lFa = 162f, rTh = -165f, rSh = 110f, lTh = -167f, lSh = 108f)),
            secs = 30, anchor = Anchor.CENTER),
        Move("laterale", "Allungamento laterale", listOf("laterale del busto", "allungamento laterale", "inclinazion"), setOf("stretch", "mobility"),
            "In piedi, gambe un po' aperte. Porta un braccio sopra la testa e piegati dall'altra parte, senza ruotare il busto.",
            listOf(Pose(rUa = 10f, rFa = 10f, lUa = -10f, lFa = -10f, rTh = 8f, rSh = 8f, lTh = -8f, lSh = -8f),
                Pose(torso = 200f, rUa = 205f, rFa = 240f, lUa = -5f, lFa = 0f, rTh = 6f, rSh = 8f, lTh = -10f, lSh = -8f)),
            secs = 25, reps = 10, perSide = true, front = true),
        Move("squat-profondo", "Squat profondo tenuto", listOf("squat profondo", "squat tenuto"), setOf("stretch", "mobility"),
            "Scendi in uno squat profondo, talloni a terra e gomiti dentro le ginocchia. Petto alto; spingi piano le ginocchia in fuori.",
            listOf(Pose(torso = 155f, rUa = 70f, rFa = 120f, lUa = 72f, lFa = 122f, rTh = 105f, rSh = -30f, lTh = 107f, lSh = -28f),
                Pose(torso = 160f, rUa = 70f, rFa = 125f, lUa = 72f, lFa = 127f, rTh = 108f, rSh = -32f, lTh = 110f, lSh = -30f)),
            secs = 30),

        // ----- mobilità e riscaldamento -----
        Move("spalle", "Rotazioni delle spalle", listOf("rotazioni delle spalle", "rotazione delle spalle", "cerchi con le braccia", "circonduzion"), setOf("mobility", "warmup", "upper"),
            "In piedi, braccia tese: disegna grandi cerchi lenti, avanti e poi indietro. Spalle basse, lontane dalle orecchie.",
            listOf(Pose(rUa = 0f, rFa = 0f, lUa = 180f, lFa = 180f), Pose(rUa = 90f, rFa = 90f, lUa = -90f, lFa = -90f),
                Pose(rUa = 180f, rFa = 180f, lUa = 0f, lFa = 0f), Pose(rUa = -90f, rFa = -90f, lUa = 90f, lFa = 90f)),
            reps = 12),
        Move("gatto", "Gatto-cammello", listOf("gatto", "cammello", "cat"), setOf("mobility", "warmup", "back", "stretch"),
            "A quattro zampe. Espirando arrotonda la schiena e guarda l'ombelico; inspirando inarca piano e guarda avanti. Lento, seguendo il respiro.",
            listOf(QUAD.copy(arch = 7f, head = -45f), QUAD.copy(arch = -5f, head = 35f)),
            reps = 10, anchor = Anchor.CENTER),
        Move("oscillazioni", "Oscillazioni delle gambe", listOf("oscillazion", "slanci", "leg swing"), setOf("mobility", "warmup", "legs"),
            "In piedi, appoggiati con una mano. Lascia oscillare una gamba avanti e indietro, morbida, sempre un po' più in alto.",
            listOf(Pose(rUa = 0f, rFa = 0f, lUa = 80f, lFa = 85f, rTh = 55f, rSh = 40f), Pose(rUa = 0f, rFa = 0f, lUa = 80f, lFa = 85f, rTh = -35f, rSh = -30f)),
            reps = 12, perSide = true),
        Move("mondo", "Affondo con rotazione", listOf("affondo con rotazione", "affondo e rotazione", "world"), setOf("mobility", "warmup", "legs"),
            "Fai un affondo lungo, appoggia a terra la mano dal lato della gamba dietro e apri l'altro braccio verso il soffitto, seguendolo con lo sguardo.",
            listOf(Pose(rUa = -35f, rFa = 32f, lUa = -30f, lFa = 35f), Pose(torso = 125f, rUa = 0f, rFa = 0f, lUa = 180f, lFa = 180f, rTh = 85f, rSh = -5f, lTh = -50f, lSh = -70f)),
            reps = 6, perSide = true),
        Move("collo", "Mobilità del collo", listOf("collo"), setOf("mobility", "warmup"),
            "Seduto o in piedi, spalle rilassate. Porta il mento al petto e poi guarda in alto, piano. Niente cerchi completi con la testa.",
            listOf(Pose(head = -40f, rUa = 0f, rFa = 0f, lUa = 2f, lFa = 2f), Pose(head = 35f, rUa = 0f, rFa = 0f, lUa = 2f, lFa = 2f)),
            reps = 8),
        Move("marcia", "Marcia sul posto", listOf("marcia", "camminata sul posto"), setOf("warmup", "cardio", "lowimpact"),
            "Cammina sul posto alzando le ginocchia e muovendo le braccia. Ritmo tranquillo, per scaldarti.",
            listOf(Pose(rUa = -30f, rFa = 40f, lUa = 30f, lFa = 110f, rTh = 60f, rSh = -10f), Pose(rUa = 30f, rFa = 110f, lUa = -30f, lFa = 40f, lTh = 60f, lSh = -10f)),
            secs = 40, reps = 30),

        // ----- gambe e glutei -----
        Move("squat", "Squat", listOf("squat"), setOf("legs", "strength"),
            "Piedi larghi come le spalle. Scendi come per sederti, petto alto e ginocchia nella direzione delle punte; risali spingendo coi talloni.",
            listOf(Pose(rUa = 70f, rFa = 80f, lUa = 72f, lFa = 82f), Pose(torso = 145f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 82f, rSh = -18f, lTh = 84f, lSh = -16f)),
            reps = 12),
        Move("sumo", "Squat sumo", listOf("sumo"), setOf("legs", "strength"),
            "Piedi molto larghi e punte in fuori. Scendi dritto con la schiena, spingi le ginocchia in fuori e risali stringendo i glutei.",
            listOf(Pose(rUa = 20f, rFa = -110f, lUa = -20f, lFa = 110f, rTh = 25f, rSh = 18f, lTh = -25f, lSh = -18f),
                Pose(rUa = 20f, rFa = -110f, lUa = -20f, lFa = 110f, rTh = 72f, rSh = 12f, lTh = -72f, lSh = -12f)),
            reps = 12, front = true),
        Move("affondi", "Affondi", listOf("affond"), setOf("legs", "strength"),
            "Fai un passo lungo e scendi finché le ginocchia sono piegate a 90°: quella dietro sfiora terra. Torna su e cambia gamba.",
            listOf(HANDS_HIPS, LUNGE), reps = 10, perSide = true),
        Move("muro", "Squat al muro", listOf("squat al muro", "sedia al muro", "wall sit", "sedia invisibile"), setOf("legs", "strength"),
            "Schiena contro il muro, scendi finché le cosce sono parallele al pavimento, ginocchia sopra le caviglie. Resta lì respirando.",
            listOf(Pose(rUa = 60f, rFa = 90f, lUa = 62f, lFa = 92f, rTh = 90f, rSh = 0f, lTh = 92f, lSh = 2f), Pose(rUa = 58f, rFa = 88f, lUa = 60f, lFa = 90f, rTh = 90f, rSh = 0f, lTh = 92f, lSh = 2f)),
            secs = 30, prop = Prop.WALL_BACK),
        Move("ponte", "Ponte per i glutei", listOf("ponte", "glute bridge"), setOf("legs", "core", "back", "strength"),
            "Sdraiato sulla schiena, ginocchia piegate e piedi a terra. Solleva il bacino stringendo i glutei fino a fare una linea spalle-ginocchia, poi scendi piano.",
            listOf(SUPINE_KNEES, Pose(torso = -58f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 100f, rSh = 12f, lTh = 102f, lSh = 14f)),
            reps = 12),
        Move("calci", "Calci all'indietro a quattro zampe", listOf("calci", "kickback", "slanci indietro"), setOf("legs", "strength"),
            "A quattro zampe, schiena ferma. Spingi un piede verso il soffitto con il ginocchio piegato, stringendo il gluteo, poi torna giù.",
            listOf(QUAD, QUAD.copy(lTh = -100f, lSh = 175f)), reps = 12, perSide = true, anchor = Anchor.HANDS),
        Move("buongiorno", "Piegamento dalle anche", listOf("good morning", "buongiorno", "piegamento dalle anche", "cerniera"), setOf("legs", "back", "strength"),
            "Mani sul petto, ginocchia morbide. Porta il bacino indietro e scendi col busto dritto fino a sentire i femorali, poi torna su.",
            listOf(Pose(rUa = -20f, rFa = 150f, lUa = -18f, lFa = 152f), Pose(torso = 100f, rUa = -20f, rFa = 150f, lUa = -18f, lFa = 152f, rTh = -12f, rSh = 0f, lTh = -10f, lSh = 2f)),
            reps = 12, armsFollow = true),

        // ----- parte alta -----
        Move("flessioni", "Flessioni", listOf("flession", "piegamenti sulle braccia", "push"), setOf("upper", "strength"),
            "Mani sotto le spalle, corpo dritto dalla testa ai talloni. Scendi col petto verso terra, gomiti a 45°, e spingi su.",
            listOf(PLANK_HIGH, Pose(torso = 105f, rUa = -60f, rFa = 40f, lUa = -56f, lFa = 44f, rTh = -75f, rSh = -75f, lTh = -73f, lSh = -73f)),
            reps = 8, minLevel = 2),
        Move("flessioni-ginocchia", "Flessioni sulle ginocchia", listOf("flessioni sulle ginocchia", "flessioni in ginocchio"), setOf("upper", "strength"),
            "Come le flessioni, ma con le ginocchia a terra: corpo dritto dalle ginocchia alla testa. Scendi col petto e spingi su.",
            listOf(Pose(torso = 127f, rUa = 0f, rFa = 0f, lUa = 5f, lFa = 5f, rTh = -53f, rSh = -115f, lTh = -51f, lSh = -113f),
                Pose(torso = 112f, rUa = -60f, rFa = 40f, lUa = -56f, lFa = 44f, rTh = -68f, rSh = -115f, lTh = -66f, lSh = -113f)),
            reps = 8, maxLevel = 2),
        Move("flessioni-muro", "Flessioni al muro", listOf("flessioni al muro"), setOf("upper", "strength", "lowimpact"),
            "In piedi davanti al muro, mani all'altezza delle spalle. Piega i gomiti e porta il petto verso il muro, poi spingi indietro.",
            listOf(Pose(torso = 155f, rUa = 115f, rFa = 115f, lUa = 117f, lFa = 117f, rTh = -25f, rSh = -25f, lTh = -24f, lSh = -24f),
                Pose(torso = 140f, rUa = 122f, rFa = 186f, lUa = 124f, lFa = 188f, rTh = -40f, rSh = -40f, lTh = -39f, lSh = -39f)),
            reps = 12, maxLevel = 1, prop = Prop.WALL_FRONT),
        Move("pugni", "Pugni in aria", listOf("pugni", "boxe", "shadow"), setOf("cardio", "upper", "lowimpact", "warmup"),
            "Gambe un po' piegate, pugni davanti al viso. Tira pugni dritti alternando le braccia, veloce ma senza bloccare i gomiti.",
            listOf(Pose(rUa = 90f, rFa = 90f, lUa = -20f, lFa = 140f, rTh = 15f, rSh = -5f, lTh = -10f, lSh = -15f),
                Pose(rUa = -20f, rFa = 140f, lUa = 90f, lFa = 90f, rTh = 15f, rSh = -5f, lTh = -10f, lSh = -15f)),
            secs = 30, reps = 30),

        Move("plank-alto", "Plank a braccia tese", listOf("plank a braccia tese", "plank alto"), setOf("core", "upper", "strength"),
            "In posizione di flessione, braccia tese e mani sotto le spalle. Corpo dritto, addominali stretti, respira con calma.",
            listOf(PLANK_HIGH, PLANK_HIGH.copy(head = 6f)), secs = 30),
        Move("tocchi-spalle", "Tocchi delle spalle in plank", listOf("tocchi delle spalle", "tocco delle spalle", "shoulder tap"), setOf("core", "upper", "strength"),
            "In plank a braccia tese, piedi un po' larghi. Tocca con una mano la spalla opposta e rimettila giù, alternando, senza far ondeggiare il bacino.",
            listOf(PLANK_HIGH, PLANK_HIGH.copy(rUa = 10f, rFa = -170f), PLANK_HIGH, PLANK_HIGH.copy(lUa = 10f, lFa = -170f)),
            reps = 16, minLevel = 2),

        // ----- addominali e core -----
        Move("plank", "Plank", listOf("plank"), setOf("core", "upper", "strength"),
            "Sugli avambracci e sulle punte dei piedi, corpo dritto come una tavola. Stringi addominali e glutei, non lasciar cadere il bacino.",
            listOf(Pose(torso = 103f, rUa = 0f, rFa = 90f, lUa = 3f, lFa = 92f, rTh = -77f, rSh = -77f, lTh = -76f, lSh = -76f),
                Pose(torso = 103f, head = 5f, rUa = 0f, rFa = 90f, lUa = 3f, lFa = 92f, rTh = -77f, rSh = -77f, lTh = -76f, lSh = -76f)),
            secs = 30),
        Move("plank-laterale", "Plank laterale", listOf("plank laterale", "plank di lato", "side plank"), setOf("core", "strength"),
            "Su un fianco, appoggiato su una mano o un avambraccio, solleva il bacino finché il corpo è dritto. L'altro braccio verso l'alto.",
            listOf(Pose(torso = 115f, rUa = 0f, rFa = 0f, lUa = 175f, lFa = 175f, rTh = -65f, rSh = -65f, lTh = -62f, lSh = -64f),
                Pose(torso = 112f, rUa = 0f, rFa = 0f, lUa = 160f, lFa = 160f, rTh = -68f, rSh = -68f, lTh = -65f, lSh = -67f)),
            secs = 20, perSide = true, front = true, anchor = Anchor.CENTER),
        Move("crunch", "Crunch", listOf("crunch", "addominali"), setOf("core", "strength"),
            "Sdraiato, ginocchia piegate. Stacca le spalle da terra portando le mani verso le ginocchia, poi torna giù piano. Il collo resta rilassato.",
            listOf(SUPINE_KNEES, SUPINE_KNEES.copy(torso = -122f, head = 15f, arch = 1.5f, rUa = 108f, rFa = 108f, lUa = 110f, lFa = 110f)),
            reps = 15),
        Move("bicicletta", "Bicicletta in aria", listOf("bicicletta", "bicycle"), setOf("core", "strength"),
            "Sdraiato, mani dietro la testa e spalle sollevate. Porta un ginocchio verso il petto mentre allunghi l'altra gamba, alternando.",
            listOf(Pose(torso = -112f, head = 15f, rUa = 150f, rFa = -60f, lUa = 152f, lFa = -58f, rTh = 155f, rSh = 95f, lTh = 105f, lSh = 100f),
                Pose(torso = -112f, head = 15f, rUa = 150f, rFa = -60f, lUa = 152f, lFa = -58f, rTh = 105f, rSh = 100f, lTh = 155f, lSh = 95f)),
            reps = 20, minLevel = 2, anchor = Anchor.CENTER),
        Move("gambe-tese", "Sollevamento delle gambe", listOf("sollevamento delle gambe", "sollevamento gambe", "leg raise"), setOf("core", "strength"),
            "Sdraiato, mani sotto i glutei. Solleva le gambe tese fino in verticale e riscendi lento senza toccare terra. La schiena resta appoggiata.",
            listOf(Pose(torso = -90f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 98f, rSh = 98f, lTh = 100f, lSh = 100f),
                Pose(torso = -90f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 178f, rSh = 178f, lTh = 176f, lSh = 176f)),
            reps = 10, minLevel = 2, anchor = Anchor.CENTER),
        Move("dead-bug", "Dead bug", listOf("dead bug", "insetto"), setOf("core", "back", "strength"),
            "Sdraiato, braccia verso il soffitto e ginocchia piegate in alto. Allunga un braccio dietro la testa e la gamba opposta, poi cambia. Schiena a terra.",
            listOf(Pose(torso = -90f, rUa = 180f, rFa = 180f, lUa = 182f, lFa = 182f, rTh = 180f, rSh = 90f, lTh = 178f, lSh = 92f),
                Pose(torso = -90f, rUa = -100f, rFa = -100f, lUa = 182f, lFa = 182f, rTh = 180f, rSh = 90f, lTh = 100f, lSh = 100f),
                Pose(torso = -90f, rUa = 180f, rFa = 180f, lUa = 182f, lFa = 182f, rTh = 180f, rSh = 90f, lTh = 178f, lSh = 92f),
                Pose(torso = -90f, rUa = 180f, rFa = 180f, lUa = -100f, lFa = -100f, rTh = 100f, rSh = 100f, lTh = 178f, lSh = 92f)),
            reps = 10, anchor = Anchor.CENTER),
        Move("bird-dog", "Bird dog", listOf("bird dog", "cane da caccia", "quadrupedia"), setOf("core", "back", "strength"),
            "A quattro zampe. Allunga in avanti un braccio e indietro la gamba opposta, in linea con la schiena. Tieni un attimo e cambia.",
            listOf(QUAD, QUAD.copy(lUa = 95f, lFa = 95f, rTh = -97f, rSh = -97f), QUAD, QUAD.copy(rUa = 95f, rFa = 95f, lUa = 0f, lFa = 0f, rTh = 0f, lTh = -97f, lSh = -97f)),
            reps = 10, anchor = Anchor.CENTER),
        Move("superman", "Superman", listOf("superman"), setOf("back", "core", "strength"),
            "A pancia in giù, braccia lunghe davanti. Solleva insieme braccia, petto e gambe di pochi centimetri, tieni un attimo e scendi.",
            listOf(Pose(torso = 90f, rUa = 95f, rFa = 95f, lUa = 96f, lFa = 96f, rTh = -90f, rSh = -90f, lTh = -90f, lSh = -90f),
                Pose(torso = 102f, head = 5f, arch = -4f, rUa = 112f, rFa = 112f, lUa = 113f, lFa = 113f, rTh = -78f, rSh = -76f, lTh = -79f, lSh = -77f)),
            reps = 12, anchor = Anchor.CENTER),
        Move("russian", "Torsioni russe", listOf("russ", "torsion"), setOf("core", "strength"),
            "Seduto, busto inclinato indietro e piedi sollevati (o a terra, più facile). Porta le mani unite da un fianco all'altro ruotando il busto.",
            listOf(Pose(torso = 210f, rUa = 120f, rFa = 70f, lUa = 125f, lFa = 75f, rTh = 130f, rSh = 75f, lTh = 132f, lSh = 77f),
                Pose(torso = 205f, rUa = 70f, rFa = 40f, lUa = 75f, lFa = 45f, rTh = 130f, rSh = 75f, lTh = 132f, lSh = 77f)),
            reps = 20, minLevel = 2, anchor = Anchor.CENTER),
        Move("scalatore", "Mountain climber", listOf("mountain", "scalatore", "climber"), setOf("cardio", "core"),
            "In posizione di flessione, porta velocemente un ginocchio verso il petto e poi l'altro, come se corressi. Bacino basso.",
            listOf(PLANK_HIGH.copy(rTh = 95f, rSh = -15f), PLANK_HIGH.copy(lTh = 95f, lSh = -15f)),
            secs = 30, minLevel = 2, anchor = Anchor.HANDS),

        // ----- cardio -----
        Move("calciata", "Calciata ai glutei", listOf("calciata", "calci ai glutei", "talloni ai glutei"), setOf("cardio", "warmup", "lowimpact"),
            "Corri o cammina sul posto portando i talloni verso i glutei, alternando. Busto dritto, braccia che accompagnano.",
            listOf(Pose(rUa = -30f, rFa = 40f, lUa = 30f, lFa = 110f, rTh = -8f, rSh = -150f), Pose(rUa = 30f, rFa = 110f, lUa = -30f, lFa = 40f, lTh = -8f, lSh = -150f)),
            secs = 30),
        Move("ginocchio-gomito", "Ginocchio al gomito in piedi", listOf("ginocchio al gomito", "ginocchio-gomito"), setOf("cardio", "core", "lowimpact"),
            "In piedi, mani dietro la testa. Porta un ginocchio in alto verso il gomito opposto piegando il busto, poi cambia lato.",
            listOf(Pose(rUa = 160f, rFa = -60f, lUa = 162f, lFa = -58f), Pose(torso = 158f, rUa = 120f, rFa = -40f, lUa = 122f, lFa = -38f, rTh = 100f, rSh = 0f)),
            reps = 12, perSide = true),
        Move("jumping-jack", "Jumping jack", listOf("jumping", "saltelli", "sforbiciate"), setOf("cardio", "warmup", "jump"),
            "Salta aprendo gambe e braccia sopra la testa, poi chiudi. Atterra morbido sulle punte.",
            listOf(Pose(rUa = 8f, rFa = 8f, lUa = -8f, lFa = -8f, rTh = 3f, rSh = 3f, lTh = -3f, lSh = -3f),
                Pose(rUa = 145f, rFa = 160f, lUa = -145f, lFa = -160f, rTh = 22f, rSh = 22f, lTh = -22f, lSh = -22f)),
            secs = 30, reps = 30, minLevel = 2, front = true),
        Move("skip", "Skip alto", listOf("skip", "ginocchia alte", "corsa sul posto"), setOf("cardio", "jump"),
            "Corri sul posto portando le ginocchia all'altezza dei fianchi, braccia che spingono. Resta sulle punte.",
            listOf(Pose(torso = 175f, rUa = -40f, rFa = 50f, lUa = 50f, lFa = 140f, rTh = 92f, rSh = -5f, lTh = -5f, lSh = -10f),
                Pose(torso = 175f, rUa = 50f, rFa = 140f, lUa = -40f, lFa = 50f, rTh = -5f, rSh = -10f, lTh = 92f, lSh = -5f)),
            secs = 30, minLevel = 2),
        Move("burpee", "Burpee", listOf("burpee"), setOf("cardio", "jump", "strength"),
            "Scendi in squat con le mani a terra, porta i piedi indietro in plank, torna in squat e salta su con le braccia in alto.",
            listOf(Pose(rUa = 175f, rFa = 178f, lUa = 177f, lFa = 180f),
                Pose(torso = 120f, rUa = 10f, rFa = 0f, lUa = 12f, lFa = 2f, rTh = 120f, rSh = -40f, lTh = 122f, lSh = -38f),
                PLANK_HIGH,
                Pose(torso = 120f, rUa = 10f, rFa = 0f, lUa = 12f, lFa = 2f, rTh = 120f, rSh = -40f, lTh = 122f, lSh = -38f)),
            reps = 8, minLevel = 3, anchor = Anchor.CENTER),
    )

    private val byId = ALL.associateBy { it.id }
    fun byId(id: String) = byId[id]

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('’', '\'')

    private val keyIndex: List<Pair<String, Move>> = ALL.flatMap { m -> (m.keys + m.name).map { norm(it) to m } }.sortedByDescending { it.first.length }

    /** L'esercizio del catalogo che corrisponde a un nome scritto a mano («Plank laterale (per lato)» → plank laterale). Vince la parola più lunga. */
    fun find(name: String): Move? { val n = norm(name); return keyIndex.firstOrNull { n.contains(it.first) }?.second }

    // ---------- ritmo del conteggio automatico ----------

    /** Il ritmo si regola: 0,5 = il doppio più veloce, 2 = il doppio più lento. */
    const val TEMPO_MIN = 0.5; const val TEMPO_MAX = 2.0

    /** Secondi per una ripetizione: veloci i salti, lenti gli allungamenti e gli esercizi di forza; [tempo] li allunga o accorcia. */
    fun repSeconds(m: Move?, tempo: Double = 1.0): Double {
        val base = when {
            m == null -> 3.0
            m.id == "burpee" -> 4.0
            "jump" in m.tags -> 1.2
            "cardio" in m.tags -> 1.5
            "stretch" in m.tags || "mobility" in m.tags -> 2.5
            else -> 3.0
        }
        return Math.round(base * tempo.coerceIn(TEMPO_MIN, TEMPO_MAX) * 10) / 10.0
    }

    /** Prima di ogni serie: «Pronti» (mentre la voce dice l'esercizio) e poi 3, 2, 1. */
    const val GET_READY_MS = 5000L

    /** Durante una serie automatica, dopo [elapsedMs]: (ripetizioni fatte, secondi di attesa che mancano; 0 = si conta). */
    fun autoState(elapsedMs: Long, reps: Int, repMs: Long): Pair<Int, Int> =
        if (elapsedMs < GET_READY_MS) 0 to ((GET_READY_MS - elapsedMs + 999) / 1000).toInt().coerceAtLeast(1)
        else ((elapsedMs - GET_READY_MS) / repMs.coerceAtLeast(1)).toInt().coerceAtMost(reps) to 0

    // ---------- generatore di schede ----------

    enum class Goal(val label: String, val emoji: String, val sub: String) {
        STRETCH("Stretching", "🧘", "Allungamenti da tenere, per tutto il corpo"),
        MOBILITY("Mobilità", "🤸", "Articolazioni sciolte: ottima al mattino"),
        FULL("Tutto il corpo", "💪", "Gambe, braccia e addominali a corpo libero"),
        LEGS("Gambe e glutei", "🦵", "Squat, affondi, ponte…"),
        UPPER("Braccia, petto e spalle", "🏋️", "Flessioni e plank"),
        CORE("Addominali e core", "🎯", "Plank, crunch, dead bug…"),
        CARDIO("Cardio a casa", "❤️", "A intervalli, per il fiato"),
        BACK("Schiena sana", "🌿", "Rinforzo e allungamento della schiena"),
    }
    val LEVELS = listOf("Principiante", "Intermedio", "Avanzato")

    private fun pool(goal: Goal, level: Int): List<Move> {
        fun ok(m: Move) = level in m.minLevel..m.maxLevel && (level > 1 || "jump" !in m.tags)
        val want: (Move) -> Boolean = when (goal) {
            Goal.STRETCH -> { m -> "stretch" in m.tags }
            Goal.MOBILITY -> { m -> "mobility" in m.tags }
            Goal.FULL -> { m -> "strength" in m.tags }
            Goal.LEGS -> { m -> "legs" in m.tags && "strength" in m.tags }
            Goal.UPPER -> { m -> "upper" in m.tags }
            Goal.CORE -> { m -> "core" in m.tags }
            Goal.CARDIO -> { m -> "cardio" in m.tags || m.id in setOf("squat", "affondi", "scalatore") }
            Goal.BACK -> { m -> "back" in m.tags }
        }
        return ALL.filter { want(it) && ok(it) }
    }

    /** Un esercizio della scheda con serie, ripetizioni o secondi giusti per il livello (0 = principiante). */
    fun toEx(m: Move, level: Int, sets: Int, rest: Int, holdSecs: Int? = null, workSecs: Int? = null, preferReps: Boolean = false): Ex {
        val f = listOf(0.75, 1.0, 1.3)[level.coerceIn(0, 2)]
        val name = m.name + if (m.perSide) " (per lato)" else ""
        return when {
            workSecs != null -> Ex(m.name + if (m.perSide) " (alternati)" else "", sets, 0, rest, seconds = workSecs)
            holdSecs != null -> Ex(name, sets * (if (m.perSide) 2 else 1), 0, rest, seconds = holdSecs)
            m.timed && !preferReps -> Ex(name, sets * (if (m.perSide) 2 else 1), 0, rest, seconds = (m.secs * f / 5).roundToInt() * 5)
            else -> Ex(name, sets * (if (m.perSide) 2 else 1), max(4, (m.reps.coerceAtLeast(8) * f).roundToInt()), rest)
        }
    }

    /**
     * Crea una scheda: [minutes] minuti circa, [level] 0..2. [seed] cambia la scelta degli esercizi («Un'altra»).
     * Allenamenti: 2 esercizi di riscaldamento, il blocco principale, 2 allungamenti finali.
     */
    fun generate(goal: Goal, minutes: Int, level: Int, seed: Long): WorkoutPlan {
        val lv = level.coerceIn(0, 2); val rnd = Random(seed)
        val target = minutes.coerceIn(5, 90) * 60
        fun secs(l: List<Ex>) = l.sumOf { e -> e.sets * ((if (e.seconds > 0) e.seconds else 40) + e.restSec) }
        val out = mutableListOf<Ex>()
        val hold = listOf(30, 40, 45)[lv]
        when (goal) {
            Goal.STRETCH, Goal.MOBILITY -> {
                val p = pool(goal, lv + 1).shuffled(rnd).let { l -> if (goal == Goal.MOBILITY) l.sortedBy { if (it.id == "collo" || it.id == "spalle") 0 else 1 } else l }
                val queue = p.toMutableList()
                while (secs(out) < target && queue.isNotEmpty()) {
                    val m = queue.removeAt(0)
                    out += if (goal == Goal.STRETCH || m.reps == 0) toEx(m, lv, 1, 10, holdSecs = hold)
                    else toEx(m, lv, 1, 10, preferReps = true)
                }
                var i = 0   // finiti gli esercizi: una serie in più, a turno
                while (secs(out) < target - 45 && out.isNotEmpty() && i < 40) { val k = i % out.size; out[k] = out[k].copy(sets = out[k].sets + (if (out[k].name.endsWith("(per lato)")) 2 else 1)); i++ }
            }
            else -> {
                val warmMoves = ALL.filter { "warmup" in it.tags && "jump" !in it.tags }.shuffled(rnd).take(2)
                val warm = warmMoves.map { if (it.secs > 0) toEx(it, lv, 1, 10, workSecs = 30) else toEx(it, lv, 1, 10).copy(reps = 10) }
                val coolPool = when (goal) { Goal.LEGS -> listOf("quadricipite", "femorali", "flessori", "polpacci"); Goal.BACK, Goal.CORE -> listOf("bambino", "cobra", "ginocchia-petto", "gatto"); else -> listOf("bambino", "femorali", "flessori", "avanti", "quadricipite") }
                val coolMoves = coolPool.shuffled(rnd).take(2).mapNotNull { byId(it) }
                val cool = coolMoves.map { toEx(it, lv, 1, 10, holdSecs = 30) }
                val cardio = goal == Goal.CARDIO
                val sets = listOf(2, 3, 3)[lv]
                val rest = if (cardio) listOf(30, 20, 15)[lv] else listOf(45, 40, 30)[lv]
                val work = listOf(25, 35, 40)[lv]
                var main = pool(goal, lv + 1).filter { it !in warmMoves && it !in coolMoves }.shuffled(rnd)
                if (goal == Goal.BACK) main = main.sortedBy { if ("strength" in it.tags) 0 else 1 }   // prima il rinforzo, poi gli allungamenti
                if (goal == Goal.FULL) {   // a turno gambe, parte alta, addominali
                    val groups = listOf("legs", "upper", "core").map { g -> main.filter { g in it.tags }.toMutableList() }
                    val mixed = mutableListOf<Move>()
                    while (groups.any { it.isNotEmpty() }) groups.forEach { g -> g.removeFirstOrNull()?.let { m -> if (m !in mixed) mixed += m } }
                    main = mixed
                }
                val body = mutableListOf<Ex>()
                val fixed = secs(warm) + secs(cool)
                for (m in main) {
                    if (fixed + secs(body) >= target) break
                    val easy = "strength" !in m.tags && !cardio   // un allungamento in mezzo: riposo breve
                    body += if (cardio) toEx(m, lv, sets, rest, workSecs = work) else toEx(m, lv, if (easy) 1 else sets, if (easy) 15 else rest, holdSecs = if (easy && m.timed) hold else null)
                }
                var i = 0
                while (fixed + secs(body) < target - 60 && body.isNotEmpty() && i < 30) { val k = i % body.size; body[k] = body[k].copy(sets = body[k].sets + (if (body[k].name.endsWith("(per lato)")) 2 else 1)); i++ }
                out += warm; out += body; out += cool
            }
        }
        return WorkoutPlan(Fitness.newPlanId(), "${goal.emoji} ${goal.label} · $minutes min · ${LEVELS[lv].lowercase()}", out)
    }
}
