package com.marco.mlwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.format.DateFormat
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Dati per disegnare: meteo, batteria e ora. */
class WData(val weather: Weather?, val battery: Battery, val now: Long, val is24: Boolean) {
    companion object {
        fun load(ctx: Context, sample: Boolean = false) = WData(
            WeatherRepo.cached(ctx) ?: if (sample) Weather.sample() else null,
            readBattery(ctx), System.currentTimeMillis(), DateFormat.is24HourFormat(ctx)
        )
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
    /** riquadro di ogni elemento (dp), per toccarli e spostarli nell'editor */
    val boxes: LinkedHashMap<String, RectF>,
    val live: List<LiveSlot>,
    val analog: RectF?
)

object Renderer {

    fun typeface(font: String): Typeface = when (font) {
        "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "regular" -> Typeface.create("sans-serif", Typeface.NORMAL)
        "medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        else -> Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val tfRegular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfBold = Typeface.create("sans-serif", Typeface.BOLD)

    const val RED = 0xFFE5484D.toInt()

    /** Solo le misure, senza disegnare (per l'editor). */
    fun measure(ctx: Context, t: Template, cfg: Cfg, wDp: Float, hDp: Float, data: WData): Drawn {
        val pic = Picture()
        val c = pic.beginRecording(wDp.toInt().coerceAtLeast(1), hDp.toInt().coerceAtLeast(1))
        val pal = Palettes.palette(ctx, cfg.style, false)
        val d = draw(ctx, c, t, cfg, wDp, hDp, pal, data, drawLive = true)
        pic.endRecording()
        return d
    }

    /**
     * Disegna il widget in coordinate dp (la tela deve essere già scalata).
     * Con [drawLive] le ore e le lancette vengono disegnate qui (anteprima); altrimenti restano come
     * posti liberi da riempire con gli orologi di sistema (widget vero).
     */
    fun draw(
        ctx: Context, c: Canvas, t: Template, cfg: Cfg, w: Float, h: Float, pal: Pal, data: WData,
        drawLive: Boolean, skipAnalog: Boolean = false
    ): Drawn {
        val s = cfg.style
        val u = min(w, h) / 100f
        val boxes = LinkedHashMap<String, RectF>()
        val live = ArrayList<LiveSlot>()
        var analog: RectF? = null

        // sfondo
        if (Argb.alpha(pal.bg) > 0) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.bg }
            val r = s.corner.toFloat().coerceAtMost(min(w, h) / 2f)
            c.drawRoundRect(RectF(0f, 0f, w, h), r, r, p)
            pal.border?.let { b ->
                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b; style = Paint.Style.STROKE; strokeWidth = 1f }
                c.drawRoundRect(RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), r, r, bp)
            }
        }

        val loc = Locale.getDefault()
        val now = Date(data.now)
        val wx = data.weather

        for (el in cfg.els) {
            if (!el.visible) continue
            val col = when (el.color) {
                "accent" -> pal.accent
                "dim" -> pal.dim
                "custom" -> el.customColor
                else -> pal.text
            }
            val ax = el.x * w
            val ay = el.y * h
            val sz = el.size * u
            fun text(str: String, tf: Typeface): RectF = drawText(c, str, ax, ay, sz, el.align, col, tf, pal)

            val box: RectF = when (el.kind) {
                "hour", "minute", "cminute", "time", "seconds" -> {
                    val (f24, f12, sample) = when (el.kind) {
                        "hour" -> Triple("HH", "hh", "88")
                        "minute" -> Triple("mm", "mm", "88")
                        "cminute" -> Triple(":mm", ":mm", ":88")
                        "seconds" -> Triple("ss", "ss", "88")
                        else -> Triple("HH:mm", "h:mm", "88:88")
                    }
                    val tf = typeface(s.font)
                    val rect = textBox(sample, ax, ay, sz, el.align, tf)
                    if (drawLive) {
                        val str = SimpleDateFormat(if (data.is24) f24 else f12, loc).format(now)
                        drawTextIn(c, str, rect, sz, el.align, col, tf, pal)
                    } else {
                        live += LiveSlot(rect, sz, f24, f12, col, s.font, pal.shadow, el.align)
                    }
                    rect
                }
                "day" -> text(SimpleDateFormat("EEEE", loc).format(now).cap(loc), tfMedium)
                "date" -> text(SimpleDateFormat("d MMMM", loc).format(now), tfRegular)
                "dayshort" -> text(SimpleDateFormat("EEE d MMM", loc).format(now).replace(".", "").cap(loc), tfMedium)
                "title" -> text("Pioggia nelle prossime 2 ore", tfBold)
                "analog" -> {
                    val r = sz / 2f
                    val rect = RectF(ax - r, ay - r, ax + r, ay + r)
                    analog = rect
                    if (!skipAnalog) {
                        Analog.drawDial(c, rect, s.dial, pal, now, loc)
                        if (drawLive) {
                            val cal = Calendar.getInstance().apply { timeInMillis = data.now }
                            val sec = cal.get(Calendar.SECOND).toFloat()
                            val min = cal.get(Calendar.MINUTE) + (if (s.seconds) sec / 60f else 0f)
                            val hr = cal.get(Calendar.HOUR) + min / 60f
                            Analog.drawHand(c, rect, hr * 30f, "hour", s, pal)
                            Analog.drawHand(c, rect, min * 6f, "minute", s, pal, cap = !s.seconds)
                            if (s.seconds) Analog.drawHand(c, rect, sec * 6f, "second", s, pal, cap = true)
                        }
                    }
                    rect
                }
                "flip" -> drawFlip(c, el, ax, ay, w, sz, s, data, drawLive, live, loc, now)
                "wicon" -> {
                    val r = RectF(ax - sz / 2, ay - sz / 2, ax + sz / 2, ay + sz / 2)
                    WeatherIcons.draw(c, r, wx?.code ?: 3, wx?.isDay ?: true)
                    r
                }
                "temp" -> text(wx?.let { "${it.temp}°" } ?: "--°", typeface(s.font))
                "cond" -> text(wx?.let { weatherText(it.code) } ?: "Apri MlWidget per il meteo", tfRegular)
                "place" -> text(wx?.place?.ifBlank { null } ?: "Posizione", tfMedium)
                "placepill" -> {
                    val str = wx?.place?.ifBlank { null } ?: java.util.TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
                    drawPill(c, str, ax, ay, sz)
                }
                "hilo" -> text(wx?.let { "Max ${it.hi}°  Min ${it.lo}°" } ?: "Max --°  Min --°", tfRegular)
                "feels" -> text("Percepita ${wx?.feels ?: "--"}°", tfRegular)
                "humidity" -> text("Umidità ${wx?.humidity ?: "--"}%", tfRegular)
                "wind" -> text("Vento ${wx?.wind ?: "--"} km/h", tfRegular)
                "updated" -> text(wx?.let { "Agg. " + SimpleDateFormat("HH:mm", loc).format(Date(it.fetched)) } ?: "", tfRegular)
                "hours" -> drawHours(c, el, ax, ay, w, sz, pal, col, wx, loc)
                "days" -> drawDays(c, el, ax, ay, w, sz, pal, col, wx, loc)
                "rain" -> drawRain(c, el, ax, ay, w, sz, u, pal, col, wx)
                "bring", "bringe" -> drawRing(c, ax, ay, sz / 2f, data.battery, pal, col, el.kind == "bring", s)
                "bpct" -> text("${data.battery.level}%", typeface(s.font))
                "bstatus" -> text(batteryStatus(data.battery), tfRegular)
                "bbar" -> {
                    val bw = el.w * w
                    val r = RectF(ax - bw / 2, ay - sz / 2, ax + bw / 2, ay + sz / 2)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.track }
                    c.drawRoundRect(r, sz / 2, sz / 2, p)
                    p.color = if (low(data.battery)) RED else pal.accent
                    val fw = (bw * data.battery.level / 100f).coerceAtLeast(sz)
                    c.drawRoundRect(RectF(r.left, r.top, r.left + fw, r.bottom), sz / 2, sz / 2, p)
                    r
                }
                "note" -> drawNote(c, s.note.ifBlank { "Tocca per scrivere una nota" }, ax, ay, el.w * w, sz, h, col, s, pal)
                "search" -> drawSearch(c, ax, ay, el.w * w, sz, pal, s)
                else -> RectF(ax, ay, ax, ay)
            }
            boxes[el.id] = box
        }
        return Drawn(boxes, live, analog)
    }

    private fun String.cap(loc: Locale) = replaceFirstChar { it.titlecase(loc) }

    fun low(b: Battery) = b.level <= 15 && !b.charging

    fun batteryStatus(b: Battery) = when {
        b.charging && b.level >= 100 -> "Carica completa"
        b.charging -> "In carica"
        low(b) -> "Batteria scarica"
        else -> "Batteria"
    }

    // ------------------------------------------------------------------ testo

    fun textPaint(size: Float, color: Int, tf: Typeface, pal: Pal?) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = tf
        if (pal?.shadow == true) {
            setShadowLayer(size * 0.08f + 1.5f, 0f, 1f, if (pal.dark) 0x99000000.toInt() else 0x88FFFFFF.toInt())
        }
    }

    /** Riquadro del testo, agganciato al punto (x, y) secondo l'allineamento, centrato in verticale. */
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

    private fun drawText(c: Canvas, str: String, x: Float, y: Float, size: Float, align: String, color: Int, tf: Typeface, pal: Pal): RectF {
        val r = textBox(str, x, y, size, align, tf)
        drawTextIn(c, str, r, size, align, color, tf, pal)
        return r
    }

    private fun drawPill(c: Canvas, str: String, x: Float, y: Float, size: Float): RectF {
        val p = textPaint(size, 0xFFFFFFFF.toInt(), tfMedium, null)
        val tw = p.measureText(str)
        val ph = size * 1.9f
        val r = RectF(x - tw / 2 - size * 0.9f, y - ph / 2, x + tw / 2 + size * 0.9f, y + ph / 2)
        c.drawRoundRect(r, ph / 2, ph / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8C000000.toInt() })
        drawTextIn(c, str, r, size, "center", 0xFFFFFFFF.toInt(), tfMedium, null)
        return r
    }

    // ------------------------------------------------------------------ meteo

    private fun drawHours(c: Canvas, el: El, x: Float, y: Float, w: Float, size: Float, pal: Pal, col: Int, wx: Weather?, loc: Locale): RectF {
        val bw = el.w * w
        val n = (bw / (size * 5.2f)).toInt().coerceIn(3, 8)
        val colW = bw / n
        val ic = size * 2.4f
        val total = size * 1.3f + ic + size * 1.3f
        val top = y - total / 2
        val list = wx?.hours?.take(n) ?: emptyList()
        for (i in 0 until n) {
            val cx = x - bw / 2 + colW * (i + 0.5f)
            val hr = list.getOrNull(i)
            val lbl = hr?.let { SimpleDateFormat("HH", loc).format(Date(it.time)) } ?: "--"
            drawTextIn(c, lbl, RectF(cx - colW / 2, top, cx + colW / 2, top + size * 1.3f), size * 0.9f, "center", pal.dim, tfRegular, pal)
            WeatherIcons.draw(c, RectF(cx - ic / 2, top + size * 1.3f, cx + ic / 2, top + size * 1.3f + ic), hr?.code ?: 3, hr?.isDay ?: true)
            drawTextIn(c, hr?.let { "${it.temp}°" } ?: "--°", RectF(cx - colW / 2, top + size * 1.3f + ic, cx + colW / 2, top + total), size, "center", col, tfMedium, pal)
        }
        return RectF(x - bw / 2, top, x + bw / 2, top + total)
    }

    private fun drawDays(c: Canvas, el: El, x: Float, y: Float, w: Float, size: Float, pal: Pal, col: Int, wx: Weather?, loc: Locale): RectF {
        val bw = el.w * w
        val n = (bw / (size * 6f)).toInt().coerceIn(3, 7)
        val colW = bw / n
        val ic = size * 2.4f
        val total = size * 1.3f + ic + size * 1.3f
        val top = y - total / 2
        val list = wx?.days?.drop(1)?.take(n) ?: emptyList()
        for (i in 0 until n) {
            val cx = x - bw / 2 + colW * (i + 0.5f)
            val d = list.getOrNull(i)
            val lbl = d?.let { SimpleDateFormat("EEE", loc).format(Date(it.time)).replace(".", "").replaceFirstChar { ch -> ch.titlecase(loc) } } ?: "--"
            drawTextIn(c, lbl, RectF(cx - colW / 2, top, cx + colW / 2, top + size * 1.3f), size * 0.9f, "center", pal.dim, tfRegular, pal)
            WeatherIcons.draw(c, RectF(cx - ic / 2, top + size * 1.3f, cx + ic / 2, top + size * 1.3f + ic), d?.code ?: 3, true)
            drawTextIn(c, d?.let { "${it.hi}° ${it.lo}°" } ?: "--", RectF(cx - colW / 2, top + size * 1.3f + ic, cx + colW / 2, top + total), size * 0.95f, "center", col, tfMedium, pal)
        }
        return RectF(x - bw / 2, top, x + bw / 2, top + total)
    }

    private fun drawRain(c: Canvas, el: El, x: Float, y: Float, w: Float, size: Float, u: Float, pal: Pal, col: Int, wx: Weather?): RectF {
        val bw = el.w * w
        val r = RectF(x - bw / 2, y - size / 2, x + bw / 2, y + size / 2)
        val lblH = 7f * u
        val chart = RectF(r.left, r.top, r.right, r.bottom - lblH)
        val vals = wx?.rain ?: emptyList()
        val maxV = maxOf(1.5f, vals.maxOrNull() ?: 0f)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.track; strokeWidth = 1f }
        c.drawLine(chart.left, chart.bottom, chart.right, chart.bottom, line)
        if (vals.isEmpty() || vals.all { it < 0.05f }) {
            drawTextIn(c, if (wx == null) "Apri MlWidget per il meteo" else "Niente pioggia nelle prossime 2 ore",
                RectF(chart.left, chart.top, chart.right, chart.bottom), 7f * u, "center", col, tfRegular, pal)
        } else {
            val n = vals.size
            val slot = chart.width() / n
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent }
            vals.forEachIndexed { i, v ->
                val bh = (v / maxV).coerceIn(0f, 1f) * chart.height()
                if (bh > 0.5f) {
                    val l = chart.left + slot * i + slot * 0.18f
                    c.drawRoundRect(RectF(l, chart.bottom - bh, l + slot * 0.64f, chart.bottom), slot * 0.15f, slot * 0.15f, bar)
                }
            }
        }
        val ls = 5.5f * u
        drawTextIn(c, "Ora", RectF(r.left, chart.bottom, r.left + bw / 3, r.bottom), ls, "left", pal.dim, tfRegular, pal)
        drawTextIn(c, "+1 h", RectF(x - bw / 6, chart.bottom, x + bw / 6, r.bottom), ls, "center", pal.dim, tfRegular, pal)
        drawTextIn(c, "+2 h", RectF(r.right - bw / 3, chart.bottom, r.right, r.bottom), ls, "right", pal.dim, tfRegular, pal)
        return r
    }

    // ------------------------------------------------------------------ batteria, nota, ricerca

    private fun drawRing(c: Canvas, x: Float, y: Float, r: Float, b: Battery, pal: Pal, col: Int, inner: Boolean, s: Style): RectF {
        val sw = r * 0.16f
        val ring = RectF(x - r + sw / 2, y - r + sw / 2, x + r - sw / 2, y + r - sw / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = sw; strokeCap = Paint.Cap.ROUND; color = pal.track }
        c.drawArc(ring, 0f, 360f, false, p)
        p.color = if (low(b)) RED else pal.accent
        c.drawArc(ring, -90f, 360f * b.level / 100f, false, p)
        if (inner) {
            val tr = RectF(x - r, y - r * 0.42f, x + r, y + r * (if (b.charging) 0.18f else 0.42f))
            drawTextIn(c, "${b.level}%", tr, r * 0.46f, "center", col, typeface(s.font), pal)
            if (b.charging) drawTextIn(c, "⚡", RectF(x - r, y + r * 0.15f, x + r, y + r * 0.6f), r * 0.3f, "center", p.color, tfRegular, null)
        } else if (b.charging) {
            drawTextIn(c, "⚡", RectF(x - r, y - r * 0.4f, x + r, y + r * 0.4f), r * 0.5f, "center", p.color, tfRegular, null)
        }
        return RectF(x - r, y - r, x + r, y + r)
    }

    private fun drawNote(c: Canvas, str: String, x: Float, y: Float, bw: Float, size: Float, h: Float, col: Int, s: Style, pal: Pal): RectF {
        val p = textPaint(size, col, typeface(s.font), pal)
        val width = bw.toInt().coerceAtLeast(10)
        val sl = StaticLayout.Builder.obtain(str, 0, str.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .setMaxLines((h * 0.9f / (size * 1.2f)).toInt().coerceAtLeast(1))
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        val left = x - bw / 2
        val top = y - sl.height / 2f
        c.save()
        c.translate(left, top)
        sl.draw(c)
        c.restore()
        return RectF(left, top, left + bw, top + sl.height)
    }

    private fun drawSearch(c: Canvas, x: Float, y: Float, bw: Float, size: Float, pal: Pal, s: Style): RectF {
        val r = RectF(x - bw / 2, y - size / 2, x + bw / 2, y + size / 2)
        val rad = min(s.corner.toFloat(), size / 2)
        c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.face })
        val ic = size * 0.36f
        val cx = r.left + size * 0.55f
        val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accent; style = Paint.Style.STROKE; strokeWidth = ic * 0.16f; strokeCap = Paint.Cap.ROUND }
        c.drawCircle(cx - ic * 0.1f, y - ic * 0.1f, ic * 0.32f, ip)
        c.drawLine(cx + ic * 0.14f, y + ic * 0.14f, cx + ic * 0.4f, y + ic * 0.4f, ip)
        drawTextIn(c, s.searchText, RectF(cx + ic * 0.8f, r.top, r.right, r.bottom), size * 0.32f, "left",
            ColorUtils.setAlphaComponent(pal.faceInk, 190), tfRegular, null)
        return r
    }

    // ------------------------------------------------------------------ cartellini (Sensation)

    private fun drawFlip(
        c: Canvas, el: El, x: Float, y: Float, w: Float, h: Float, s: Style, data: WData,
        drawLive: Boolean, live: MutableList<LiveSlot>, loc: Locale, now: Date
    ): RectF {
        val bw = el.w * w
        val plate = RectF(x - bw / 2, y - h / 2, x + bw / 2, y + h / 2)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, plate.top, 0f, plate.bottom, 0x4D000000, 0x73000000, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(plate, 10f, 10f, pp)
        c.drawRoundRect(plate, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x2EFFFFFF })
        val pad = h * 0.08f
        val gap = h * 0.1f
        val cw = (bw - pad * 2 - gap) / 2
        val tf = typeface(s.font)
        for (i in 0..1) {
            val l = plate.left + pad + i * (cw + gap)
            val card = RectF(l, plate.top + pad, l + cw, plate.bottom - pad)
            val mid = card.centerY()
            val top = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, card.top, 0f, mid, intArrayOf(0xFFFFFFFF.toInt(), 0xFFF0F0F0.toInt(), 0xFFDADADA.toInt()), null, Shader.TileMode.CLAMP)
            }
            val bot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, mid, 0f, card.bottom, intArrayOf(0xFFCFCFCF.toInt(), 0xFFE9E9E9.toInt(), 0xFFF7F7F7.toInt()), null, Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(card, 6f, 6f, bot)
            c.save(); c.clipRect(card.left, card.top, card.right, mid)
            c.drawRoundRect(card, 6f, 6f, top)
            c.restore()
            val size = min(card.height() * 0.8f, card.width() * 0.62f)
            val f24 = if (i == 0) "HH" else "mm"
            val f12 = if (i == 0) "hh" else "mm"
            if (drawLive) {
                drawTextIn(c, SimpleDateFormat(if (data.is24) f24 else f12, loc).format(now), card, size, "center", 0xFF1C1C1C.toInt(), tf, null)
            } else {
                live += LiveSlot(RectF(card), size, f24, f12, 0xFF1C1C1C.toInt(), s.font, false)
            }
            // fessura e cerniere
            c.drawRect(card.left, mid - 0.75f, card.right, mid + 0.75f, Paint().apply { color = 0xFF2B2B2B.toInt() })
            val hinge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A8D92.toInt() }
            c.drawRoundRect(RectF(card.left - 1f, mid - card.height() * 0.08f, card.left + 3f, mid + card.height() * 0.08f), 2f, 2f, hinge)
            c.drawRoundRect(RectF(card.right - 3f, mid - card.height() * 0.08f, card.right + 1f, mid + card.height() * 0.08f), 2f, 2f, hinge)
        }
        return plate
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
        if (dial == "sense") {
            drawSenseDial(c, cx, cy, r, now, loc)
            return
        }
        if (dial == "none") return
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.face })
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.tick; strokeCap = Paint.Cap.ROUND }
        when (dial) {
            "numbers" -> {
                for (i in 1..12) {
                    val (x, y) = polar(cx, cy, r * 0.76f, i * 30f)
                    val tr = RectF(x - r * 0.2f, y - r * 0.15f, x + r * 0.2f, y + r * 0.15f)
                    Renderer.drawTextIn(c, i.toString(), tr, r * (if (i % 3 == 0) 0.24f else 0.17f), "center", pal.faceInk,
                        Typeface.create("sans-serif-medium", Typeface.NORMAL), null)
                }
            }
            "dots" -> {
                for (i in 0 until 12) {
                    val (x, y) = polar(cx, cy, r * 0.82f, i * 30f)
                    tick.color = if (i % 3 == 0) pal.accent else pal.tick
                    c.drawCircle(x, y, r * (if (i % 3 == 0) 0.06f else 0.035f), tick)
                }
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
            else -> {
                for (i in 0 until 12) {
                    val big = i % 3 == 0
                    val (x1, y1) = polar(cx, cy, r * 0.90f, i * 30f)
                    val (x2, y2) = polar(cx, cy, r * (if (big) 0.76f else 0.83f), i * 30f)
                    tick.strokeWidth = r * (if (big) 0.055f else 0.035f)
                    c.drawLine(x1, y1, x2, y2, tick)
                }
            }
        }
    }

    /** Colori delle lancette: con il quadrante Sensation sono sempre argento. */
    private fun handColor(kind: String, s: Style, pal: Pal): Int = when {
        kind == "second" -> if (s.dial == "sense") 0xFFF2F3F5.toInt() else Renderer.RED
        kind == "minute" -> if (s.dial == "none") pal.accent else pal.accent
        else -> if (s.dial == "none") pal.text else pal.faceInk
    }

    /**
     * Disegna una lancetta nel quadrato [rect] ruotata di [deg] gradi (0 = ore 12).
     * Con [cap] disegna anche il perno centrale (va sopra tutte le lancette).
     */
    fun drawHand(c: Canvas, rect: RectF, deg: Float, kind: String, s: Style, pal: Pal, cap: Boolean = false) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f
        val col = handColor(kind, s, pal)
        val style = if (s.dial == "sense" && s.hands == "round") "sense" else s.hands
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; strokeCap = Paint.Cap.ROUND }
        c.save()
        c.rotate(deg, cx, cy)
        if (kind == "second") {
            p.strokeWidth = r * 0.018f
            c.drawLine(cx, cy + r * 0.16f, cx, cy - r * 0.78f, p)
            if (style != "sense") c.drawCircle(cx, cy, r * 0.045f, p)
        } else {
            val hour = kind == "hour"
            when (style) {
                "thin" -> {
                    p.strokeWidth = r * (if (hour) 0.05f else 0.035f)
                    c.drawLine(cx, cy + r * 0.1f, cx, cy - r * (if (hour) 0.5f else 0.76f), p)
                }
                "sense" -> senseHand(c, cx, cy, if (hour) r * 0.36f else r * 0.74f, if (hour) r * 0.10f else r * 0.12f,
                    if (hour) r * 0.078f else r * 0.058f)
                "dauphine" -> {
                    val len = r * (if (hour) 0.52f else 0.78f)
                    val wd = r * (if (hour) 0.09f else 0.07f)
                    val path = Path().apply {
                        moveTo(cx, cy - len); lineTo(cx + wd / 2, cy - len * 0.12f); lineTo(cx, cy + r * 0.06f)
                        lineTo(cx - wd / 2, cy - len * 0.12f); close()
                    }
                    c.drawPath(path, p)
                }
                "arrow" -> {
                    val len = r * (if (hour) 0.5f else 0.76f)
                    p.strokeWidth = r * (if (hour) 0.06f else 0.045f)
                    c.drawLine(cx, cy, cx, cy - len + r * 0.08f, p)
                    val a = r * (if (hour) 0.09f else 0.075f)
                    val path = Path().apply { moveTo(cx, cy - len - a * 0.3f); lineTo(cx + a, cy - len + a * 1.1f); lineTo(cx - a, cy - len + a * 1.1f); close() }
                    c.drawPath(path, p)
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
            if (style == "sense") {
                c.drawCircle(cx, cy, r * 0.055f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E3135.toInt() })
                c.drawCircle(cx, cy, r * 0.042f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx - r * 0.015f, cy - r * 0.015f, r * 0.05f, 0xFFFFFFFF.toInt(), 0xFF9A9EA4.toInt(), Shader.TileMode.CLAMP)
                })
            } else {
                c.drawCircle(cx, cy, r * 0.07f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (s.seconds) Renderer.RED else pal.accent })
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

    /** Quadrante di HTC Sense: anello scuro con i numeri e quadrante d'argento con giorno e data. */
    private fun drawSenseDial(c: Canvas, cx: Float, cy: Float, r0: Float, now: Date, loc: Locale) {
        val r = r0 * 0.97f
        val a = Paint(Paint.ANTI_ALIAS_FLAG)
        a.color = 0x59000000
        c.drawCircle(cx, cy + r * 0.04f, r, a)
        a.color = 0
        a.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(0xFF73777D.toInt(), 0xFF35383D.toInt(), 0xFF1A1C1F.toInt(), 0xFF2C2F33.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, a)
        a.shader = null
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
//  Icone meteo (le stesse del widget di MLauncher)
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
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.58f); lineTo(s * 0.40f, s * 0.80f); lineTo(s * 0.50f, s * 0.80f)
                    lineTo(s * 0.44f, s * 0.97f); lineTo(s * 0.64f, s * 0.72f); lineTo(s * 0.53f, s * 0.72f); close()
                }
                c.drawPath(bolt, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BOLT })
            }
        }
        c.restore()
    }
}
