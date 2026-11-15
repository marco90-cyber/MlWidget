package com.marco.mlwidget

import android.app.WallpaperManager
import android.content.Context
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Colori finali di un widget, già scelti per chiaro o scuro. */
class Pal(
    val bg: Int,
    val text: Int,
    val dim: Int,
    val accent: Int,
    val face: Int,
    val faceInk: Int,
    val tick: Int,
    val track: Int,
    val border: Int?,
    /** testo senza sfondo: serve un'ombra per leggerlo */
    val shadow: Boolean,
    val dark: Boolean,
    /** lo sfondo scelto davvero (tonal, ios, retro, none...) */
    val bgKind: String = "tonal"
)

/** Una famiglia di colori: tonalità da 0 (nero) a 100 (bianco) per accento 1/2/3 e neutri. */
interface Tones {
    fun a1(t: Int): Int
    fun a2(t: Int): Int
    fun a3(t: Int): Int
    fun n1(t: Int): Int
    fun n2(t: Int): Int
}

/** Material You del sistema (Android 12+). */
private class SystemTones(private val ctx: Context) : Tones {
    private val steps = intArrayOf(100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)
    private fun idx(t: Int) = steps.indices.minByOrNull { abs(steps[it] - t) } ?: 0
    private fun get(ids: IntArray, t: Int) = ctx.getColor(ids[idx(t)])

    private val A1 = intArrayOf(
        android.R.color.system_accent1_0, android.R.color.system_accent1_10, android.R.color.system_accent1_50,
        android.R.color.system_accent1_100, android.R.color.system_accent1_200, android.R.color.system_accent1_300,
        android.R.color.system_accent1_400, android.R.color.system_accent1_500, android.R.color.system_accent1_600,
        android.R.color.system_accent1_700, android.R.color.system_accent1_800, android.R.color.system_accent1_900,
        android.R.color.system_accent1_1000
    )
    private val A2 = intArrayOf(
        android.R.color.system_accent2_0, android.R.color.system_accent2_10, android.R.color.system_accent2_50,
        android.R.color.system_accent2_100, android.R.color.system_accent2_200, android.R.color.system_accent2_300,
        android.R.color.system_accent2_400, android.R.color.system_accent2_500, android.R.color.system_accent2_600,
        android.R.color.system_accent2_700, android.R.color.system_accent2_800, android.R.color.system_accent2_900,
        android.R.color.system_accent2_1000
    )
    private val A3 = intArrayOf(
        android.R.color.system_accent3_0, android.R.color.system_accent3_10, android.R.color.system_accent3_50,
        android.R.color.system_accent3_100, android.R.color.system_accent3_200, android.R.color.system_accent3_300,
        android.R.color.system_accent3_400, android.R.color.system_accent3_500, android.R.color.system_accent3_600,
        android.R.color.system_accent3_700, android.R.color.system_accent3_800, android.R.color.system_accent3_900,
        android.R.color.system_accent3_1000
    )
    private val N1 = intArrayOf(
        android.R.color.system_neutral1_0, android.R.color.system_neutral1_10, android.R.color.system_neutral1_50,
        android.R.color.system_neutral1_100, android.R.color.system_neutral1_200, android.R.color.system_neutral1_300,
        android.R.color.system_neutral1_400, android.R.color.system_neutral1_500, android.R.color.system_neutral1_600,
        android.R.color.system_neutral1_700, android.R.color.system_neutral1_800, android.R.color.system_neutral1_900,
        android.R.color.system_neutral1_1000
    )
    private val N2 = intArrayOf(
        android.R.color.system_neutral2_0, android.R.color.system_neutral2_10, android.R.color.system_neutral2_50,
        android.R.color.system_neutral2_100, android.R.color.system_neutral2_200, android.R.color.system_neutral2_300,
        android.R.color.system_neutral2_400, android.R.color.system_neutral2_500, android.R.color.system_neutral2_600,
        android.R.color.system_neutral2_700, android.R.color.system_neutral2_800, android.R.color.system_neutral2_900,
        android.R.color.system_neutral2_1000
    )

    override fun a1(t: Int) = get(A1, t)
    override fun a2(t: Int) = get(A2, t)
    override fun a3(t: Int) = get(A3, t)
    override fun n1(t: Int) = get(N1, t)
    override fun n2(t: Int) = get(N2, t)
}

/**
 * Famiglia di colori costruita da un solo colore (sfondo del telefono o colore personale):
 * la luminosità segue la tonalità richiesta, tinta e saturazione restano quelle del colore di partenza.
 */
private class SeedTones(seed: Int) : Tones {
    private val hue: Double
    private val chroma: Double

    init {
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(seed, lab)
        hue = Math.atan2(lab[2], lab[1])
        chroma = hypot(lab[1], lab[2]).coerceIn(18.0, 60.0)
    }

    private fun tone(t: Int, c: Double, h: Double): Int {
        // vicino al bianco e al nero il colore si spegne, come nelle tavolozze Material
        val k = 1.0 - (abs(t - 50) / 50.0).let { it * it }
        val cc = c * (0.25 + 0.75 * k)
        return ColorUtils.LABToColor(t.toDouble(), cc * cos(h), cc * sin(h))
    }

    override fun a1(t: Int) = tone(t, chroma, hue)
    override fun a2(t: Int) = tone(t, chroma / 3.0, hue)
    override fun a3(t: Int) = tone(t, chroma * 0.8, hue + Math.toRadians(60.0))
    override fun n1(t: Int) = tone(t, 4.0, hue)
    override fun n2(t: Int) = tone(t, 8.0, hue)
}

object Palettes {
    @Volatile private var wallSeed: Int? = null
    @Volatile private var wallRead = 0L

    /** Colore principale dello sfondo del telefono (riletto al massimo ogni 10 secondi). */
    fun wallpaperSeed(ctx: Context): Int {
        val now = System.currentTimeMillis()
        wallSeed?.let { if (now - wallRead < 10_000) return it }
        val c = try {
            WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
        } catch (e: Throwable) {
            null
        } ?: ctx.getColor(android.R.color.system_accent1_500)
        wallSeed = c
        wallRead = now
        return c
    }

    fun tones(ctx: Context, s: Style): Tones = when (s.colorSource) {
        "wallpaper" -> SeedTones(wallpaperSeed(ctx))
        "custom" -> SeedTones(s.customColor)
        else -> SystemTones(ctx)
    }

    fun isNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /** Il widget è scuro? Con "auto" decide la modalità scura richiesta (quella del telefono se null). */
    fun darkFor(s: Style, nightNow: Boolean) = when (s.darkMode) {
        "light" -> false
        "dark" -> true
        else -> nightNow
    }

    /** Colore vivo dello stile (per iOS e Retrò, dove i colori sono pieni). */
    fun vivid(ctx: Context, s: Style, dark: Boolean): Int = when (s.colorSource) {
        "custom" -> s.customColor
        else -> tones(ctx, s).a1(if (dark) 70 else 50)
    }

    /** Lo sfondo "dello stile" per ogni tipo di widget. */
    fun lookBackground(t: Template?): String = when (t?.look) {
        null -> "tonal"
        "material" -> when (t.kind) {
            "glance" -> "none"
            "weather", "analog", "calendar" -> "tonal3"
            "battery", "device", "music" -> "tonal2"
            else -> "tonal"
        }
        "ios" -> "ios"
        "retro" -> "retro"
        "oneui" -> "oneui"
        "hyper" -> "hyper"
        "nothing" -> "nothing"
        else -> "none"
    }

    fun palette(ctx: Context, s: Style, dark: Boolean, tpl: Template? = null): Pal {
        val t = tones(ctx, s)
        fun a(alpha: Int, c: Int) = ColorUtils.setAlphaComponent(c, alpha)
        val op = (s.opacity.coerceIn(0, 100) * 255 / 100)
        var border: Int? = null
        var shadow = false
        val bg: Int
        val text: Int
        var accent: Int
        var dimC: Int? = null
        val kind = if (s.background == "look") lookBackground(tpl) else s.background
        when (kind) {
            "tonal2" -> { bg = if (dark) t.a2(30) else t.a2(90); text = if (dark) t.a2(90) else t.a2(10); accent = if (dark) t.a1(80) else t.a1(40) }
            "tonal3" -> { bg = if (dark) t.a3(30) else t.a3(90); text = if (dark) t.a3(90) else t.a3(10); accent = if (dark) t.a1(80) else t.a1(40) }
            "neutral" -> { bg = if (dark) t.n1(17) else t.n1(94); text = if (dark) t.n1(90) else t.n1(10); accent = if (dark) t.a1(80) else t.a1(40) }
            "filled" -> { bg = if (dark) t.a1(80) else t.a1(40); text = if (dark) t.a1(20) else t.a1(100); accent = if (dark) t.a1(30) else t.a1(90) }
            "ios" -> {
                bg = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
                text = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                dimC = 0xFF8E8E93.toInt()
                accent = vivid(ctx, s, dark)
            }
            "oneui" -> {
                bg = if (dark) 0xFF232323.toInt() else 0xFFFCFCFC.toInt()
                text = if (dark) 0xFFFAFAFA.toInt() else 0xFF111111.toInt()
                dimC = if (dark) 0xFFA0A0A0.toInt() else 0xFF767676.toInt()
                accent = vivid(ctx, s, dark)
            }
            "hyper" -> {
                bg = if (dark) 0xFF1E1F22.toInt() else 0xFFF5F6F8.toInt()
                text = if (dark) 0xFFF2F2F2.toInt() else 0xFF1A1A1A.toInt()
                dimC = if (dark) 0xFF9A9CA1.toInt() else 0xFF7C7E83.toInt()
                accent = vivid(ctx, s, dark)
            }
            "nothing" -> {
                bg = if (dark) 0xFF1B1B1B.toInt() else 0xFFEFEFEF.toInt()
                text = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                dimC = 0xFF8A8A8A.toInt()
                accent = vivid(ctx, s, dark)
            }
            "retro" -> {
                bg = if (dark) 0xFF1B1B1D.toInt() else 0xFFECE6D8.toInt()
                text = if (dark) 0xFFEDEDED.toInt() else 0xFF2A2622.toInt()
                accent = vivid(ctx, s, dark)
            }
            "glass" -> {
                bg = if (dark) a(66, 0xFF000000.toInt()) else a(97, 0xFFFFFFFF.toInt())
                text = if (dark) 0xFFFFFFFF.toInt() else 0xFF15171C.toInt()
                accent = if (dark) t.a1(80) else t.a1(40)
                border = if (dark) a(56, 0xFFFFFFFF.toInt()) else a(150, 0xFFFFFFFF.toInt())
            }
            "none" -> {
                bg = 0
                text = if (dark) 0xFFFFFFFF.toInt() else 0xFF15171C.toInt()
                accent = if (dark) t.a1(80) else t.a1(40)
                shadow = true
            }
            else -> { bg = if (dark) t.a1(30) else t.a1(90); text = if (dark) t.a1(90) else t.a1(10); accent = if (dark) t.a1(80) else t.a1(40) }
        }
        if (s.colorSource == "custom" && kind !in setOf("ios", "retro", "filled", "oneui", "hyper", "nothing")) {
            // colore personale: l'accento resta riconoscibile ma leggibile sul fondo
            accent = if (dark) t.a1(80) else t.a1(40)
        }
        val finalBg = if (kind == "glass" || kind == "none") bg else ColorUtils.setAlphaComponent(bg, op)
        return Pal(
            bg = finalBg,
            text = text,
            dim = dimC ?: ColorUtils.setAlphaComponent(text, 190),
            accent = accent,
            face = when (kind) {
                "ios" -> if (dark) 0xFF2C2C2E.toInt() else 0xFFF2F2F7.toInt()
                "nothing" -> if (dark) 0xFF2A2A2A.toInt() else 0xFFE2E2E2.toInt()
                "oneui", "hyper" -> if (dark) 0xFF2E2F33.toInt() else 0xFFEBECEF.toInt()
                else -> if (dark) t.n1(12) else t.n1(99)
            },
            faceInk = if (dark) t.n1(90) else t.n1(10),
            tick = if (dark) t.n2(70) else t.n2(40),
            track = ColorUtils.setAlphaComponent(text, 40),
            border = border,
            shadow = shadow || s.opacity < 50,
            dark = dark,
            bgKind = kind
        )
    }
}
