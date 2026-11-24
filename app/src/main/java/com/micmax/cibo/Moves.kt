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
    /** Quanto è staccato da terra (salti, o in piedi sul gradino). */
    val air: Float = 0f,
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
    /** Attrezzi che servono (vuoto = corpo libero): manubri, kettlebell, elastico, corda, sbarra, sedia. */
    val equip: Set<String> = emptySet(),
) {
    val timed get() = secs > 0
}

enum class Anchor { FEET, HANDS, CENTER, NEAR_FOOT }
enum class Prop { NONE, WALL_FRONT, WALL_BACK, STEP, DUMBBELLS, KETTLEBELL, KB_RIGHT, BAND_FRONT, BAND_HANDS, BAND_FEET, BAND_KNEES, ROPE, BAR, CHAIR, SEAT_HANDS, SEAT_FEET, SEAT_BACK_FOOT, SEAT_HIP, DB_RIGHT, WALL_HAND_BACK, BALL_BACK, BALL_FEET }

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
        a.air + (b.air - a.air) * t,
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
        Anchor.NEAR_FOOT -> o[R_FOOT * 2]
        Anchor.CENTER -> { var a = Float.MAX_VALUE; var b = -Float.MAX_VALUE; for (i in 0 until SPINE) { a = min(a, o[i * 2]); b = max(b, o[i * 2]) }; (a + b) / 2 }
    }

    /** Dove sta il punto d'appoggio nel riquadro 100×100: si sceglie una volta (con tutti i fotogrammi) così l'omino non esce dal riquadro. */
    private class Fit(val x: Float, val scale: Float)
    private val fitCache = HashMap<String, Fit>()
    private fun fit(m: Move): Fit = synchronized(fitCache) { fitCache.getOrPut(m.id) {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var top = 0f
        for (k in 0 until m.frames.size * 8) {
            val ps = pose(m, k / 8f)
            val o = raw(ps, m.front, m.armsFollow); val ax = anchorX(m, o); val low = lowest(o) + ps.air
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
        val ps = pose(m, t)
        val o = raw(ps, m.front, m.armsFollow); val f = fit(m)
        val ax = anchorX(m, o); val low = lowest(o) + ps.air
        for (i in 0 until POINTS) { o[i * 2] = 50f + (o[i * 2] - ax + f.x) * f.scale; o[i * 2 + 1] = FLOOR - (low - o[i * 2 + 1]) * f.scale }
        return o
    }

    // ---------- gli attrezzi e l'ambiente disegnati ----------

    /** Un pezzo di disegno: linea, cerchio pieno, rettangolo pieno o curva (da a1 a a2 passando vicino a c). */
    sealed class Shape {
        data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val w: Float) : Shape()
        data class Dot(val x: Float, val y: Float, val r: Float) : Shape()
        data class Box(val l: Float, val t: Float, val r: Float, val b: Float) : Shape()
        data class Curve(val x1: Float, val y1: Float, val cx: Float, val cy: Float, val x2: Float, val y2: Float, val w: Float) : Shape()
    }

    /** Muro, gradino, sedia, manubri, kettlebell, elastico, corda, sbarra per la posa [o] (punti di [figure]) al tempo [t]. */
    fun propShapes(m: Move, t: Float, o: FloatArray): List<Shape> {
        fun x(i: Int) = o[i * 2]
        fun y(i: Int) = o[i * 2 + 1]
        val f0 by lazy { figure(m, 0f) }
        val k = scale(m)
        val out = ArrayList<Shape>()
        fun dumbbell(i: Int, e: Int) {   // il manubrio è perpendicolare all'avambraccio
            val dx = x(i) - x(e); val dy = y(i) - y(e); val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
            val px = -dy / l * 4.5f * k; val py = dx / l * 4.5f * k
            out += Shape.Line(x(i) - px, y(i) - py, x(i) + px, y(i) + py, 2.2f * k)
            out += Shape.Dot(x(i) - px, y(i) - py, 2.4f * k); out += Shape.Dot(x(i) + px, y(i) + py, 2.4f * k)
        }
        when (m.prop) {
            Prop.WALL_FRONT -> { val wx = maxOf(f0[R_HAND * 2], f0[L_HAND * 2]) + 3f * k; out += Shape.Box(wx, 8f, wx + 4f, FLOOR + 2f) }
            Prop.WALL_BACK -> { val wx = minOf(f0[HIP * 2], f0[NECK * 2]) - 3f * k; out += Shape.Box(wx - 4f, 8f, wx, FLOOR + 2f) }
            Prop.STEP -> { val fx = f0[R_FOOT * 2]; val fy = f0[R_FOOT * 2 + 1] + 2.5f * k; out += Shape.Box(fx - 10f * k, fy, fx + 12f * k, FLOOR + 3f) }
            Prop.CHAIR -> { val hx = (f0[R_HAND * 2] + f0[L_HAND * 2]) / 2; val hy = f0[R_HAND * 2 + 1] + 2f * k
                out += Shape.Box(hx - 14f * k, hy, hx + 2f * k, hy + 3f * k); out += Shape.Box(hx - 14f * k, hy, hx - 11f * k, FLOOR + 3f)
                out += Shape.Box(hx - 1f * k, hy, hx + 2f * k, FLOOR + 3f); out += Shape.Box(hx - 14f * k, hy - 22f * k, hx - 11f * k, hy) }
            Prop.DUMBBELLS -> { dumbbell(L_HAND, L_ELB); dumbbell(R_HAND, R_ELB) }
            Prop.KETTLEBELL -> { val hx = (x(R_HAND) + x(L_HAND)) / 2; val hy = (y(R_HAND) + y(L_HAND)) / 2; out += Shape.Dot(hx, hy + 4.5f * k, 4.5f * k) }
            Prop.BAND_FRONT -> { val ax = (f0[R_HAND * 2] + 0f).coerceAtLeast(x(R_HAND)) + 12f * k; val ay = f0[R_HAND * 2 + 1]
                out += Shape.Line(x(R_HAND), y(R_HAND), ax, ay, 1.6f * k); out += Shape.Box(ax, ay - 3f * k, ax + 2.5f * k, ay + 3f * k) }
            Prop.BAND_HANDS -> out += Shape.Line(x(L_HAND), y(L_HAND), x(R_HAND), y(R_HAND), 1.6f * k)
            Prop.BAND_FEET -> { out += Shape.Line(x(R_FOOT), y(R_FOOT), x(R_HAND), y(R_HAND), 1.4f * k); out += Shape.Line(x(L_FOOT), y(L_FOOT), x(L_HAND), y(L_HAND), 1.4f * k) }
            Prop.BAND_KNEES -> out += Shape.Line(x(L_KNEE), y(L_KNEE) + 3f * k, x(R_KNEE), y(R_KNEE) + 3f * k, 2f * k)
            Prop.KB_RIGHT -> out += Shape.Dot(x(R_HAND), y(R_HAND) + 4.5f * k, 4.5f * k)
            Prop.DB_RIGHT -> dumbbell(R_HAND, R_ELB)
            Prop.WALL_HAND_BACK -> { val wx = minOf(f0[R_HAND * 2], f0[L_HAND * 2]) - 3f * k; out += Shape.Box(wx - 4f, 8f, wx, FLOOR + 2f) }
            Prop.SEAT_HIP -> { val sx = f0[HIP * 2] - 3f * k; val sy = f0[HIP * 2 + 1] + 3f * k   // la sedia dove si è seduti all'inizio
                out += Shape.Box(sx - 12f * k, sy, sx + 10f * k, sy + 3f * k); out += Shape.Box(sx - 12f * k, sy - 24f * k, sx - 9f * k, FLOOR + 3f); out += Shape.Box(sx + 7f * k, sy, sx + 10f * k, FLOOR + 3f) }
            Prop.BALL_BACK -> { val r = ((FLOOR - y(HIP)) / 2 + 1f).coerceAtLeast(5f); out += Shape.Dot(x(HIP) - 3f * k, FLOOR - r + 1f, r) }
            Prop.BALL_FEET -> { val fx = f0[R_FOOT * 2]; val fy = f0[R_FOOT * 2 + 1]; val r = ((FLOOR - fy) / 2).coerceAtLeast(5f); out += Shape.Dot(fx, fy + r + 1f, r) }
            Prop.SEAT_HANDS, Prop.SEAT_FEET, Prop.SEAT_BACK_FOOT -> {   // una sedia con il piano sotto mani o piedi (posa di partenza)
                val (a, b) = when (m.prop) { Prop.SEAT_HANDS -> R_HAND to L_HAND; Prop.SEAT_FEET -> R_FOOT to L_FOOT; else -> L_FOOT to L_FOOT }
                val sx = (f0[a * 2] + f0[b * 2]) / 2; val sy = maxOf(f0[a * 2 + 1], f0[b * 2 + 1]) + 2.5f * k
                out += Shape.Box(sx - 11f * k, sy, sx + 11f * k, sy + 3f * k)
                out += Shape.Box(sx - 11f * k, sy, sx - 8f * k, FLOOR + 3f); out += Shape.Box(sx + 8f * k, sy, sx + 11f * k, FLOOR + 3f)
            }
            Prop.ROPE -> {   // la corda gira: sotto i piedi a metà del salto, sopra la testa quando si atterra
                val ph = ((t % 2f) + 2f) % 2f / 2f
                val c = kotlin.math.cos(ph * 2 * Math.PI).toFloat()
                val top = y(HEAD) - HEAD_R * k - 6f * k; val bottom = FLOOR + 2f
                val cy = (top + bottom) / 2 + (bottom - top) / 2 * -c * 1.15f
                val cx = (x(R_HAND) + x(HIP)) / 2
                // di profilo la corda è un anello: un lato davanti al corpo e uno dietro
                fun half(hx: Float, hy: Float, side: Float) = Shape.Curve(hx, hy, (hx + cx) / 2 + side * 11f * k * kotlin.math.abs(c), (hy + cy) / 2, cx, cy, 1.4f * k)
                out += half(x(R_HAND), y(R_HAND), 1f); out += half(x(L_HAND), y(L_HAND), -1f)
            }
            Prop.BAR -> { val by = minOf(f0[R_HAND * 2 + 1], f0[L_HAND * 2 + 1]) - 1.5f * k; val bx = f0[R_HAND * 2]
                out += Shape.Line(bx - 22f * k, by, bx + 22f * k, by, 2.4f * k); out += Shape.Line(bx - 22f * k, by, bx - 22f * k, 4f, 2f * k); out += Shape.Line(bx + 22f * k, by, bx + 22f * k, 4f, 2f * k) }
            Prop.NONE -> {}
        }
        return out
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
        Move("skip", "Skip alto", listOf("skip", "ginocchia alte"), setOf("cardio", "jump"),
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

        // ----- altri esercizi a corpo libero -----
        Move("squat-salto", "Squat con salto", listOf("squat con salto", "squat jump", "jump squat"), setOf("legs", "cardio", "jump", "strength"),
            "Scendi in squat con le braccia indietro, poi salta in alto spingendo con le gambe e slanciando le braccia. Atterra morbido e riscendi subito.",
            listOf(Pose(torso = 145f, rUa = -40f, rFa = -30f, lUa = -38f, lFa = -28f, rTh = 82f, rSh = -18f, lTh = 84f, lSh = -16f),
                Pose(torso = 178f, rUa = 165f, rFa = 170f, lUa = 167f, lFa = 172f, rTh = -4f, rSh = -12f, lTh = -2f, lSh = -10f, air = 14f)),
            reps = 10, minLevel = 2, anchor = Anchor.CENTER),
        Move("affondi-salto", "Affondi saltati", listOf("affondi saltati", "affondi con salto", "jumping lunge"), setOf("legs", "cardio", "jump", "strength"),
            "Da un affondo salta e cambia gamba in aria, atterrando nell'affondo con l'altra gamba davanti. Busto dritto, atterraggio morbido.",
            listOf(LUNGE.copy(rUa = 60f, rFa = 80f, lUa = -40f, lFa = 0f),
                Pose(rUa = 10f, rFa = 40f, lUa = 10f, lFa = 40f, rTh = 25f, rSh = -15f, lTh = -10f, lSh = -30f, air = 12f),
                Pose(rUa = -40f, rFa = 0f, lUa = 60f, lFa = 80f, rTh = -30f, rSh = -82f, lTh = 85f, lSh = 0f),
                Pose(rUa = 10f, rFa = 40f, lUa = 10f, lFa = 40f, rTh = -10f, rSh = -30f, lTh = 25f, lSh = -15f, air = 12f)),
            reps = 12, minLevel = 3, anchor = Anchor.CENTER),
        Move("pistol", "Squat su una gamba", listOf("squat su una gamba", "pistol", "squat monopodalico"), setOf("legs", "strength"),
            "In piedi su una gamba, l'altra tesa in avanti. Scendi piano più che puoi, tenendo il tallone a terra, e risali. All'inizio tieniti a una sedia.",
            listOf(Pose(rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, lTh = 45f, lSh = 45f),
                Pose(torso = 142f, rUa = 95f, rFa = 95f, lUa = 97f, lFa = 97f, rTh = 105f, rSh = -32f, lTh = 88f, lSh = 88f)),
            reps = 6, perSide = true, minLevel = 3, anchor = Anchor.NEAR_FOOT),
        Move("pike", "Flessioni a V", listOf("flessioni a v", "pike"), setOf("upper", "strength"),
            "Mani e piedi a terra con il bacino in alto, come una V rovesciata. Piega i gomiti e porta la testa verso terra tra le mani, poi spingi su. Lavora le spalle.",
            listOf(Pose(torso = 45f, rUa = 45f, rFa = 45f, lUa = 46f, lFa = 46f, rTh = -20f, rSh = -20f, lTh = -21f, lSh = -21f),
                Pose(torso = 28f, rUa = -25f, rFa = 70f, lUa = -24f, lFa = 71f, rTh = -24f, rSh = -24f, lTh = -25f, lSh = -25f)),
            reps = 8, minLevel = 3, anchor = Anchor.HANDS),
        Move("situp", "Sit-up", listOf("sit-up", "sit up", "situp", "addominali completi"), setOf("core", "strength"),
            "Sdraiato, ginocchia piegate e piedi a terra. Sali fino a sederti portando le braccia in avanti, poi torna giù piano, vertebra dopo vertebra.",
            listOf(SUPINE_KNEES.copy(rUa = -95f, rFa = -95f, lUa = -97f, lFa = -97f),
                SUPINE_KNEES.copy(torso = 160f, arch = 2f, rUa = 100f, rFa = 100f, lUa = 102f, lFa = 102f)),
            reps = 12, minLevel = 2, anchor = Anchor.FEET),
        Move("plank-su-giu", "Plank su e giù", listOf("plank su e giù", "plank su e giu", "plank up"), setOf("core", "upper", "strength"),
            "Dal plank sugli avambracci sali su una mano e poi sull'altra fino al plank a braccia tese, poi riscendi. Bacino fermo.",
            listOf(Pose(torso = 103f, rUa = 0f, rFa = 90f, lUa = 3f, lFa = 92f, rTh = -77f, rSh = -77f, lTh = -76f, lSh = -76f), PLANK_HIGH),
            reps = 10, minLevel = 2, anchor = Anchor.FEET),
        Move("sprawl", "Sprawl", listOf("sprawl"), setOf("cardio", "core"),
            "Come un burpee senza salto e senza flessione: mani a terra, butta i piedi indietro fino a sdraiarti, poi torna su veloce.",
            listOf(Pose(rUa = 10f, rFa = 10f, lUa = 12f, lFa = 12f),
                Pose(torso = 120f, rUa = 10f, rFa = 0f, lUa = 12f, lFa = 2f, rTh = 120f, rSh = -40f, lTh = 122f, lSh = -38f),
                Pose(torso = 98f, head = 10f, arch = -3f, rUa = -10f, rFa = 0f, lUa = -8f, lFa = 2f, rTh = -88f, rSh = -88f, lTh = -87f, lSh = -87f),
                Pose(torso = 120f, rUa = 10f, rFa = 0f, lUa = 12f, lFa = 2f, rTh = 120f, rSh = -40f, lTh = 122f, lSh = -38f)),
            reps = 10, minLevel = 2, anchor = Anchor.CENTER),
        Move("corsa", "Corsa sul posto", listOf("corsa sul posto", "corsa veloce", "running"), setOf("cardio", "warmup"),
            "Corri sul posto veloce, sulle punte, con le braccia piegate che spingono avanti e indietro.",
            listOf(Pose(torso = 172f, rUa = -45f, rFa = 60f, lUa = 45f, lFa = 130f, rTh = 50f, rSh = -20f, lTh = -10f, lSh = -40f, air = 3f),
                Pose(torso = 172f, rUa = 45f, rFa = 130f, lUa = -45f, lFa = 60f, rTh = -10f, rSh = -40f, lTh = 50f, lSh = -20f, air = 3f)),
            secs = 30),
        Move("star-jump", "Salto a stella", listOf("salto a stella", "star jump", "stella"), setOf("cardio", "jump"),
            "Accovacciati con le mani verso terra, poi salta in alto aprendo braccia e gambe a stella. Atterra morbido a gambe unite.",
            listOf(Pose(torso = 180f, rUa = 15f, rFa = -10f, lUa = -15f, lFa = 10f, rTh = 60f, rSh = -15f, lTh = -60f, lSh = 15f),
                Pose(rUa = 140f, rFa = 145f, lUa = -140f, lFa = -145f, rTh = 30f, rSh = 30f, lTh = -30f, lSh = -30f, air = 14f)),
            reps = 10, minLevel = 2, front = true),
        Move("step-up", "Salita sul gradino", listOf("step-up", "step up", "salita sul gradino", "gradino"), setOf("legs", "strength", "lowimpact"),
            "Sali su un gradino o su una sedia ben ferma spingendo con il piede che è sopra, porta su l'altro ginocchio, poi scendi con calma.",
            listOf(Pose(rUa = 20f, rFa = 40f, lUa = -20f, lFa = 10f, rTh = 75f, rSh = -10f),
                Pose(rUa = -20f, rFa = 10f, lUa = 20f, lFa = 40f, rTh = 0f, rSh = 0f, lTh = 70f, lSh = -5f, air = 15.1f)),
            reps = 10, perSide = true, anchor = Anchor.NEAR_FOOT, prop = Prop.STEP, equip = setOf("sedia")),
        Move("affondo-laterale", "Affondo laterale", listOf("affondo laterale", "affondi laterali", "lateral lunge"), setOf("legs", "strength", "mobility"),
            "Gambe larghe. Piega un ginocchio spingendo il bacino indietro e di lato, l'altra gamba resta tesa. Torna al centro e cambia lato.",
            listOf(Pose(rUa = 20f, rFa = -110f, lUa = -20f, lFa = 110f, rTh = 20f, rSh = 20f, lTh = -20f, lSh = -20f),
                Pose(torso = 170f, rUa = 20f, rFa = -110f, lUa = -20f, lFa = 110f, rTh = 60f, rSh = -5f, lTh = -50f, lSh = -50f)),
            reps = 10, perSide = true, front = true),
        Move("barchetta", "Barchetta", listOf("barchetta", "hollow", "barca"), setOf("core", "strength"),
            "Sdraiato, stacca da terra spalle e gambe tese, braccia lunghe sopra la testa: il corpo fa una barchetta. La parte bassa della schiena resta appoggiata.",
            listOf(Pose(torso = -110f, head = 10f, rUa = -125f, rFa = -125f, lUa = -127f, lFa = -127f, rTh = 108f, rSh = 108f, lTh = 110f, lSh = 110f),
                Pose(torso = -113f, head = 10f, rUa = -128f, rFa = -128f, lUa = -130f, lFa = -130f, rTh = 112f, rSh = 112f, lTh = 114f, lSh = 114f)),
            secs = 20, minLevel = 2, anchor = Anchor.CENTER),
        Move("ponte-una-gamba", "Ponte su una gamba", listOf("ponte su una gamba", "ponte monopodalico"), setOf("legs", "core", "strength"),
            "Come il ponte per i glutei, ma con una gamba tesa in alto. Spingi con il tallone a terra e sali, senza far cadere il bacino da un lato.",
            listOf(SUPINE_KNEES.copy(lTh = 150f, lSh = 150f), Pose(torso = -58f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 100f, rSh = 12f, lTh = 122f, lSh = 122f)),
            reps = 10, perSide = true, minLevel = 2),

        // ----- con gli attrezzi -----
        Move("curl", "Curl con i manubri", listOf("curl"), setOf("upper", "strength"),
            "In piedi, un manubrio per mano, braccia lungo i fianchi. Piega i gomiti e porta i pesi verso le spalle, poi scendi piano.",
            listOf(Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f), Pose(rUa = 6f, rFa = 165f, lUa = 8f, lFa = 167f)),
            reps = 12, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("rematore", "Rematore con i manubri", listOf("rematore con i manubri", "rematore manubri", "dumbbell row"), setOf("upper", "back", "strength"),
            "Busto inclinato in avanti a schiena dritta, ginocchia morbide, manubri a braccia tese. Tira i gomiti indietro verso i fianchi, poi scendi.",
            listOf(Pose(torso = 115f, rUa = 0f, rFa = 0f, lUa = 2f, lFa = 2f, rTh = 15f, rSh = -10f, lTh = 17f, lSh = -8f),
                Pose(torso = 115f, rUa = -65f, rFa = 10f, lUa = -63f, lFa = 12f, rTh = 15f, rSh = -10f, lTh = 17f, lSh = -8f)),
            reps = 12, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("spalle-manubri", "Distensioni per le spalle", listOf("distensioni per le spalle", "lento avanti", "shoulder press"), setOf("upper", "strength"),
            "In piedi, manubri all'altezza delle spalle con i gomiti in fuori. Spingi i pesi sopra la testa senza inarcare la schiena, poi torna giù.",
            listOf(Pose(rUa = 95f, rFa = 178f, lUa = -95f, lFa = -178f, rTh = 6f, rSh = 6f, lTh = -6f, lSh = -6f),
                Pose(rUa = 165f, rFa = 175f, lUa = -165f, lFa = -175f, rTh = 6f, rSh = 6f, lTh = -6f, lSh = -6f)),
            reps = 10, front = true, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("stacco", "Stacco rumeno con i manubri", listOf("stacco"), setOf("legs", "back", "strength"),
            "Manubri davanti alle cosce, ginocchia appena piegate. Porta il bacino indietro e scendi con la schiena dritta facendo scivolare i pesi lungo le gambe, poi risali.",
            listOf(Pose(rUa = 5f, rFa = 5f, lUa = 6f, lFa = 6f, rTh = 2f, rSh = 2f), Pose(torso = 100f, rUa = 5f, rFa = 5f, lUa = 6f, lFa = 6f, rTh = -14f, rSh = 0f, lTh = -12f, lSh = 2f)),
            reps = 10, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("goblet", "Squat con peso al petto", listOf("goblet", "squat con peso", "squat con kettlebell", "squat con manubrio"), setOf("legs", "strength"),
            "Tieni un manubrio o una kettlebell davanti al petto. Scendi in squat con il petto alto e i gomiti tra le ginocchia, poi spingi coi talloni.",
            listOf(Pose(rUa = 15f, rFa = 160f, lUa = 17f, lFa = 162f), Pose(torso = 150f, rUa = 45f, rFa = 165f, lUa = 47f, lFa = 167f, rTh = 85f, rSh = -15f, lTh = 87f, lSh = -13f)),
            reps = 12, prop = Prop.KETTLEBELL, equip = setOf("kettlebell")),
        Move("swing", "Swing con la kettlebell", listOf("swing"), setOf("legs", "cardio", "strength"),
            "Piedi larghi, kettlebell tra le gambe. Spingi il bacino in avanti con forza e lascia salire il peso fino al petto a braccia tese, poi lascialo ricadere piegandoti dalle anche.",
            listOf(Pose(torso = 112f, rUa = -25f, rFa = -25f, lUa = -24f, lFa = -24f, rTh = 25f, rSh = -15f, lTh = 27f, lSh = -13f),
                Pose(torso = 182f, rUa = 92f, rFa = 92f, lUa = 93f, lFa = 93f)),
            reps = 15, minLevel = 2, prop = Prop.KETTLEBELL, equip = setOf("kettlebell")),
        Move("elastico-rematore", "Rematore con l'elastico", listOf("rematore con l'elastico", "rematore elastico", "band row"), setOf("upper", "back", "strength", "lowimpact"),
            "Fissa l'elastico davanti a te (a una maniglia chiusa o sotto i piedi). Tira i gomiti indietro stringendo le scapole, poi lascia tornare lento.",
            listOf(Pose(rUa = 88f, rFa = 88f, lUa = 90f, lFa = 90f, rTh = 8f, rSh = -4f), Pose(rUa = -40f, rFa = 85f, lUa = -38f, lFa = 87f, rTh = 8f, rSh = -4f)),
            reps = 15, prop = Prop.BAND_FRONT, equip = setOf("elastico")),
        Move("elastico-aperture", "Aperture con l'elastico", listOf("aperture con l'elastico", "pull apart"), setOf("upper", "back", "lowimpact"),
            "Braccia tese davanti al petto, elastico tra le mani. Apri le braccia di lato portando l'elastico al petto, scapole strette, poi richiudi piano.",
            listOf(Pose(rUa = 55f, rFa = -60f, lUa = -55f, lFa = 60f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f),
                Pose(rUa = 92f, rFa = 92f, lUa = -92f, lFa = -92f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f)),
            reps = 15, front = true, prop = Prop.BAND_HANDS, equip = setOf("elastico")),
        Move("corda", "Salto con la corda", listOf("corda", "rope"), setOf("cardio", "jump"),
            "Gomiti vicini ai fianchi, la corda gira con i polsi. Salti piccoli sulle punte, appena sopra la corda.",
            listOf(Pose(torso = 178f, rUa = -10f, rFa = 50f, lUa = -8f, lFa = 52f, rTh = 4f, rSh = -6f, lTh = 6f, lSh = -4f),
                Pose(torso = 178f, rUa = -10f, rFa = 50f, lUa = -8f, lFa = 52f, rTh = -2f, rSh = -14f, lTh = 0f, lSh = -12f, air = 5f)),
            secs = 40, minLevel = 2, prop = Prop.ROPE, equip = setOf("corda")),
        Move("trazioni", "Trazioni alla sbarra", listOf("trazion", "pull-up", "pullup"), setOf("upper", "back", "strength"),
            "Appeso alla sbarra, mani poco più larghe delle spalle. Tira portando il petto verso la sbarra e i gomiti verso i fianchi, poi scendi lento fino a braccia tese.",
            listOf(Pose(rUa = 180f, rFa = 180f, lUa = 182f, lFa = 182f, rTh = 10f, rSh = -30f, lTh = 12f, lSh = -28f, air = 3f),
                Pose(rUa = 20f, rFa = 175f, lUa = 22f, lFa = 177f, rTh = 10f, rSh = -30f, lTh = 12f, lSh = -28f, air = 32f)),
            reps = 5, minLevel = 2, anchor = Anchor.HANDS, prop = Prop.BAR, equip = setOf("sbarra")),
        Move("dip-sedia", "Dip sulla sedia", listOf("dip", "tricipiti alla sedia"), setOf("upper", "strength"),
            "Mani sul bordo di una sedia ferma dietro di te, gambe avanti. Piega i gomiti indietro e scendi col bacino vicino alla sedia, poi spingi su.",
            listOf(Pose(rUa = -15f, rFa = -15f, lUa = -13f, lFa = -13f, rTh = 80f, rSh = 0f, lTh = 82f, lSh = 2f),
                Pose(rUa = -80f, rFa = 10f, lUa = -78f, lFa = 12f, rTh = 80f, rSh = 65f, lTh = 82f, lSh = 67f)),
            reps = 10, anchor = Anchor.HANDS, prop = Prop.CHAIR, equip = setOf("sedia")),

        // ----- altri esercizi con gli attrezzi -----
        Move("affondi-manubri", "Affondi con i manubri", listOf("affondi con i manubri", "affondi con manubri"), setOf("legs", "strength"),
            "Un manubrio per mano lungo i fianchi. Fai un passo lungo e scendi finché il ginocchio dietro sfiora terra, poi torna su e cambia gamba.",
            listOf(Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f), LUNGE.copy(rUa = 0f, rFa = 0f, lUa = 2f, lFa = 2f)),
            reps = 10, perSide = true, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("squat-manubri", "Squat con i manubri", listOf("squat con i manubri", "squat con manubri"), setOf("legs", "strength"),
            "Manubri lungo i fianchi. Scendi in squat con il petto alto e i pesi che scendono dritti, poi risali spingendo coi talloni.",
            listOf(Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f), Pose(torso = 148f, rUa = 8f, rFa = 8f, lUa = 10f, lFa = 10f, rTh = 82f, rSh = -18f, lTh = 84f, lSh = -16f)),
            reps = 12, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("alzate-laterali", "Alzate laterali", listOf("alzate laterali", "lateral raise"), setOf("upper", "strength"),
            "In piedi, manubri lungo i fianchi, gomiti appena piegati. Alza le braccia di lato fino all'altezza delle spalle, poi scendi piano.",
            listOf(Pose(rUa = 10f, rFa = 12f, lUa = -10f, lFa = -12f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f), Pose(rUa = 85f, rFa = 92f, lUa = -85f, lFa = -92f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f)),
            reps = 12, front = true, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("alzate-frontali", "Alzate frontali", listOf("alzate frontali", "front raise"), setOf("upper", "strength"),
            "Manubri davanti alle cosce. Alza le braccia tese davanti a te fino all'altezza delle spalle, poi scendi piano senza dondolare.",
            listOf(Pose(rUa = 8f, rFa = 8f, lUa = 10f, lFa = 10f), Pose(rUa = 88f, rFa = 90f, lUa = 86f, lFa = 88f)),
            reps = 12, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("floor-press", "Distensioni a terra con i manubri", listOf("distensioni a terra", "floor press", "panca a terra"), setOf("upper", "strength"),
            "Sdraiato, ginocchia piegate, gomiti appoggiati a terra e manubri sopra il petto. Spingi i pesi in alto a braccia tese, poi riscendi finché i gomiti toccano terra.",
            listOf(SUPINE_KNEES.copy(rUa = 80f, rFa = 172f, lUa = 82f, lFa = 174f), SUPINE_KNEES.copy(rUa = 178f, rFa = 178f, lUa = 180f, lFa = 180f)),
            reps = 12, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("tricipiti-testa", "Estensioni per i tricipiti", listOf("estensioni per i tricipiti", "french press", "tricipiti sopra la testa"), setOf("upper", "strength"),
            "In piedi, un manubrio tenuto con due mani sopra la testa. Piega i gomiti e porta il peso dietro la testa, poi distendi le braccia. Gomiti stretti e fermi.",
            listOf(Pose(rUa = 175f, rFa = 177f, lUa = 177f, lFa = 179f), Pose(rUa = 172f, rFa = -12f, lUa = 174f, lFa = -10f)),
            reps = 12, prop = Prop.KETTLEBELL, equip = setOf("manubri")),
        Move("kickback", "Slanci indietro per i tricipiti", listOf("slanci indietro per i tricipiti", "kickback tricipiti", "tricipiti indietro"), setOf("upper", "strength"),
            "Busto inclinato a schiena dritta, gomiti alti vicino ai fianchi. Distendi le braccia indietro fino a tenderle, poi torna con calma.",
            listOf(Pose(torso = 118f, rUa = -62f, rFa = 0f, lUa = -60f, lFa = 2f, rTh = 15f, rSh = -10f, lTh = 17f, lSh = -8f),
                Pose(torso = 118f, rUa = -62f, rFa = -62f, lUa = -60f, lFa = -60f, rTh = 15f, rSh = -10f, lTh = 17f, lSh = -8f)),
            reps = 12, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("thruster", "Squat e spinta in alto", listOf("thruster", "squat e spinta", "squat con distensione"), setOf("legs", "upper", "cardio", "strength"),
            "Manubri alle spalle. Scendi in squat, poi risali con forza e spingi i pesi sopra la testa nello stesso movimento. Riporta i pesi alle spalle e riparti.",
            listOf(Pose(rUa = 30f, rFa = 175f, lUa = -30f, lFa = -175f, rTh = 68f, rSh = 12f, lTh = -68f, lSh = -12f), Pose(rUa = 165f, rFa = 175f, lUa = -165f, lFa = -175f, rTh = 8f, rSh = 8f, lTh = -8f, lSh = -8f)),
            reps = 10, minLevel = 2, front = true, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("renegade", "Rematore in plank", listOf("rematore in plank", "renegade"), setOf("upper", "core", "strength"),
            "In plank a braccia tese con le mani sui manubri, piedi larghi. Tira un manubrio verso il fianco tenendo il bacino fermo, posalo e cambia braccio.",
            listOf(PLANK_HIGH, PLANK_HIGH.copy(rUa = -65f, rFa = 10f), PLANK_HIGH, PLANK_HIGH.copy(lUa = -65f, lFa = 10f)),
            reps = 10, minLevel = 3, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("stacco-kb", "Stacco con la kettlebell", listOf("stacco con la kettlebell", "stacco kettlebell"), setOf("legs", "back", "strength"),
            "Kettlebell a terra tra i piedi. Piegati dalle anche con la schiena dritta e ginocchia morbide, prendi il peso e alzati spingendo il bacino avanti.",
            listOf(Pose(rUa = 4f, rFa = 4f, lUa = 5f, lFa = 5f), Pose(torso = 108f, rUa = 0f, rFa = 0f, lUa = 1f, lFa = 1f, rTh = 25f, rSh = -15f, lTh = 27f, lSh = -13f)),
            reps = 12, prop = Prop.KETTLEBELL, equip = setOf("kettlebell")),
        Move("rematore-kb", "Rematore con la kettlebell", listOf("rematore con la kettlebell", "rematore kettlebell"), setOf("upper", "back", "strength"),
            "Una mano su un ginocchio, busto inclinato e schiena dritta, kettlebell nell'altra mano. Tira il gomito indietro verso il fianco, poi scendi.",
            listOf(Pose(torso = 112f, rUa = 0f, rFa = 0f, lUa = 40f, lFa = 25f, rTh = 15f, rSh = -10f, lTh = 30f, lSh = -5f),
                Pose(torso = 112f, rUa = -65f, rFa = 10f, lUa = 40f, lFa = 25f, rTh = 15f, rSh = -10f, lTh = 30f, lSh = -5f)),
            reps = 10, perSide = true, prop = Prop.KB_RIGHT, equip = setOf("kettlebell")),
        Move("squat-elastico", "Squat con l'elastico", listOf("squat con l'elastico", "squat elastico"), setOf("legs", "strength", "lowimpact"),
            "Elastico sotto i piedi, estremità tenute all'altezza delle spalle. Scendi in squat e risali spingendo contro l'elastico.",
            listOf(Pose(rUa = -20f, rFa = 160f, lUa = -18f, lFa = 162f), Pose(torso = 148f, rUa = 20f, rFa = 160f, lUa = 22f, lFa = 162f, rTh = 82f, rSh = -18f, lTh = 84f, lSh = -16f)),
            reps = 15, prop = Prop.BAND_FEET, equip = setOf("elastico")),
        Move("spalle-elastico", "Distensioni con l'elastico", listOf("distensioni con l'elastico", "spalle elastico"), setOf("upper", "strength", "lowimpact"),
            "Elastico sotto i piedi, mani alle spalle. Spingi le braccia sopra la testa contro l'elastico, poi torna giù con controllo.",
            listOf(Pose(rUa = 95f, rFa = 178f, lUa = -95f, lFa = -178f, rTh = 6f, rSh = 6f, lTh = -6f, lSh = -6f), Pose(rUa = 165f, rFa = 175f, lUa = -165f, lFa = -175f, rTh = 6f, rSh = 6f, lTh = -6f, lSh = -6f)),
            reps = 12, front = true, prop = Prop.BAND_FEET, equip = setOf("elastico")),
        Move("curl-elastico", "Curl con l'elastico", listOf("curl con l'elastico", "curl elastico"), setOf("upper", "strength", "lowimpact"),
            "Elastico sotto i piedi, palmi in avanti. Piega i gomiti portando le mani alle spalle, gomiti fermi, poi scendi lento.",
            listOf(Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f), Pose(rUa = 6f, rFa = 165f, lUa = 8f, lFa = 167f)),
            reps = 15, prop = Prop.BAND_FEET, equip = setOf("elastico")),
        Move("passi-elastico", "Passi laterali con l'elastico", listOf("passi laterali", "camminata laterale", "mostro"), setOf("legs", "strength", "lowimpact"),
            "Elastico sopra le ginocchia, gambe un po' piegate. Fai un passo di lato tenendo l'elastico teso, poi avvicina l'altro piede senza farlo cedere.",
            listOf(Pose(torso = 175f, rUa = 25f, rFa = -15f, lUa = -25f, lFa = 15f, rTh = 18f, rSh = 6f, lTh = -18f, lSh = -6f),
                Pose(torso = 175f, rUa = 25f, rFa = -15f, lUa = -25f, lFa = 15f, rTh = 34f, rSh = 20f, lTh = -14f, lSh = -4f)),
            reps = 12, perSide = true, front = true, prop = Prop.BAND_KNEES, equip = setOf("elastico")),
        Move("ginocchia-appeso", "Ginocchia al petto alla sbarra", listOf("ginocchia al petto alla sbarra", "hanging knee", "sollevamento ginocchia"), setOf("core", "strength"),
            "Appeso alla sbarra, spalle attive. Porta le ginocchia verso il petto senza dondolare, poi scendi lento.",
            listOf(Pose(rUa = 180f, rFa = 180f, lUa = 182f, lFa = 182f, rTh = 2f, rSh = 0f, lTh = 4f, lSh = 2f, air = 3f),
                Pose(rUa = 180f, rFa = 180f, lUa = 182f, lFa = 182f, rTh = 100f, rSh = 0f, lTh = 102f, lSh = 2f, air = 26.5f)),
            reps = 10, minLevel = 2, anchor = Anchor.HANDS, prop = Prop.BAR, equip = setOf("sbarra")),
        Move("appeso", "Appeso alla sbarra", listOf("appeso alla sbarra", "dead hang", "sospensione"), setOf("upper", "back"),
            "Appeso alla sbarra a braccia tese, piedi staccati da terra. Respira e tieni: allunga la schiena e rinforza la presa.",
            listOf(Pose(rUa = 180f, rFa = 180f, lUa = 182f, lFa = 182f, rTh = 2f, rSh = 0f, lTh = 4f, lSh = 2f, air = 3f), Pose(rUa = 180f, rFa = 180f, lUa = 182f, lFa = 182f, rTh = 6f, rSh = -6f, lTh = 0f, lSh = 4f, air = 3f)),
            secs = 20, anchor = Anchor.HANDS, prop = Prop.BAR, equip = setOf("sbarra")),
        Move("bulgari", "Affondi bulgari", listOf("affondi bulgari", "bulgarian", "affondo bulgaro"), setOf("legs", "strength"),
            "Piede dietro appoggiato su una sedia, piede davanti lontano. Scendi dritto finché la coscia davanti è parallela al pavimento, poi risali spingendo col tallone.",
            listOf(Pose(rUa = 20f, rFa = 40f, lUa = -20f, lFa = 10f, rTh = 25f, rSh = 5f, lTh = -20f, lSh = -100f),
                Pose(rUa = 20f, rFa = 40f, lUa = -20f, lFa = 10f, rTh = 85f, rSh = 0f, lTh = -28f, lSh = -159f)),
            reps = 8, perSide = true, minLevel = 2, anchor = Anchor.NEAR_FOOT, prop = Prop.SEAT_BACK_FOOT, equip = setOf("sedia")),
        Move("flessioni-sedia", "Flessioni con le mani sulla sedia", listOf("flessioni con le mani sulla sedia", "flessioni inclinate"), setOf("upper", "strength"),
            "Mani sul bordo di una sedia ferma (contro il muro), corpo dritto. Scendi con il petto verso la sedia e spingi su. Più facile delle flessioni a terra.",
            listOf(Pose(torso = 145f, rUa = 0f, rFa = 0f, lUa = 3f, lFa = 3f, rTh = -35f, rSh = -35f, lTh = -34f, lSh = -34f),
                Pose(torso = 131f, rUa = -60f, rFa = 40f, lUa = -56f, lFa = 44f, rTh = -49f, rSh = -49f, lTh = -48f, lSh = -48f)),
            reps = 10, anchor = Anchor.HANDS, prop = Prop.SEAT_HANDS, equip = setOf("sedia")),
        Move("flessioni-rialzate", "Flessioni con i piedi sulla sedia", listOf("flessioni con i piedi sulla sedia", "flessioni piedi rialzati"), setOf("upper", "strength"),
            "Piedi su una sedia, mani a terra sotto le spalle, corpo dritto. Scendi col petto verso terra e spingi su. Lavora di più la parte alta del petto e le spalle.",
            listOf(Pose(torso = 95f, rUa = 0f, rFa = 0f, lUa = 3f, lFa = 3f, rTh = -85f, rSh = -85f, lTh = -84f, lSh = -84f),
                Pose(torso = 86f, rUa = -60f, rFa = 40f, lUa = -56f, lFa = 44f, rTh = -94f, rSh = -94f, lTh = -93f, lSh = -93f)),
            reps = 8, minLevel = 3, anchor = Anchor.HANDS, prop = Prop.SEAT_FEET, equip = setOf("sedia")),

        // ----- corpo libero: gambe -----
        Move("polpacci-su", "Sollevamenti sui polpacci", listOf("sollevamenti sui polpacci", "calf raise", "punte dei piedi su"), setOf("legs", "strength", "lowimpact"),
            "In piedi, piedi paralleli. Sali sulle punte più in alto che puoi, tieni un attimo e scendi piano. Se serve appoggiati a un muro.",
            listOf(Pose(rUa = 4f, rFa = 4f, lUa = 6f, lFa = 6f), Pose(rUa = 4f, rFa = 4f, lUa = 6f, lFa = 6f, rSh = 4f, lSh = 4f, air = 4f)),
            reps = 15),
        Move("ponte-rialzato", "Ponte con i piedi sulla sedia", listOf("ponte con i piedi sulla sedia", "ponte rialzato"), setOf("legs", "core", "strength"),
            "Sdraiato, talloni sul bordo di una sedia, ginocchia piegate. Spingi con i talloni e solleva il bacino, poi scendi piano.",
            listOf(Pose(torso = -90f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 150f, rSh = 100f, lTh = 152f, lSh = 102f),
                Pose(torso = -62f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 112f, rSh = 82f, lTh = 114f, lSh = 84f)),
            reps = 12, minLevel = 2, prop = Prop.SEAT_FEET, equip = setOf("sedia")),
        Move("affondi-indietro", "Affondi all'indietro", listOf("affondi all'indietro", "affondo indietro", "reverse lunge"), setOf("legs", "strength"),
            "Fai un passo lungo all'indietro e scendi finché il ginocchio dietro sfiora terra. Spingi col piede davanti e torna su. Più gentile per le ginocchia.",
            listOf(Pose(rUa = 80f, rFa = 90f, lUa = 82f, lFa = 92f), LUNGE.copy(rUa = 80f, rFa = 90f, lUa = 82f, lFa = 92f)),
            reps = 10, perSide = true, anchor = Anchor.NEAR_FOOT),
        Move("affondi-camminata", "Affondi in camminata", listOf("affondi in camminata", "walking lunge"), setOf("legs", "strength", "cardio"),
            "Fai un affondo, poi porta avanti la gamba dietro e fai subito l'affondo con l'altra, avanzando. Busto dritto.",
            listOf(LUNGE, Pose(rUa = -35f, rFa = 32f, lUa = -30f, lFa = 35f, rTh = 20f, rSh = 0f, lTh = 40f, lSh = -30f), LUNGE.copy(rTh = -30f, rSh = -82f, lTh = 85f, lSh = 0f)),
            reps = 12, minLevel = 2, anchor = Anchor.CENTER),
        Move("sumo-salto", "Squat sumo con salto", listOf("squat sumo con salto", "sumo jump"), setOf("legs", "cardio", "jump"),
            "Gambe larghe, punte in fuori. Scendi in squat sumo e salta in alto, atterrando morbido di nuovo a gambe larghe.",
            listOf(Pose(rUa = 20f, rFa = -110f, lUa = -20f, lFa = 110f, rTh = 72f, rSh = 12f, lTh = -72f, lSh = -12f),
                Pose(rUa = 150f, rFa = 160f, lUa = -150f, lFa = -160f, rTh = 18f, rSh = 14f, lTh = -18f, lSh = -14f, air = 12f)),
            reps = 10, minLevel = 2, front = true),
        Move("slanci-laterali", "Slanci laterali da sdraiati", listOf("slanci laterali", "gamba laterale", "side leg raise"), setOf("legs", "strength", "lowimpact"),
            "Sdraiato su un fianco, testa sul braccio, gambe tese. Solleva la gamba di sopra senza ruotare il bacino, poi scendi piano.",
            listOf(Pose(torso = 90f, rUa = 90f, rFa = 95f, lUa = 2f, lFa = 2f, rTh = -90f, rSh = -90f, lTh = -88f, lSh = -88f),
                Pose(torso = 90f, rUa = 90f, rFa = 95f, lUa = 2f, lFa = 2f, rTh = -90f, rSh = -90f, lTh = -50f, lSh = -50f)),
            reps = 15, perSide = true, front = true, anchor = Anchor.CENTER),

        // ----- corpo libero: parte alta -----
        Move("flessioni-strette", "Flessioni strette", listOf("flessioni strette", "flessioni diamante", "diamond"), setOf("upper", "strength"),
            "Come le flessioni, ma con le mani vicine sotto il petto e i gomiti che scendono lungo i fianchi. Lavorano di più i tricipiti.",
            listOf(PLANK_HIGH, Pose(torso = 105f, rUa = -70f, rFa = 30f, lUa = -68f, lFa = 32f, rTh = -75f, rSh = -75f, lTh = -73f, lSh = -73f)),
            reps = 8, minLevel = 2),
        Move("flessioni-larghe", "Flessioni larghe", listOf("flessioni larghe", "wide push"), setOf("upper", "strength"),
            "Come le flessioni, con le mani più larghe delle spalle. Scendi col petto fra le mani: lavora di più il petto.",
            listOf(PLANK_HIGH, Pose(torso = 103f, rUa = -45f, rFa = 50f, lUa = -42f, lFa = 52f, rTh = -77f, rSh = -77f, lTh = -76f, lSh = -76f)),
            reps = 8, minLevel = 2),
        Move("flessioni-t", "Flessioni con rotazione", listOf("flessioni con rotazione", "flessioni a t", "t push"), setOf("upper", "core", "strength"),
            "Fai una flessione, poi ruota il corpo su un braccio portando l'altro verso il soffitto: il corpo fa una T. Torna giù e cambia lato.",
            listOf(Pose(torso = 115f, rUa = 0f, rFa = 0f, lUa = 6f, lFa = 6f, rTh = -65f, rSh = -65f, lTh = -62f, lSh = -64f),
                Pose(torso = 115f, rUa = 0f, rFa = 0f, lUa = 175f, lFa = 175f, rTh = -65f, rSh = -65f, lTh = -62f, lSh = -64f)),
            reps = 8, minLevel = 3, front = true, anchor = Anchor.CENTER),
        Move("nuotata", "Nuotata a terra", listOf("nuotata", "swimmer", "nuoto a terra"), setOf("back", "core", "strength"),
            "A pancia in giù, braccia lunghe davanti. Solleva un braccio e la gamba opposta, poi cambia, come se nuotassi. Collo lungo.",
            listOf(Pose(torso = 94f, rUa = 108f, rFa = 108f, lUa = 96f, lFa = 96f, rTh = -90f, rSh = -90f, lTh = -80f, lSh = -78f),
                Pose(torso = 94f, rUa = 96f, rFa = 96f, lUa = 108f, lFa = 108f, rTh = -80f, rSh = -78f, lTh = -90f, lSh = -90f)),
            reps = 20, anchor = Anchor.CENTER),

        // ----- corpo libero: addominali -----
        Move("crunch-inverso", "Crunch inverso", listOf("crunch inverso", "reverse crunch"), setOf("core", "strength"),
            "Sdraiato, ginocchia piegate in alto. Porta le ginocchia verso il petto staccando un po' il bacino da terra, poi scendi lento.",
            listOf(Pose(torso = -90f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 178f, rSh = 92f, lTh = 176f, lSh = 94f),
                Pose(torso = -80f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = -150f, rSh = 120f, lTh = -152f, lSh = 118f)),
            reps = 12, anchor = Anchor.CENTER),
        Move("sforbiciate", "Sforbiciate", listOf("sforbiciate", "flutter", "battiti di gambe"), setOf("core", "strength"),
            "Sdraiato, mani sotto i glutei, gambe tese e staccate da terra. Alza e abbassa le gambe alternandole veloce, schiena appoggiata.",
            listOf(Pose(torso = -100f, head = 15f, rUa = 92f, rFa = 92f, lUa = 94f, lFa = 94f, rTh = 100f, rSh = 100f, lTh = 120f, lSh = 120f),
                Pose(torso = -100f, head = 15f, rUa = 92f, rFa = 92f, lUa = 94f, lFa = 94f, rTh = 120f, rSh = 120f, lTh = 100f, lSh = 100f)),
            secs = 30, minLevel = 2, anchor = Anchor.CENTER),
        Move("tocchi-talloni", "Tocchi dei talloni", listOf("tocchi dei talloni", "heel touch"), setOf("core", "strength"),
            "Sdraiato, ginocchia piegate e spalle staccate. Allunga una mano verso il tallone dello stesso lato piegandoti di lato, poi l'altra.",
            listOf(SUPINE_KNEES.copy(torso = -115f, head = 15f, rUa = 100f, rFa = 100f, lUa = 108f, lFa = 108f),
                SUPINE_KNEES.copy(torso = -115f, head = 15f, rUa = 108f, rFa = 108f, lUa = 100f, lFa = 100f)),
            reps = 20, anchor = Anchor.CENTER),
        Move("inclinazioni-manubrio", "Inclinazioni laterali con il manubrio", listOf("inclinazioni laterali con il manubrio", "side bend"), setOf("core", "strength"),
            "In piedi, un manubrio in una mano, l'altra dietro la testa. Piegati di lato verso il peso, poi risali stringendo il fianco opposto.",
            listOf(Pose(rUa = 4f, rFa = 4f, lUa = -150f, lFa = -40f, rTh = 6f, rSh = 6f, lTh = -6f, lSh = -6f),
                Pose(torso = 165f, rUa = 12f, rFa = 12f, lUa = -165f, lFa = -55f, rTh = 6f, rSh = 6f, lTh = -6f, lSh = -6f)),
            reps = 12, perSide = true, front = true, prop = Prop.DB_RIGHT, equip = setOf("manubri")),

        // ----- corpo libero: cardio -----
        Move("skater", "Salti laterali", listOf("salti laterali", "skater", "pattinatore"), setOf("cardio", "legs", "jump"),
            "Salta di lato atterrando su un piede, l'altra gamba passa dietro. Le braccia oscillano come un pattinatore. Poi salta dall'altra parte.",
            listOf(Pose(torso = 170f, rUa = -40f, rFa = -30f, lUa = -40f, lFa = -30f, rTh = 25f, rSh = -10f, lTh = 30f, lSh = 45f),
                Pose(rUa = 10f, rFa = 10f, lUa = -10f, lFa = -10f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f, air = 8f),
                Pose(torso = 190f, rUa = 40f, rFa = 30f, lUa = 40f, lFa = 30f, rTh = -30f, rSh = -45f, lTh = -25f, lSh = 10f),
                Pose(rUa = 10f, rFa = 10f, lUa = -10f, lFa = -10f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f, air = 8f)),
            reps = 16, minLevel = 2, front = true, anchor = Anchor.CENTER),
        Move("affondi-veloci", "Affondi alternati veloci", listOf("affondi alternati veloci", "affondi veloci"), setOf("cardio", "legs"),
            "Affondi alternati a ritmo sostenuto, senza saltare: indietro con una gamba, su, indietro con l'altra.",
            listOf(Pose(rUa = -35f, rFa = 32f, lUa = -30f, lFa = 35f), LUNGE, Pose(rUa = -35f, rFa = 32f, lUa = -30f, lFa = 35f), LUNGE.copy(rTh = -30f, rSh = -82f, lTh = 85f, lSh = 0f)),
            secs = 30, anchor = Anchor.CENTER),
        Move("orso", "Camminata dell'orso", listOf("camminata dell'orso", "bear crawl", "orso"), setOf("cardio", "core", "upper"),
            "A quattro zampe con le ginocchia sollevate di poco da terra. Cammina avanti muovendo insieme mano e piede opposti, schiena piatta.",
            listOf(Pose(torso = 100f, rUa = 0f, rFa = 0f, lUa = 15f, lFa = 15f, rTh = 15f, rSh = -75f, lTh = -10f, lSh = -80f),
                Pose(torso = 100f, rUa = 15f, rFa = 15f, lUa = 0f, lFa = 0f, rTh = -10f, rSh = -80f, lTh = 15f, lSh = -75f)),
            secs = 30, minLevel = 2, anchor = Anchor.CENTER),
        Move("inchworm", "Bruco", listOf("bruco", "inchworm"), setOf("warmup", "mobility", "core", "upper"),
            "In piedi, piegati e appoggia le mani a terra. Cammina con le mani in avanti fino al plank, poi torna indietro con le mani e risali.",
            listOf(Pose(), Pose(torso = 18f, rUa = 5f, rFa = 0f, lUa = 6f, lFa = 1f, rTh = -4f, rSh = 4f, lTh = -4f, lSh = 4f),
                Pose(torso = 70f, rUa = 30f, rFa = 30f, lUa = 31f, lFa = 31f, rTh = -15f, rSh = -15f, lTh = -16f, lSh = -16f), PLANK_HIGH,
                Pose(torso = 70f, rUa = 30f, rFa = 30f, lUa = 31f, lFa = 31f, rTh = -15f, rSh = -15f, lTh = -16f, lSh = -16f),
                Pose(torso = 18f, rUa = 5f, rFa = 0f, lUa = 6f, lFa = 1f, rTh = -4f, rSh = 4f, lTh = -4f, lSh = 4f)),
            reps = 6, anchor = Anchor.FEET),

        // ----- equilibrio -----
        Move("una-gamba", "Equilibrio su una gamba", listOf("equilibrio su una gamba", "su una gamba", "fenicottero"), setOf("balance", "legs", "lowimpact"),
            "In piedi vicino a un muro o a una sedia. Alza un piede e resta in equilibrio sull'altro, sguardo fisso davanti. Più difficile: chiudi gli occhi.",
            listOf(Pose(rUa = 60f, rFa = 80f, lUa = -20f, lFa = 10f, lTh = 85f, lSh = 0f), Pose(rUa = 65f, rFa = 85f, lUa = -15f, lFa = 15f, lTh = 88f, lSh = -4f)),
            secs = 30, perSide = true),
        Move("tallone-punta", "Camminata tallone-punta", listOf("tallone-punta", "tallone punta", "camminata in linea"), setOf("balance", "lowimpact"),
            "Cammina in linea retta mettendo il tallone di un piede davanti alla punta dell'altro, braccia aperte. Lento, sguardo avanti.",
            listOf(Pose(rUa = 70f, rFa = 80f, lUa = -70f, lFa = -80f, rTh = 2f, rSh = 2f, lTh = -2f, lSh = -2f), Pose(rUa = 75f, rFa = 85f, lUa = -65f, lFa = -75f, rTh = -1f, rSh = 3f, lTh = 1f, lSh = -3f)),
            secs = 40, front = true),
        Move("alzarsi-sedia", "Alzarsi dalla sedia senza mani", listOf("alzarsi dalla sedia", "seduto in piedi", "sit to stand"), setOf("balance", "legs", "strength", "lowimpact"),
            "Seduto sul bordo della sedia, braccia incrociate sul petto. Alzati senza usare le mani, poi risiediti piano. Allena gambe ed equilibrio.",
            listOf(Pose(torso = 160f, rUa = -20f, rFa = 150f, lUa = -18f, lFa = 152f, rTh = 90f, rSh = 0f, lTh = 92f, lSh = 2f), Pose(rUa = -20f, rFa = 150f, lUa = -18f, lFa = 152f)),
            reps = 10, anchor = Anchor.FEET, prop = Prop.SEAT_HIP, equip = setOf("sedia")),
        Move("punte-appoggio", "Punte con appoggio", listOf("punte con appoggio", "sollevamenti sulle punte con appoggio"), setOf("balance", "legs", "lowimpact"),
            "In piedi davanti a un muro, mani appoggiate. Sali sulle punte, poi torna sui talloni e solleva un po' le punte. Lento e controllato.",
            listOf(Pose(torso = 172f, rUa = 95f, rFa = 95f, lUa = 97f, lFa = 97f), Pose(torso = 172f, rUa = 95f, rFa = 95f, lUa = 97f, lFa = 97f, rSh = 4f, lSh = 4f, air = 4f)),
            reps = 12, prop = Prop.WALL_FRONT),

        // ----- stretching -----
        Move("petto-muro", "Allungamento del petto al muro", listOf("allungamento del petto", "petto al muro", "pettorali"), setOf("stretch"),
            "Mano e avambraccio appoggiati a un muro dietro di te, all'altezza della spalla. Ruota piano il busto dall'altra parte finché senti tirare il petto.",
            listOf(Pose(rUa = -90f, rFa = -90f, lUa = 10f, lFa = 10f), Pose(torso = 175f, rUa = -95f, rFa = -95f, lUa = 15f, lFa = 15f)),
            secs = 30, perSide = true, prop = Prop.WALL_HAND_BACK),
        Move("spalla-incrociata", "Allungamento della spalla", listOf("allungamento della spalla", "braccio incrociato"), setOf("stretch", "mobility"),
            "Porta un braccio teso davanti al petto e tienilo con l'altra mano all'altezza del gomito. Spalla bassa, tieni e cambia.",
            listOf(Pose(rUa = -80f, rFa = -90f, lUa = 10f, lFa = 70f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f), Pose(rUa = -85f, rFa = -95f, lUa = 12f, lFa = 75f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f)),
            secs = 30, perSide = true, front = true),
        Move("tricipite-stretch", "Allungamento del tricipite", listOf("allungamento del tricipite", "tricipite"), setOf("stretch"),
            "Porta una mano dietro la testa con il gomito in alto. Con l'altra mano spingi piano il gomito: senti tirare dietro il braccio.",
            listOf(Pose(rUa = 175f, rFa = -10f, lUa = 150f, lFa = 200f), Pose(rUa = 178f, rFa = -15f, lUa = 152f, lFa = 205f)),
            secs = 30, perSide = true),
        Move("piriforme", "Allungamento del gluteo (gamba a 4)", listOf("piriforme", "gamba a 4", "figura 4"), setOf("stretch", "back"),
            "Sdraiato, una caviglia appoggiata sul ginocchio opposto. Porta le gambe verso il petto tirando la coscia di sotto: senti tirare il gluteo.",
            listOf(Pose(torso = -90f, rUa = 130f, rFa = 160f, lUa = 132f, lFa = 162f, rTh = -165f, rSh = 105f, lTh = 150f, lSh = 70f),
                Pose(torso = -90f, rUa = 135f, rFa = 165f, lUa = 137f, lFa = 167f, rTh = -155f, rSh = 112f, lTh = 160f, lSh = 80f)),
            secs = 30, perSide = true, anchor = Anchor.CENTER),
        Move("torsione", "Torsione da sdraiati", listOf("torsione da sdraiati", "torsione"), setOf("stretch", "back"),
            "Sdraiato, braccia aperte. Porta le ginocchia piegate da un lato tenendo le spalle a terra, sguardo dalla parte opposta. Respira e cambia.",
            listOf(Pose(torso = -90f, rUa = -92f, rFa = -92f, lUa = -94f, lFa = -94f, rTh = 150f, rSh = 70f, lTh = 150f, lSh = 70f),
                Pose(torso = -90f, rUa = -92f, rFa = -92f, lUa = -94f, lFa = -94f, rTh = 120f, rSh = 95f, lTh = 118f, lSh = 93f)),
            secs = 30, perSide = true, anchor = Anchor.CENTER),
        Move("farfalla", "Farfalla", listOf("farfalla", "butterfly"), setOf("stretch", "mobility"),
            "Seduto, piante dei piedi unite e ginocchia aperte. Tieni i piedi con le mani e lascia scendere le ginocchia, schiena dritta.",
            listOf(Pose(rUa = 15f, rFa = -25f, lUa = -15f, lFa = 25f, rTh = 82f, rSh = -60f, lTh = -82f, lSh = 60f), Pose(rUa = 15f, rFa = -25f, lUa = -15f, lFa = 25f, rTh = 72f, rSh = -66f, lTh = -72f, lSh = 66f)),
            secs = 30, front = true),
        Move("polsi", "Mobilità dei polsi", listOf("mobilità dei polsi", "polsi"), setOf("mobility", "warmup"),
            "Braccia avanti, mani rilassate. Piega i polsi su e giù, poi fai dei cerchi lenti nei due versi. Utile prima di flessioni e plank.",
            listOf(Pose(rUa = 85f, rFa = 70f, lUa = 87f, lFa = 72f), Pose(rUa = 85f, rFa = 110f, lUa = 87f, lFa = 112f)),
            reps = 10),
        Move("caviglie", "Mobilità delle caviglie", listOf("mobilità delle caviglie", "caviglie", "cerchi con la caviglia"), setOf("mobility", "warmup", "balance"),
            "In piedi su una gamba (appoggiato se serve), l'altra un po' sollevata. Disegna cerchi con la punta del piede, poi cambia verso e gamba.",
            listOf(Pose(rUa = 60f, rFa = 80f, lUa = -20f, lFa = 10f, lTh = 40f, lSh = 20f), Pose(rUa = 60f, rFa = 80f, lUa = -20f, lFa = 10f, lTh = 40f, lSh = 35f),
                Pose(rUa = 60f, rFa = 80f, lUa = -20f, lFa = 10f, lTh = 45f, lSh = 30f), Pose(rUa = 60f, rFa = 80f, lUa = -20f, lFa = 10f, lTh = 38f, lSh = 25f)),
            reps = 10, perSide = true),

        // ----- altri attrezzi -----
        Move("curl-martello", "Curl a martello", listOf("curl a martello", "hammer curl"), setOf("upper", "strength"),
            "Come il curl, ma con i palmi rivolti verso le cosce (presa a martello). Gomiti fermi, su e giù senza dondolare.",
            listOf(Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f), Pose(rUa = 6f, rFa = 160f, lUa = 8f, lFa = 162f)),
            reps = 12, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("croci", "Croci a terra con i manubri", listOf("croci", "flyes", "aperture con i manubri"), setOf("upper", "strength"),
            "Sdraiato, manubri sopra il petto a braccia quasi tese. Apri le braccia di lato fino a sfiorare terra, poi richiudi come in un abbraccio.",
            listOf(SUPINE_KNEES.copy(rUa = 178f, rFa = 172f, lUa = 180f, lFa = 174f), SUPINE_KNEES.copy(rUa = 100f, rFa = 95f, lUa = -100f, lFa = -95f)),
            reps = 12, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("polpacci-manubri", "Sollevamenti sui polpacci con i manubri", listOf("polpacci con i manubri", "calf raise con manubri"), setOf("legs", "strength"),
            "Manubri lungo i fianchi. Sali sulle punte più in alto che puoi, tieni un attimo, scendi lento.",
            listOf(Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f), Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f, rSh = 4f, lSh = 4f, air = 4f)),
            reps = 15, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("camminata-pesi", "Camminata con i pesi", listOf("camminata con i pesi", "farmer"), setOf("upper", "core", "strength", "cardio"),
            "Un peso pesante per mano, braccia lungo i fianchi. Cammina dritto a passi corti, spalle basse e pancia stretta.",
            listOf(Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f, rTh = 25f, rSh = 0f, lTh = -15f, lSh = -10f), Pose(rUa = 2f, rFa = 2f, lUa = 4f, lFa = 4f, rTh = -15f, rSh = -10f, lTh = 25f, lSh = 0f)),
            secs = 40, prop = Prop.DUMBBELLS, equip = setOf("manubri")),
        Move("halo", "Halo con la kettlebell", listOf("halo"), setOf("upper", "mobility", "core"),
            "Tieni la kettlebell per le maniglie davanti al petto. Girala intorno alla testa, vicina, in un verso e poi nell'altro. Pancia stretta.",
            listOf(Pose(rUa = 30f, rFa = -150f, lUa = -30f, lFa = 150f), Pose(rUa = 120f, rFa = -120f, lUa = -60f, lFa = 160f),
                Pose(rUa = 150f, rFa = -100f, lUa = -150f, lFa = 100f), Pose(rUa = 60f, rFa = -160f, lUa = -120f, lFa = 120f)),
            reps = 8, front = true, prop = Prop.KETTLEBELL, equip = setOf("kettlebell")),
        Move("sumo-kb", "Squat sumo con la kettlebell", listOf("squat sumo con la kettlebell", "sumo con kettlebell"), setOf("legs", "strength"),
            "Gambe larghe, punte in fuori, kettlebell tenuta a braccia tese tra le gambe. Scendi dritto e risali stringendo i glutei.",
            listOf(Pose(rUa = -12f, rFa = -5f, lUa = 12f, lFa = 5f, rTh = 25f, rSh = 18f, lTh = -25f, lSh = -18f), Pose(rUa = -12f, rFa = -5f, lUa = 12f, lFa = 5f, rTh = 72f, rSh = 12f, lTh = -72f, lSh = -12f)),
            reps = 12, front = true, prop = Prop.KETTLEBELL, equip = setOf("kettlebell")),
        Move("petto-elastico", "Aperture del petto con l'elastico", listOf("aperture del petto con l'elastico", "petto elastico"), setOf("upper", "strength", "lowimpact"),
            "Elastico dietro la schiena, un capo per mano, braccia aperte. Porta le mani avanti e unite davanti al petto, poi riapri lento.",
            listOf(Pose(rUa = 92f, rFa = 92f, lUa = -92f, lFa = -92f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f), Pose(rUa = 55f, rFa = -60f, lUa = -55f, lFa = 60f, rTh = 5f, rSh = 5f, lTh = -5f, lSh = -5f)),
            reps = 15, front = true, prop = Prop.BAND_HANDS, equip = setOf("elastico")),
        Move("buongiorno-elastico", "Piegamento dalle anche con l'elastico", listOf("piegamento dalle anche con l'elastico", "good morning con l'elastico"), setOf("legs", "back", "strength"),
            "Elastico sotto i piedi e dietro il collo (o tenuto alle spalle). Porta il bacino indietro con la schiena dritta, poi risali spingendo coi fianchi.",
            listOf(Pose(rUa = -20f, rFa = 150f, lUa = -18f, lFa = 152f), Pose(torso = 105f, rUa = -20f, rFa = 150f, lUa = -18f, lFa = 152f, rTh = -12f, rSh = 0f, lTh = -10f, lSh = 2f)),
            reps = 12, armsFollow = true, prop = Prop.BAND_FEET, equip = setOf("elastico")),
        Move("glutei-elastico", "Slanci indietro con l'elastico", listOf("slanci indietro con l'elastico", "kickback elastico"), setOf("legs", "strength", "lowimpact"),
            "Elastico intorno alle caviglie, mani su un muro o una sedia. Porta una gamba tesa indietro stringendo il gluteo, senza inarcare la schiena.",
            listOf(Pose(torso = 170f, rUa = 95f, rFa = 95f, lUa = 97f, lFa = 97f, rTh = -2f, rSh = -2f), Pose(torso = 165f, rUa = 95f, rFa = 95f, lUa = 97f, lFa = 97f, rTh = -40f, rSh = -38f)),
            reps = 15, perSide = true, prop = Prop.BAND_KNEES, equip = setOf("elastico")),
        Move("crunch-palla", "Crunch sulla fitball", listOf("crunch sulla fitball", "crunch sulla palla"), setOf("core", "strength"),
            "Sdraiato con la parte bassa della schiena sulla palla, piedi a terra larghi. Sali con le spalle stringendo la pancia, poi torna giù lento.",
            listOf(Pose(torso = -100f, rUa = 150f, rFa = -60f, lUa = 152f, lFa = -58f, rTh = 90f, rSh = 0f, lTh = 92f, lSh = 2f),
                Pose(torso = -130f, head = 15f, rUa = 150f, rFa = -60f, lUa = 152f, lFa = -58f, rTh = 90f, rSh = 0f, lTh = 92f, lSh = 2f)),
            reps = 15, prop = Prop.BALL_BACK, equip = setOf("palla")),
        Move("ponte-palla", "Ponte con i piedi sulla fitball", listOf("ponte con i piedi sulla fitball", "ponte sulla palla"), setOf("legs", "core", "strength"),
            "Sdraiato, talloni sulla palla, braccia a terra. Solleva il bacino fino a fare una linea spalle-piedi, tieni la palla ferma e scendi.",
            listOf(Pose(torso = -90f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 160f, rSh = 120f, lTh = 162f, lSh = 122f),
                Pose(torso = -70f, rUa = 90f, rFa = 90f, lUa = 92f, lFa = 92f, rTh = 140f, rSh = 110f, lTh = 142f, lSh = 112f)),
            reps = 12, minLevel = 2, prop = Prop.BALL_FEET, equip = setOf("palla")),

        // ----- respiro -----
        Move("respiro", "Respirazione lenta", listOf("respirazione", "respiro"), setOf("breath", "stretch"),
            "In piedi o seduto, spalle rilassate. Inspira dal naso per 4 secondi gonfiando la pancia, espira dalla bocca per 6 secondi. Il cuore rallenta.",
            listOf(Pose(rUa = 4f, rFa = 4f, lUa = 6f, lFa = 6f), Pose(head = 6f, rUa = 30f, rFa = 30f, lUa = 32f, lFa = 32f)),
            secs = 60),
    )

    private val byId = ALL.associateBy { it.id }
    fun byId(id: String) = byId[id] ?: custom.firstOrNull { it.id == id }

    /** Esercizi creati con l'AI e salvati dalla persona: si aggiungono al catalogo. */
    @Volatile var custom: List<Move> = emptyList(); private set
    fun setCustom(l: List<Move>) { custom = l; keyIndex = buildIndex() }
    /** Tutti gli esercizi: quelli dell'app più quelli creati con l'AI. */
    fun every(): List<Move> = ALL + custom

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('’', '\'')

    private fun buildIndex(): List<Pair<String, Move>> = (ALL + custom).flatMap { m -> (m.keys + m.name).map { norm(it) to m } }.filter { it.first.isNotBlank() }.sortedByDescending { it.first.length }
    @Volatile private var keyIndex: List<Pair<String, Move>> = buildIndex()

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
        BALANCE("Equilibrio", "🧍", "Stabilità e prevenzione delle cadute"),
    }
    val LEVELS = listOf("Principiante", "Intermedio", "Avanzato")

    /**
     * Cosa si può fare: niente salti (anche per il silenzio), attrezzi che hai, spazio ([spaceCap] 0 = pochissimo,
     * 1 = un tappetino, 2 = una stanza) e parti del corpo da non caricare.
     */
    data class Opts(val noJumps: Boolean = false, val equip: Set<String> = emptySet(), val spaceCap: Int = 2, val avoid: Set<String> = emptySet())

    fun allowed(m: Move, o: Opts): Boolean {
        val info = MoveInfos.of(m.id)
        return !(o.noJumps && "jump" in m.tags) && o.equip.containsAll(m.equip) && info.space <= o.spaceCap && info.stress.none { it in o.avoid }
    }

    private fun pool(goal: Goal, level: Int, o: Opts = Opts()): List<Move> {
        fun ok(m: Move) = level in m.minLevel..m.maxLevel && (level > 1 || "jump" !in m.tags) && allowed(m, o)
        val want: (Move) -> Boolean = when (goal) {
            Goal.STRETCH -> { m -> "stretch" in m.tags }
            Goal.MOBILITY -> { m -> "mobility" in m.tags }
            Goal.FULL -> { m -> "strength" in m.tags }
            Goal.LEGS -> { m -> "legs" in m.tags && "strength" in m.tags }
            Goal.UPPER -> { m -> "upper" in m.tags }
            Goal.CORE -> { m -> "core" in m.tags }
            Goal.CARDIO -> { m -> "cardio" in m.tags || m.id in setOf("squat", "affondi", "scalatore") }
            Goal.BACK -> { m -> "back" in m.tags }
            Goal.BALANCE -> { m -> "balance" in m.tags || m.id in setOf("ponte", "bird-dog", "squat", "flessioni-muro") }
        }
        return every().filter { want(it) && ok(it) }
    }

    /** Se con queste scelte non resta niente (poco spazio, niente attrezzi…), si allarga un po' la scelta: prima altri livelli, poi altri tipi. */
    private fun poolOrEasier(goal: Goal, level: Int, o: Opts): List<Move> =
        pool(goal, level, o).ifEmpty { (1..3).flatMap { pool(goal, it, o) }.distinct() }
            .ifEmpty { ALL.filter { level in it.minLevel..it.maxLevel && allowed(it, o) && ("strength" in it.tags || "stretch" in it.tags || "mobility" in it.tags) } }

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
    fun generate(goal: Goal, minutes: Int, level: Int, seed: Long, noJumps: Boolean = false, opts: Opts = Opts(noJumps = noJumps)): WorkoutPlan {
        val o = if (noJumps) opts.copy(noJumps = true) else opts
        val lv = level.coerceIn(0, 2); val rnd = Random(seed)
        val target = minutes.coerceIn(5, 90) * 60
        fun secs(l: List<Ex>) = l.sumOf { e -> e.sets * ((if (e.seconds > 0) e.seconds else 40) + e.restSec) }
        val out = mutableListOf<Ex>()
        val hold = listOf(30, 40, 45)[lv]
        when (goal) {
            Goal.STRETCH, Goal.MOBILITY -> {
                val p = poolOrEasier(goal, lv + 1, o).shuffled(rnd).let { l -> if (goal == Goal.MOBILITY) l.sortedBy { if (it.id == "collo" || it.id == "spalle") 0 else 1 } else l }
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
                val short = minutes <= 10   // schede brevi: un solo esercizio di riscaldamento e uno di allungamento
                val warmMoves = ALL.filter { "warmup" in it.tags && "jump" !in it.tags && allowed(it, o) }.shuffled(rnd).take(if (short) 1 else 2)
                val warm = warmMoves.map { if (it.secs > 0) toEx(it, lv, 1, 10, workSecs = 30) else toEx(it, lv, 1, 10).copy(reps = 10) }
                val coolPool = when (goal) { Goal.LEGS -> listOf("quadricipite", "femorali", "flessori", "polpacci"); Goal.BACK, Goal.CORE -> listOf("bambino", "cobra", "ginocchia-petto", "gatto"); else -> listOf("bambino", "femorali", "flessori", "avanti", "quadricipite") }
                val coolMoves = coolPool.mapNotNull { byId(it) }.filter { allowed(it, o) }.ifEmpty { ALL.filter { "stretch" in it.tags && allowed(it, o) } }.shuffled(rnd).take(if (short) 1 else 2)
                val cool = coolMoves.map { toEx(it, lv, 1, 10, holdSecs = 30) }
                val cardio = goal == Goal.CARDIO
                val sets = listOf(2, 3, 3)[lv]
                val rest = if (cardio) listOf(30, 20, 15)[lv] else listOf(45, 40, 30)[lv]
                val work = listOf(25, 35, 40)[lv]
                var main = poolOrEasier(goal, lv + 1, o).filter { it !in warmMoves && it !in coolMoves }.shuffled(rnd)
                if (o.equip.isNotEmpty()) main = main.sortedBy { if (it.equip.isNotEmpty()) 0 else 1 }   // se hai attrezzi, si usano
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
                    val ex = if (cardio) toEx(m, lv, sets, rest, workSecs = work) else toEx(m, lv, if (easy) 1 else sets, if (easy) 15 else rest, holdSecs = if (easy && m.timed) hold else null)
                    if (body.isNotEmpty() && fixed + secs(body + ex) > target + 120) continue   // troppo lungo: si prova il prossimo
                    body += ex
                }
                var i = 0
                while (fixed + secs(body) < target - 60 && body.isNotEmpty() && i < 30) { val k = i % body.size; body[k] = body[k].copy(sets = body[k].sets + (if (body[k].name.endsWith("(per lato)")) 2 else 1)); i++ }
                out += warm; out += body; out += cool
                // a fine allenamento un minuto di respirazione lenta per calmare il battito
                if (minutes >= 15) byId("respiro")?.let { out += Ex(it.name, 1, 0, 0, seconds = 60) }
            }
        }
        return WorkoutPlan(Fitness.newPlanId(), "${goal.emoji} ${goal.label} · $minutes min · ${LEVELS[lv].lowercase()}", out)
    }
}
