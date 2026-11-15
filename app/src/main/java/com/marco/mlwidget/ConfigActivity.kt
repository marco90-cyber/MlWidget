package com.marco.mlwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLTheme

/** Si apre quando si aggiunge un widget dalla Home: si sceglie quale dei nostri widget mostrare. */
class ConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent {
            MLTheme {
                MLSettingsScaffold("Scegli il widget", onBack = { finish() }) {
                    val current = remember { Store.boundTemplate(this@ConfigActivity, widgetId)?.id }
                    CATEGORIES.forEach { (key, name) ->
                        MLGroup(name) {
                            Templates.all.filter { it.category == key }.forEach { t ->
                                PickRow(t, t.id == current) { pick(t) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pick(t: Template) {
        Store.bind(this, widgetId, t.id)
        WidgetUpdater.update(this, AppWidgetManager.getInstance(this), widgetId)
        Refresh.schedule(this)
        if (t.usesWeather && WeatherRepo.cached(this) == null) Refresh.now(this, true)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun PickRow(t: Template, current: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val cfg = remember(t.id) { Store.load(ctx, t) }
    val dark = Palettes.isNight(ctx)
    Column(
        Modifier.fillMaxWidth().background(if (current) cs.secondaryContainer else cs.surfaceContainer).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(if (t.rows >= 3) 170.dp else if (t.rows == 1) 70.dp else 120.dp)
                .clip(RoundedCornerShape(18.dp)).background(wallpaperBrush(dark)),
            contentAlignment = Alignment.Center
        ) {
            val (w, h) = WidgetUpdater.defaultDp(t.cols, t.rows)
            val k = minOf((maxWidth.value - 20f) / w, (maxHeight.value - 16f) / h)
            WidgetPreview(t, cfg, dark, w, h, k)
        }
        Text(t.name + if (current) " · in uso" else "", fontWeight = FontWeight.SemiBold)
        Text("da ${t.from} · ${t.cols}×${t.rows}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
