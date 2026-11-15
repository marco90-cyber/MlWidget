package com.marco.mlwidget

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mltheme.MLChoiceRow
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLThemeSettingsGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(onBack: () -> Unit, onLog: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var showCity by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                if (WeatherRepo.mode(ctx) == "gps") WeatherRepo.updateGps(ctx)
                WeatherRepo.fetch(ctx)
                WidgetUpdater.updateAll(ctx)
            }
            busy = false
            tick++
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { WeatherRepo.setGps(ctx); refresh() }
    }

    if (showCity) CityDialog(onDismiss = { showCity = false }) { pl ->
        showCity = false
        WeatherRepo.setCity(ctx, pl)
        refresh()
    }
    if (showInfo) InfoDialog { showInfo = false }

    MLSettingsScaffold("Impostazioni", onBack = onBack) {
        if (tick < 0) return@MLSettingsScaffold
        val w = WeatherRepo.cached(ctx)
        val gps = WeatherRepo.mode(ctx) == "gps"
        MLGroup("Meteo") {
            MLNavRow(
                "Usa la mia posizione",
                if (!WeatherRepo.hasLocationPermission(ctx)) "Serve il permesso della posizione"
                else if (gps) "In uso" + (WeatherRepo.savedGps(ctx)?.name?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") else null,
                icon = Icons.Outlined.MyLocation,
                trailing = null
            ) {
                if (WeatherRepo.hasLocationPermission(ctx)) { WeatherRepo.setGps(ctx); refresh() }
                else perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            MLNavRow(
                "Scegli una città",
                if (!gps) "In uso: " + (WeatherRepo.city(ctx)?.name ?: "") else null,
                icon = Icons.Outlined.LocationOn
            ) { showCity = true }
            MLChoiceRow(
                "Aggiorna il meteo ogni",
                listOf("15" to "15 minuti", "30" to "30 minuti", "60" to "1 ora", "120" to "2 ore"),
                WeatherRepo.interval(ctx).toString()
            ) { WeatherRepo.setInterval(ctx, it.toInt()); tick++ }
            MLNavRow(
                if (busy) "Aggiornamento…" else "Aggiorna adesso",
                w?.let { "Ultimo: " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(it.fetched)) + " · " + it.place } ?: "Nessun dato",
                icon = Icons.Outlined.Refresh,
                trailing = if (busy) ({ CircularProgressIndicator(Modifier.padding(4.dp).heightIn(max = 22.dp), strokeWidth = 2.dp) }) else null
            ) { if (!busy) refresh() }
        }

        MLThemeSettingsGroup("Tema dell'app")

        MLGroup("App") {
            MLNavRow("Aggiornamenti", "Cerca una nuova versione di MlWidget", icon = Icons.Outlined.SystemUpdate) { AppUpdate.open(ctx) }
            MLNavRow("Registro", "Errori dei widget, utile per le segnalazioni", icon = Icons.Outlined.ListAlt) { onLog() }
            MLNavRow("Informazioni", "Versione ${versionName(ctx)} · crediti", icon = Icons.Outlined.Info) { showInfo = true }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun versionName(ctx: android.content.Context) = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (e: Exception) { "" }

@Composable
private fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Chiudi") } },
        title = { Text("MlWidget") },
        text = {
            Text(
                "Tutti i widget delle MLapps in un posto solo, personalizzabili.\n\n" +
                    "Widget presi da MLauncher (orologi, meteo, batteria, note, ricerca, tema Sensation) e da MLMeteo (meteo e pioggia).\n\n" +
                    "Dati meteo: Open-Meteo (open-meteo.com), licenza CC BY 4.0."
            )
        }
    )
}

@Composable
fun CityDialog(onDismiss: () -> Unit, onPick: (Place) -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        try {
            results = withContext(Dispatchers.IO) { WeatherRepo.search(q) }
            error = if (results.isEmpty()) "Nessuna città trovata" else null
        } catch (e: Exception) {
            error = "Nessuna connessione"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Annulla") } },
        title = { Text("Scegli una città") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, label = { Text("Cerca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(results) { p ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            if (p.detail.isNotBlank()) Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun LogScreen(onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    MLSettingsScaffold("Registro", onBack = onBack) {
        if (tick < 0) return@MLSettingsScaffold
        MLGroup {
            MLNavRow("Cancella il registro", null, icon = Icons.Outlined.DeleteSweep, trailing = null) { AppLog.clear(); tick++ }
        }
        MLGroup {
            val lines = AppLog.all()
            if (lines.isEmpty()) Text("Nessun errore", Modifier.padding(16.dp))
            lines.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        }
    }
}
