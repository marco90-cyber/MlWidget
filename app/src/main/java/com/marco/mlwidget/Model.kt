package com.marco.mlwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Un elemento del widget (ora, data, icona meteo, lancette...).
 * La posizione è il punto di aggancio in frazioni del widget (0..1), la grandezza è in "unità":
 * 1 unità = 1% del lato corto del widget, così tutto cresce insieme quando il widget si ingrandisce.
 */
class El(
    val id: String,
    val kind: String,
    var x: Float,
    var y: Float,
    var size: Float,
    var visible: Boolean = true,
    /** text · accent · dim · custom */
    var color: String = "text",
    /** left · center · right: dove sta il testo rispetto al punto di aggancio */
    var align: String = "center",
    /** larghezza in frazione del widget (solo per righe, grafici, note, barra di ricerca) */
    var w: Float = 0f,
    var customColor: Int = 0xFFFFFFFF.toInt()
) {
    fun copy() = El(id, kind, x, y, size, visible, color, align, w, customColor)

    fun toJson() = JSONObject().apply {
        put("id", id); put("kind", kind); put("x", x.toDouble()); put("y", y.toDouble()); put("size", size.toDouble())
        put("visible", visible); put("color", color); put("align", align); put("w", w.toDouble()); put("cc", customColor)
    }

    companion object {
        fun fromJson(o: JSONObject) = El(
            o.getString("id"), o.getString("kind"),
            o.optDouble("x", 0.5).toFloat(), o.optDouble("y", 0.5).toFloat(), o.optDouble("size", 10.0).toFloat(),
            o.optBoolean("visible", true), o.optString("color", "text"), o.optString("align", "center"),
            o.optDouble("w", 0.0).toFloat(), o.optInt("cc", 0xFFFFFFFF.toInt())
        )
    }
}

/** Le scelte di stile di un widget. */
class Style(
    /** system (Material You) · wallpaper (colori dello sfondo) · custom (colore scelto) */
    var colorSource: String = "system",
    var customColor: Int = 0xFF4F6BED.toInt(),
    /** auto (segue la modalità scura) · light · dark */
    var darkMode: String = "auto",
    /** tonal · tonal2 · tonal3 · neutral · filled · glass · none */
    var background: String = "tonal",
    var opacity: Int = 100,
    var corner: Int = 28,
    /** light · regular · medium · bold · condensed */
    var font: String = "bold",
    var dial: String = "minimal",
    var hands: String = "round",
    var seconds: Boolean = false,
    var note: String = "",
    var searchText: String = "Cerca"
) {
    fun copy() = Style(colorSource, customColor, darkMode, background, opacity, corner, font, dial, hands, seconds, note, searchText)

    fun toJson() = JSONObject().apply {
        put("colorSource", colorSource); put("customColor", customColor); put("darkMode", darkMode)
        put("background", background); put("opacity", opacity); put("corner", corner); put("font", font)
        put("dial", dial); put("hands", hands); put("seconds", seconds); put("note", note); put("searchText", searchText)
    }

    companion object {
        fun fromJson(o: JSONObject, def: Style) = Style(
            o.optString("colorSource", def.colorSource), o.optInt("customColor", def.customColor),
            o.optString("darkMode", def.darkMode), o.optString("background", def.background),
            o.optInt("opacity", def.opacity), o.optInt("corner", def.corner), o.optString("font", def.font),
            o.optString("dial", def.dial), o.optString("hands", def.hands), o.optBoolean("seconds", def.seconds),
            o.optString("note", def.note), o.optString("searchText", def.searchText)
        )
    }
}

/** Configurazione completa di un widget: stile + elementi. */
class Cfg(val style: Style, val els: List<El>) {
    fun copy() = Cfg(style.copy(), els.map { it.copy() })
    fun el(id: String) = els.firstOrNull { it.id == id }
}

/** Un modello di widget, preso da una delle nostre app. */
class Template(
    val id: String,
    val name: String,
    val category: String,
    /** da quale app arriva */
    val from: String,
    val cols: Int,
    val rows: Int,
    /** cosa succede toccando il widget: clock · weather · battery · note · search · app */
    val tap: String,
    val style: Style,
    val els: List<El>
) {
    val analog get() = els.any { it.kind == "analog" }
    val usesWeather get() = els.any { it.kind in WEATHER_KINDS }
    fun defaultCfg() = Cfg(style.copy(), els.map { it.copy() })
}

val WEATHER_KINDS = setOf("wicon", "temp", "cond", "place", "placepill", "hilo", "hours", "days", "rain", "updated", "feels", "humidity", "wind")

/** Elementi che sul widget vero sono orologi di sistema (si aggiornano da soli, senza consumare batteria). */
val LIVE_KINDS = setOf("hour", "minute", "cminute", "time", "seconds")

val CATEGORIES = listOf(
    "clock" to "Orologi",
    "weather" to "Meteo",
    "system" to "Sistema",
    "tools" to "Note e strumenti"
)

/** Nomi degli elementi nell'editor. */
fun elementName(kind: String): String = when (kind) {
    "hour" -> "Ore"
    "minute" -> "Minuti"
    "cminute" -> "Minuti (con i due punti)"
    "time" -> "Ora"
    "seconds" -> "Secondi"
    "date" -> "Data"
    "day" -> "Giorno"
    "dayshort" -> "Giorno e data"
    "analog" -> "Orologio analogico"
    "flip" -> "Cartellini"
    "wicon" -> "Icona meteo"
    "temp" -> "Temperatura"
    "cond" -> "Condizioni"
    "place" -> "Località"
    "placepill" -> "Località (pillola)"
    "hilo" -> "Massima e minima"
    "hours" -> "Prossime ore"
    "days" -> "Prossimi giorni"
    "rain" -> "Grafico pioggia"
    "updated" -> "Ultimo aggiornamento"
    "feels" -> "Percepita"
    "humidity" -> "Umidità"
    "wind" -> "Vento"
    "bring" -> "Anello batteria"
    "bringe" -> "Anello batteria (vuoto)"
    "bpct" -> "Percentuale"
    "bstatus" -> "Stato batteria"
    "bbar" -> "Barra batteria"
    "note" -> "Testo della nota"
    "search" -> "Barra di ricerca"
    "title" -> "Titolo"
    else -> kind
}

/** Gli elementi con larghezza regolabile. */
fun hasWidth(kind: String) = kind in setOf("hours", "days", "rain", "bbar", "note", "search", "flip")

object Store {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun load(ctx: Context, t: Template): Cfg {
        val raw = sp(ctx).getString("tpl_${t.id}", null) ?: return t.defaultCfg()
        return try {
            val o = JSONObject(raw)
            val style = Style.fromJson(o.getJSONObject("style"), t.style)
            val saved = HashMap<String, El>()
            val arr = o.optJSONArray("els") ?: JSONArray()
            for (i in 0 until arr.length()) El.fromJson(arr.getJSONObject(i)).let { saved[it.id] = it }
            // gli elementi nuovi di una versione futura compaiono con i valori predefiniti
            Cfg(style, t.els.map { d -> saved[d.id]?.takeIf { it.kind == d.kind } ?: d.copy() })
        } catch (e: Exception) {
            t.defaultCfg()
        }
    }

    fun save(ctx: Context, t: Template, cfg: Cfg) {
        val o = JSONObject().apply {
            put("style", cfg.style.toJson())
            put("els", JSONArray().apply { cfg.els.forEach { put(it.toJson()) } })
        }
        sp(ctx).edit().putString("tpl_${t.id}", o.toString()).apply()
    }

    fun reset(ctx: Context, t: Template) = sp(ctx).edit().remove("tpl_${t.id}").apply()

    fun isModified(ctx: Context, t: Template) = sp(ctx).contains("tpl_${t.id}")

    /** Quale modello mostra ogni widget sulla Home. */
    fun bind(ctx: Context, widgetId: Int, templateId: String) =
        sp(ctx).edit().putString("bind_$widgetId", templateId).apply()

    fun boundTemplate(ctx: Context, widgetId: Int): Template? =
        sp(ctx).getString("bind_$widgetId", null)?.let { Templates.byId(it) }

    fun unbind(ctx: Context, widgetId: Int) = sp(ctx).edit().remove("bind_$widgetId").apply()

    fun widgetsUsing(ctx: Context, templateId: String): List<Int> =
        sp(ctx).all.filter { it.key.startsWith("bind_") && it.value == templateId }
            .mapNotNull { it.key.removePrefix("bind_").toIntOrNull() }
}
