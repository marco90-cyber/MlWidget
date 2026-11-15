package com.marco.mlwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLTheme

/** Si apre quando si aggiunge un widget dalla Home: si sceglie quale widget mostrare. */
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
                    val dark = Palettes.isNight(this@ConfigActivity)
                    CATEGORIES.forEach { (key, name) ->
                        Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp))
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Templates.all.filter { it.category == key }.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { t -> TemplateCard(t, dark, 0, Modifier.weight(1f), selected = t.id == current) { pick(t) } }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
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
        if (t.usesWeather && WeatherRepo.isOld(this)) Refresh.now(this, true)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
