package com.marco.mlwidget

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.AlignHorizontalCenter
import androidx.compose.material.icons.outlined.AlignVerticalCenter
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.marco.mltheme.MLChoiceRow
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLSliderRow
import com.marco.mltheme.MLSwitch
import com.marco.mltheme.MLSwitchRow
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Editor di un widget: anteprima con gli elementi da spostare col dito e tutte le scelte di stile. */
@Composable
fun EditorScreen(t: Template, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val cfg = remember(t.id) { Store.load(ctx, t).copy() }
    var version by remember { mutableIntStateOf(0) }
    var saved by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var dark by remember { mutableStateOf(Palettes.darkFor(cfg.style, Palettes.isNight(ctx))) }
    var guideX by remember { mutableStateOf(false) }
    var guideY by remember { mutableStateOf(false) }
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
        text = { Text("Posizioni, colori e stile tornano com'erano all'inizio.") },
        confirmButton = {
            TextButton({
                confirmReset = false
                val d = t.defaultCfg()
                cfg.style.apply {
                    colorSource = d.style.colorSource; customColor = d.style.customColor; darkMode = d.style.darkMode
                    background = d.style.background; opacity = d.style.opacity; corner = d.style.corner; font = d.style.font
                    dial = d.style.dial; hands = d.style.hands; seconds = d.style.seconds
                }
                cfg.els.forEach { e ->
                    d.el(e.id)?.let { o -> e.x = o.x; e.y = o.y; e.size = o.size; e.visible = o.visible; e.color = o.color; e.align = o.align; e.w = o.w }
                }
                selected = null
                changed()
            }) { Text("Ripristina") }
        },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Annulla") } }
    )

    MLSettingsScaffold(title = t.name, onBack = onBack) {
        // ---------------------------------------------------------------- anteprima
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            val (w, h) = WidgetUpdater.defaultDp(t.cols, t.rows)
            val maxW = maxWidth.value - 32f
            val k = minOf(maxW / w, 300f / h, 1.6f)
            val density = LocalDensity.current.density
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(wallpaperBrush(dark))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    fun hit(px: Float, py: Float): String? {
                        val x = px / (density * k)
                        val y = py / (density * k)
                        val d = Renderer.measure(ctx, t, cfg, w, h, WData.load(ctx, true))
                        return d.boxes.entries.lastOrNull { (id, r) ->
                            cfg.el(id)?.visible == true && x >= r.left - 6 && x <= r.right + 6 && y >= r.top - 6 && y <= r.bottom + 6
                        }?.key
                    }
                    WidgetPreview(
                        t, cfg, dark, w, h, k, version = version, selected = selected, guides = guideX to guideY,
                        modifier = Modifier
                            .pointerInput(t.id) {
                                detectTapGestures { off -> selected = hit(off.x, off.y); changed() }
                            }
                            .pointerInput(t.id) {
                                var rawX = 0f
                                var rawY = 0f
                                detectDragGestures(
                                    onDragStart = { off ->
                                        val id = hit(off.x, off.y) ?: selected
                                        selected = id
                                        cfg.el(id ?: "")?.let { rawX = it.x; rawY = it.y }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        changed()
                                    },
                                    onDragEnd = { guideX = false; guideY = false; changed() },
                                    onDragCancel = { guideX = false; guideY = false; changed() },
                                    onDrag = { ch, amount ->
                                        ch.consume()
                                        val e = cfg.el(selected ?: "")
                                        if (e != null) {
                                        rawX = (rawX + amount.x / (density * k) / w).coerceIn(0f, 1f)
                                        rawY = (rawY + amount.y / (density * k) / h).coerceIn(0f, 1f)
                                        // si aggancia al centro, con un piccolo scatto
                                        val snapX = e.align == "center" && abs(rawX - 0.5f) < 0.02f
                                        val snapY = abs(rawY - 0.5f) < 0.025f
                                        if (snapX && !guideX) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (snapY && !guideY) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        guideX = snapX; guideY = snapY
                                        e.x = if (snapX) 0.5f else rawX
                                        e.y = if (snapY) 0.5f else rawY
                                        changed()
                                        }
                                    }
                                )
                            }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!dark, { dark = false }, { Text("Chiaro") })
                    FilterChip(dark, { dark = true }, { Text("Scuro") })
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (!Pin.request(ctx, t)) Toast.makeText(ctx, "Tieni premuto sulla Home → Widget → MlWidget", Toast.LENGTH_LONG).show()
                    }) {
                        Icon(Icons.Outlined.Widgets, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aggiungi alla Home")
                    }
                }
                Text(
                    if (onHome > 0) "Sulla Home: $onHome · le modifiche si vedono subito" else "Tocca un elemento per sceglierlo, trascinalo per spostarlo",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // ---------------------------------------------------------------- elemento scelto
        val sel = cfg.el(selected ?: "")
        if (sel != null) ElementGroup(sel, version) { changed() }

        // ---------------------------------------------------------------- elenco elementi
        MLGroup("Elementi") {
            cfg.els.forEach { e ->
                MLNavRow(
                    title = elementName(e.kind),
                    subtitle = if (e.id == selected) "Scelto" else null,
                    icon = if (e.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    trailing = { MLSwitch(checked = e.visible, onCheckedChange = { e.visible = it; changed() }) },
                    onClick = { selected = e.id; changed() }
                )
            }
        }

        StyleGroups(t, cfg, version) { changed() }

        MLGroup {
            MLNavRow("Ripristina il widget", "Torna alle posizioni e ai colori iniziali", icon = Icons.Outlined.RestartAlt, trailing = null) {
                confirmReset = true
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ElementGroup(e: El, version: Int, changed: () -> Unit) {
    MLGroup("${elementName(e.kind)} · elemento scelto") {
        if (version < 0) return@MLGroup
        MLSliderRow("Grandezza", e.size.roundToInt().toString(), e.size, 2f..100f, 0) { e.size = it; changed() }
        if (hasWidth(e.kind)) {
            MLSliderRow("Larghezza", "${(e.w * 100).roundToInt()}%", e.w, 0.2f..1f, 0) { e.w = it; changed() }
        }
        val isText = e.kind !in setOf("analog", "wicon", "flip", "hours", "days", "rain", "bring", "bringe", "bbar", "note", "search", "placepill")
        if (isText) {
            MLChoiceRow("Allineamento", listOf("left" to "A sinistra del punto", "center" to "Centrato", "right" to "A destra del punto"), e.align) {
                e.align = it; changed()
            }
        }
        if (e.kind !in setOf("analog", "wicon", "flip", "placepill", "search")) {
            MLChoiceRow("Colore", EL_COLORS, e.color, preview = { ColorDot(it, e) }) { e.color = it; changed() }
            if (e.color == "custom") ColorPicker(e.customColor) { e.customColor = it; changed() }
        }
        MLNavRow("Centra in orizzontale", null, icon = Icons.Outlined.AlignHorizontalCenter, trailing = null) { e.x = 0.5f; e.align = if (e.align == "left" || e.align == "right") "center" else e.align; changed() }
        MLNavRow("Centra in verticale", null, icon = Icons.Outlined.AlignVerticalCenter, trailing = null) { e.y = 0.5f; changed() }
    }
}

@Composable
private fun ColorDot(key: String, e: El) {
    val cs = MaterialTheme.colorScheme
    val c = when (key) {
        "accent" -> cs.primary
        "dim" -> cs.onSurfaceVariant
        "custom" -> Color(e.customColor)
        else -> cs.onSurface
    }
    Box(Modifier.size(26.dp).clip(CircleShape).background(c).border(1.dp, cs.outlineVariant, CircleShape))
}

@Composable
private fun StyleGroups(t: Template, cfg: Cfg, version: Int, changed: () -> Unit) {
    val s = cfg.style
    if (version < 0) return
    val hasText = cfg.els.any { it.kind in LIVE_KINDS || it.kind in setOf("temp", "bpct", "note", "bring", "flip") }

    if (t.analog || hasText || t.tap == "note" || t.tap == "search") MLGroup("Stile") {
        if (hasText) MLChoiceRow("Carattere dei numeri", FONTS, s.font, preview = { FontPreview(it) }) { s.font = it; changed() }
        if (t.analog) {
            MLChoiceRow("Quadrante", DIALS, s.dial, preview = { AnalogPreview(cfg, dial = it) }) { s.dial = it; changed() }
            MLChoiceRow("Lancette", HANDS, s.hands, preview = { AnalogPreview(cfg, hands = it) }) { s.hands = it; changed() }
            MLSwitchRow("Lancetta dei secondi", "Consuma un po' più di batteria", s.seconds) { s.seconds = it; changed() }
        }
        if (t.tap == "note") NoteField(s.note) { s.note = it; changed() }
        if (t.tap == "search") NoteField(s.searchText, "Testo nella barra") { s.searchText = it; changed() }
    }

    MLGroup("Colori") {
        MLChoiceRow("Colori presi da", COLOR_SOURCES, s.colorSource, preview = { SourcePreview(it, s) }) { s.colorSource = it; changed() }
        if (s.colorSource == "custom") ColorPicker(s.customColor) { s.customColor = it; changed() }
        MLChoiceRow("Chiaro e scuro", DARK_MODES, s.darkMode) { s.darkMode = it; changed() }
    }

    MLGroup("Sfondo") {
        MLChoiceRow("Sfondo del widget", BACKGROUNDS, s.background, preview = { BgPreview(it, s) }) { s.background = it; changed() }
        if (s.background != "none" && s.background != "glass") {
            MLSliderRow("Opacità", "${s.opacity}%", s.opacity.toFloat(), 0f..100f, 0) { s.opacity = it.roundToInt(); changed() }
        }
        MLSliderRow("Angoli arrotondati", "${s.corner} dp", s.corner.toFloat(), 0f..48f, 0) { s.corner = it.roundToInt(); changed() }
    }
}

@Composable
private fun NoteField(value: String, label: String = "Testo della nota", onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        minLines = if (label.startsWith("Testo della")) 3 else 1
    )
}

@Composable
private fun FontPreview(key: String) {
    val cs = MaterialTheme.colorScheme
    val tf = remember(key) { Renderer.typeface(key) }
    val ink = cs.onSurface.toArgb()
    Canvas(Modifier.size(44.dp, 30.dp)) {
        drawIntoCanvas {
            val p = Renderer.textPaint(size.height * 0.8f, ink, tf, null)
            p.textAlign = android.graphics.Paint.Align.CENTER
            it.nativeCanvas.drawText("12", size.width / 2, size.height * 0.82f, p)
        }
    }
}

@Composable
private fun AnalogPreview(cfg: Cfg, dial: String? = null, hands: String? = null) {
    val ctx = LocalContext.current
    val dark = Palettes.isNight(ctx)
    val st = cfg.style.copy().apply { dial?.let { this.dial = it }; hands?.let { this.hands = it }; if (this.dial == "none") this.dial = "minimal" }
    Canvas(Modifier.size(40.dp)) {
        drawIntoCanvas {
            val nc = it.nativeCanvas
            val pal = Palettes.palette(ctx, st, dark)
            val r = android.graphics.RectF(0f, 0f, size.width, size.height)
            Analog.drawDial(nc, r, st.dial, pal, java.util.Date(), java.util.Locale.getDefault())
            Analog.drawHand(nc, r, 300f, "hour", st, pal)
            Analog.drawHand(nc, r, 60f, "minute", st, pal, cap = true)
        }
    }
}

@Composable
private fun SourcePreview(key: String, s: Style) {
    val ctx = LocalContext.current
    val st = s.copy().apply { colorSource = key; background = "tonal" }
    val p = Palettes.palette(ctx, st, Palettes.isNight(ctx))
    Row(Modifier.clip(CircleShape)) {
        Box(Modifier.size(15.dp, 30.dp).background(Color(p.bg)))
        Box(Modifier.size(15.dp, 30.dp).background(Color(p.accent)))
    }
}

@Composable
private fun BgPreview(key: String, s: Style) {
    val ctx = LocalContext.current
    val dark = Palettes.isNight(ctx)
    val st = s.copy().apply { background = key; opacity = 100 }
    val p = Palettes.palette(ctx, st, dark)
    Box(
        Modifier.size(40.dp, 28.dp).clip(RoundedCornerShape(8.dp)).background(wallpaperBrush(dark)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(32.dp, 20.dp).clip(RoundedCornerShape(6.dp)).background(Color(p.bg)), contentAlignment = Alignment.Center) {
            Text("12", color = Color(p.text), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val SWATCHES = listOf(
    0xFF4F6BED, 0xFF7B5CE6, 0xFFB04FD0, 0xFFE0508A, 0xFFE5484D, 0xFFF07B3F,
    0xFFF0B429, 0xFF8BC34A, 0xFF2EAD6B, 0xFF1FA5A0, 0xFF2E9BD6, 0xFF607D8B,
    0xFF8D6E63, 0xFF212121, 0xFFF5F5F5
).map { it.toInt() }

/** Colore personale: tavolozza veloce + tinta, saturazione e luminosità. */
@Composable
fun ColorPicker(value: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hsv = remember(value) { FloatArray(3).also { android.graphics.Color.colorToHSV(value, it) } }
    var hue by remember(value) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(value) { mutableFloatStateOf(hsv[1]) }
    var lum by remember(value) { mutableFloatStateOf(hsv[2]) }
    fun emit() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, lum)))
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(vertical = 10.dp)) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
