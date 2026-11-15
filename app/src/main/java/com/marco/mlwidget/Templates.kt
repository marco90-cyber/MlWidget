package com.marco.mlwidget

/**
 * Tutti i widget presi dalle nostre app (MLauncher e MLMeteo), ridisegnati come modelli modificabili.
 * Posizioni: punto di aggancio in frazioni del widget. Grandezze: % del lato corto.
 */
object Templates {

    private fun el(
        id: String, kind: String, x: Float, y: Float, size: Float,
        color: String = "text", align: String = "center", w: Float = 0f, visible: Boolean = true
    ) = El(id, kind, x, y, size, visible, color, align, w)

    val all: List<Template> = listOf(
        // ------------------------------------------------------------------ Orologi
        Template(
            "clock_wide", "Orologio", "clock", "MLauncher", 4, 2, "clock",
            Style(background = "tonal", font = "bold"),
            listOf(
                el("hour", "hour", 0.31f, 0.50f, 46f, align = "right"),
                el("minute", "cminute", 0.31f, 0.50f, 46f, color = "accent", align = "left"),
                el("seconds", "seconds", 0.62f, 0.62f, 16f, color = "accent", align = "left", visible = false),
                el("day", "day", 0.93f, 0.40f, 11f, align = "right"),
                el("date", "date", 0.93f, 0.61f, 10f, color = "dim", align = "right")
            )
        ),
        Template(
            "clock_square", "Orologio verticale", "clock", "MLauncher", 2, 2, "clock",
            Style(background = "tonal", font = "bold"),
            listOf(
                el("hour", "hour", 0.14f, 0.28f, 36f, align = "left"),
                el("minute", "minute", 0.14f, 0.60f, 36f, color = "accent", align = "left"),
                el("dayshort", "dayshort", 0.14f, 0.87f, 9f, align = "left")
            )
        ),
        Template(
            "clock_text", "Orologio solo testo", "clock", "MLauncher", 4, 2, "clock",
            Style(background = "none", font = "light"),
            listOf(
                el("time", "time", 0.5f, 0.42f, 52f),
                el("dayshort", "dayshort", 0.5f, 0.82f, 10f)
            )
        ),
        Template(
            "analog", "Orologio analogico", "clock", "MLauncher", 2, 2, "clock",
            Style(background = "tonal3", dial = "minimal", hands = "round"),
            listOf(el("analog", "analog", 0.5f, 0.5f, 86f))
        ),
        Template(
            "analog_wide", "Orologio analogico con data", "clock", "MLauncher", 4, 2, "clock",
            Style(background = "tonal3", dial = "minimal", hands = "round"),
            listOf(
                el("analog", "analog", 0.25f, 0.5f, 84f),
                el("day", "day", 0.52f, 0.40f, 13f, align = "left"),
                el("date", "date", 0.52f, 0.62f, 12f, color = "dim", align = "left")
            )
        ),
        Template(
            "sensation", "Orologio Sensation", "clock", "MLauncher · tema Sensation", 2, 2, "clock",
            Style(background = "none", dial = "sense", hands = "sense"),
            listOf(
                el("analog", "analog", 0.5f, 0.45f, 84f),
                el("placepill", "placepill", 0.5f, 0.94f, 7f)
            )
        ),
        Template(
            "flip", "Orologio a cartellini", "clock", "MLauncher · tema Sensation", 4, 2, "clock",
            Style(background = "none", font = "condensed"),
            listOf(
                el("flip", "flip", 0.5f, 0.31f, 58f, w = 0.94f),
                el("place", "place", 0.05f, 0.76f, 9f, align = "left"),
                el("cond", "cond", 0.05f, 0.90f, 7f, align = "left"),
                el("wicon", "wicon", 0.5f, 0.80f, 32f),
                el("dayshort", "dayshort", 0.95f, 0.74f, 7f, align = "right"),
                el("temp", "temp", 0.95f, 0.90f, 13f, align = "right")
            )
        ),

        // ------------------------------------------------------------------ Meteo
        Template(
            "weather_wide", "Meteo", "weather", "MLauncher", 4, 2, "weather",
            Style(background = "tonal3", font = "bold"),
            listOf(
                el("wicon", "wicon", 0.17f, 0.5f, 62f),
                el("temp", "temp", 0.33f, 0.42f, 40f, align = "left"),
                el("cond", "cond", 0.33f, 0.70f, 9f, align = "left"),
                el("place", "place", 0.95f, 0.40f, 9f, align = "right"),
                el("hilo", "hilo", 0.95f, 0.60f, 8f, color = "dim", align = "right")
            )
        ),
        Template(
            "weather_square", "Meteo quadrato", "weather", "MLauncher", 2, 2, "weather",
            Style(background = "tonal3", font = "bold"),
            listOf(
                el("wicon", "wicon", 0.25f, 0.25f, 36f),
                el("place", "place", 0.92f, 0.14f, 8f, align = "right"),
                el("temp", "temp", 0.08f, 0.60f, 30f, align = "left"),
                el("cond", "cond", 0.08f, 0.79f, 8f, align = "left"),
                el("hilo", "hilo", 0.08f, 0.91f, 7f, color = "dim", align = "left")
            )
        ),
        Template(
            "weather_mini", "Meteo mini", "weather", "MLMeteo", 2, 1, "weather",
            Style(background = "neutral", font = "bold"),
            listOf(
                el("wicon", "wicon", 0.27f, 0.5f, 62f),
                el("temp", "temp", 0.70f, 0.5f, 38f)
            )
        ),
        Template(
            "weather_full", "Meteo completo", "weather", "MLMeteo", 4, 3, "weather",
            Style(background = "neutral", font = "bold", corner = 24),
            listOf(
                el("place", "place", 0.06f, 0.11f, 6.5f, align = "left"),
                el("wicon", "wicon", 0.13f, 0.30f, 20f),
                el("temp", "temp", 0.25f, 0.29f, 17f, align = "left"),
                el("cond", "cond", 0.94f, 0.23f, 5.5f, align = "right"),
                el("hilo", "hilo", 0.94f, 0.34f, 5.5f, color = "dim", align = "right"),
                el("updated", "updated", 0.94f, 0.11f, 4.5f, color = "dim", align = "right"),
                el("hours", "hours", 0.5f, 0.58f, 5.5f, w = 0.9f),
                el("days", "days", 0.5f, 0.85f, 5.5f, w = 0.9f)
            )
        ),
        Template(
            "weather_details", "Meteo con dettagli", "weather", "MLMeteo", 4, 2, "weather",
            Style(background = "neutral", font = "bold", corner = 24),
            listOf(
                el("wicon", "wicon", 0.14f, 0.42f, 44f),
                el("temp", "temp", 0.28f, 0.38f, 30f, align = "left"),
                el("place", "place", 0.06f, 0.86f, 8f, align = "left"),
                el("feels", "feels", 0.95f, 0.22f, 8f, align = "right"),
                el("humidity", "humidity", 0.95f, 0.44f, 8f, align = "right"),
                el("wind", "wind", 0.95f, 0.66f, 8f, align = "right"),
                el("hilo", "hilo", 0.95f, 0.86f, 8f, color = "dim", align = "right")
            )
        ),
        Template(
            "rain", "Pioggia in arrivo", "weather", "MLMeteo", 4, 2, "weather",
            Style(background = "neutral", font = "bold", corner = 24),
            listOf(
                el("title", "title", 0.05f, 0.15f, 8f, align = "left"),
                el("place", "place", 0.95f, 0.15f, 7f, color = "dim", align = "right"),
                el("rain", "rain", 0.5f, 0.62f, 48f, w = 0.9f)
            )
        ),

        // ------------------------------------------------------------------ Sistema
        Template(
            "battery", "Batteria", "system", "MLauncher", 2, 2, "battery",
            Style(background = "tonal2", font = "bold"),
            listOf(el("bring", "bring", 0.5f, 0.5f, 74f))
        ),
        Template(
            "battery_wide", "Batteria larga", "system", "MLauncher", 4, 2, "battery",
            Style(background = "tonal2", font = "bold"),
            listOf(
                el("bringe", "bringe", 0.19f, 0.5f, 70f),
                el("bpct", "bpct", 0.40f, 0.42f, 26f, align = "left"),
                el("bstatus", "bstatus", 0.40f, 0.68f, 9f, color = "dim", align = "left")
            )
        ),
        Template(
            "battery_bar", "Batteria a barra", "system", "MLauncher", 4, 1, "battery",
            Style(background = "tonal2", font = "bold"),
            listOf(
                el("bstatus", "bstatus", 0.06f, 0.30f, 20f, align = "left"),
                el("bpct", "bpct", 0.94f, 0.30f, 22f, align = "right"),
                el("bbar", "bbar", 0.5f, 0.72f, 16f, w = 0.88f)
            )
        ),

        // ------------------------------------------------------------------ Note e strumenti
        Template(
            "note", "Nota veloce", "tools", "MLauncher", 2, 2, "note",
            Style(background = "tonal", font = "medium", note = "Tocca per scrivere una nota"),
            listOf(el("note", "note", 0.5f, 0.5f, 9f, w = 0.84f))
        ),
        Template(
            "note_wide", "Nota veloce larga", "tools", "MLauncher", 4, 2, "note",
            Style(background = "tonal", font = "medium", note = "Tocca per scrivere una nota"),
            listOf(el("note", "note", 0.5f, 0.5f, 10f, w = 0.9f))
        ),
        Template(
            "search", "Barra di ricerca", "tools", "MLauncher", 4, 1, "search",
            Style(background = "none", font = "regular", corner = 40),
            listOf(el("search", "search", 0.5f, 0.5f, 62f, w = 0.96f))
        )
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

val DIALS = listOf(
    "minimal" to "Tacche (MLauncher)",
    "numbers" to "Numeri",
    "dots" to "Puntini",
    "roman" to "Numeri romani",
    "sense" to "Sensation (argento)",
    "none" to "Nessuno"
)

val HANDS = listOf(
    "round" to "Arrotondate (MLauncher)",
    "thin" to "Sottili",
    "sense" to "Affusolate (Sensation)",
    "dauphine" to "Dauphine",
    "arrow" to "Freccia",
    "block" to "Bastone"
)

val BACKGROUNDS = listOf(
    "tonal" to "Tonale",
    "tonal2" to "Tonale tenue",
    "tonal3" to "Tonale terziario",
    "neutral" to "Neutro (come MLMeteo)",
    "filled" to "Pieno",
    "glass" to "Vetro",
    "none" to "Nessuno"
)

val FONTS = listOf(
    "light" to "Sottile",
    "regular" to "Normale",
    "medium" to "Medio",
    "bold" to "Grassetto",
    "condensed" to "Stretto"
)

val COLOR_SOURCES = listOf(
    "system" to "Sistema (Material You)",
    "wallpaper" to "Sfondo del telefono",
    "custom" to "Personale"
)

val DARK_MODES = listOf(
    "auto" to "Segue la modalità scura",
    "light" to "Sempre chiaro",
    "dark" to "Sempre scuro"
)

val EL_COLORS = listOf(
    "text" to "Testo",
    "accent" to "Accento",
    "dim" to "Tenue",
    "custom" to "Personale"
)
