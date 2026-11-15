package com.marco.mlwidget

/** Tutti i widget, divisi per stile grafico. */
object Templates {

    private val P_DATE = "date" to "Data"
    private val P_WEATHER_ALL = listOf(
        "place" to "Località", "cond" to "Condizioni", "hilo" to "Massima e minima",
        "hours" to "Prossime ore (se c'è spazio)", "days" to "Prossimi giorni (se c'è spazio)",
        "details" to "Percepita, umidità e vento", "updated" to "Ora dell'aggiornamento"
    )
    private val P_MUSIC = listOf("cover" to "Copertina", "artist" to "Artista", "controls" to "Tasti")
    private val P_CAL = listOf("events" to "Appuntamenti", "month" to "Mese (se c'è spazio)")

    private fun st(
        bg: String = "look", font: String = "bold", dial: String = "minimal", hands: String = "round",
        seconds: Boolean = false, corner: Int = 28, colorSource: String = "system", custom: Int = 0xFF4F6BED.toInt(),
        note: String = "", off: List<String> = emptyList()
    ) = Style(
        colorSource = colorSource, customColor = custom, background = bg, font = font, dial = dial, hands = hands,
        seconds = seconds, corner = corner, note = note, parts = HashMap(off.associateWith { false })
    )

    val all: List<Template> = listOf(
        // ------------------------------------------------------------------ Material You
        Template("m_clock", "Orologio", "material", "digital", 4, 2, st(), listOf(P_DATE)),
        Template("m_analog", "Orologio analogico", "material", "analog", 2, 2, st(), listOf("date" to "Data (se c'è spazio)")),
        Template("m_glance", "In breve", "material", "glance", 4, 1, st(font = "regular"), listOf(P_DATE, "weather" to "Meteo")),
        Template("m_weather", "Meteo", "material", "weather", 4, 2, st(off = listOf("details", "updated")), P_WEATHER_ALL),
        Template("m_battery", "Batteria", "material", "battery", 2, 2, st(), listOf("status" to "Stato", "accessories" to "Accessori Bluetooth")),
        Template("m_calendar", "Calendario", "material", "calendar", 4, 2, st(), P_CAL),
        Template("m_music", "Musica", "material", "music", 4, 2, st(), P_MUSIC),
        Template("m_device", "Dispositivo", "material", "device", 2, 2, st(), listOf("storage" to "Memoria", "ram" to "RAM", "battery" to "Batteria")),
        Template("m_note", "Nota", "material", "note", 2, 2, st(font = "medium", note = "Tocca per scrivere una nota")),
        Template("m_search", "Ricerca", "material", "search", 4, 1, st(font = "regular")),

        // ------------------------------------------------------------------ iOS
        Template("i_clock", "Orologio", "ios", "analog", 2, 2, st(dial = "ios", hands = "ios", seconds = true, corner = 22), listOf("date" to "Città e data (se c'è spazio)")),
        Template("i_weather", "Meteo", "ios", "weather", 2, 2, st(font = "light", corner = 22, off = listOf("details", "updated")), P_WEATHER_ALL),
        Template("i_battery", "Batterie", "ios", "battery", 2, 2, st(corner = 22, colorSource = "custom", custom = 0xFF34C759.toInt()), listOf("accessories" to "Accessori Bluetooth")),
        Template("i_calendar", "Calendario", "ios", "calendar", 2, 2, st(corner = 22, colorSource = "custom", custom = 0xFFFF3B30.toInt()), P_CAL),
        Template("i_music", "Musica", "ios", "music", 4, 2, st(corner = 22, colorSource = "custom", custom = 0xFFFA2D48.toInt()), P_MUSIC),
        Template("i_note", "Note", "ios", "note", 2, 2, st(font = "regular", corner = 22, colorSource = "custom", custom = 0xFFFFCC00.toInt(), note = "Tocca per scrivere una nota")),

        // ------------------------------------------------------------------ Sense
        Template("s_clock", "Orologio Sensation", "sense", "analog", 2, 2, st(dial = "sense", hands = "sense"), listOf("place" to "Città")),
        Template("s_flipweather", "Cartellini con meteo", "sense", "flip", 4, 2, st(font = "condensed"), listOf("weather" to "Meteo (se c'è spazio)")),
        Template("s_flip", "Cartellini", "sense", "flip", 4, 1, st(font = "condensed", off = listOf("weather")), listOf("weather" to "Meteo (se c'è spazio)")),

        // ------------------------------------------------------------------ One UI
        Template("o_clock", "Orologio", "oneui", "digital", 4, 2, st(corner = 26), listOf(P_DATE)),
        Template("o_weather", "Meteo", "oneui", "weather", 4, 2, st(corner = 26, off = listOf("details", "updated", "days")), P_WEATHER_ALL),
        Template("o_battery", "Batteria", "oneui", "battery", 4, 2, st(corner = 26, colorSource = "custom", custom = 0xFF3E91FF.toInt()), listOf("accessories" to "Accessori Bluetooth")),
        Template("o_calendar", "Calendario", "oneui", "calendar", 4, 2, st(corner = 26, colorSource = "custom", custom = 0xFF3E91FF.toInt()), P_CAL),
        Template("o_music", "Musica", "oneui", "music", 4, 2, st(corner = 26, colorSource = "custom", custom = 0xFF3E91FF.toInt()), P_MUSIC),

        // ------------------------------------------------------------------ HyperOS
        Template("h_clock", "Orologio", "hyper", "digital", 4, 2, st(font = "light", corner = 24), listOf(P_DATE)),
        Template("h_analog", "Orologio analogico", "hyper", "analog", 2, 2, st(dial = "hyper", hands = "round", corner = 24, colorSource = "custom", custom = 0xFFFF6A00.toInt()), listOf("date" to "Data (se c'è spazio)")),
        Template("h_weather", "Meteo", "hyper", "weather", 2, 2, st(font = "light", corner = 24, off = listOf("details", "updated")), P_WEATHER_ALL),
        Template("h_battery", "Batteria", "hyper", "battery", 2, 2, st(font = "light", corner = 24, colorSource = "custom", custom = 0xFF30C46B.toInt()), listOf("status" to "Stato", "accessories" to "Accessori Bluetooth")),
        Template("h_calendar", "Calendario", "hyper", "calendar", 2, 2, st(corner = 24, colorSource = "custom", custom = 0xFFFF6A00.toInt()), P_CAL),
        Template("h_note", "Note", "hyper", "note", 2, 2, st(font = "regular", corner = 24, colorSource = "custom", custom = 0xFFFFB300.toInt(), note = "Tocca per scrivere una nota")),

        // ------------------------------------------------------------------ Nothing
        Template("x_clock", "Orologio", "nothing", "digital", 2, 2, st(font = "dot", corner = 26, colorSource = "custom", custom = NOTHING_RED), listOf(P_DATE)),
        Template("x_analog", "Orologio analogico", "nothing", "analog", 2, 2, st(dial = "nothing", hands = "nothing", seconds = true, corner = 26, colorSource = "custom", custom = NOTHING_RED), listOf("date" to "Data (se c'è spazio)")),
        Template("x_weather", "Meteo", "nothing", "weather", 2, 2, st(font = "dot", corner = 26, colorSource = "custom", custom = NOTHING_RED, off = listOf("details", "updated", "days")), P_WEATHER_ALL),
        Template("x_battery", "Batteria", "nothing", "battery", 2, 2, st(font = "dot", corner = 26, colorSource = "custom", custom = NOTHING_RED), listOf("status" to "Stato")),
        Template("x_earbuds", "Cuffie e accessori", "nothing", "earbuds", 2, 2, st(font = "dot", corner = 26, colorSource = "custom", custom = NOTHING_RED)),
        Template("x_steps", "Passi", "nothing", "steps", 2, 2, st(font = "dot", corner = 26, colorSource = "custom", custom = NOTHING_RED), listOf("goal" to "Obiettivo (8000 passi)")),
        Template("x_quote", "Citazione del giorno", "nothing", "quote", 4, 2, st(font = "regular", corner = 26, colorSource = "custom", custom = NOTHING_RED)),
        Template("x_calendar", "Calendario", "nothing", "calendar", 2, 2, st(font = "dot", corner = 26, colorSource = "custom", custom = NOTHING_RED), P_CAL),
        Template("x_search", "Ricerca", "nothing", "search", 4, 1, st(font = "regular", corner = 40, colorSource = "custom", custom = NOTHING_RED)),

        // ------------------------------------------------------------------ Retrò
        Template("r_flip", "Orologio a cartellini", "retro", "flip", 4, 2, st(font = "condensed"), listOf(P_DATE)),
        Template("r_lcd", "Sveglia LCD", "retro", "lcd", 4, 2, st(font = "mono", corner = 18), listOf(P_DATE)),
        Template("r_ipod", "iPod", "retro", "ipod", 2, 2, st(corner = 24), listOf("cover" to "Copertina")),
        Template("r_tearoff", "Calendario a strappo", "retro", "tearoff", 2, 2, st(corner = 14, colorSource = "custom", custom = 0xFFD32F2F.toInt()), listOf("events" to "Primo appuntamento")),

        // ------------------------------------------------------------------ Minimal
        Template("n_clock", "Ora", "minimal", "digital", 4, 2, st(font = "light"), listOf(P_DATE)),
        Template("n_analog", "Lancette", "minimal", "analog", 2, 2, st(dial = "bare", hands = "thin")),
        Template("n_weather", "Meteo in una riga", "minimal", "weather", 4, 1, st(font = "light", off = listOf("hours", "days", "details", "updated")), P_WEATHER_ALL),
        Template("n_battery", "Batteria a linea", "minimal", "battery", 4, 1, st(font = "light"), listOf("status" to "Stato")),
        Template("n_music", "Musica in una riga", "minimal", "music", 4, 1, st(font = "regular", off = listOf("cover")), P_MUSIC)
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

const val NOTHING_RED = 0xFFD71921.toInt()

val DIALS = listOf(
    "minimal" to "Tacche",
    "numbers" to "Numeri",
    "ios" to "iOS",
    "dots" to "Puntini",
    "roman" to "Numeri romani",
    "sense" to "Sensation (argento)",
    "bare" to "Quattro tacche",
    "hyper" to "HyperOS",
    "nothing" to "Nothing",
    "none" to "Nessuno"
)

val HANDS = listOf(
    "round" to "Arrotondate",
    "ios" to "iOS",
    "nothing" to "Nothing",
    "thin" to "Sottili",
    "sense" to "Affusolate (Sensation)",
    "dauphine" to "Dauphine",
    "arrow" to "Freccia",
    "block" to "Bastone"
)

val BACKGROUNDS = listOf(
    "look" to "Quello dello stile",
    "tonal" to "Tonale",
    "tonal2" to "Tonale tenue",
    "tonal3" to "Tonale terziario",
    "neutral" to "Neutro",
    "filled" to "Pieno",
    "glass" to "Vetro",
    "none" to "Nessuno"
)

val FONTS = listOf(
    "light" to "Sottile",
    "regular" to "Normale",
    "medium" to "Medio",
    "bold" to "Grassetto",
    "condensed" to "Stretto",
    "mono" to "Digitale",
    "dot" to "A puntini (Nothing)"
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

/** Grandezze per provare l'anteprima. */
val PREVIEW_SIZES = listOf(2 to 1, 2 to 2, 4 to 1, 4 to 2, 4 to 3)
