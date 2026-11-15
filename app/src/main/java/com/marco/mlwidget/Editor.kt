package com.marco.mlwidget

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marco.mltheme.MLChoiceRow
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSliderRow
import com.marco.mltheme.MLSwitchRow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Editor di un widget: anteprima ferma in alto, le scelte scorrono sotto. */
@Composable
fun EditorScreen(t: Template, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val cfg = remember(t.id) { Store.load(ctx, t).copy() }
    var version by remember { mutableIntStateOf(0) }
    var saved by remember { mutableIntStateOf(0) }
    var dark by remember { mutableStateOf(Palettes.darkFor(cfg.style, Palettes.isNight(ctx))) }
    var size by remember { mutableStateOf(t.cols to t.rows) }
    var confirmReset by remember { mutableStateOf(false) }
    val onHome = remember(saved) { Store.widgetsUsing(ctx, t.id).size }
    fun changed() { version++ }

    // salva e aggiorna i widget sulla Home poco dopo l'ultima modifica
    LaunchedEffect(version) {
        if (version == 0) return@LaunchedEffect
        delay(350)
        Store.save(ctx, t, cfg)
        WidgetUpdater.updateTemplateAsync(ctx, t.id)
        saved++
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Ripristinare il widget?") },
        text = { Text("Colori, stile e parti tornano com'erano all'inizio.") },
        confirmButton = {
            TextButton({
                confirmReset = false
                cfg.style.setFrom(t.style.copy())
                changed()
            }) { Text("Ripristina") }
        },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Annulla") } }
    )

    Column(Modifier.fillMaxSize().background(cs.surface)) {
        // ---------------------------------------------------------------- barra e anteprima (fisse)
        Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Indietro") }
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        (CATEGORIES.firstOrNull { it.first == t.look }?.second ?: "") + if (onHome > 0) " · sulla Home: $onHome" else "",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = { dark = !dark }) {
                    Icon(if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, if (dark) "Anteprima scura" else "Anteprima chiara")
                }
                FilledTonalIconButton(onClick = {
                    if (!Pin.request(ctx, t)) Toast.makeText(ctx, "Tieni premuto sulla Home → Widget → MlWidget", Toast.LENGTH_LONG).show()
                }) { Icon(Icons.Outlined.Widgets, "Aggiungi alla Home") }
                Spacer(Modifier.width(4.dp))
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(24.dp)).background(wallpaperBrush(dark)),
                contentAlignment = Alignment.Center
            ) {
                val (w, h) = WidgetUpdater.defaultDp(size.first, size.second)
                val k = minOf((maxWidth.value - 28f) / w, (maxHeight.value - 28f) / h, 1.4f)
                WidgetPreview(t, cfg, dark, w, h, k, version = version)
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Prova:", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                PREVIEW_SIZES.forEach { sz ->
                    FilterChip(size == sz, { size = sz }, { Text("${sz.first}×${sz.second}") })
                }
            }
            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
        }

        // ---------------------------------------------------------------- scelte (scorrono)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (version < 0) return@Column
            PermissionGroup(t)
            if (t.parts.isNotEmpty()) MLGroup("Mostra") {
                t.parts.forEach { (key, label) ->
                    MLSwitchRow(label, null, cfg.on(key)) { cfg.style.parts[key] = it; changed() }
                }
            }
            StyleGroups(t, cfg) { changed() }
            MLGroup {
                MLNavRow("Ripristina il widget", "Torna ai colori e allo stile iniziali", icon = Icons.Outlined.RestartAlt, trailing = null) {
                    confirmReset = true
                }
            }
            Text(
                "Il widget si adatta da solo alla grandezza che gli dai sulla Home: prova le grandezze qui sopra.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Permessi che servono a questo widget e mancano. */
@Composable
private fun PermissionGroup(t: Template) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        tick++
        if (it) {
            Refresh.now(ctx, true)
            WidgetUpdater.updateAllAsync(ctx)
        }
    }
    if (tick < 0) return
    val rows = ArrayList<@Composable () -> Unit>()
    if (t.usesWeather && WeatherRepo.currentPlace(ctx) == null) rows += {
        MLNavRow("Posizione per il meteo", "Tocca per usare la tua posizione", icon = Icons.Outlined.LocationOn) {
            perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    if (t.usesCalendar && !CalendarRepo.hasPermission(ctx)) rows += {
        MLNavRow("Mostra gli appuntamenti", "Serve il permesso del calendario", icon = Icons.Outlined.CalendarMonth) {
            perm.launch(Manifest.permission.READ_CALENDAR)
        }
    }
    if (t.usesMusic && !MusicRepo.hasAccess(ctx)) rows += {
        MLNavRow("Mostra il brano in riproduzione", "Attiva MlWidget in \"Accesso alle notifiche\"", icon = Icons.Outlined.MusicNote) {
            MusicRepo.openAccessSettings(ctx)
        }
    }
    if (t.kind == "battery" && !hasBtPermission(ctx)) rows += {
        MLNavRow("Batteria degli accessori", "Cuffie e orologi Bluetooth", icon = Icons.Outlined.Bluetooth) {
            perm.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    if (rows.isNotEmpty()) MLGroup("Da attivare") { rows.forEach { it() } }
}

@Composable
private fun StyleGroups(t: Template, cfg: Cfg, changed: () -> Unit) {
    val s = cfg.style
    val clocky = t.kind in setOf("digital", "analog", "flip", "lcd")
    MLGroup("Stile") {
        if (t.hasDigits) MLChoiceRow("Carattere dei numeri", FONTS, s.font, preview = { FontPreview(it) }) { s.font = it; changed() }
        MLSliderRow("Grandezza del testo", "${s.textScale}%", s.textScale.toFloat(), 70f..140f, 13) { s.textScale = it.roundToInt(); changed() }
        if (t.analog) {
            MLChoiceRow("Quadrante", DIALS, s.dial, preview = { AnalogPreview(t, cfg, dial = it) }) { s.dial = it; changed() }
            MLChoiceRow("Lancette", HANDS, s.hands, preview = { AnalogPreview(t, cfg, hands = it) }) { s.hands = it; changed() }
        }
        if (clocky) MLSwitchRow(if (t.analog) "Lancetta dei secondi" else "Secondi", "Consuma un po' più di batteria", s.seconds) { s.seconds = it; changed() }
        if (t.kind == "note") TextField(s.note, "Testo della nota", lines = 3) { s.note = it; changed() }
        if (t.kind == "search") TextField(s.note, "Testo nella barra", lines = 1) { s.note = it; changed() }
    }

    MLGroup("Colori") {
        MLChoiceRow("Colori presi da", COLOR_SOURCES, s.colorSource, preview = { SourcePreview(t, it, s) }) { s.colorSource = it; changed() }
        if (s.colorSource == "custom") ColorPicker(s.customColor) { s.customColor = it; changed() }
        MLChoiceRow("Chiaro e scuro", DARK_MODES, s.darkMode) { s.darkMode = it; changed() }
    }

    MLGroup("Sfondo") {
        MLChoiceRow("Sfondo del widget", BACKGROUNDS, s.background, preview = { BgPreview(t, it, s) }) { s.background = it; changed() }
        if (s.background != "none" && s.background != "glass") {
            MLSliderRow("Opacità", "${s.opacity}%", s.opacity.toFloat(), 0f..100f, 0) { s.opacity = it.roundToInt(); changed() }
        }
        MLSliderRow("Angoli arrotondati", "${s.corner} dp", s.corner.toFloat(), 0f..48f, 0) { s.corner = it.roundToInt(); changed() }
    }
}

@Composable
private fun TextField(value: String, label: String, lines: Int, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        minLines = lines,
        singleLine = lines == 1
    )
}

@Composable
private fun FontPreview(key: String) {
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    val tf = remember(key) { Renderer.typeface(key) }
    Canvas(Modifier.size(44.dp, 30.dp)) {
        drawIntoCanvas {
            val p = Renderer.textPaint(size.height * 0.8f, ink, tf, null)
            p.textAlign = android.graphics.Paint.Align.CENTER
            it.nativeCanvas.drawText("12", size.width / 2, size.height * 0.82f, p)
        }
    }
}

@Composable
private fun AnalogPreview(t: Template, cfg: Cfg, dial: String? = null, hands: String? = null) {
    val ctx = LocalContext.current
    val dark = Palettes.isNight(ctx)
    val st = cfg.style.copy().apply {
        dial?.let { this.dial = it }; hands?.let { this.hands = it }
        if (this.dial == "none") this.dial = "minimal"
    }
    Canvas(Modifier.size(40.dp)) {
        drawIntoCanvas {
            val nc = it.nativeCanvas
            val pal = Palettes.palette(ctx, st, dark, t)
            val r = android.graphics.RectF(0f, 0f, size.width, size.height)
            Analog.drawDial(nc, r, st.dial, pal, java.util.Date(), java.util.Locale.getDefault())
            Analog.drawHand(nc, r, 300f, "hour", st, pal)
            Analog.drawHand(nc, r, 60f, "minute", st, pal, cap = true)
        }
    }
}

@Composable
private fun SourcePreview(t: Template, key: String, s: Style) {
    val ctx = LocalContext.current
    val st = s.copy().apply { colorSource = key; background = "tonal" }
    val p = Palettes.palette(ctx, st, Palettes.isNight(ctx), t)
    Row(Modifier.clip(CircleShape)) {
        Box(Modifier.size(15.dp, 30.dp).background(Color(p.bg)))
        Box(Modifier.size(15.dp, 30.dp).background(Color(p.accent)))
    }
}

@Composable
private fun BgPreview(t: Template, key: String, s: Style) {
    val ctx = LocalContext.current
    val dark = Palettes.isNight(ctx)
    val st = s.copy().apply { background = key; opacity = 100 }
    val p = Palettes.palette(ctx, st, dark, t)
    Box(Modifier.size(40.dp, 28.dp).clip(RoundedCornerShape(8.dp)).background(wallpaperBrush(dark)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp, 20.dp).clip(RoundedCornerShape(6.dp)).background(Color(p.bg)), contentAlignment = Alignment.Center) {
            Text("12", color = Color(p.text), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val SWATCHES = listOf(
    0xFF4F6BED, 0xFF7B5CE6, 0xFFB04FD0, 0xFFE0508A, 0xFFE5484D, 0xFFFF3B30, 0xFFF07B3F,
    0xFFF0B429, 0xFFFFCC00, 0xFF8BC34A, 0xFF34C759, 0xFF2EAD6B, 0xFF1FA5A0, 0xFF2E9BD6,
    0xFF607D8B, 0xFF8D6E63, 0xFF212121, 0xFFF5F5F5
).map { it.toInt() }

/** Colore personale: tavolozza veloce + tinta, intensità e luminosità. */
@Composable
fun ColorPicker(value: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hsv = remember(value) { FloatArray(3).also { android.graphics.Color.colorToHSV(value, it) } }
    var hue by remember(value) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(value) { mutableFloatStateOf(hsv[1]) }
    var lum by remember(value) { mutableFloatStateOf(hsv[2]) }
    fun emit() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, lum)))
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(vertical = 10.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(SWATCHES) { c ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                        .border(if (c == value) 3.dp else 1.dp, if (c == value) cs.primary else cs.outlineVariant, CircleShape)
                        .clickable { onChange(c) }
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tinta", Modifier.width(96.dp))
            Slider(hue, { hue = it; emit() }, valueRange = 0f..360f, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Intensità", Modifier.width(96.dp))
            Slider(sat, { sat = it; emit() }, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Luminosità", Modifier.width(96.dp))
            Slider(lum, { lum = it; emit() }, modifier = Modifier.weight(1f))
        }
    }
}
