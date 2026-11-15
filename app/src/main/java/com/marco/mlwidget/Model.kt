package com.marco.mlwidget

import android.content.Context
import org.json.JSONObject

/** Le scelte di stile di un widget. */
class Style(
    /** system (Material You) · wallpaper (colori dello sfondo) · custom (colore scelto) */
    var colorSource: String = "system",
    var customColor: Int = 0xFF4F6BED.toInt(),
    /** auto (segue la modalità scura) · light · dark */
    var darkMode: String = "auto",
    /** look (quello dello stile) · tonal · tonal2 · tonal3 · neutral · filled · glass · none */
    var background: String = "look",
    var opacity: Int = 100,
    var corner: Int = 28,
    /** light · regular · medium · bold · condensed · mono */
    var font: String = "bold",
    var dial: String = "minimal",
    var hands: String = "round",
    var seconds: Boolean = false,
    /** grandezza del testo in % */
    var textScale: Int = 100,
    var note: String = "",
    /** parti accese o spente (chiave -> acceso) */
    val parts: MutableMap<String, Boolean> = HashMap()
) {
    fun copy() = Style(colorSource, customColor, darkMode, background, opacity, corner, font, dial, hands, seconds, textScale, note, HashMap(parts))

    fun toJson() = JSONObject().apply {
        put("colorSource", colorSource); put("customColor", customColor); put("darkMode", darkMode)
        put("background", background); put("opacity", opacity); put("corner", corner); put("font", font)
        put("dial", dial); put("hands", hands); put("seconds", seconds); put("textScale", textScale); put("note", note)
        put("parts", JSONObject().apply { parts.forEach { (k, v) -> put(k, v) } })
    }

    fun setFrom(o: Style) {
        colorSource = o.colorSource; customColor = o.customColor; darkMode = o.darkMode; background = o.background
        opacity = o.opacity; corner = o.corner; font = o.font; dial = o.dial; hands = o.hands; seconds = o.seconds
        textScale = o.textScale; note = o.note; parts.clear(); parts.putAll(o.parts)
    }

    companion object {
        fun fromJson(o: JSONObject, def: Style): Style {
            val p = HashMap(def.parts)
            o.optJSONObject("parts")?.let { j -> j.keys().forEach { k -> p[k] = j.optBoolean(k, true) } }
            return Style(
                o.optString("colorSource", def.colorSource), o.optInt("customColor", def.customColor),
                o.optString("darkMode", def.darkMode), o.optString("background", def.background),
                o.optInt("opacity", def.opacity), o.optInt("corner", def.corner), o.optString("font", def.font),
                o.optString("dial", def.dial), o.optString("hands", def.hands), o.optBoolean("seconds", def.seconds),
                o.optInt("textScale", def.textScale), o.optString("note", def.note), p
            )
        }
    }
}

/**
 * Un widget: tipo (orologio, meteo...) + stile grafico (Material You, iOS...).
 * Il disegno si adatta da solo alla grandezza; si possono accendere e spegnere le sue parti.
 */
class Template(
    val id: String,
    val name: String,
    /** material · ios · sense · retro · minimal */
    val look: String,
    /** digital · analog · flip · weather · battery · calendar · music · device · note · search · glance · lcd · tearoff · ipod */
    val kind: String,
    val cols: Int,
    val rows: Int,
    val style: Style,
    /** parti che si possono accendere e spegnere: chiave -> nome */
    val parts: List<Pair<String, String>> = emptyList()
) {
    val category get() = look
    val analog get() = kind == "analog"
    val usesWeather get() = kind == "weather" || kind == "glance" || (kind == "flip" && look == "sense") ||
        (kind == "analog" && look == "sense")
    val usesBattery get() = kind == "battery" || kind == "device"
    val usesMusic get() = kind == "music" || kind == "ipod"
    val usesCalendar get() = kind == "calendar" || kind == "tearoff"
    val hasDigits get() = kind in setOf("digital", "flip", "lcd", "weather", "battery", "calendar", "glance", "device", "tearoff")
    fun defaultCfg() = Cfg(style.copy())
}

/** Configurazione salvata di un widget. */
class Cfg(val style: Style) {
    fun copy() = Cfg(style.copy())
    fun on(key: String): Boolean = style.parts[key] ?: true
}

val CATEGORIES = listOf(
    "material" to "Material You",
    "ios" to "iOS",
    "sense" to "Sense",
    "retro" to "Retrò",
    "minimal" to "Minimal"
)

object Store {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widgets2", Context.MODE_PRIVATE)

    fun load(ctx: Context, t: Template): Cfg {
        val raw = sp(ctx).getString("tpl_${t.id}", null) ?: return t.defaultCfg()
        return try {
            Cfg(Style.fromJson(JSONObject(raw), t.style))
        } catch (e: Exception) {
            t.defaultCfg()
        }
    }

    fun save(ctx: Context, t: Template, cfg: Cfg) =
        sp(ctx).edit().putString("tpl_${t.id}", cfg.style.toJson().toString()).apply()

    /** Quale widget mostra ogni widget sulla Home. */
    fun bind(ctx: Context, widgetId: Int, templateId: String) =
        sp(ctx).edit().putString("bind_$widgetId", templateId).apply()

    fun boundTemplate(ctx: Context, widgetId: Int): Template? =
        sp(ctx).getString("bind_$widgetId", null)?.let { Templates.byId(it) }

    fun unbind(ctx: Context, widgetId: Int) = sp(ctx).edit().remove("bind_$widgetId").apply()

    fun widgetsUsing(ctx: Context, templateId: String): List<Int> =
        sp(ctx).all.filter { it.key.startsWith("bind_") && it.value == templateId }
            .mapNotNull { it.key.removePrefix("bind_").toIntOrNull() }
}
