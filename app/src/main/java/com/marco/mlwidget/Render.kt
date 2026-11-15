package com.marco.mlwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Tutto quello che serve per disegnare: meteo, batteria, musica, calendario e ora. */
class WData(
    val weather: Weather?,
    val battery: Battery,
    val device: Device,
    val events: List<Event>,
    val calendarAllowed: Boolean,
    val track: Track?,
    val musicAllowed: Boolean,
    val now: Long,
    val is24: Boolean,
    val steps: Int = 0,
    val stepsAllowed: Boolean = true
) {
    fun at(time: Long) = WData(weather, battery, device, events, calendarAllowed, track, musicAllowed, time, is24, steps, stepsAllowed)

    companion object {
        /** Con [sample] i dati che mancano vengono riempiti con un esempio (per le anteprime). */
        fun load(ctx: Context, sample: Boolean = false, t: Template? = null): WData {
            val need = { k: Boolean -> t == null || k }
            val calOk = CalendarRepo.hasPermission(ctx)
            var events = if (need(t?.usesCalendar == true)) CalendarRepo.upcoming(ctx) else emptyList()
            if (sample && events.isEmpty()) events = sampleEvents()
            var track = if (need(t?.usesMusic == true)) MusicRepo.read(ctx) else null
            if (sample && track == null) track = Track("Nome del brano", "Artista", true, "", null)
            return WData(
                WeatherRepo.cached(ctx) ?: if (sample) Weather.sample() else null,
                readBattery(ctx), readDevice(ctx), events, calOk || sample, track, MusicRepo.hasAccess(ctx) || sample,
                System.currentTimeMillis(), DateFormat.is24HourFormat(ctx),
                StepsRepo.cached(ctx).let { if (sample && it == 0) 5234 else it },
                StepsRepo.hasPermission(ctx) || sample
            )
        }

        private fun sampleEvents(): List<Event> {
            val c = Calendar.getInstance().apply { set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
            c.add(Calendar.HOUR_OF_DAY, 1)
            val a = c.timeInMillis
            c.add(Calendar.HOUR_OF_DAY, 3)
            val b = c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
            val d = c.timeInMillis
            return listOf(
                Event("Riunione", a, a + 3_600_000L, false, 0xFF4F6BED.toInt()),
                Event("Palestra", b, b + 3_600_000L, false, 0xFF2EAD6B.toInt()),
                Event("Compleanno di Luca", d, d + 3_600_000L, true, 0xFFE0508A.toInt())
            )
        }
    }
}

/** Un orologio "vivo": sul widget vero è un orologio di sistema posizionato sopra l'immagine. */
class LiveSlot(
    val rect: RectF,
    val textSize: Float,
    val format24: String,
    val format12: String,
    val color: Int,
    val font: String,
    val shadow: Boolean,
    val align: String = "center"
)

class Drawn(
    val live: List<LiveSlot>,
    val analog: RectF?,
    /** zone da toccare (prev, play, next) */
    val zones: List<Pair<RectF, String>>
)

object Renderer {
    const val RED = 0xFFE5484D.toInt()

    fun typeface(font: String): Typeface = when (font) {
        "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "regular" -> Typeface.create("sans-serif", Typeface.NORMAL)
        "medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        "mono" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        "dot" -> dot ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        else -> Typeface.create("sans-serif", Typeface.BOLD)
    }

    /** Carattere a puntini (Doto, licenza OFL), caricato all'avvio dell'app. */
    @Volatile var dot: Typeface? = null

    fun loadFonts(ctx: Context) {
        if (dot == null) dot = try { androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.doto) } catch (e: Exception) { null }
    }

    fun textPaint(size: Float, color: Int, tf: Typeface, pal: Pal?) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = tf
        if (pal?.shadow == true) setShadowLayer(size * 0.08f + 1.5f, 0f, 1f, if (pal.dark) 0x99000000.toInt() else 0x88FFFFFF.toInt())
    }

    /** Riquadro del testo agganciato al punto (x, y) secondo l'allineamento, centrato in verticale. */
    fun textBox(str: String, x: Float, y: Float, size: Float, align: String, tf: Typeface): RectF {
        val p = textPaint(size, 0, tf, null)
        val tw = p.measureText(str) + size * 0.08f
        val fm = p.fontMetrics
        val th = fm.descent - fm.ascent
        val left = when (align) {
            "left" -> x
            "right" -> x - tw
            else -> x - tw / 2
        }
        return RectF(left, y - th / 2, left + tw, y + th / 2)
    }

    fun drawTextIn(c: Canvas, str: String, r: RectF, size: Float, align: String, color: Int, tf: Typeface, pal: Pal?) {
        val p = textPaint(size, color, tf, pal)
        val fm = p.fontMetrics
        val base = r.centerY() - (fm.ascent + fm.descent) / 2
        when (align) {
            "left" -> { p.textAlign = Paint.Align.LEFT; c.drawText(str, r.left, base, p) }
            "right" -> { p.textAlign = Paint.Align.RIGHT; c.drawText(str, r.right, base, p) }
            else -> { p.textAlign = Paint.Align.CENTER; c.drawText(str, r.centerX(), base, p) }
        }
    }

    fun low(b: Battery) = b.level <= 15 && !b.charging

    fun batteryStatus(b: Battery) = when {
        b.charging && b.level >= 100 -> "Carica completa"
        b.charging -> "In carica"
        low(b) -> "Batteria scarica"
        else -> "Batteria"
    }

    /** Disegna il widget in coordinate dp (la tela deve essere già scalata). */
    fun draw(
        ctx: Context, c: Canvas, t: Template, cfg: Cfg, w: Float, h: Float, pal: Pal, data: WData,
        drawLive: Boolean, skipAnalog: Boolean = false
    ): Drawn {
        val r = Painter(ctx, c, t, cfg, w, h, pal, data, drawLive, skipAnalog)
        try {
            r.run()
        } catch (e: Throwable) {
            AppLog.e("Disegno ${t.id}", e)
        }
        return Drawn(r.live, r.analog, r.zones)
    }
}

/** Il disegno di un widget: un oggetto per ogni ridisegno, con tutti gli strumenti a portata di mano. */
private class Painter(
    val ctx: Context, val c: Canvas, val t: Template, val cfg: Cfg, val w: Float, val h: Float,
    val pal: Pal, val d: WData, val drawLive: Boolean, val skipAnalog: Boolean
) {
    val s = cfg.style
    val live = ArrayList<LiveSlot>()
    var analog: RectF? = null
    val zones = ArrayList<Pair<RectF, String>>()
    val loc: Locale = Locale.getDefault()
    val now = Date(d.now)
    val k = s.textScale / 100f
    val m = min(w, h)
    val tfNum = Renderer.typeface(s.font)
    val tfL: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val tfR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val tfM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val tfB: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val wx = d.weather

    fun on(key: String) = cfg.on(key)
    fun fmt(p: String) = SimpleDateFormat(p, loc).format(now)
    fun cap(x: String) = x.replaceFirstChar { it.titlecase(loc) }
    val nothingLook = t.look == "nothing"
    fun up(x: String) = if (nothingLook) x.uppercase(loc) else x
    val weekday get() = up(cap(fmt("EEEE")))
    val dayMonth get() = up(fmt("d MMMM"))
    val shortDate get() = up(cap(fmt("EEE d MMM").replace(".", "")))
    /** Carattere per date e scritte piccole: a puntini con Nothing. */
    val tfD: Typeface get() = if (nothingLook) Renderer.typeface("dot") else tfM

    // ------------------------------------------------------------------ strumenti

    fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    fun background(color: Int = pal.bg, corner: Float = s.corner.toFloat()) {
        if (Argb.alpha(color) == 0) return
        val r = corner.coerceAtMost(m / 2f)
        c.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint(color))
        pal.border?.let { b ->
            c.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r, r, paint(b).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
        }
    }

    /** Grandezza perché il testo stia in larghezza (e non superi [size]). */
    fun fit(str: String, size: Float, maxW: Float, tf: Typeface): Float {
        if (maxW <= 0f) return size
        val wNow = Renderer.textPaint(size, 0, tf, null).measureText(str)
        return if (wNow <= maxW) size else size * maxW / wNow
    }

    /**
     * Testo con il centro verticale in [y]. Rimpicciolisce fino a 3/4 per stare in [maxW], poi taglia con "…".
     * Restituisce la larghezza occupata.
     */
    fun text(
        str: String, x: Float, y: Float, size0: Float, color: Int, tf: Typeface = tfR, align: String = "left",
        maxW: Float = Float.MAX_VALUE, shadow: Boolean = pal.shadow
    ): Float {
        if (str.isEmpty() || size0 <= 0.5f) return 0f
        var size = size0 * k
        val p = Renderer.textPaint(size, color, tf, if (shadow) pal else null)
        var tw = p.measureText(str)
        var out = str
        if (tw > maxW) {
            size = max(size * maxW / tw, size * 0.75f)
            p.textSize = size
            tw = p.measureText(str)
            if (tw > maxW) {
                out = TextUtils.ellipsize(str, p, maxW, TextUtils.TruncateAt.END).toString()
                tw = p.measureText(out)
            }
        }
        val fm = p.fontMetrics
        val base = y - (fm.ascent + fm.descent) / 2
        p.textAlign = when (align) { "right" -> Paint.Align.RIGHT; "center" -> Paint.Align.CENTER; else -> Paint.Align.LEFT }
        c.drawText(out, x, base, p)
        return tw
    }

    /** Altezza di una riga di testo di quella grandezza. */
    fun lineH(size: Float) = size * k * 1.18f

    /**
     * Ora digitale nel riquadro [box]: si ingrandisce quanto possibile. Ore e minuti possono avere colori diversi.
     * Sul widget vero diventano orologi di sistema.
     */
    fun clock(
        box: RectF, align: String, hc: Int, mc: Int, tf: Typeface = tfNum, font: String = s.font,
        secs: Boolean = false, maxSize: Float = Float.MAX_VALUE, shadow: Boolean = pal.shadow
    ): RectF {
        val sample = "88:88"
        val p100 = Renderer.textPaint(100f, 0, tf, null)
        val wid = p100.measureText(sample) + (if (secs) p100.measureText(" 88") * 0.45f else 0f)
        val fm = p100.fontMetrics
        val capH = (fm.descent - fm.ascent) * 0.78f
        var size = min(box.width() / wid * 100f, box.height() / capH * 100f).coerceAtMost(maxSize)
        size = max(size, 6f)
        val hw = Renderer.textPaint(size, 0, tf, null).measureText("88")
        val mw = Renderer.textPaint(size, 0, tf, null).measureText(":88")
        val sw = if (secs) Renderer.textPaint(size * 0.45f, 0, tf, null).measureText(" 88") else 0f
        val total = hw + mw + sw
        val left = when (align) { "left" -> box.left; "right" -> box.right - total; else -> box.centerX() - total / 2 }
        val cy = box.centerY()
        val same = hc == mc
        if (same) {
            liveText(RectF(left, cy, left + hw + mw, cy), size, "HH:mm", "h:mm", hc, font, tf, "center", shadow)
        } else {
            liveText(RectF(left, cy, left + hw, cy), size, "HH", "h", hc, font, tf, "right", shadow)
            liveText(RectF(left + hw, cy, left + hw + mw, cy), size, ":mm", ":mm", mc, font, tf, "left", shadow)
        }
        if (secs) {
            val ss = size * 0.45f
            val sx = left + hw + mw
            val base = Renderer.textPaint(size, 0, tf, null).fontMetrics
            val sCy = cy + (base.descent - base.ascent) * 0.18f
            liveText(RectF(sx, sCy, sx + sw, sCy), ss, " ss", " ss", ColorUtils.setAlphaComponent(mc, 200), font, tf, "left", shadow)
        }
        val fmS = Renderer.textPaint(size, 0, tf, null).fontMetrics
        return RectF(left, cy - (fmS.descent - fmS.ascent) / 2, left + total, cy + (fmS.descent - fmS.ascent) / 2)
    }

    /** Un pezzo di ora: [r] dà sinistra, destra e centro verticale. */
    fun liveText(r: RectF, size: Float, f24: String, f12: String, color: Int, font: String, tf: Typeface, align: String, shadow: Boolean) {
        val fm = Renderer.textPaint(size, 0, tf, null).fontMetrics
        val th = fm.descent - fm.ascent
        val box = RectF(r.left, r.centerY() - th / 2, r.right, r.centerY() + th / 2)
        if (drawLive) {
            Renderer.drawTextIn(c, SimpleDateFormat(if (d.is24) f24 else f12, loc).format(now), box, size, align, color, tf, if (shadow) pal else null)
        } else {
            live += LiveSlot(box, size, f24, f12, color, font, shadow, align)
        }
    }

    fun ring(cx: Float, cy: Float, r: Float, frac: Float, color: Int, track: Int = pal.track, sw: Float = r * 0.16f) {
        val rr = RectF(cx - r + sw / 2, cy - r + sw / 2, cx + r - sw / 2, cy + r - sw / 2)
        val p = paint(track).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND }
        c.drawArc(rr, 0f, 360f, false, p)
        p.color = color
        if (frac > 0.001f) c.drawArc(rr, -90f, 360f * frac.coerceIn(0f, 1f), false, p)
    }

    fun bar(r: RectF, frac: Float, color: Int, track: Int = pal.track) {
        val rad = r.height() / 2
        c.drawRoundRect(r, rad, rad, paint(track))
        if (frac <= 0f) return
        val fw = max(r.width() * frac.coerceIn(0f, 1f), r.height())
        c.drawRoundRect(RectF(r.left, r.top, r.left + fw, r.bottom), rad, rad, paint(color))
    }

    /** Con lo stile Nothing le icone sono di un solo colore. */
    val monoIcon: Int? = if (t.look == "nothing") pal.text else null

    fun icon(cx: Float, cy: Float, size: Float, code: Int, isDay: Boolean) {
        val r = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        val mono = monoIcon
        if (mono == null) { WeatherIcons.draw(c, r, code, isDay); return }
        val save = c.saveLayer(r, null)
        WeatherIcons.draw(c, r, code, isDay)
        c.drawColor(mono, android.graphics.PorterDuff.Mode.SRC_IN)
        c.restoreToCount(save)
    }

    fun bitmap(b: Bitmap, r: RectF, radius: Float) {
        val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val sc = max(r.width() / b.width, r.height() / b.height)
        val mx = Matrix().apply {
            setScale(sc, sc)
            postTranslate(r.centerX() - b.width * sc / 2, r.centerY() - b.height * sc / 2)
        }
        sh.setLocalMatrix(mx)
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = sh })
    }

    fun shade(col: Int, f: Float) = ColorUtils.blendARGB(col, 0xFF000000.toInt(), f)
    fun tint(col: Int, f: Float) = ColorUtils.blendARGB(col, 0xFFFFFFFF.toInt(), f)

    // ------------------------------------------------------------------ smistamento

    fun run() {
        when (t.kind) {
            "digital" -> digital()
            "analog" -> analogClock()
            "flip" -> flip()
            "lcd" -> lcd()
            "weather" -> when (t.look) {
                "ios" -> weatherIos()
                "oneui" -> weatherOneUi()
                "hyper" -> weatherHyper()
                "nothing" -> weatherNothing()
                "minimal" -> weatherMinimal()
                else -> weatherMaterial()
            }
            "battery" -> when (t.look) {
                "ios" -> batteryIos()
                "oneui" -> batteryOneUi()
                "hyper" -> batteryHyper()
                "nothing" -> batteryNothing()
                "minimal" -> batteryMinimal()
                else -> batteryMaterial()
            }
            "calendar" -> calendar()
            "tearoff" -> tearoff()
            "music" -> music()
            "ipod" -> ipod()
            "device" -> device()
            "glance" -> glance()
            "note" -> note()
            "search" -> search()
            "earbuds" -> earbuds()
            "steps" -> steps()
            "quote" -> quote()
        }
    }

    // ------------------------------------------------------------------ orologio digitale

    fun digital() {
        background()
        val minimal = t.look == "minimal" || t.look == "hyper"
        val nothing = t.look == "nothing"
        val hc = pal.text
        val mc = if (t.look == "material") pal.accent else pal.text
        if (nothing) {
            // il puntino rosso di Nothing
            c.drawCircle(w - m * 0.1f, m * 0.1f, m * 0.035f, paint(pal.accent))
        }
        val pad = (m * 0.12f).coerceIn(8f, 20f)
        val ratio = w / h
        val date = on("date")
        if (ratio >= 2.1f || h < 70f) {
            // in riga: l'ora a sinistra, la data a destra
            val showDate = date && w >= 180f
            val tw = if (showDate) w * 0.58f else w - pad * 2
            val al = if (showDate || !minimal) "left" else "center"
            clock(RectF(pad, h * 0.12f, pad + tw, h * 0.88f), al, hc, mc, secs = s.seconds)
            if (showDate) {
                val ds = (h * 0.15f).coerceIn(10f, 20f)
                val maxW = w - pad * 2 - tw - 6f
                val long = maxW > ds * 7
                text(if (long) weekday else up(cap(fmt("EEE").replace(".", ""))), w - pad, h * 0.5f - lineH(ds) * 0.55f, ds, pal.text, tfD, "right", maxW)
                text(if (long) dayMonth else up(fmt("d MMM").replace(".", "")), w - pad, h * 0.5f + lineH(ds) * 0.55f, ds, pal.dim, if (nothingLook) tfD else tfR, "right", maxW)
            }
        } else if (ratio >= 1.25f) {
            // ora grande con la data sotto
            val ds = (h * 0.11f).coerceIn(10f, 22f)
            val dateH = if (date && h >= 90f) lineH(ds) + 4f else 0f
            val al = if (minimal) "center" else "left"
            val r = clock(RectF(pad, pad * 0.6f, w - pad, h - pad * 0.6f - dateH), al, hc, mc, secs = s.seconds)
            if (dateH > 0f) {
                val x = if (al == "center") w / 2 else r.left + 2f
                text("$weekday $dayMonth", x, r.bottom + lineH(ds) * 0.45f, ds, pal.text, tfD, al, w - pad * 2)
            }
        } else {
            // quadrato o alto: ore sopra i minuti
            val ds = (w * 0.085f).coerceIn(10f, 18f)
            val dateH = if (date && h >= 110f) lineH(ds) + 6f else 0f
            val al = if (minimal) "center" else "left"
            val avail = h - pad * 1.2f - dateH
            val box1 = RectF(pad, pad * 0.6f, w - pad, pad * 0.6f + avail / 2)
            val box2 = RectF(pad, pad * 0.6f + avail / 2, w - pad, pad * 0.6f + avail)
            val p100 = Renderer.textPaint(100f, 0, tfNum, null)
            val fm = p100.fontMetrics
            val size = min(box1.width() / p100.measureText("88") * 100f, box1.height() / ((fm.descent - fm.ascent) * 0.8f) * 100f)
            val x0 = if (al == "center") box1.left else box1.left
            liveText(RectF(x0, box1.centerY() + size * 0.04f, box1.right, box1.centerY() + size * 0.04f), size, "HH", "hh", hc, s.font, tfNum, al, pal.shadow)
            liveText(RectF(x0, box2.centerY() - size * 0.04f, box2.right, box2.centerY() - size * 0.04f), size, "mm", "mm", mc, s.font, tfNum, al, pal.shadow)
            if (dateH > 0f) text(shortDate, if (al == "center") w / 2 else pad + 2f, h - pad * 0.6f - lineH(ds) / 2, ds, pal.text, tfD, al, w - pad * 2)
        }
    }

    // ------------------------------------------------------------------ orologio analogico

    fun analogClock() {
        val sense = t.look == "sense"
        val ios = t.look == "ios"
        if (!sense) background()
        val pad = if (sense || t.look == "minimal") m * 0.03f else m * 0.08f
        val wide = w >= h * 1.5f
        val placeOn = sense && on("place")
        val pillH = if (placeOn) (m * 0.13f).coerceIn(14f, 30f) else 0f
        val dateSide = !sense && wide && on("date")
        var size = min(if (dateSide) h - pad * 2 else w - pad * 2, h - pad * 2 - pillH * 1.25f)
        size = max(size, 10f)
        val cx = if (dateSide) pad + size / 2 + (h - size) * 0.1f else w / 2
        val cy = pad + (h - pad * 2 - pillH * 1.25f) / 2
        val rect = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
        analog = rect
        if (!skipAnalog) {
            Analog.drawDial(c, rect, s.dial, pal, now, loc)
            if (drawLive) {
                val cal = Calendar.getInstance().apply { timeInMillis = d.now }
                val sec = cal.get(Calendar.SECOND).toFloat()
                val mn = cal.get(Calendar.MINUTE) + (if (s.seconds) sec / 60f else 0f)
                val hr = cal.get(Calendar.HOUR) + mn / 60f
                Analog.drawHand(c, rect, hr * 30f, "hour", s, pal)
                Analog.drawHand(c, rect, mn * 6f, "minute", s, pal, cap = !s.seconds)
                if (s.seconds) Analog.drawHand(c, rect, sec * 6f, "second", s, pal, cap = true)
            }
        }
        if (placeOn) {
            val str = wx?.place?.ifBlank { null } ?: java.util.TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
            pill(str, w / 2, h - pad - pillH / 2, pillH * 0.55f)
        }
        if (dateSide) {
            val x = rect.right + (w - rect.right) * 0.14f
            val maxW = w - x - pad
            val fs = (h * 0.13f).coerceIn(11f, 22f)
            if (ios) {
                val city = (wx?.place?.ifBlank { null } ?: java.util.TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' '))
                text(city, x, h * 0.36f, fs * 1.1f, pal.text, tfB, "left", maxW)
                text("Oggi", x, h * 0.36f + lineH(fs) * 1.05f, fs * 0.85f, pal.dim, tfR, "left", maxW)
                text(dayMonth, x, h * 0.36f + lineH(fs) * 1.95f, fs * 0.85f, pal.dim, tfR, "left", maxW)
            } else {
                text(weekday, x, h * 0.42f, fs, pal.text, tfM, "left", maxW)
                text(dayMonth, x, h * 0.42f + lineH(fs), fs, pal.dim, tfR, "left", maxW)
            }
        }
    }

    fun pill(str: String, x: Float, y: Float, size: Float) {
        val p = Renderer.textPaint(size * k, 0xFFFFFFFF.toInt(), tfM, null)
        val tw = min(p.measureText(str), w * 0.8f)
        val ph = size * k * 1.9f
        val r = RectF(x - tw / 2 - size * 0.9f, y - ph / 2, x + tw / 2 + size * 0.9f, y + ph / 2)
        c.drawRoundRect(r, ph / 2, ph / 2, paint(0x8C000000.toInt()))
        text(str, x, y, size, 0xFFFFFFFF.toInt(), tfM, "center", tw, shadow = false)
    }

    // ------------------------------------------------------------------ cartellini

    fun flip() {
        val sense = t.look == "sense"
        if (!sense && Argb.alpha(pal.bg) > 0 && pal.bgKind != "retro") background()
        val pad = m * 0.04f
        val weatherOn = sense && on("weather") && h >= w * 0.36f
        val dateOn = !sense && on("date") && h >= 60f
        val vertical = h > w * 1.15f
        val bottom = when {
            weatherOn -> h * 0.40f
            dateOn -> (h * 0.2f).coerceIn(16f, 34f)
            else -> 0f
        }
        val plate = RectF(pad, pad, w - pad, h - pad - bottom)
        if (sense) {
            val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, plate.top, 0f, plate.bottom, 0x4D000000, 0x73000000, Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(plate, 10f, 10f, pp)
            c.drawRoundRect(plate, 10f, 10f, paint(0x2EFFFFFF).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
        }
        val gap = m * 0.05f
        val inner = if (sense) m * 0.05f else 0f
        val cards = if (s.seconds && !vertical && w >= h * 2.4f) 3 else 2
        val formats = listOf(Triple("HH", "hh", 0), Triple("mm", "mm", 1), Triple("ss", "ss", 2)).take(cards)
        for ((f24, f12, i) in formats) {
            val card = if (vertical) {
                val ch = (plate.height() - inner * 2 - gap) / 2
                val top = plate.top + inner + i * (ch + gap)
                RectF(plate.left + inner, top, plate.right - inner, top + ch)
            } else {
                val cw = (plate.width() - inner * 2 - gap * (cards - 1)) / cards
                val l = plate.left + inner + i * (cw + gap)
                RectF(l, plate.top + inner, l + cw, plate.bottom - inner)
            }
            flipCard(card, f24, f12, light = sense)
        }
        if (weatherOn) flipWeather(RectF(pad, h - bottom, w - pad, h - pad))
        if (dateOn) {
            val fs = bottom * 0.5f
            text(shortDate, w / 2, h - pad - bottom / 2, fs, if (pal.bgKind == "retro") pal.text else pal.text, tfM, "center", w - pad * 2)
        }
    }

    fun flipCard(card: RectF, f24: String, f12: String, light: Boolean) {
        val mid = card.centerY()
        val rad = min(card.width(), card.height()) * 0.08f
        val topCols = if (light) intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F0F0.toInt(), 0xFFDADADA.toInt())
            else intArrayOf(0xFF3A3A3D.toInt(), 0xFF2C2C2F.toInt(), 0xFF232326.toInt())
        val botCols = if (light) intArrayOf(0xFFCFCFCF.toInt(), 0xFFE9E9E9.toInt(), 0xFFF7F7F7.toInt())
            else intArrayOf(0xFF1C1C1E.toInt(), 0xFF262629.toInt(), 0xFF2E2E31.toInt())
        // ombra
        c.drawRoundRect(RectF(card.left, card.top + 1.5f, card.right, card.bottom + 1.5f), rad, rad, paint(0x40000000))
        c.drawRoundRect(card, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, mid, 0f, card.bottom, botCols, null, Shader.TileMode.CLAMP)
        })
        c.save(); c.clipRect(card.left, card.top, card.right, mid)
        c.drawRoundRect(card, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, card.top, 0f, mid, topCols, null, Shader.TileMode.CLAMP)
        })
        c.restore()
        val ink = if (light) 0xFF1C1C1C.toInt() else 0xFFF2F2F2.toInt()
        val p100 = Renderer.textPaint(100f, 0, tfNum, null)
        val fm = p100.fontMetrics
        val size = min(card.width() * 0.86f / p100.measureText("88") * 100f, card.height() * 0.92f / ((fm.descent - fm.ascent) * 0.78f) * 100f)
        liveText(RectF(card.left, mid, card.right, mid), size, f24, f12, ink, s.font, tfNum, "center", false)
        c.drawRect(card.left, mid - 0.8f, card.right, mid + 0.8f, paint(if (light) 0xFF2B2B2B.toInt() else 0xFF0A0A0A.toInt()))
        val hinge = paint(0xFF8A8D92.toInt())
        val hh = card.height() * 0.08f
        c.drawRoundRect(RectF(card.left - 1f, mid - hh, card.left + 3f, mid + hh), 2f, 2f, hinge)
        c.drawRoundRect(RectF(card.right - 3f, mid - hh, card.right + 1f, mid + hh), 2f, 2f, hinge)
    }

    fun flipWeather(r: RectF) {
        val white = 0xFFFFFFFF.toInt()
        val fs = (r.height() * 0.24f).coerceIn(8f, 18f)
        val iconS = min(r.height() * 1.05f, w * 0.24f)
        if (wx == null) {
            text("Meteo in arrivo…", r.centerX(), r.centerY(), fs, white, tfR, "center", r.width())
            return
        }
        val third = r.width() / 3
        text(wx.place.ifBlank { "Meteo" }, r.left + 4f, r.centerY() - lineH(fs) * 0.5f, fs * 1.15f, white, tfB, "left", third + iconS * 0.1f)
        text(weatherText(wx.code), r.left + 4f, r.centerY() + lineH(fs) * 0.6f, fs * 0.9f, white, tfR, "left", third)
        icon(r.centerX(), r.top + r.height() * 0.3f, iconS, wx.code, wx.isDay)
        text(shortDate, r.right - 4f, r.centerY() - lineH(fs) * 0.5f, fs * 0.9f, white, tfR, "right", third)
        text("${wx.temp}°  ${wx.hi}°/${wx.lo}°", r.right - 4f, r.centerY() + lineH(fs) * 0.6f, fs * 1.05f, white, tfM, "right", third)
    }

    // ------------------------------------------------------------------ sveglia LCD

    fun lcd() {
        val dark = pal.dark
        val body = if (pal.bgKind == "retro") (if (dark) 0xFF141416.toInt() else 0xFF2A2A2E.toInt()) else pal.bg
        background(body)
        val pad = m * 0.09f
        val screen = RectF(pad, pad, w - pad, h - pad)
        val scrCol = if (dark) 0xFF070A08.toInt() else 0xFFA9B89A.toInt()
        c.drawRoundRect(screen, m * 0.05f, m * 0.05f, paint(scrCol))
        // riflesso del vetro
        c.drawRoundRect(screen, m * 0.05f, m * 0.05f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, screen.top, 0f, screen.bottom, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        val ink = if (dark) pal.accent else 0xFF1C2618.toInt()
        val dateOn = on("date") && h >= 70f
        val ds = (screen.height() * 0.14f).coerceIn(8f, 16f)
        val tBox = RectF(screen.left + screen.width() * 0.06f, screen.top + screen.height() * 0.1f, screen.right - screen.width() * 0.06f,
            screen.bottom - screen.height() * 0.1f - (if (dateOn) lineH(ds) + 2f else 0f))
        // cifre spente dietro (come i segmenti di una vera sveglia)
        val r = clockGhost(tBox, ink)
        clock(tBox, "center", ink, ink, secs = s.seconds, shadow = false)
        if (dateOn) {
            val str = fmt("EEE d MMM").replace(".", "").uppercase(loc)
            text(str, r.centerX(), screen.bottom - screen.height() * 0.08f - lineH(ds) / 2, ds, ColorUtils.setAlphaComponent(ink, 210), Renderer.typeface("mono"), "center", screen.width() * 0.9f, shadow = false)
        }
    }

    fun clockGhost(box: RectF, ink: Int): RectF {
        val p100 = Renderer.textPaint(100f, 0, tfNum, null)
        val wid = p100.measureText("88:88") + (if (s.seconds) p100.measureText(" 88") * 0.45f else 0f)
        val fm = p100.fontMetrics
        val size = min(box.width() / wid * 100f, box.height() / ((fm.descent - fm.ascent) * 0.78f) * 100f)
        val p = Renderer.textPaint(size, ColorUtils.setAlphaComponent(ink, 26), tfNum, null)
        val tw = p.measureText("88:88")
        val sw = if (s.seconds) Renderer.textPaint(size * 0.45f, 0, tfNum, null).measureText(" 88") else 0f
        val left = box.centerX() - (tw + sw) / 2
        val f = p.fontMetrics
        val base = box.centerY() - (f.ascent + f.descent) / 2
        c.drawText("88:88", left, base, p)
        return RectF(left, box.top, left + tw + sw, box.bottom)
    }

    // ------------------------------------------------------------------ meteo

    fun hiloStr(x: Weather) = "Max ${x.hi}°  Min ${x.lo}°"

    fun noWeather(color: Int) {
        val fs = (m * 0.12f).coerceIn(10f, 15f)
        text("Meteo in arrivo…", w / 2, h / 2 - lineH(fs) * 0.5f, fs, color, tfM, "center", w * 0.9f)
        text("Scegli la posizione nell'app", w / 2, h / 2 + lineH(fs) * 0.6f, fs * 0.85f, ColorUtils.setAlphaComponent(color, 180), tfR, "center", w * 0.9f)
    }

    fun weatherMaterial() {
        background()
        val x = wx ?: return noWeather(pal.text)
        val pad = (m * 0.1f).coerceIn(8f, 18f)
        when {
            w < 150f || h < 64f -> {
                // piccolissimo: icona e temperatura
                val ic = min(h * 0.7f, w * 0.42f)
                icon(pad + ic / 2, h / 2, ic, x.code, x.isDay)
                val ts = min(h * 0.5f, (w - ic - pad * 2.5f) * 0.55f)
                text("${x.temp}°", w - pad, h / 2, ts, pal.text, tfNum, "right", w - ic - pad * 2.5f)
            }
            h < 118f || (w >= 250f && h < 150f && !(on("hours") || on("days"))) -> weatherRow(x, RectF(0f, 0f, w, h), pad)
            w < 250f -> {
                // quadrato
                val ic = m * 0.36f
                icon(pad + ic / 2, pad + ic / 2, ic, x.code, x.isDay)
                val fs = (m * 0.085f).coerceIn(10f, 15f)
                if (on("place")) text(x.place, w - pad, pad + fs * 0.7f, fs, pal.text, tfM, "right", w - ic - pad * 3)
                val ts = min(m * 0.28f, h * 0.3f)
                val ty = h - pad - (if (on("hilo")) lineH(fs) else 0f) - (if (on("cond")) lineH(fs) else 0f) - lineH(ts) * 0.42f
                text("${x.temp}°", pad, ty, ts, pal.text, tfNum, "left", w - pad * 2)
                var y = ty + lineH(ts) * 0.42f + lineH(fs) * 0.5f
                if (on("cond")) { text(weatherText(x.code), pad, y, fs, pal.text, tfR, "left", w - pad * 2); y += lineH(fs) }
                if (on("hilo")) text("${x.hi}° / ${x.lo}°", pad, y, fs, pal.dim, tfR, "left", w - pad * 2)
            }
            else -> {
                // grande: intestazione + ore + giorni
                val wantH = on("hours"); val wantD = on("days")
                val headH = min(h * (if (wantH && wantD) 0.36f else 0.5f), 100f)
                weatherRow(x, RectF(0f, 0f, w, headH), pad)
                var top = headH
                val rest = h - headH - pad * 0.6f
                val rows = (if (wantH) 1 else 0) + (if (wantD) 1 else 0)
                if (rows == 0) return
                val rh = rest / rows
                if (wantH) { hoursRow(x, RectF(pad, top, w - pad, top + rh), pal.text, pal.dim); top += rh }
                if (wantD) daysRow(x, RectF(pad, top, w - pad, top + rh), pal.text, pal.dim)
            }
        }
        if (on("updated") && w >= 200f && h >= 100f) {
            text("Agg. " + SimpleDateFormat("HH:mm", loc).format(Date(x.fetched)), w - pad, h - pad * 0.7f, 9f, pal.dim, tfR, "right")
        }
    }

    /** Riga principale: icona, temperatura, condizioni a sinistra; località e max/min a destra. */
    fun weatherRow(x: Weather, r: RectF, pad: Float) {
        val hh = r.height()
        val ic = min(hh * 0.66f, r.width() * 0.22f)
        icon(r.left + pad + ic / 2, r.centerY(), ic, x.code, x.isDay)
        val ts = min(hh * 0.42f, r.width() * 0.13f)
        val tx = r.left + pad + ic + pad * 0.6f
        val fs = (hh * 0.13f).coerceIn(10f, 15f)
        val cond = on("cond")
        val tw = text("${x.temp}°", tx, r.centerY() - (if (cond) lineH(fs) * 0.45f else 0f), ts, pal.text, tfNum, "left")
        if (cond) text(weatherText(x.code), tx, r.centerY() + lineH(ts) * 0.32f + lineH(fs) * 0.15f, fs, pal.text, tfR, "left", r.width() * 0.4f)
        val rx = r.right - pad
        val maxW = r.right - pad - (tx + max(tw, r.width() * 0.2f)) - 8f
        if (maxW < 40f) return
        val lines = ArrayList<Pair<String, Int>>()
        if (on("place")) lines += x.place.ifBlank { "Posizione" } to pal.text
        if (on("details") && r.width() >= 300f) {
            lines += "Percepita ${x.feels}°" to pal.dim
            lines += "Umidità ${x.humidity}%" to pal.dim
            lines += "Vento ${x.wind} km/h" to pal.dim
        }
        if (on("hilo")) lines += hiloStr(x) to pal.dim
        val fit = ((hh - pad) / lineH(fs)).toInt().coerceAtLeast(1)
        val shown = lines.take(fit)
        var y = r.centerY() - lineH(fs) * (shown.size - 1) / 2f
        shown.forEachIndexed { i, (str, col) ->
            text(str, rx, y, fs, col, if (i == 0 && on("place")) tfM else tfR, "right", maxW)
            y += lineH(fs)
        }
    }

    fun hoursRow(x: Weather, r: RectF, ink: Int, dim: Int) {
        val n = (r.width() / 46f).toInt().coerceIn(3, 8)
        val cw = r.width() / n
        val fs = (r.height() * 0.2f).coerceIn(8f, 13f)
        val ic = min(r.height() - lineH(fs) * 2.2f, cw * 0.7f).coerceAtLeast(8f)
        for (i in 0 until n) {
            val hr = x.hours.getOrNull(i) ?: break
            val cx = r.left + cw * (i + 0.5f)
            text(SimpleDateFormat("HH", loc).format(Date(hr.time)), cx, r.top + lineH(fs) * 0.6f, fs, dim, tfR, "center", cw)
            icon(cx, r.centerY(), ic, hr.code, hr.isDay)
            text("${hr.temp}°", cx, r.bottom - lineH(fs) * 0.6f, fs, ink, tfM, "center", cw)
        }
    }

    fun daysRow(x: Weather, r: RectF, ink: Int, dim: Int) {
        val n = (r.width() / 52f).toInt().coerceIn(3, 6)
        val cw = r.width() / n
        val fs = (r.height() * 0.2f).coerceIn(8f, 13f)
        val ic = min(r.height() - lineH(fs) * 2.2f, cw * 0.7f).coerceAtLeast(8f)
        val days = x.days.drop(1)
        for (i in 0 until n) {
            val dd = days.getOrNull(i) ?: break
            val cx = r.left + cw * (i + 0.5f)
            text(cap(SimpleDateFormat("EEE", loc).format(Date(dd.time)).replace(".", "")), cx, r.top + lineH(fs) * 0.6f, fs, dim, tfR, "center", cw)
            icon(cx, r.centerY(), ic, dd.code, true)
            text("${dd.hi}° ${dd.lo}°", cx, r.bottom - lineH(fs) * 0.6f, fs, ink, tfM, "center", cw)
        }
    }

    fun skyColors(code: Int, isDay: Boolean): IntArray = when {
        !isDay -> intArrayOf(0xFF0B1630.toInt(), 0xFF26375E.toInt())
        code in 51..99 -> intArrayOf(0xFF46566B.toInt(), 0xFF6C7D92.toInt())
        code == 3 || code == 45 || code == 48 -> intArrayOf(0xFF5E7290.toInt(), 0xFF8EA1BB.toInt())
        else -> intArrayOf(0xFF2F6FD0.toInt(), 0xFF68A8EE.toInt())
    }

    fun weatherIos() {
        val x = wx
        val white = 0xFFFFFFFF.toInt()
        val sky = pal.bgKind == "ios"
        if (sky) {
            val cols = skyColors(x?.code ?: 0, x?.isDay ?: true)
            val r = s.corner.toFloat().coerceAtMost(m / 2f)
            c.drawRoundRect(RectF(0f, 0f, w, h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, h, cols, null, Shader.TileMode.CLAMP)
                alpha = s.opacity * 255 / 100
            })
        } else background()
        val ink = if (sky) white else pal.text
        val dim = if (sky) 0xD9FFFFFF.toInt() else pal.dim
        if (x == null) return noWeather(ink)
        val pad = (m * 0.1f).coerceIn(9f, 16f)
        val big = h >= 230f && w >= 250f
        val wide = w >= 250f
        val leftW = if (wide) min(w * 0.42f, 170f) else w - pad * 2
        val headH = if (big) min(h * 0.42f, 160f) else h
        val fs = (min(headH, leftW * 1.2f) * 0.085f).coerceIn(9f, 15f)
        // in alto a sinistra: città, temperatura grande
        var y = pad + lineH(fs) * 0.5f
        if (on("place")) { text(x.place.ifBlank { "Posizione" }, pad, y, fs * 1.08f, ink, tfB, "left", leftW, shadow = false); y += lineH(fs) * 0.5f }
        val ts = min(headH * 0.3f, leftW * 0.4f)
        text("${x.temp}°", pad - ts * 0.04f, y + lineH(ts) * 0.42f, ts, ink, tfL, "left", leftW, shadow = false)
        // in basso a sinistra: icona, condizioni, max/min
        var by = headH - pad - lineH(fs) * 0.5f
        if (on("hilo")) { text("Max ${x.hi}°  Min ${x.lo}°", pad, by, fs, ink, tfM, "left", leftW, shadow = false); by -= lineH(fs) }
        if (on("cond")) { text(weatherText(x.code), pad, by, fs, ink, tfM, "left", leftW, shadow = false); by -= lineH(fs) * 0.6f }
        val ic = (fs * 1.6f).coerceAtMost(by - (y + lineH(ts) * 0.9f))
        if (ic > 10f) icon(pad + ic / 2, by - ic / 2, ic, x.code, x.isDay)
        // a destra: prossime ore
        if (wide && on("hours")) hoursRow(x, RectF(pad + leftW + pad, pad, w - pad, headH - pad), ink, dim)
        // sotto: giorni in elenco
        if (big && on("days")) {
            c.drawLine(pad, headH, w - pad, headH, paint(0x40FFFFFF).apply { strokeWidth = 1f; if (!sky) color = pal.track })
            val days = x.days.drop(1)
            val n = ((h - headH - pad) / 26f).toInt().coerceIn(1, 6)
            val rh = (h - headH - pad) / n
            val all = days.take(n)
            val lo = all.minOfOrNull { it.lo } ?: 0
            val hi = all.maxOfOrNull { it.hi } ?: 1
            all.forEachIndexed { i, dd ->
                val cy = headH + rh * (i + 0.5f)
                val dfs = (rh * 0.42f).coerceIn(9f, 15f)
                text(cap(SimpleDateFormat("EEE", loc).format(Date(dd.time)).replace(".", "")), pad, cy, dfs, ink, tfM, "left", w * 0.2f, shadow = false)
                icon(w * 0.3f, cy, rh * 0.7f, dd.code, true)
                text("${dd.lo}°", w * 0.44f, cy, dfs, dim, tfM, "right", shadow = false)
                val br = RectF(w * 0.47f, cy - 2f, w - pad - w * 0.11f, cy + 2f)
                c.drawRoundRect(br, 2f, 2f, paint(0x33FFFFFF).apply { if (!sky) color = pal.track })
                val span = (hi - lo).coerceAtLeast(1)
                val l = br.left + br.width() * (dd.lo - lo) / span
                val rr = br.left + br.width() * (dd.hi - lo) / span
                c.drawRoundRect(RectF(l, br.top, max(rr, l + 4f), br.bottom), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(l, 0f, max(rr, l + 4f), 0f, 0xFF8FD3FF.toInt(), 0xFFFFC14D.toInt(), Shader.TileMode.CLAMP)
                })
                text("${dd.hi}°", w - pad, cy, dfs, ink, tfM, "right", shadow = false)
            }
        }
    }

    fun weatherMinimal() {
        background()
        val x = wx ?: return noWeather(pal.text)
        val parts = ArrayList<String>()
        if (on("cond")) parts += weatherText(x.code)
        if (on("place") && x.place.isNotBlank()) parts += x.place
        if (on("hilo")) parts += "${x.hi}°/${x.lo}°"
        if (h < w * 0.45f) {
            val fs = min(h * 0.32f, 26f)
            val line = (listOf("${x.temp}°") + parts).joinToString("  ·  ")
            text(line, w / 2, h / 2, fs, pal.text, tfNum, "center", w * 0.94f)
        } else {
            val ts = min(h * 0.4f, w * 0.3f)
            val fs = (ts * 0.22f).coerceIn(10f, 18f)
            text("${x.temp}°", w / 2, h * 0.42f, ts, pal.text, tfNum, "center", w * 0.9f)
            text(parts.joinToString("  ·  "), w / 2, h * 0.42f + lineH(ts) * 0.5f + lineH(fs) * 0.4f, fs, pal.dim, tfR, "center", w * 0.92f)
        }
    }

    // ------------------------------------------------------------------ batteria

    fun accessories() = if (on("accessories")) d.battery.accessories else emptyList()

    fun batteryMaterial() {
        background()
        val b = d.battery
        val col = if (Renderer.low(b)) Renderer.RED else pal.accent
        val acc = accessories()
        val pad = (m * 0.1f).coerceIn(8f, 16f)
        val status = Renderer.batteryStatus(b)
        when {
            w >= 220f && h >= 150f -> {
                // grande: barre per telefono e accessori
                val rows = listOf(Accessory("Telefono", b.level, "phone")) + acc
                val rh = ((h - pad * 2) / rows.size).coerceAtMost(54f)
                val n = ((h - pad * 2) / rh).toInt().coerceAtLeast(1)
                rows.take(n).forEachIndexed { i, a ->
                    val top = pad + i * rh + (h - pad * 2 - rh * min(n, rows.size)) / 2
                    val fs = (rh * 0.3f).coerceIn(10f, 17f)
                    val label = if (i == 0) (if (b.charging) "Telefono · in carica" else "Telefono") else a.name
                    text(label, pad, top + rh * 0.32f, fs, pal.text, tfM, "left", w * 0.65f)
                    text("${a.level}%", w - pad, top + rh * 0.32f, fs, pal.text, tfB, "right")
                    bar(RectF(pad, top + rh * 0.62f, w - pad, top + rh * 0.62f + (if (i == 0) 10f else 7f)), a.level / 100f, if (i == 0) col else pal.accent)
                }
            }
            w >= h * 1.6f -> {
                // largo: anello, percentuale e stato, accessori a destra
                val rr = (h - pad * 2) / 2
                ring(pad + rr, h / 2, rr, b.level / 100f, col)
                if (b.charging) bolt(pad + rr, h / 2, rr * 0.5f, col)
                val tx = pad + rr * 2 + pad
                val ts = min(h * 0.3f, w * 0.12f)
                val colW = if (acc.isEmpty()) w - tx - pad else (w - tx - pad) * 0.45f
                text("${b.level}%", tx, h / 2 - (if (on("status")) lineH(ts) * 0.18f else 0f), ts, pal.text, tfNum, "left", colW)
                if (on("status")) text(status, tx, h / 2 + lineH(ts) * 0.42f, (ts * 0.4f).coerceIn(10f, 16f), pal.dim, tfR, "left", colW)
                if (acc.isNotEmpty()) {
                    val ax = tx + colW + pad * 0.5f
                    val fs = ((h - pad * 2) / 4.4f).coerceIn(9f, 14f)
                    val shown = acc.take(((h - pad * 2) / lineH(fs)).toInt().coerceAtLeast(1))
                    var y = h / 2 - lineH(fs) * (shown.size - 1) / 2f
                    shown.forEach { a ->
                        text(a.name, ax, y, fs, pal.text, tfR, "left", w - ax - pad - fs * 3)
                        text("${a.level}%", w - pad, y, fs, pal.text, tfB, "right")
                        y += lineH(fs)
                    }
                }
            }
            else -> {
                val rr = (m - pad * 2) / 2 * (if (on("status") && h >= 120f) 0.84f else 1f)
                val cy = if (on("status") && h >= 120f) pad + rr else h / 2
                ring(w / 2, cy, rr, b.level / 100f, col)
                val ts = rr * 0.5f
                text("${b.level}%", w / 2, cy - (if (b.charging) rr * 0.1f else 0f), ts, pal.text, tfNum, "center", rr * 1.5f)
                if (b.charging) bolt(w / 2, cy + rr * 0.38f, rr * 0.2f, col)
                if (on("status") && h >= 120f) text(status, w / 2, h - pad - lineH(11f) * 0.4f, (m * 0.08f).coerceIn(10f, 14f), pal.dim, tfR, "center", w - pad * 2)
            }
        }
    }

    fun bolt(cx: Float, cy: Float, s0: Float, col: Int) {
        val p = Path().apply {
            moveTo(cx + s0 * 0.15f, cy - s0); lineTo(cx - s0 * 0.55f, cy + s0 * 0.12f); lineTo(cx - s0 * 0.02f, cy + s0 * 0.12f)
            lineTo(cx - s0 * 0.2f, cy + s0); lineTo(cx + s0 * 0.55f, cy - s0 * 0.15f); lineTo(cx + s0 * 0.02f, cy - s0 * 0.15f); close()
        }
        c.drawPath(p, paint(col))
    }

    /** Piccole figure dei dispositivi (telefono, cuffie, orologio). */
    fun deviceGlyph(kind: String, cx: Float, cy: Float, sz: Float, col: Int) {
        val p = paint(col).apply { style = Paint.Style.STROKE; strokeWidth = sz * 0.11f; strokeCap = Paint.Cap.ROUND }
        when (kind) {
            "audio" -> {
                c.drawArc(RectF(cx - sz * 0.42f, cy - sz * 0.45f, cx + sz * 0.42f, cy + sz * 0.4f), 180f, 180f, false, p)
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(cx - sz * 0.48f, cy - sz * 0.02f, cx - sz * 0.26f, cy + sz * 0.4f), sz * 0.08f, sz * 0.08f, p)
                c.drawRoundRect(RectF(cx + sz * 0.26f, cy - sz * 0.02f, cx + sz * 0.48f, cy + sz * 0.4f), sz * 0.08f, sz * 0.08f, p)
            }
            "watch" -> {
                c.drawRoundRect(RectF(cx - sz * 0.28f, cy - sz * 0.32f, cx + sz * 0.28f, cy + sz * 0.32f), sz * 0.12f, sz * 0.12f, p)
                c.drawLine(cx - sz * 0.16f, cy - sz * 0.32f, cx - sz * 0.12f, cy - sz * 0.5f, p)
                c.drawLine(cx + sz * 0.16f, cy - sz * 0.32f, cx + sz * 0.12f, cy - sz * 0.5f, p)
                c.drawLine(cx - sz * 0.16f, cy + sz * 0.32f, cx - sz * 0.12f, cy + sz * 0.5f, p)
                c.drawLine(cx + sz * 0.16f, cy + sz * 0.32f, cx + sz * 0.12f, cy + sz * 0.5f, p)
            }
            else -> {
                c.drawRoundRect(RectF(cx - sz * 0.26f, cy - sz * 0.45f, cx + sz * 0.26f, cy + sz * 0.45f), sz * 0.1f, sz * 0.1f, p)
                c.drawLine(cx - sz * 0.07f, cy + sz * 0.32f, cx + sz * 0.07f, cy + sz * 0.32f, p)
            }
        }
    }

    fun batteryIos() {
        background()
        val b = d.battery
        val green = pal.accent
        val devs = listOf(Accessory("Telefono", b.level, "phone")) + accessories()
        fun colFor(a: Accessory, i: Int) = if (a.level <= 20 && !(i == 0 && b.charging)) Renderer.RED else green
        val pad = (m * 0.1f).coerceIn(8f, 16f)
        if (h >= 200f && w >= 250f) {
            // grande: elenco
            val rh = ((h - pad * 2) / 4f)
            devs.take(4).forEachIndexed { i, a ->
                val cy = pad + rh * (i + 0.5f)
                val rr = rh * 0.38f
                ring(pad + rr, cy, rr, a.level / 100f, colFor(a, i), sw = rr * 0.18f)
                deviceGlyph(a.kind, pad + rr, cy, rr * 0.9f, pal.text)
                val fs = (rh * 0.26f).coerceIn(10f, 16f)
                text(if (i == 0) "Telefono" else a.name, pad + rr * 2 + pad, cy, fs, pal.text, tfM, "left", w * 0.55f)
                text("${a.level}%", w - pad, cy, fs * 1.2f, pal.text, tfB, "right")
            }
            return
        }
        val n = min(devs.size, 4)
        if (w >= h * 1.6f) {
            val slots = 4
            val cw = (w - pad * 2) / slots
            val fs = (h * 0.13f).coerceIn(10f, 16f)
            val rr = min(cw * 0.38f, (h - pad * 2 - lineH(fs)) * 0.42f)
            for (i in 0 until slots) {
                val cx = pad + cw * (i + 0.5f)
                val cy = pad + rr + (h - pad * 2 - rr * 2 - lineH(fs) * 1.1f) / 2
                val a = devs.getOrNull(i)
                ring(cx, cy, rr, (a?.level ?: 0) / 100f, if (a != null) colFor(a, i) else pal.track, sw = rr * 0.16f)
                if (a != null) {
                    deviceGlyph(a.kind, cx, cy, rr * 0.9f, pal.text)
                    if (i == 0 && b.charging) bolt(cx + rr * 0.75f, cy - rr * 0.75f, rr * 0.22f, green)
                    text("${a.level}%", cx, cy + rr + lineH(fs) * 0.65f, fs, pal.text, tfM, "center", cw)
                }
            }
        } else if (n <= 1) {
            val fs = (m * 0.13f).coerceIn(11f, 20f)
            val rr = (min(w - pad * 2, h - pad * 2 - lineH(fs)) / 2) * 0.92f
            val cy = pad + rr + 2f
            ring(w / 2, cy, rr, b.level / 100f, colFor(devs[0], 0), sw = rr * 0.14f)
            deviceGlyph("phone", w / 2, cy, rr * 0.8f, pal.text)
            if (b.charging) bolt(w / 2 + rr * 0.72f, cy - rr * 0.72f, rr * 0.18f, green)
            text("${b.level}%", w / 2, h - pad - lineH(fs) * 0.35f, fs, pal.text, tfB, "center")
        } else {
            // 2×2 anelli
            val cw = (w - pad * 2) / 2
            val ch = (h - pad * 2) / 2
            for (i in 0 until 4) {
                val a = devs.getOrNull(i)
                val cx = pad + cw * (i % 2 + 0.5f)
                val cy = pad + ch * (i / 2 + 0.5f)
                val rr = min(cw, ch) * 0.4f
                ring(cx, cy, rr, (a?.level ?: 0) / 100f, if (a != null) colFor(a, i) else pal.track, sw = rr * 0.16f)
                if (a != null) deviceGlyph(a.kind, cx, cy, rr * 0.9f, pal.text)
            }
        }
    }

    fun batteryMinimal() {
        background()
        val b = d.battery
        val col = if (Renderer.low(b)) Renderer.RED else pal.text
        val pad = (m * 0.12f).coerceIn(6f, 16f)
        if (h < w * 0.5f) {
            val fs = min(h * 0.34f, 24f)
            val tw = text("${b.level}%", pad, h * 0.42f, fs, pal.text, tfNum, "left")
            if (on("status")) text(Renderer.batteryStatus(b), w - pad, h * 0.42f, fs * 0.55f, pal.dim, tfR, "right", w - tw - pad * 3)
            bar(RectF(pad, h * 0.42f + lineH(fs) * 0.55f, w - pad, h * 0.42f + lineH(fs) * 0.55f + 3f), b.level / 100f, col, pal.track)
        } else {
            val ts = min(h * 0.4f, w * 0.34f)
            text("${b.level}%", w / 2, h * 0.44f, ts, pal.text, tfNum, "center", w - pad * 2)
            bar(RectF(w * 0.2f, h * 0.44f + lineH(ts) * 0.5f, w * 0.8f, h * 0.44f + lineH(ts) * 0.5f + 3f), b.level / 100f, col, pal.track)
            if (on("status")) text(Renderer.batteryStatus(b), w / 2, h * 0.44f + lineH(ts) * 0.5f + 18f, (ts * 0.22f).coerceIn(9f, 15f), pal.dim, tfR, "center", w - pad * 2)
        }
    }

    // ------------------------------------------------------------------ calendario

    fun eventTime(e: Event): String {
        val today = Calendar.getInstance().apply { timeInMillis = d.now }
        val ec = Calendar.getInstance().apply { timeInMillis = e.begin }
        val sameDay = today.get(Calendar.YEAR) == ec.get(Calendar.YEAR) && today.get(Calendar.DAY_OF_YEAR) == ec.get(Calendar.DAY_OF_YEAR)
        val tomorrow = Calendar.getInstance().apply { timeInMillis = d.now; add(Calendar.DAY_OF_YEAR, 1) }
        val isTom = tomorrow.get(Calendar.YEAR) == ec.get(Calendar.YEAR) && tomorrow.get(Calendar.DAY_OF_YEAR) == ec.get(Calendar.DAY_OF_YEAR)
        val day = when {
            sameDay -> ""
            isTom -> "Domani "
            else -> cap(SimpleDateFormat("EEE d", loc).format(Date(e.begin)).replace(".", "")) + " "
        }
        return if (e.allDay) (day.ifBlank { "Oggi " } + "· tutto il giorno").trim()
        else day + SimpleDateFormat(if (d.is24) "HH:mm" else "h:mm a", loc).format(Date(e.begin))
    }

    /** Elenco degli appuntamenti nel riquadro: righe con barretta colorata, titolo e ora. */
    fun events(r: RectF, ink: Int, dim: Int, fs0: Float) {
        if (!d.calendarAllowed) {
            text("Consenti il calendario nell'app", r.left, r.top + lineH(fs0) * 0.6f, fs0 * 0.9f, dim, tfR, "left", r.width())
            return
        }
        if (d.events.isEmpty()) {
            text("Nessun appuntamento", r.left, r.top + lineH(fs0) * 0.6f, fs0 * 0.9f, dim, tfR, "left", r.width())
            return
        }
        val rowH = lineH(fs0) * 2.25f
        val n = (r.height() / rowH).toInt().coerceAtLeast(1)
        d.events.take(n).forEachIndexed { i, e ->
            val top = r.top + i * rowH
            c.drawRoundRect(RectF(r.left, top + 3f, r.left + 3.5f, top + rowH - 5f), 2f, 2f, paint(e.color or 0xFF000000.toInt()))
            text(e.title, r.left + 9f, top + lineH(fs0) * 0.65f, fs0, ink, tfM, "left", r.width() - 9f)
            text(eventTime(e), r.left + 9f, top + lineH(fs0) * 1.6f, fs0 * 0.85f, dim, tfR, "left", r.width() - 9f)
        }
    }

    /** Mese con il giorno di oggi evidenziato. */
    fun month(r: RectF, ink: Int, dim: Int, accent: Int, onAccent: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = d.now }
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val firstDow = cal.firstDayOfWeek
        val offset = ((first.get(Calendar.DAY_OF_WEEK) - firstDow) + 7) % 7
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val rows = (offset + days + 6) / 7
        val titleH = r.height() / (rows + 2.2f)
        val cw = r.width() / 7
        val rh = (r.height() - titleH) / (rows + 1)
        val fs = min(rh * 0.5f, cw * 0.45f)
        text(cap(SimpleDateFormat("LLLL", loc).format(Date(d.now))), r.left + cw * 0.15f, r.top + titleH * 0.5f, fs * 1.1f, accent, tfB, "left", r.width(), shadow = false)
        val names = (0 until 7).map {
            val cc = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, (firstDow - 1 + it) % 7 + 1) }
            SimpleDateFormat("EEEEE", loc).format(cc.time).uppercase(loc)
        }
        names.forEachIndexed { i, n -> text(n, r.left + cw * (i + 0.5f), r.top + titleH + rh * 0.5f, fs * 0.85f, dim, tfM, "center", shadow = false) }
        for (dd in 1..days) {
            val idx = offset + dd - 1
            val cx = r.left + cw * (idx % 7 + 0.5f)
            val cy = r.top + titleH + rh * (idx / 7 + 1.5f)
            if (dd == today) c.drawCircle(cx, cy, min(rh, cw) * 0.44f, paint(accent))
            text(dd.toString(), cx, cy, fs, if (dd == today) onAccent else ink, if (dd == today) tfB else tfR, "center", shadow = false)
        }
    }

    fun calendar() {
        background()
        val ios = t.look == "ios"
        val pad = (m * 0.1f).coerceIn(9f, 16f)
        val accent = pal.accent
        val onAcc = if (ColorUtils.calculateLuminance(accent) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val big = h >= 250f && w >= 240f && on("month")
        if (big) {
            val mh = min(h * 0.55f, w * 0.75f)
            month(RectF(pad, pad, w - pad, pad + mh), pal.text, pal.dim, accent, onAcc)
            if (on("events")) events(RectF(pad, pad + mh + pad * 0.5f, w - pad, h - pad), pal.text, pal.dim, (m * 0.055f).coerceIn(10f, 14f))
            return
        }
        val wide = w >= h * 1.5f
        val colW = if (wide) min(w * 0.36f, h * 0.95f) else w - pad * 2
        // data: giorno della settimana e numero grande
        val ws = (min(colW, h) * 0.12f).coerceIn(9f, 16f)
        val ds = min(h * 0.36f, colW * 0.5f)
        val wdStr = if (ios) weekday.uppercase(loc) else weekday
        text(wdStr, pad, pad + lineH(ws) * 0.5f, ws, if (ios) accent else pal.accent, tfB, "left", colW)
        text(fmt("d"), pad - ds * 0.03f, pad + lineH(ws) + lineH(ds) * 0.42f, ds, pal.text, if (ios) tfR else tfNum, "left", colW)
        val belowY = pad + lineH(ws) + lineH(ds) * 0.85f
        if (!ios) text(cap(fmt("MMMM")), pad, belowY + lineH(ws) * 0.4f, ws, pal.dim, tfR, "left", colW)
        if (wide) {
            val rx = pad + colW + pad
            if (on("month") && w >= 300f && !on("events")) month(RectF(rx, pad, w - pad, h - pad), pal.text, pal.dim, accent, onAcc)
            else if (on("events")) events(RectF(rx, pad, w - pad, h - pad), pal.text, pal.dim, (h * 0.075f).coerceIn(10f, 14f))
            else if (on("month")) month(RectF(rx, pad, w - pad, h - pad), pal.text, pal.dim, accent, onAcc)
        } else if (on("events") && h - belowY > 40f) {
            events(RectF(pad, belowY + (if (!ios) lineH(ws) else 0f), w - pad, h - pad * 0.6f), pal.text, pal.dim, (m * 0.075f).coerceIn(9f, 13f))
        }
    }

    fun tearoff() {
        val r0 = s.corner.toFloat().coerceAtMost(m / 4)
        val dark = pal.dark
        val paper = if (pal.bgKind == "retro") (if (dark) 0xFF2A2724.toInt() else 0xFFFBF8F1.toInt()) else pal.bg
        val ink = if (pal.bgKind == "retro") (if (dark) 0xFFF1ECE2.toInt() else 0xFF2A2622.toInt()) else pal.text
        val red = pal.accent
        // fogli sotto (si vedono i bordi)
        c.drawRoundRect(RectF(3f, 6f, w - 1f, h), r0, r0, paint(shade(paper, 0.12f)))
        c.drawRoundRect(RectF(1.5f, 3f, w - 2.5f, h - 2f), r0, r0, paint(shade(paper, 0.06f)))
        val page = RectF(0f, 0f, w - 4f, h - 4f)
        c.drawRoundRect(page, r0, r0, paint(paper))
        val bandH = page.height() * 0.24f
        c.save(); c.clipRect(page.left, page.top, page.right, page.top + bandH)
        c.drawRoundRect(page, r0, r0, paint(red))
        c.restore()
        // anelli
        val holes = if (w > h * 1.4f) 4 else 2
        for (i in 0 until holes) {
            val hx = page.width() * (i + 1) / (holes + 1)
            c.drawCircle(hx, bandH * 0.22f, bandH * 0.09f, paint(shade(red, 0.45f)))
            c.drawRoundRect(RectF(hx - bandH * 0.035f, -bandH * 0.05f, hx + bandH * 0.035f, bandH * 0.24f), 3f, 3f, paint(0xFFB9BCC2.toInt()))
        }
        val white = 0xFFFFFFFF.toInt()
        text(cap(fmt("MMMM")).uppercase(loc) + " " + fmt("yyyy"), page.centerX(), bandH * 0.64f, bandH * 0.3f, white, tfB, "center", page.width() * 0.9f, shadow = false)
        val evOn = on("events") && h >= 130f && d.events.isNotEmpty() && d.calendarAllowed
        val bottomH = if (evOn) lineH(11f) * 1.6f else 0f
        val wdS = (page.height() * 0.09f).coerceIn(9f, 16f)
        val area = RectF(page.left, bandH, page.right, page.bottom - bottomH - lineH(wdS) - 6f)
        val ds = min(area.height() * 0.95f, page.width() * 0.55f)
        val isSunday = Calendar.getInstance().apply { timeInMillis = d.now }.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        text(fmt("d"), page.centerX(), area.centerY() + ds * 0.02f, ds, if (isSunday) red else ink, Typeface.create("serif", Typeface.BOLD), "center", page.width() * 0.9f, shadow = false)
        text(weekday, page.centerX(), area.bottom + lineH(wdS) * 0.55f, wdS, if (isSunday) red else ink, tfM, "center", page.width() * 0.9f, shadow = false)
        if (evOn) {
            val e = d.events.first()
            text("${eventTime(e)} · ${e.title}", page.centerX(), page.bottom - bottomH * 0.55f, 10f, ColorUtils.setAlphaComponent(ink, 190), tfR, "center", page.width() * 0.9f, shadow = false)
        }
    }

    // ------------------------------------------------------------------ musica

    fun controls(r: RectF, ink: Int, playBg: Int?) {
        val tr = d.track
        val playing = tr?.playing == true
        val cw = r.width() / 3
        val sz = min(cw * 0.5f, r.height() * 0.62f)
        val acts = listOf("prev", "play", "next")
        for (i in 0..2) {
            val cx = r.left + cw * (i + 0.5f)
            val cy = r.centerY()
            val a = acts[i]
            if (a == "play" && playBg != null) {
                c.drawCircle(cx, cy, sz * 0.82f, paint(playBg))
                glyph(if (playing) "pause" else "play", cx, cy, sz * 0.62f,
                    if (ColorUtils.calculateLuminance(playBg) > 0.5) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
            } else {
                glyph(if (a == "play") (if (playing) "pause" else "play") else a, cx, cy, if (a == "play") sz * 0.8f else sz * 0.66f, ink)
            }
            zones += RectF(cx - cw / 2, r.top, cx + cw / 2, r.bottom) to a
        }
    }

    fun glyph(kind: String, cx: Float, cy: Float, sz: Float, col: Int) {
        val p = paint(col)
        val hs = sz / 2
        when (kind) {
            "play" -> c.drawPath(Path().apply { moveTo(cx - hs * 0.7f, cy - hs); lineTo(cx + hs, cy); lineTo(cx - hs * 0.7f, cy + hs); close() }, p.apply { pathEffect = android.graphics.CornerPathEffect(hs * 0.2f) })
            "pause" -> {
                c.drawRoundRect(RectF(cx - hs * 0.75f, cy - hs, cx - hs * 0.2f, cy + hs), hs * 0.15f, hs * 0.15f, p)
                c.drawRoundRect(RectF(cx + hs * 0.2f, cy - hs, cx + hs * 0.75f, cy + hs), hs * 0.15f, hs * 0.15f, p)
            }
            "next", "prev" -> {
                c.save()
                if (kind == "prev") c.scale(-1f, 1f, cx, cy)
                c.drawPath(Path().apply { moveTo(cx - hs, cy - hs * 0.8f); lineTo(cx + hs * 0.05f, cy); lineTo(cx - hs, cy + hs * 0.8f); close() }, p)
                c.drawPath(Path().apply { moveTo(cx - hs * 0.1f, cy - hs * 0.8f); lineTo(cx + hs * 0.85f, cy); lineTo(cx - hs * 0.1f, cy + hs * 0.8f); close() }, p)
                c.drawRect(cx + hs * 0.82f, cy - hs * 0.8f, cx + hs, cy + hs * 0.8f, p)
                c.restore()
            }
            "note" -> {
                c.drawCircle(cx - hs * 0.35f, cy + hs * 0.55f, hs * 0.3f, p)
                c.drawRect(cx - hs * 0.12f, cy - hs * 0.8f, cx + hs * 0.02f, cy + hs * 0.55f, p)
                c.drawPath(Path().apply { moveTo(cx - hs * 0.12f, cy - hs * 0.8f); lineTo(cx + hs * 0.6f, cy - hs * 0.55f); lineTo(cx + hs * 0.6f, cy - hs * 0.25f); lineTo(cx - hs * 0.12f, cy - hs * 0.5f); close() }, p)
            }
        }
    }

    fun cover(r: RectF, radius: Float, placeholder: Int) {
        val art = d.track?.art
        if (art != null) bitmap(art, r, radius)
        else {
            c.drawRoundRect(r, radius, radius, paint(placeholder))
            glyph("note", r.centerX(), r.centerY(), min(r.width(), r.height()) * 0.45f,
                if (ColorUtils.calculateLuminance(placeholder) > 0.5) 0x66000000 else 0x88FFFFFF.toInt())
        }
    }

    fun music() {
        val ios = t.look == "ios"
        val minimal = t.look == "minimal"
        val tr = d.track
        var ink = pal.text
        var dim = pal.dim
        if (ios && pal.bgKind == "ios") {
            val avg = MusicRepo.averageColor(tr?.art) ?: if (pal.dark) 0xFF2C2C2E.toInt() else 0xFF8E8E93.toInt()
            val top = shade(avg, 0.25f); val bot = shade(avg, 0.55f)
            val r = s.corner.toFloat().coerceAtMost(m / 2f)
            c.drawRoundRect(RectF(0f, 0f, w, h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, h, top, bot, Shader.TileMode.CLAMP)
                alpha = s.opacity * 255 / 100
            })
            ink = 0xFFFFFFFF.toInt(); dim = 0xB3FFFFFF.toInt()
        } else background()
        val pad = (m * 0.1f).coerceIn(8f, 16f)
        val title = tr?.title ?: "Nessuna musica"
        val artist = tr?.artist?.takeIf { it.isNotBlank() } ?: if (tr == null) (if (d.musicAllowed) "Avvia un brano in un lettore" else "Consenti l'accesso alle notifiche") else ""
        val showCover = on("cover") && !minimal
        val showArtist = on("artist")
        val showCtl = on("controls")
        if (minimal || h < 80f) {
            // una riga: copertina piccola, titolo, tasti
            val cs = if (showCover || (on("cover") && minimal)) h - pad * 2 else 0f
            if (cs > 0f) cover(RectF(pad, pad, pad + cs, pad + cs), cs * 0.18f, pal.track)
            val ctlW = if (showCtl) min(w * 0.36f, h * 2.6f) else 0f
            val tx = pad + cs + (if (cs > 0f) pad * 0.8f else 0f)
            val tw = w - tx - ctlW - pad
            val fs = (h * 0.24f).coerceIn(10f, 16f)
            if (showArtist && artist.isNotBlank()) {
                text(title, tx, h / 2 - lineH(fs) * 0.42f, fs, ink, tfM, "left", tw)
                text(artist, tx, h / 2 + lineH(fs) * 0.5f, fs * 0.85f, dim, tfR, "left", tw)
            } else text(if (artist.isNotBlank() && minimal) "$title — $artist" else title, tx, h / 2, fs, ink, tfM, "left", tw)
            if (showCtl) controls(RectF(w - pad - ctlW, 0f, w - pad, h), ink, null)
            return
        }
        val accent = if (ios) pal.accent else pal.accent
        if (h >= w * 0.85f) {
            // quadrato o alto: copertina in alto
            val ctlH = if (showCtl) min(h * 0.22f, 52f) else 0f
            val fs = (m * 0.075f).coerceIn(10f, 16f)
            val textH = lineH(fs) * (if (showArtist) 2.1f else 1.1f)
            val cs = if (showCover) min(w - pad * 2, h - pad * 2 - ctlH - textH - 4f) else 0f
            if (cs > 16f) cover(RectF(pad, pad, pad + cs, pad + cs), cs * 0.1f, pal.track)
            val ty = pad + (if (cs > 16f) cs + 4f else (h - ctlH - textH) / 2 - pad)
            text(title, pad, ty + lineH(fs) * 0.55f, fs, ink, tfB, "left", w - pad * 2)
            if (showArtist) text(artist, pad, ty + lineH(fs) * 1.55f, fs * 0.9f, dim, tfR, "left", w - pad * 2)
            if (showCtl) controls(RectF(pad, h - pad * 0.5f - ctlH, w - pad, h - pad * 0.5f), ink, if (ios) null else accent)
        } else {
            // largo: copertina a sinistra, testo e tasti a destra
            val cs = if (showCover) h - pad * 2 else 0f
            if (cs > 0f) cover(RectF(pad, pad, pad + cs, pad + cs), cs * (if (ios) 0.09f else 0.14f), pal.track)
            val tx = pad + cs + (if (cs > 0f) pad else 0f)
            val tw = w - tx - pad
            val fs = (h * 0.12f).coerceIn(10f, 17f)
            val ctlH = if (showCtl) min(h * 0.36f, 54f) else 0f
            val textTop = pad + (h - pad * 2 - ctlH - lineH(fs) * 2.1f) / 2
            text(title, tx, textTop + lineH(fs) * 0.55f, fs * 1.1f, ink, tfB, "left", tw)
            if (showArtist) text(artist, tx, textTop + lineH(fs) * 1.6f, fs * 0.92f, dim, tfR, "left", tw)
            if (showCtl) controls(RectF(tx - pad * 0.3f, h - pad - ctlH, min(w - pad, tx + tw), h - pad), ink, if (ios) null else accent)
        }
    }

    fun ipod() {
        val dark = pal.dark
        val bodyTop = if (dark) 0xFF2B2B2E.toInt() else 0xFFF4F4F6.toInt()
        val bodyBot = if (dark) 0xFF121214.toInt() else 0xFFD9DADF.toInt()
        val r0 = s.corner.toFloat().coerceAtMost(m / 2f)
        c.drawRoundRect(RectF(0f, 0f, w, h), r0, r0, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h, bodyTop, bodyBot, Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r0, r0, paint(if (dark) 0x33FFFFFF else 0x22000000).apply { style = Paint.Style.STROKE; strokeWidth = 1f })
        val pad = m * 0.07f
        val side = w >= h * 1.25f
        val screen: RectF
        val wheelC: Pair<Float, Float>
        val wr: Float
        if (side) {
            screen = RectF(pad, pad, w * 0.56f, h - pad)
            wr = min(w - screen.right - pad * 2, h - pad * 2) / 2
            wheelC = (screen.right + (w - screen.right) / 2) to h / 2
        } else {
            screen = RectF(pad, pad, w - pad, h * 0.47f)
            wr = min(w - pad * 2, h - screen.bottom - pad * 2) / 2
            wheelC = w / 2 to (screen.bottom + (h - screen.bottom) / 2)
        }
        // schermo
        val scrR = m * 0.035f
        c.drawRoundRect(RectF(screen.left - 1.5f, screen.top - 1.5f, screen.right + 1.5f, screen.bottom + 1.5f), scrR, scrR, paint(0xFF1A1A1A.toInt()))
        c.drawRoundRect(screen, scrR, scrR, paint(0xFFF7F9FC.toInt()))
        val barH = screen.height() * 0.16f
        c.save(); c.clipRect(screen.left, screen.top, screen.right, screen.top + barH)
        c.drawRoundRect(screen, scrR, scrR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, screen.top, 0f, screen.top + barH, 0xFFE9EDF2.toInt(), 0xFFC7CFD8.toInt(), Shader.TileMode.CLAMP)
        })
        c.restore()
        val ink = 0xFF111111.toInt()
        val tr = d.track
        text("In riproduzione", screen.centerX(), screen.top + barH / 2, barH * 0.5f, ink, tfB, "center", screen.width() * 0.9f, shadow = false)
        val inner = RectF(screen.left + screen.width() * 0.06f, screen.top + barH + screen.height() * 0.06f, screen.right - screen.width() * 0.06f, screen.bottom - screen.height() * 0.08f)
        val cs = if (on("cover")) min(inner.height(), inner.width() * 0.45f) else 0f
        if (cs > 10f) cover(RectF(inner.left, inner.centerY() - cs / 2, inner.left + cs, inner.centerY() + cs / 2), 2f, 0xFFB9C2CC.toInt())
        val tx = inner.left + (if (cs > 10f) cs + inner.width() * 0.06f else 0f)
        val tw = inner.right - tx
        val fs = (inner.height() * 0.18f).coerceIn(7f, 14f)
        text(tr?.title ?: "Nessun brano", tx, inner.centerY() - lineH(fs) * 0.5f, fs, ink, tfB, "left", tw, shadow = false)
        text(tr?.artist ?: (if (d.musicAllowed) "Avvia la musica" else "Consenti le notifiche"), tx, inner.centerY() + lineH(fs) * 0.55f, fs * 0.9f, 0xFF5A5A5A.toInt(), tfR, "left", tw, shadow = false)
        // ghiera
        val (cx, cy) = wheelC
        val wheelCol = if (dark) 0xFF3A3A3E.toInt() else 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy + 1f, wr, paint(0x22000000))
        c.drawCircle(cx, cy, wr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy - wr * 0.3f, wr * 1.3f, tint(wheelCol, 0.05f), shade(wheelCol, 0.08f), Shader.TileMode.CLAMP)
        })
        val btnCol = if (dark) 0xFF1E1E21.toInt() else 0xFFE4E5E9.toInt()
        c.drawCircle(cx, cy, wr * 0.36f, paint(btnCol))
        val lbl = if (dark) 0xFFD8D8DC.toInt() else 0xFF9A9CA3.toInt()
        text("MENU", cx, cy - wr * 0.72f, wr * 0.13f, lbl, tfB, "center", shadow = false)
        glyph("prev", cx - wr * 0.7f, cy, wr * 0.2f, lbl)
        glyph("next", cx + wr * 0.7f, cy, wr * 0.2f, lbl)
        glyph("play", cx - wr * 0.08f, cy + wr * 0.7f, wr * 0.13f, lbl)
        glyph("pause", cx + wr * 0.1f, cy + wr * 0.7f, wr * 0.12f, lbl)
        zones += RectF(cx - wr, cy - wr * 0.45f, cx - wr * 0.4f, cy + wr * 0.45f) to "prev"
        zones += RectF(cx + wr * 0.4f, cy - wr * 0.45f, cx + wr, cy + wr * 0.45f) to "next"
        zones += RectF(cx - wr * 0.4f, cy - wr * 0.4f, cx + wr * 0.4f, cy + wr) to "play"
    }

    // ------------------------------------------------------------------ dispositivo

    fun device() {
        background()
        val dv = d.device
        val b = d.battery
        val items = ArrayList<Triple<String, Float, String>>()
        if (on("storage")) items += Triple("Memoria", dv.storageUsed.toFloat() / dv.storageTotal, "${gb(dv.storageUsed)} di ${gb(dv.storageTotal)}")
        if (on("ram")) items += Triple("RAM", dv.ramUsed.toFloat() / dv.ramTotal, "${gb(dv.ramUsed)} di ${gb(dv.ramTotal)}")
        if (on("battery")) items += Triple("Batteria", b.level / 100f, if (b.temp > 0f) "${"%.1f".format(b.temp).replace('.', ',')} °C" else Renderer.batteryStatus(b))
        if (items.isEmpty()) return
        val pad = (m * 0.1f).coerceIn(8f, 16f)
        fun col(f: Float, i: Int) = if (f >= 0.9f && i < 2) Renderer.RED else pal.accent
        if (w >= h * 1.7f && h < 160f) {
            val cw = (w - pad * 2) / items.size
            val fs = (h * 0.11f).coerceIn(9f, 14f)
            val rr = min(cw * 0.36f, (h - pad * 2 - lineH(fs) * 2) / 2)
            items.forEachIndexed { i, (name, f, _) ->
                val cx = pad + cw * (i + 0.5f)
                val cy = pad + rr
                ring(cx, cy, rr, f, col(f, i))
                text("${(f * 100).toInt()}%", cx, cy, rr * 0.5f, pal.text, tfNum, "center", rr * 1.5f)
                text(name, cx, cy + rr + lineH(fs) * 0.7f, fs, pal.dim, tfM, "center", cw)
            }
        } else {
            val rowH = (h - pad * 2) / items.size
            val fs = (rowH * 0.24f).coerceIn(9f, 16f)
            items.forEachIndexed { i, (name, f, sub) ->
                val top = pad + rowH * i
                text(name, pad, top + rowH * 0.3f, fs, pal.text, tfM, "left", w * 0.5f)
                text("${(f * 100).toInt()}%", w - pad, top + rowH * 0.3f, fs, pal.text, tfB, "right")
                bar(RectF(pad, top + rowH * 0.55f, w - pad, top + rowH * 0.55f + max(5f, rowH * 0.1f)), f, col(f, i))
                if (rowH >= 46f) text(sub, pad, top + rowH * 0.84f, fs * 0.78f, pal.dim, tfR, "left", w - pad * 2)
            }
        }
    }

    // ------------------------------------------------------------------ in breve, nota, ricerca

    fun glance() {
        background()
        val pad = m * 0.1f
        val x = wx
        val showW = on("weather") && x != null
        val dateStr = if (on("date")) "$weekday $dayMonth" else ""
        if (h < w * 0.4f) {
            // in riga: data | icona temperatura
            val fs = min(h * 0.32f, 24f)
            val ic = fs * 1.3f
            val wStr = if (showW) "${x!!.temp}°" else ""
            val wp = Renderer.textPaint(fs * k, 0, tfNum, null)
            val wWidth = if (showW) ic + 4f + wp.measureText(wStr) else 0f
            val dw = if (dateStr.isNotEmpty()) Renderer.textPaint(fs * k, 0, tfNum, null).measureText(dateStr) else 0f
            val sep = if (showW && dateStr.isNotEmpty()) fs * 1.2f else 0f
            val total = min(dw, w - pad * 2 - wWidth - sep) + sep + wWidth
            var xx = w / 2 - total / 2
            if (dateStr.isNotEmpty()) { xx += text(dateStr, xx, h / 2, fs, pal.text, tfNum, "left", w - pad * 2 - wWidth - sep) }
            if (sep > 0f) {
                c.drawLine(xx + sep / 2, h / 2 - fs * 0.45f, xx + sep / 2, h / 2 + fs * 0.45f, paint(pal.text).apply { strokeWidth = 1.5f; alpha = 140 })
                xx += sep
            }
            if (showW) {
                icon(xx + ic / 2, h / 2, ic, x!!.code, x.isDay)
                text(wStr, xx + ic + 4f, h / 2, fs, pal.text, tfNum, "left")
            }
        } else {
            val fs = min(h * 0.2f, w * 0.09f)
            text(dateStr.ifEmpty { weekday }, pad, h * 0.38f, fs, pal.text, tfNum, "left", w - pad * 2)
            if (showW) {
                val ic = fs * 1.2f
                icon(pad + ic / 2, h * 0.38f + lineH(fs) * 1.0f, ic, x!!.code, x.isDay)
                text("${x.temp}° · ${weatherText(x.code)}", pad + ic + 6f, h * 0.38f + lineH(fs) * 1.0f, fs * 0.7f, pal.text, tfR, "left", w - pad * 2 - ic)
            }
        }
    }

    fun note() {
        val ios = t.look == "ios"
        background()
        val pad = (m * 0.1f).coerceIn(9f, 18f)
        var top = pad
        if (ios) {
            val hs = (m * 0.09f).coerceIn(10f, 15f)
            text("Note", pad, pad + lineH(hs) * 0.5f, hs, pal.accent, tfB, "left")
            top += lineH(hs) + 4f
            c.drawLine(pad, top, w - pad, top, paint(pal.track).apply { strokeWidth = 1f })
            top += 6f
        }
        val str = s.note.ifBlank { "Tocca per scrivere una nota" }
        val maxW = (w - pad * 2).toInt().coerceAtLeast(10)
        val maxH = h - top - pad
        var size = (m * 0.16f).coerceAtMost(30f) * k
        var layout: StaticLayout
        val tf = Renderer.typeface(s.font)
        while (true) {
            val p = Renderer.textPaint(size, pal.text, tf, pal)
            layout = StaticLayout.Builder.obtain(str, 0, str.length, p, maxW).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.08f).build()
            if (layout.height <= maxH || size <= 9f) break
            size *= 0.9f
        }
        if (layout.height > maxH) {
            val p = Renderer.textPaint(size, pal.text, tf, pal)
            val lines = (maxH / (layout.height.toFloat() / layout.lineCount)).toInt().coerceAtLeast(1)
            layout = StaticLayout.Builder.obtain(str, 0, str.length, p, maxW).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.08f).build()
        }
        c.save()
        c.translate(pad, if (ios) top else top + (maxH - layout.height) / 2f)
        layout.draw(c)
        c.restore()
    }

    fun search() {
        val bgCol = if (pal.bgKind == "tonal" && s.background == "look") pal.face else pal.bg
        val r = RectF(m * 0.05f, h * 0.12f, w - m * 0.05f, h * 0.88f)
        val rad = min(s.corner.toFloat(), r.height() / 2)
        c.drawRoundRect(r, rad, rad, paint(bgCol))
        val ink = if (pal.bgKind == "tonal" && s.background == "look") pal.faceInk else pal.text
        val ic = r.height() * 0.38f
        val cx = r.left + r.height() * 0.55f
        val cy = r.centerY()
        val ip = paint(pal.accent).apply { style = Paint.Style.STROKE; strokeWidth = ic * 0.16f; strokeCap = Paint.Cap.ROUND }
        c.drawCircle(cx - ic * 0.1f, cy - ic * 0.1f, ic * 0.32f, ip)
        c.drawLine(cx + ic * 0.14f, cy + ic * 0.14f, cx + ic * 0.4f, cy + ic * 0.4f, ip)
        text(s.note.ifBlank { "Cerca" }, cx + ic * 0.8f, cy, r.height() * 0.32f, ColorUtils.setAlphaComponent(ink, 200), tfR, "left", r.right - cx - ic, shadow = false)
    }

    // ------------------------------------------------------------------ One UI

    fun weatherOneUi() {
        val x = wx
        val sky = pal.bgKind == "oneui"
        val white = 0xFFFFFFFF.toInt()
        if (sky) {
            // colori morbidi come il meteo di Samsung
            val cols = when {
                x == null -> intArrayOf(0xFF6F8FB8.toInt(), 0xFF8FAAD0.toInt())
                !x.isDay -> intArrayOf(0xFF27315A.toInt(), 0xFF475484.toInt())
                x.code in 51..99 -> intArrayOf(0xFF5A6F86.toInt(), 0xFF7F93A9.toInt())
                x.code == 3 || x.code == 45 || x.code == 48 -> intArrayOf(0xFF7E8EA6.toInt(), 0xFFA3B1C6.toInt())
                else -> intArrayOf(0xFF3E8CF0.toInt(), 0xFF7DB8FA.toInt())
            }
            val r = s.corner.toFloat().coerceAtMost(m / 2f)
            c.drawRoundRect(RectF(0f, 0f, w, h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, w, h, cols, null, Shader.TileMode.CLAMP)
                alpha = s.opacity * 255 / 100
            })
        } else background()
        val ink = if (sky) white else pal.text
        val dim = if (sky) 0xDDFFFFFF.toInt() else pal.dim
        if (x == null) return noWeather(ink)
        val pad = (m * 0.11f).coerceIn(10f, 18f)
        val wide = w >= 250f
        val hoursOn = on("hours") && wide && h >= 150f
        val top = if (hoursOn) h * 0.55f else h
        val fs = (min(top, w) * 0.09f).coerceIn(10f, 16f)
        // località con il segnaposto
        var y = pad + lineH(fs) * 0.5f
        if (on("place")) {
            pin(pad + fs * 0.4f, y, fs * 0.9f, ink)
            text(x.place.ifBlank { "Posizione" }, pad + fs * 1.05f, y, fs, ink, tfB, "left", w * 0.6f, shadow = false)
        }
        val ts = min(top * 0.42f, w * (if (wide) 0.2f else 0.32f))
        y = pad + lineH(fs) + lineH(ts) * 0.45f
        text("${x.temp}°", pad - ts * 0.03f, y, ts, ink, tfNum, "left", w * 0.6f, shadow = false)
        val ic = min(top - pad * 1.6f, w * 0.36f)
        icon(w - pad - ic / 2, pad * 0.8f + ic / 2, ic, x.code, x.isDay)
        var by = top - pad * 0.8f - lineH(fs) * 0.5f
        if (on("hilo")) { text("${x.hi}° / ${x.lo}°", pad, by, fs, dim, tfM, "left", w * 0.6f, shadow = false); by -= lineH(fs) }
        if (on("cond") && by > y + lineH(ts) * 0.4f) text(weatherText(x.code), pad, by, fs, ink, tfM, "left", w * 0.6f, shadow = false)
        if (hoursOn) {
            c.drawRoundRect(RectF(pad * 0.6f, top, w - pad * 0.6f, h - pad * 0.6f), 16f, 16f, paint(if (sky) 0x26FFFFFF else pal.track))
            hoursRow(x, RectF(pad, top + 4f, w - pad, h - pad * 0.6f - 4f), ink, dim)
        }
    }

    fun pin(cx: Float, cy: Float, sz: Float, col: Int) {
        val p = Path().apply {
            addCircle(cx, cy - sz * 0.15f, sz * 0.32f, Path.Direction.CW)
            moveTo(cx - sz * 0.3f, cy - sz * 0.05f); lineTo(cx, cy + sz * 0.5f); lineTo(cx + sz * 0.3f, cy - sz * 0.05f); close()
        }
        c.drawPath(p, paint(col))
        c.drawCircle(cx, cy - sz * 0.15f, sz * 0.12f, paint(0x55000000))
    }

    fun batteryOneUi() {
        background()
        val b = d.battery
        val devs = listOf(Accessory("Telefono", b.level, "phone")) + accessories()
        val pad = (m * 0.1f).coerceIn(10f, 18f)
        if (w < h * 1.3f && w < 200f) {
            // piccolo: anello grande con il telefono
            val fs = (m * 0.12f).coerceIn(11f, 18f)
            val rr = (min(w, h - lineH(fs)) - pad * 2) / 2
            val col = if (Renderer.low(b)) Renderer.RED else pal.accent
            ring(w / 2, pad + rr, rr, b.level / 100f, col, sw = rr * 0.12f)
            deviceGlyph("phone", w / 2, pad + rr, rr * 0.7f, pal.text)
            text("${b.level}%", w / 2, h - pad * 0.7f - lineH(fs) * 0.4f, fs, pal.text, tfB, "center")
            return
        }
        // elenco con icona tonda, nome, percentuale e barra
        val n = ((h - pad * 2) / 40f).toInt().coerceIn(1, 4)
        val rows = devs.take(n)
        val rh = (h - pad * 2) / n
        rows.forEachIndexed { i, a ->
            val cy = pad + rh * (i + 0.5f)
            val ir = min(rh * 0.36f, 20f)
            c.drawCircle(pad + ir, cy, ir, paint(pal.face))
            deviceGlyph(a.kind, pad + ir, cy, ir * 1.1f, pal.text)
            if (i == 0 && b.charging) bolt(pad + ir * 1.8f, cy - ir * 0.8f, ir * 0.35f, pal.accent)
            val fs = (rh * 0.28f).coerceIn(10f, 15f)
            val tx = pad + ir * 2 + pad * 0.7f
            text(if (i == 0) "Telefono" else a.name, tx, cy - lineH(fs) * 0.35f, fs, pal.text, tfM, "left", w - tx - pad - fs * 3)
            text("${a.level}%", w - pad, cy - lineH(fs) * 0.35f, fs, pal.text, tfB, "right")
            val low = a.level <= 15 && !(i == 0 && b.charging)
            bar(RectF(tx, cy + lineH(fs) * 0.35f, w - pad, cy + lineH(fs) * 0.35f + 4f), a.level / 100f, if (low) Renderer.RED else pal.accent)
        }
    }

    // ------------------------------------------------------------------ HyperOS

    fun weatherHyper() {
        val x = wx
        val tinted = pal.bgKind == "hyper"
        if (tinted) {
            val base = when {
                x == null -> 0xFF8FA7C8.toInt()
                !x.isDay -> 0xFF2D3A63.toInt()
                x.code in 51..99 -> 0xFF6A7D92.toInt()
                x.code == 3 || x.code == 45 || x.code == 48 -> 0xFF8C9BB0.toInt()
                else -> 0xFF4AA3F5.toInt()
            }
            val top = if (pal.dark) shade(base, 0.35f) else base
            val bot = if (pal.dark) shade(base, 0.6f) else tint(base, 0.35f)
            val r = s.corner.toFloat().coerceAtMost(m / 2f)
            c.drawRoundRect(RectF(0f, 0f, w, h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, h, top, bot, Shader.TileMode.CLAMP)
                alpha = s.opacity * 255 / 100
            })
            // un grande cerchio di luce come negli sfondi di HyperOS
            c.drawCircle(w * 0.85f, h * 0.1f, m * 0.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(w * 0.85f, h * 0.1f, m * 0.7f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
            })
        } else background()
        val ink = if (tinted) 0xFFFFFFFF.toInt() else pal.text
        val dim = if (tinted) 0xCCFFFFFF.toInt() else pal.dim
        if (x == null) return noWeather(ink)
        val pad = (m * 0.1f).coerceIn(10f, 18f)
        val wide = w >= 250f
        val ts = min(h * 0.42f, w * (if (wide) 0.24f else 0.42f))
        text("${x.temp}°", pad - ts * 0.04f, pad + lineH(ts) * 0.42f, ts, ink, tfNum, "left", w * 0.62f, shadow = false)
        val ic = min(h * 0.38f, w * 0.34f)
        icon(w - pad - ic / 2, pad + ic / 2, ic, x.code, x.isDay)
        val fs = (m * 0.085f).coerceIn(10f, 15f)
        var by = h - pad - lineH(fs) * 0.5f
        if (on("hilo")) { text("${x.hi}° / ${x.lo}°", pad, by, fs, dim, tfR, "left", w * 0.6f, shadow = false) }
        if (on("place")) text(x.place, w - pad, by, fs, dim, tfR, "right", w * 0.4f, shadow = false)
        by -= lineH(fs)
        if (on("cond")) text(weatherText(x.code), pad, by, fs * 1.15f, ink, tfM, "left", w - pad * 2, shadow = false)
        if (wide && on("hours") && h >= 150f) hoursRow(x, RectF(w * 0.45f, pad + ic + 4f, w - pad, by - lineH(fs)), ink, dim)
    }

    fun batteryHyper() {
        background()
        val b = d.battery
        val pad = (m * 0.1f).coerceIn(10f, 18f)
        val col = if (Renderer.low(b)) Renderer.RED else pal.accent
        // pila che si riempie dal basso
        val tall = h >= w * 0.8f
        val cellW = if (tall) min(w * 0.36f, (h - pad * 2) * 0.5f) else min(w * 0.22f, h - pad * 2) * 0.62f
        val cellH = if (tall) h - pad * 2 else h - pad * 2
        val cell = RectF(w - pad - cellW, pad, w - pad, pad + cellH)
        val rad = cellW * 0.26f
        c.drawRoundRect(cell, rad, rad, paint(pal.face))
        val fillTop = cell.bottom - cell.height() * b.level / 100f
        c.save()
        val clip = Path().apply { addRoundRect(cell, rad, rad, Path.Direction.CW) }
        c.clipPath(clip)
        c.drawRect(cell.left, fillTop, cell.right, cell.bottom, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, fillTop, 0f, cell.bottom, tint(col, 0.15f), col, Shader.TileMode.CLAMP)
        })
        // onda sul bordo del liquido
        val wave = Path().apply {
            moveTo(cell.left, fillTop)
            quadTo(cell.left + cell.width() * 0.25f, fillTop - 3f, cell.centerX(), fillTop)
            quadTo(cell.left + cell.width() * 0.75f, fillTop + 3f, cell.right, fillTop)
            lineTo(cell.right, fillTop + 4f); lineTo(cell.left, fillTop + 4f); close()
        }
        c.drawPath(wave, paint(tint(col, 0.3f)))
        c.restore()
        if (b.charging) bolt(cell.centerX(), cell.centerY(), cellW * 0.22f, 0xFFFFFFFF.toInt())
        val tx = pad
        val maxW = cell.left - pad * 1.5f
        val ts = min(h * 0.32f, maxW * 0.42f)
        text("${b.level}", tx, h * 0.42f, ts, pal.text, tfNum, "left", maxW)
        val tw = Renderer.textPaint(ts * k, 0, tfNum, null).measureText("${b.level}")
        text("%", tx + min(tw, maxW) + 2f, h * 0.42f + ts * 0.18f, ts * 0.38f, pal.dim, tfR, "left")
        if (on("status")) text(Renderer.batteryStatus(b), tx, h * 0.42f + lineH(ts) * 0.55f, (ts * 0.24f).coerceIn(10f, 15f), pal.dim, tfR, "left", maxW)
        val acc = accessories()
        if (acc.isNotEmpty() && h >= 120f) {
            val fs = 11f
            var y = h - pad - lineH(fs) * 0.5f
            acc.take(2).reversed().forEach { a ->
                text("${a.name} ${a.level}%", tx, y, fs, pal.dim, tfR, "left", maxW)
                y -= lineH(fs)
            }
        }
    }

    // ------------------------------------------------------------------ Nothing

    val tfDot: Typeface get() = Renderer.typeface("dot")

    /** Fila di puntini: quelli accesi fino alla percentuale. */
    fun dots(r: RectF, frac: Float, on: Int, off: Int) {
        val d0 = r.height()
        val n = (r.width() / (d0 * 1.6f)).toInt().coerceIn(5, 30)
        val step = r.width() / n
        val lit = (frac * n + 0.0001f).toInt().coerceIn(0, n)
        for (i in 0 until n) c.drawCircle(r.left + step * (i + 0.5f), r.centerY(), d0 / 2, paint(if (i < lit) on else off))
    }

    fun weatherNothing() {
        background()
        val x = wx ?: return noWeather(pal.text)
        val pad = (m * 0.1f).coerceIn(10f, 18f)
        val wide = w >= 250f
        val leftW = if (wide && on("hours")) w * 0.45f else w - pad * 2
        val fs = (m * 0.08f).coerceIn(9f, 14f)
        if (on("place")) text(x.place.ifBlank { "POSIZIONE" }.uppercase(loc), pad, pad + lineH(fs) * 0.5f, fs, pal.dim, tfDot, "left", leftW - m * 0.3f, shadow = false)
        val ic = min(m * 0.3f, 56f)
        icon(pad + leftW - ic / 2, pad + ic / 2, ic, x.code, x.isDay)
        val ts = min(h * 0.38f, leftW * 0.5f)
        val ty = h - pad - (if (on("cond") || on("hilo")) lineH(fs) * 1.2f else 0f) - lineH(ts) * 0.42f
        text("${x.temp}°", pad - 2f, ty, ts, pal.text, tfDot, "left", leftW, shadow = false)
        val line = listOfNotNull(
            if (on("cond")) weatherText(x.code).uppercase(loc) else null,
            if (on("hilo")) "${x.hi}°/${x.lo}°" else null
        ).joinToString("  ")
        text(line, pad, h - pad - lineH(fs) * 0.5f, fs, pal.text, tfDot, "left", leftW, shadow = false)
        if (wide && on("hours")) {
            c.drawLine(pad + leftW + pad * 0.5f, pad, pad + leftW + pad * 0.5f, h - pad, paint(pal.track).apply { strokeWidth = 1f })
            hoursRow(x, RectF(pad + leftW + pad, pad, w - pad, h - pad), pal.text, pal.dim)
        }
        c.drawCircle(w - m * 0.1f, m * 0.1f, m * 0.03f, paint(pal.accent))
    }

    fun batteryNothing() {
        background()
        val b = d.battery
        val pad = (m * 0.11f).coerceIn(10f, 18f)
        val col = if (Renderer.low(b)) pal.accent else pal.text
        val fs = (m * 0.08f).coerceIn(9f, 14f)
        text(if (b.charging) "IN CARICA" else "BATTERIA", pad, pad + lineH(fs) * 0.5f, fs, if (b.charging) pal.accent else pal.dim, tfDot, "left", w - pad * 2, shadow = false)
        val ts = min(h * 0.42f, w * 0.38f)
        text("${b.level}%", pad - 2f, h * 0.52f, ts, pal.text, tfDot, "left", w - pad * 2, shadow = false)
        val dh = (m * 0.05f).coerceIn(4f, 9f)
        dots(RectF(pad, h - pad - dh, w - pad, h - pad), b.level / 100f, col, pal.track)
        if (on("status") && w >= 220f) text(Renderer.batteryStatus(b).uppercase(loc), w - pad, pad + lineH(fs) * 0.5f, fs, pal.dim, tfDot, "right", w * 0.45f, shadow = false)
    }

    fun earbuds() {
        background()
        val pad = (m * 0.11f).coerceIn(10f, 18f)
        val fs = (m * 0.08f).coerceIn(9f, 14f)
        text("ACCESSORI", pad, pad + lineH(fs) * 0.5f, fs, pal.dim, tfDot, "left", w - pad * 2, shadow = false)
        c.drawCircle(w - m * 0.1f, m * 0.1f, m * 0.03f, paint(pal.accent))
        val list = d.battery.accessories
        val area = RectF(pad, pad + lineH(fs) * 1.3f, w - pad, h - pad)
        if (!hasBtPermission(ctx)) {
            text("CONSENTI IL BLUETOOTH", area.centerX(), area.centerY(), fs, pal.text, tfDot, "center", area.width(), shadow = false)
            return
        }
        if (list.isEmpty()) {
            text("NESSUN ACCESSORIO", area.centerX(), area.centerY(), fs * 1.1f, pal.text, tfDot, "center", area.width(), shadow = false)
            return
        }
        val n = min(list.size, if (w >= h * 1.6f) 4 else 2)
        val cw = area.width() / n
        list.take(n).forEachIndexed { i, a ->
            val cx = area.left + cw * (i + 0.5f)
            val rr = min(cw * 0.4f, (area.height() - lineH(fs) * 1.3f) * 0.45f)
            val cy = area.top + rr + 2f
            // anello a puntini
            val dotsN = 24
            val lit = (a.level / 100f * dotsN).toInt()
            for (j in 0 until dotsN) {
                val ang = Math.toRadians(j * 360.0 / dotsN - 90)
                c.drawCircle(cx + rr * cos(ang).toFloat(), cy + rr * sin(ang).toFloat(), rr * 0.07f,
                    paint(if (j < lit) (if (a.level <= 15) pal.accent else pal.text) else pal.track))
            }
            deviceGlyph(a.kind, cx, cy - rr * 0.18f, rr * 0.55f, pal.text)
            text("${a.level}%", cx, cy + rr * 0.42f, rr * 0.32f, pal.text, tfDot, "center", rr * 1.4f, shadow = false)
            text(a.name.uppercase(loc), cx, cy + rr + lineH(fs) * 0.7f, fs * 0.9f, pal.dim, tfDot, "center", cw - 4f, shadow = false)
        }
    }

    fun steps() {
        background()
        val pad = (m * 0.11f).coerceIn(10f, 18f)
        val fs = (m * 0.08f).coerceIn(9f, 14f)
        text("PASSI DI OGGI", pad, pad + lineH(fs) * 0.5f, fs, pal.dim, tfDot, "left", w - pad * 2, shadow = false)
        c.drawCircle(w - m * 0.1f, m * 0.1f, m * 0.03f, paint(pal.accent))
        if (!d.stepsAllowed) {
            text("CONSENTI L'ATTIVITÀ FISICA", w / 2, h / 2, fs, pal.text, tfDot, "center", w - pad * 2, shadow = false)
            return
        }
        val goal = StepsRepo.GOAL
        val ts = min(h * 0.36f, w * 0.3f)
        val str = "%,d".format(loc, d.steps)
        text(str, pad - 2f, h * 0.5f, ts, pal.text, tfDot, "left", w - pad * 2, shadow = false)
        if (on("goal")) {
            val dh = (m * 0.05f).coerceIn(4f, 9f)
            val frac = d.steps.toFloat() / goal
            dots(RectF(pad, h - pad - dh, w - pad, h - pad), frac, if (frac >= 1f) pal.accent else pal.text, pal.track)
            text("${(frac * 100).toInt().coerceAtMost(999)}% DI ${"%,d".format(loc, goal)}", pad, h - pad - dh - lineH(fs) * 0.8f, fs * 0.9f, pal.dim, tfDot, "left", w - pad * 2, shadow = false)
        }
    }

    fun quote() {
        background()
        val (q, kind) = Quotes.today(d.now)
        val pad = (m * 0.11f).coerceIn(10f, 20f)
        val fs = (m * 0.08f).coerceIn(9f, 14f)
        text(if (kind == "Proverbio") "PROVERBIO DEL GIORNO" else "CURIOSITÀ DEL GIORNO", pad, pad + lineH(fs) * 0.5f, fs, pal.dim, tfDot, "left", w - pad * 2, shadow = false)
        c.drawCircle(w - m * 0.1f, m * 0.1f, m * 0.03f, paint(pal.accent))
        val top = pad + lineH(fs) * 1.4f
        val maxW = (w - pad * 2).toInt().coerceAtLeast(10)
        val maxH = h - top - pad
        var size = (m * 0.16f).coerceAtMost(26f) * k
        val tf = Renderer.typeface(s.font)
        var layout: StaticLayout
        while (true) {
            val p = Renderer.textPaint(size, pal.text, tf, null)
            layout = StaticLayout.Builder.obtain(q, 0, q.length, p, maxW).setLineSpacing(0f, 1.1f).build()
            if (layout.height <= maxH || size <= 9f) break
            size *= 0.92f
        }
        c.save()
        c.translate(pad, top + (maxH - layout.height).coerceAtLeast(0f) / 2f)
        layout.draw(c)
        c.restore()
    }
}

/** Funzioni di colore senza dipendere da Compose. */
object Argb {
    fun alpha(c: Int) = (c ushr 24) and 0xFF
}

// ---------------------------------------------------------------------------------------------
//  Orologio analogico: quadranti e lancette
// ---------------------------------------------------------------------------------------------

object Analog {

    private fun polar(cx: Float, cy: Float, r: Float, deg: Float): Pair<Float, Float> {
        val a = Math.toRadians(deg - 90.0)
        return (cx + r * cos(a).toFloat()) to (cy + r * sin(a).toFloat())
    }

    fun drawDial(c: Canvas, rect: RectF, dial: String, pal: Pal, now: Date, loc: Locale) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f
        if (dial == "sense") { drawSenseDial(c, cx, cy, r, now, loc); return }
        if (dial == "none") return
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.tick; strokeCap = Paint.Cap.ROUND }
        val med = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        when (dial) {
            "bare" -> {
                // senza quadrante: solo quattro tacche
                tick.color = pal.text; tick.strokeWidth = r * 0.05f
                for (i in 0 until 4) {
                    val (x1, y1) = polar(cx, cy, r * 0.96f, i * 90f)
                    val (x2, y2) = polar(cx, cy, r * 0.82f, i * 90f)
                    c.drawLine(x1, y1, x2, y2, tick)
                }
                return
            }
            "nothing" -> {
                c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.face })
                tick.color = pal.text
                for (i in 0 until 12) {
                    val (x, y) = polar(cx, cy, r * 0.84f, i * 30f)
                    c.drawCircle(x, y, r * (if (i % 3 == 0) 0.045f else 0.022f), tick)
                }
                return
            }
            "hyper" -> {
                c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.face })
                tick.color = pal.text; tick.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 12) {
                    val big = i % 3 == 0
                    if (big) {
                        tick.strokeWidth = r * 0.06f
                        val (x1, y1) = polar(cx, cy, r * 0.86f, i * 30f)
                        val (x2, y2) = polar(cx, cy, r * 0.7f, i * 30f)
                        c.drawLine(x1, y1, x2, y2, tick)
                    } else {
                        val (x, y) = polar(cx, cy, r * 0.82f, i * 30f)
                        c.drawCircle(x, y, r * 0.025f, tick)
                    }
                }
                return
            }
            "ios" -> {
                val face = if (pal.dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                val ink = if (pal.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = face })
                c.drawCircle(cx, cy, r - 0.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000; style = Paint.Style.STROKE; strokeWidth = 1.2f })
                for (i in 0 until 60) {
                    val big = i % 5 == 0
                    val (x1, y1) = polar(cx, cy, r * 0.95f, i * 6f)
                    val (x2, y2) = polar(cx, cy, r * (if (big) 0.86f else 0.9f), i * 6f)
                    tick.color = if (big) ink else 0xFF8E8E93.toInt()
                    tick.strokeWidth = r * (if (big) 0.022f else 0.01f)
                    c.drawLine(x1, y1, x2, y2, tick)
                }
                val tf = Typeface.create("sans-serif", Typeface.NORMAL)
                for (i in 1..12) {
                    val (x, y) = polar(cx, cy, r * 0.72f, i * 30f)
                    Renderer.drawTextIn(c, i.toString(), RectF(x - r * 0.2f, y - r * 0.15f, x + r * 0.2f, y + r * 0.15f), r * 0.2f, "center", ink, tf, null)
                }
                return
            }
        }
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.face })
        when (dial) {
            "numbers" -> for (i in 1..12) {
                val (x, y) = polar(cx, cy, r * 0.76f, i * 30f)
                Renderer.drawTextIn(c, i.toString(), RectF(x - r * 0.2f, y - r * 0.15f, x + r * 0.2f, y + r * 0.15f),
                    r * (if (i % 3 == 0) 0.24f else 0.17f), "center", pal.faceInk, med, null)
            }
            "dots" -> for (i in 0 until 12) {
                val (x, y) = polar(cx, cy, r * 0.82f, i * 30f)
                tick.color = if (i % 3 == 0) pal.accent else pal.tick
                c.drawCircle(x, y, r * (if (i % 3 == 0) 0.06f else 0.035f), tick)
            }
            "roman" -> {
                val names = mapOf(0 to "XII", 3 to "III", 6 to "VI", 9 to "IX")
                for (i in 0 until 12) {
                    val n = names[i]
                    if (n != null) {
                        val (x, y) = polar(cx, cy, r * 0.74f, i * 30f)
                        Renderer.drawTextIn(c, n, RectF(x - r * 0.3f, y - r * 0.15f, x + r * 0.3f, y + r * 0.15f), r * 0.2f, "center",
                            pal.faceInk, Typeface.create("serif", Typeface.NORMAL), null)
                    } else {
                        val (x1, y1) = polar(cx, cy, r * 0.9f, i * 30f)
                        val (x2, y2) = polar(cx, cy, r * 0.82f, i * 30f)
                        tick.strokeWidth = r * 0.03f
                        c.drawLine(x1, y1, x2, y2, tick)
                    }
                }
            }
            else -> for (i in 0 until 12) {
                val big = i % 3 == 0
                val (x1, y1) = polar(cx, cy, r * 0.90f, i * 30f)
                val (x2, y2) = polar(cx, cy, r * (if (big) 0.76f else 0.83f), i * 30f)
                tick.strokeWidth = r * (if (big) 0.055f else 0.035f)
                c.drawLine(x1, y1, x2, y2, tick)
            }
        }
    }

    /** Colori delle lancette secondo il quadrante. */
    private fun handColor(kind: String, s: Style, pal: Pal): Int = when {
        kind == "second" -> when (s.dial) {
            "sense" -> 0xFFF2F3F5.toInt()
            "ios" -> 0xFFFF9500.toInt()
            "nothing", "hyper" -> pal.accent
            else -> Renderer.RED
        }
        s.dial == "nothing" || s.dial == "hyper" -> pal.text
        s.dial == "ios" -> if (pal.dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        s.dial == "bare" || s.dial == "none" -> if (kind == "minute") pal.accent else pal.text
        kind == "minute" -> pal.accent
        else -> pal.faceInk
    }

    /** Lancetta nel quadrato [rect] ruotata di [deg] gradi (0 = ore 12). Con [cap] anche il perno centrale. */
    fun drawHand(c: Canvas, rect: RectF, deg: Float, kind: String, s: Style, pal: Pal, cap: Boolean = false) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f
        val col = handColor(kind, s, pal)
        val style = s.hands
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; strokeCap = Paint.Cap.ROUND }
        c.save()
        c.rotate(deg, cx, cy)
        if (kind == "second") {
            p.strokeWidth = r * 0.018f
            c.drawLine(cx, cy + r * 0.16f, cx, cy - r * 0.84f, p)
            if (style != "sense") c.drawCircle(cx, cy, r * 0.045f, p)
        } else {
            val hour = kind == "hour"
            when (style) {
                "thin" -> {
                    p.strokeWidth = r * (if (hour) 0.05f else 0.035f)
                    c.drawLine(cx, cy + r * 0.1f, cx, cy - r * (if (hour) 0.5f else 0.78f), p)
                }
                "nothing" -> {
                    // lancette larghe e arrotondate, con il bordo staccato dal centro
                    p.strokeWidth = r * (if (hour) 0.11f else 0.075f)
                    c.drawLine(cx, cy - r * 0.16f, cx, cy - r * (if (hour) 0.5f else 0.76f), p)
                }
                "ios" -> {
                    // stelo sottile vicino al centro, poi lancetta piena
                    p.strokeWidth = r * 0.03f
                    c.drawLine(cx, cy, cx, cy - r * 0.12f, p)
                    p.strokeWidth = r * (if (hour) 0.075f else 0.06f)
                    c.drawLine(cx, cy - r * 0.12f, cx, cy - r * (if (hour) 0.5f else 0.8f), p)
                }
                "sense" -> senseHand(c, cx, cy, if (hour) r * 0.36f else r * 0.74f, if (hour) r * 0.10f else r * 0.12f,
                    if (hour) r * 0.078f else r * 0.058f)
                "dauphine" -> {
                    val len = r * (if (hour) 0.52f else 0.78f)
                    val wd = r * (if (hour) 0.09f else 0.07f)
                    c.drawPath(Path().apply {
                        moveTo(cx, cy - len); lineTo(cx + wd / 2, cy - len * 0.12f); lineTo(cx, cy + r * 0.06f)
                        lineTo(cx - wd / 2, cy - len * 0.12f); close()
                    }, p)
                }
                "arrow" -> {
                    val len = r * (if (hour) 0.5f else 0.76f)
                    p.strokeWidth = r * (if (hour) 0.06f else 0.045f)
                    c.drawLine(cx, cy, cx, cy - len + r * 0.08f, p)
                    val a = r * (if (hour) 0.09f else 0.075f)
                    c.drawPath(Path().apply { moveTo(cx, cy - len - a * 0.3f); lineTo(cx + a, cy - len + a * 1.1f); lineTo(cx - a, cy - len + a * 1.1f); close() }, p)
                }
                "block" -> {
                    val wd = r * (if (hour) 0.1f else 0.07f)
                    val len = r * (if (hour) 0.5f else 0.76f)
                    c.drawRect(cx - wd / 2, cy - len, cx + wd / 2, cy + r * 0.12f, p)
                }
                else -> {
                    p.strokeWidth = r * (if (hour) 0.12f else 0.085f)
                    c.drawLine(cx, cy, cx, cy - r * (if (hour) 0.46f else 0.68f), p)
                }
            }
        }
        c.restore()
        if (cap) {
            when (style) {
                "sense" -> {
                    c.drawCircle(cx, cy, r * 0.055f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E3135.toInt() })
                    c.drawCircle(cx, cy, r * 0.042f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(cx - r * 0.015f, cy - r * 0.015f, r * 0.05f, 0xFFFFFFFF.toInt(), 0xFF9A9EA4.toInt(), Shader.TileMode.CLAMP)
                    })
                }
                "nothing" -> c.drawCircle(cx, cy, r * 0.05f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (s.seconds) pal.accent else pal.text })
                "ios" -> {
                    c.drawCircle(cx, cy, r * 0.05f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (s.seconds) 0xFFFF9500.toInt() else col })
                    c.drawCircle(cx, cy, r * 0.02f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (pal.dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() })
                }
                else -> c.drawCircle(cx, cy, r * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (s.seconds) Renderer.RED else pal.accent })
            }
        }
    }

    /** Lancetta affusolata di Sensation (punta verso le 12, già ruotata dal chiamante). */
    private fun senseHand(c: Canvas, cx: Float, cy: Float, len: Float, back: Float, width: Float) {
        val p = Path().apply {
            moveTo(cx - width / 2f, cy + back)
            lineTo(cx - width * 0.30f, cy - len)
            lineTo(cx, cy - len - width * 0.55f)
            lineTo(cx + width * 0.30f, cy - len)
            lineTo(cx + width / 2f, cy + back)
            close()
        }
        c.save(); c.translate(0f, width * 0.25f)
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x47000000 })
        c.restore()
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx - width / 2f, 0f, cx + width / 2f, 0f, 0xFFFFFFFF.toInt(), 0xFFD5D8DC.toInt(), Shader.TileMode.CLAMP)
        })
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5A5E64.toInt(); style = Paint.Style.STROKE; strokeWidth = 0.9f })
    }

    /** Quadrante di Sense: anello scuro con i numeri e quadrante d'argento con giorno e data. */
    private fun drawSenseDial(c: Canvas, cx: Float, cy: Float, r0: Float, now: Date, loc: Locale) {
        val r = r0 * 0.97f
        val a = Paint(Paint.ANTI_ALIAS_FLAG)
        a.color = 0x59000000
        c.drawCircle(cx, cy + r * 0.04f, r, a)
        a.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(0xFF73777D.toInt(), 0xFF35383D.toInt(), 0xFF1A1C1F.toInt(), 0xFF2C2F33.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, a)
        c.drawCircle(cx, cy, r - 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC3C6CB.toInt(); style = Paint.Style.STROKE; strokeWidth = 1.4f })
        c.drawArc(RectF(cx - r * 0.92f, cy - r * 0.92f, cx + r * 0.92f, cy + r * 0.92f), 195f, 120f, false,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x29FFFFFF; style = Paint.Style.STROKE; strokeWidth = r * 0.06f; strokeCap = Paint.Cap.ROUND })
        val med = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val reg = Typeface.create("sans-serif", Typeface.NORMAL)
        for (i in 1..12) {
            val big = i % 3 == 0
            val (x, y) = polar(cx, cy, r * 0.805f, i * 30f)
            val p = Renderer.textPaint(r * (if (big) 0.23f else 0.165f), 0xFFEFF1F3.toInt(), if (big) med else reg, null).apply {
                setShadowLayer(r * 0.02f, 0f, r * 0.012f, 0xB3000000.toInt())
                textAlign = Paint.Align.CENTER
            }
            val fm = p.fontMetrics
            c.drawText(i.toString(), x, y - (fm.ascent + fm.descent) / 2, p)
        }
        val ri = r * 0.62f
        c.drawCircle(cx, cy, ri + r * 0.025f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80000000.toInt() })
        c.drawCircle(cx, cy, ri, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx - ri * 0.3f, cy - ri * 0.35f, ri * 1.6f,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFEDEFF1.toInt(), 0xFFC4C8CD.toInt()), null, Shader.TileMode.CLAMP)
        })
        c.drawCircle(cx, cy, ri, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E9298.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f })
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 60) {
            val h = i % 5 == 0
            tp.color = if (h) 0xFF3B3E43.toInt() else 0xFF9A9EA4.toInt()
            tp.strokeWidth = if (h) r * 0.024f else r * 0.009f
            val (x1, y1) = polar(cx, cy, ri * 0.95f, i * 6f)
            val (x2, y2) = polar(cx, cy, ri * (if (h) 0.80f else 0.89f), i * 6f)
            c.drawLine(x1, y1, x2, y2, tp)
        }
        val day = SimpleDateFormat("EEE", loc).format(now).replace(".", "").uppercase(loc)
        val date = SimpleDateFormat("d MMM", loc).format(now).replace(".", "").uppercase(loc)
        val ts = r * 0.088f
        Renderer.drawTextIn(c, day, RectF(cx - ri * 0.8f, cy - ts, cx - ri * 0.2f, cy + ts), ts, "center", 0xFF5A5E64.toInt(), med, null)
        Renderer.drawTextIn(c, date, RectF(cx + ri * 0.2f, cy - ts, cx + ri * 0.8f, cy + ts), ts, "center", 0xFF5A5E64.toInt(), med, null)
    }
}

// ---------------------------------------------------------------------------------------------
//  Icone meteo
// ---------------------------------------------------------------------------------------------

object WeatherIcons {
    private const val SUN = 0xFFFFB300.toInt()
    private const val MOON = 0xFFFFE082.toInt()
    private const val CLOUD = 0xFF90A4AE.toInt()
    private const val RAIN = 0xFF42A5F5.toInt()
    private const val SNOW = 0xFF81D4FA.toInt()
    private const val BOLT = 0xFFFFC107.toInt()

    private fun kind(code: Int) = when (code) {
        0 -> 0
        1, 2 -> 1
        3 -> 2
        45, 48 -> 3
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> 4
        71, 73, 75, 77, 85, 86 -> 5
        95, 96, 99 -> 6
        else -> 2
    }

    private fun sun(c: Canvas, x: Float, y: Float, r: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SUN; strokeCap = Paint.Cap.ROUND; strokeWidth = r * 0.16f }
        c.drawCircle(x, y, r * 0.62f, p)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
            c.drawLine(x + dx * r * 0.88f, y + dy * r * 0.88f, x + dx * r * 1.2f, y + dy * r * 1.2f, p)
        }
    }

    private fun moon(c: Canvas, x: Float, y: Float, r: Float) {
        val a = Path().apply { addOval(RectF(x - r * 0.8f, y - r * 0.8f, x + r * 0.8f, y + r * 0.8f), Path.Direction.CW) }
        val b = Path().apply { addOval(RectF(x - r * 0.35f, y - r, x + r * 1.15f, y + r * 0.5f), Path.Direction.CW) }
        a.op(b, Path.Op.DIFFERENCE)
        c.drawPath(a, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MOON })
    }

    private fun cloud(c: Canvas, x: Float, y: Float, w: Float, color: Int) {
        val h = w * 0.6f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawCircle(x + w * 0.30f, y + h * 0.55f, h * 0.38f, p)
        c.drawCircle(x + w * 0.56f, y + h * 0.45f, h * 0.48f, p)
        c.drawRoundRect(RectF(x + w * 0.08f, y + h * 0.5f, x + w * 0.92f, y + h), h * 0.25f, h * 0.25f, p)
    }

    fun draw(c: Canvas, r: RectF, code: Int, isDay: Boolean) {
        val s = min(r.width(), r.height())
        if (s <= 1f) return
        c.save()
        c.translate(r.centerX() - s / 2, r.centerY() - s / 2)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        when (kind(code)) {
            0 -> if (isDay) sun(c, s / 2, s / 2, s * 0.3f) else moon(c, s / 2, s / 2, s * 0.3f)
            1 -> {
                if (isDay) sun(c, s * 0.62f, s * 0.36f, s * 0.2f) else moon(c, s * 0.6f, s * 0.36f, s * 0.2f)
                cloud(c, s * 0.06f, s * 0.34f, s * 0.86f, CLOUD)
            }
            2 -> cloud(c, s * 0.04f, s * 0.22f, s * 0.92f, CLOUD)
            3 -> {
                cloud(c, s * 0.1f, s * 0.08f, s * 0.8f, CLOUD)
                line.color = CLOUD; line.strokeWidth = s * 0.05f
                for (i in 0 until 3) { val y = s * (0.68f + i * 0.11f); c.drawLine(s * 0.2f, y, s * 0.8f, y, line) }
            }
            4 -> {
                cloud(c, s * 0.06f, s * 0.08f, s * 0.88f, CLOUD)
                line.color = RAIN; line.strokeWidth = s * 0.06f
                for (i in 0 until 3) { val x = s * (0.3f + i * 0.2f); c.drawLine(x, s * 0.7f, x - s * 0.06f, s * 0.9f, line) }
            }
            5 -> {
                cloud(c, s * 0.06f, s * 0.08f, s * 0.88f, CLOUD)
                line.color = SNOW
                for (i in 0 until 3) c.drawCircle(s * (0.3f + i * 0.2f), s * (0.76f + (i % 2) * 0.09f), s * 0.045f, line)
            }
            else -> {
                cloud(c, s * 0.06f, s * 0.06f, s * 0.88f, 0xFF607D8B.toInt())
                c.drawPath(Path().apply {
                    moveTo(s * 0.54f, s * 0.58f); lineTo(s * 0.40f, s * 0.80f); lineTo(s * 0.50f, s * 0.80f)
                    lineTo(s * 0.44f, s * 0.97f); lineTo(s * 0.64f, s * 0.72f); lineTo(s * 0.53f, s * 0.72f); close()
                }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BOLT })
            }
        }
        c.restore()
    }
}
