package com.marco.mlwidget

import android.app.WallpaperManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Ora attuale che si aggiorna da sola (ogni secondo o ogni minuto). */
@Composable
fun rememberNow(seconds: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(seconds) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (seconds) 1000L - now % 1000L + 5L else 60_000L - now % 60_000L + 30L)
        }
    }
    return now
}

/** Colori dello sfondo del telefono, per mettere le anteprime su qualcosa che gli somigli. */
@Composable
fun wallpaperBrush(dark: Boolean): Brush {
    val ctx = LocalContext.current
    val cols = remember(dark) {
        val wc = try { WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) } catch (e: Throwable) { null }
        val list = listOfNotNull(wc?.primaryColor, wc?.secondaryColor, wc?.tertiaryColor).map { Color(it.toArgb()) }
            .ifEmpty { listOf(Color(0xFF5B6EE1), Color(0xFF9C5BD6), Color(0xFF3FA7A0)) }
        val shade = if (dark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.15f)
        list.map { androidx.compose.ui.graphics.lerp(it, shade.copy(alpha = 1f), shade.alpha) }
    }
    return if (cols.size == 1) Brush.linearGradient(listOf(cols[0], cols[0])) else Brush.linearGradient(cols)
}

/**
 * Anteprima di un widget, disegnata con lo stesso codice del widget vero.
 * [scale] riduce o ingrandisce il widget sullo schermo; [selected] mostra il riquadro dell'elemento scelto.
 */
@Composable
fun WidgetPreview(
    t: Template,
    cfg: Cfg,
    dark: Boolean,
    wDp: Float,
    hDp: Float,
    scale: Float,
    modifier: Modifier = Modifier,
    version: Int = 0,
    selected: String? = null,
    guides: Pair<Boolean, Boolean> = false to false,
    sample: Boolean = true
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val now = rememberNow(cfg.style.seconds || cfg.els.any { it.kind == "seconds" && it.visible })
    val accent = remember(version, dark) { Palettes.palette(ctx, cfg.style, dark).accent }
    Canvas(modifier.size((wDp * scale).dp, (hDp * scale).dp)) {
        if (version < 0) return@Canvas
        val base = WData.load(ctx, sample)
        val data = WData(base.weather, base.battery, now, base.is24)
        val pal = Palettes.palette(ctx, cfg.style, dark)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.scale(density * scale, density * scale)
            val d = Renderer.draw(ctx, nc, t, cfg, wDp, hDp, pal, data, drawLive = true)
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.5f / scale
                color = accent
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f / scale, 4f / scale), 0f)
            }
            if (guides.first) nc.drawLine(wDp / 2, 0f, wDp / 2, hDp, p)
            if (guides.second) nc.drawLine(0f, hDp / 2, wDp, hDp / 2, p)
            selected?.let { id ->
                d.boxes[id]?.let { r ->
                    val pad = 3f
                    p.pathEffect = null
                    p.color = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                    p.alpha = 150
                    nc.drawRoundRect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, 6f, 6f, p)
                }
            }
            nc.restore()
        }
    }
}

/** Anteprima su uno "sfondo" che somiglia a quello del telefono. */
@Composable
fun PreviewOnWallpaper(
    t: Template, cfg: Cfg, dark: Boolean, wDp: Float, hDp: Float, scale: Float,
    modifier: Modifier = Modifier, version: Int = 0, padding: Float = 12f
) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(wallpaperBrush(dark))
            .size(((wDp * scale) + padding * 2).dp, ((hDp * scale) + padding * 2).dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        WidgetPreview(t, cfg, dark, wDp, hDp, scale, version = version)
    }
}
