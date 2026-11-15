package com.marco.mlwidget

import android.app.WallpaperManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        val shade = if (dark) Color.Black else Color.White
        list.map { androidx.compose.ui.graphics.lerp(it, shade, if (dark) 0.45f else 0.15f) }
    }
    return if (cols.size == 1) Brush.linearGradient(listOf(cols[0], cols[0])) else Brush.linearGradient(cols)
}

/** Anteprima di un widget, disegnata con lo stesso codice del widget vero. */
@Composable
fun WidgetPreview(
    t: Template,
    cfg: Cfg,
    dark: Boolean,
    wDp: Float,
    hDp: Float,
    scale: Float,
    modifier: Modifier = Modifier,
    version: Int = 0
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val secs = cfg.style.seconds && t.kind in setOf("digital", "analog", "flip", "lcd")
    val now = rememberNow(secs)
    val data = remember(t.id, version, now / 60_000L) { WData.load(ctx, sample = true, t = t) }
    Canvas(modifier.size((wDp * scale).dp, (hDp * scale).dp)) {
        if (version < 0) return@Canvas
        val pal = Palettes.palette(ctx, cfg.style, dark, t)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.save()
            nc.scale(density * scale, density * scale)
            Renderer.draw(ctx, nc, t, cfg, wDp, hDp, pal, data.at(now), drawLive = true)
            nc.restore()
        }
    }
}
