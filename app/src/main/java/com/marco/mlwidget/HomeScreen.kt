package com.marco.mlwidget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Tutti i widget, divisi per categoria. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (Template) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var filter by rememberSaveable { mutableStateOf("all") }
    val dark = Palettes.isNight(ctx)
    val onHome = remember { WidgetUpdater.allIds(ctx).mapNotNull { Store.boundTemplate(ctx, it)?.id }.groupingBy { it }.eachCount() }
    val needPlace = remember { WeatherRepo.currentPlace(ctx) == null }

    Scaffold(
        containerColor = cs.surface,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("MlWidget") },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Impostazioni") }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surfaceContainer)
            )
        }
    ) { pad ->
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { FilterChip(filter == "all", { filter = "all" }, { Text("Tutti") }) }
                    items(CATEGORIES) { (k, n) -> FilterChip(filter == k, { filter = k }, { Text(n) }) }
                }
            }
            if (needPlace) item {
                Surface(
                    color = cs.secondaryContainer, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onSettings)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = cs.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text("Scegli la posizione per i widget meteo", color = cs.onSecondaryContainer)
                    }
                }
            }
            CATEGORIES.filter { filter == "all" || filter == it.first }.forEach { (key, name) ->
                val list = Templates.all.filter { it.category == key }
                item(key = "h_$key") {
                    Text(
                        name, style = MaterialTheme.typography.titleMedium, color = cs.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 10.dp)
                    )
                }
                // due colonne; i widget larghi occupano tutta la riga
                val rows = ArrayList<List<Template>>()
                var pending: Template? = null
                list.forEach { t ->
                    if (t.cols >= 4) rows += listOf(t)
                    else if (pending == null) pending = t
                    else { rows += listOf(pending!!, t); pending = null }
                }
                pending?.let { rows += listOf(it) }
                rows.forEachIndexed { i, row ->
                    item(key = "r_${key}_$i") {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { t ->
                                TemplateCard(t, dark, onHome[t.id] ?: 0, Modifier.weight(if (t.cols >= 4) 2f else 1f)) { onOpen(t) }
                            }
                            if (row.size == 1 && row[0].cols < 4) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(t: Template, dark: Boolean, onHome: Int, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val cfg = remember(t.id) { Store.load(ctx, t) }
    Surface(
        color = cs.surfaceContainer,
        shape = RoundedCornerShape(26.dp),
        modifier = modifier.clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(10.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(if (t.rows >= 3) 200.dp else if (t.rows == 1) 84.dp else 140.dp)
                .clip(RoundedCornerShape(18.dp)).background(wallpaperBrush(dark)),
                contentAlignment = Alignment.Center
            ) {
                val (w, h) = WidgetUpdater.defaultDp(t.cols, t.rows)
                val k = minOf((maxWidth.value - 20f) / w, (maxHeight.value - 20f) / h)
                WidgetPreview(t, cfg, dark, w, h, k)
            }
            Text(
                t.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, top = 10.dp)
            )
            Text(
                buildString {
                    append("da ${t.from} · ${t.cols}×${t.rows}")
                    if (onHome > 0) append(" · sulla Home: $onHome")
                },
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
            )
        }
    }
}
