package com.marco.mlwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

// ---------------------------------------------------------------------------------------------
//  I widget che si aggiungono alla Home: tutti uguali, cambia solo la grandezza di partenza.
// ---------------------------------------------------------------------------------------------

open class BaseProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetUpdater.update(context, manager, it) }
        Refresh.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle?) {
        WidgetUpdater.update(context, manager, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { Store.unbind(context, it) }
    }

    override fun onEnabled(context: Context) = Refresh.schedule(context)
}

class WidgetMini : BaseProvider()
class WidgetSmall : BaseProvider()
class WidgetBar : BaseProvider()
class WidgetWide : BaseProvider()
class WidgetLarge : BaseProvider()

object WidgetUpdater {
    val PROVIDERS = listOf(WidgetMini::class.java, WidgetSmall::class.java, WidgetBar::class.java, WidgetWide::class.java, WidgetLarge::class.java)

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "widget-update").apply { isDaemon = true } }

    fun providerFor(t: Template): Class<*> = when {
        t.rows <= 1 && t.cols >= 4 -> WidgetBar::class.java
        t.rows <= 1 -> WidgetMini::class.java
        t.cols <= 2 -> WidgetSmall::class.java
        t.rows >= 3 -> WidgetLarge::class.java
        else -> WidgetWide::class.java
    }

    fun allIds(ctx: Context): List<Int> {
        val m = AppWidgetManager.getInstance(ctx)
        return PROVIDERS.flatMap { m.getAppWidgetIds(ComponentName(ctx, it)).toList() }
    }

    fun onHome(ctx: Context): List<Template> = allIds(ctx).mapNotNull { Store.boundTemplate(ctx, it) }

    fun anyWeather(ctx: Context) = onHome(ctx).any { it.usesWeather }
    fun anyBattery(ctx: Context) = onHome(ctx).any { it.usesBattery }

    fun updateAllAsync(ctx: Context) {
        val app = ctx.applicationContext
        io.execute { updateAll(app) }
    }

    fun updateAll(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        allIds(ctx).forEach { update(ctx, m, it) }
    }

    /** Aggiorna solo i widget di certi tipi (batteria, musica...). */
    fun updateKindAsync(ctx: Context, kinds: Set<String>) {
        val app = ctx.applicationContext
        io.execute {
            val m = AppWidgetManager.getInstance(app)
            allIds(app).forEach { id -> if (Store.boundTemplate(app, id)?.kind in kinds) update(app, m, id) }
        }
    }

    fun updateTemplateAsync(ctx: Context, templateId: String) {
        val app = ctx.applicationContext
        io.execute {
            val m = AppWidgetManager.getInstance(app)
            Store.widgetsUsing(app, templateId).forEach { update(app, m, it) }
        }
    }

    /** Grandezza del widget in dp (telefono in verticale). */
    fun sizeDp(m: AppWidgetManager, id: Int, t: Template?): Pair<Float, Float> {
        val o = m.getAppWidgetOptions(id)
        var w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toFloat()
        var h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).toFloat()
        if (w <= 0f || h <= 0f) {
            val (dw, dh) = defaultDp(t?.cols ?: 2, t?.rows ?: 2)
            w = dw; h = dh
        }
        return w to h
    }

    fun defaultDp(cols: Int, rows: Int) = (cols * 80f - 8f) to (rows * 92f - 12f)

    fun update(ctx: Context, m: AppWidgetManager, id: Int) {
        try {
            val t = Store.boundTemplate(ctx, id)
            m.updateAppWidget(id, if (t == null) placeholder(ctx, id) else build(ctx, m, id, t))
        } catch (e: Throwable) {
            AppLog.e("Aggiornamento widget $id", e)
        }
    }

    private fun placeholder(ctx: Context, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_empty)
        val i = Intent(ctx, ConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return rv
    }

    private class Pic(val bmp: Bitmap, val drawn: Drawn, val pal: Pal)

    private fun picture(ctx: Context, t: Template, cfg: Cfg, w: Float, h: Float, dark: Boolean, data: WData): Pic {
        val d = ctx.resources.displayMetrics.density
        val scale = min(d, 900f / max(w, h))
        val bmp = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(4), (h * scale).toInt().coerceAtLeast(4), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val pal = Palettes.palette(ctx, cfg.style, dark, t)
        val drawn = Renderer.draw(ctx, c, t, cfg, w, h, pal, data, drawLive = false, skipAnalog = true)
        return Pic(bmp, drawn, pal)
    }

    /** Quadrante o lancetta: un quadrato grande quanto l'orologio, che Android fa girare da solo. */
    private fun analogLayer(ctx: Context, rect: RectF, draw: (Canvas, RectF) -> Unit): Icon {
        val viewPx = rect.width() * ctx.resources.displayMetrics.density
        val px = min(viewPx, 460f).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        bmp.density = (ctx.resources.displayMetrics.densityDpi * px / viewPx).toInt().coerceAtLeast(1)
        val c = Canvas(bmp)
        val s = px / rect.width()
        c.scale(s, s)
        draw(c, RectF(0f, 0f, rect.width(), rect.height()))
        return Icon.createWithBitmap(bmp)
    }

    private fun place(rv: RemoteViews, vid: Int, r: RectF) {
        rv.setViewLayoutMargin(vid, RemoteViews.MARGIN_LEFT, r.left, TypedValue.COMPLEX_UNIT_DIP)
        rv.setViewLayoutMargin(vid, RemoteViews.MARGIN_TOP, r.top, TypedValue.COMPLEX_UNIT_DIP)
        rv.setViewLayoutWidth(vid, r.width(), TypedValue.COMPLEX_UNIT_DIP)
        rv.setViewLayoutHeight(vid, r.height(), TypedValue.COMPLEX_UNIT_DIP)
    }

    fun build(ctx: Context, m: AppWidgetManager, id: Int, t: Template): RemoteViews {
        val cfg = Store.load(ctx, t)
        val (w, h) = sizeDp(m, id, t)
        val data = WData.load(ctx, sample = false, t = t)
        val auto = cfg.style.darkMode == "auto"
        val light = picture(ctx, t, cfg, w, h, if (auto) false else Palettes.darkFor(cfg.style, false), data)
        val dark = if (auto) picture(ctx, t, cfg, w, h, true, data) else light

        val rv = RemoteViews(ctx.packageName, R.layout.widget)
        rv.setIcon(R.id.bg, "setImageIcon", Icon.createWithBitmap(light.bmp), Icon.createWithBitmap(dark.bmp))

        // orologi digitali di sistema
        SlotIds.ALL.forEach { rv.setViewVisibility(it, View.GONE) }
        light.drawn.live.take(4).forEachIndexed { i, sl ->
            val darkSl = dark.drawn.live.getOrNull(i) ?: sl
            val vid = SlotIds.id(i, sl.font, sl.shadow)
            rv.setViewVisibility(vid, View.VISIBLE)
            rv.setCharSequence(vid, "setFormat24Hour", sl.format24)
            rv.setCharSequence(vid, "setFormat12Hour", sl.format12)
            rv.setTextViewTextSize(vid, TypedValue.COMPLEX_UNIT_DIP, sl.textSize)
            rv.setInt(vid, "setGravity", gravityFor(sl.align))
            rv.setColorInt(vid, "setTextColor", sl.color, darkSl.color)
            val r = sl.rect
            place(rv, vid, RectF(r.left - 2f, r.top, r.right + 4f, r.bottom + 2f))
        }

        // orologio analogico di sistema con il nostro quadrante e le nostre lancette
        val ar = light.drawn.analog
        if (ar != null) {
            val s = cfg.style
            val now = java.util.Date()
            val loc = java.util.Locale.getDefault()
            rv.setViewVisibility(R.id.analog, View.VISIBLE)
            place(rv, R.id.analog, ar)
            val same = dark === light
            fun pair(make: (Pal) -> Icon): Pair<Icon, Icon> {
                val a = make(light.pal)
                return a to (if (same) a else make(dark.pal))
            }
            val (dl, dd) = pair { p -> analogLayer(ctx, ar) { c, r -> Analog.drawDial(c, r, s.dial, p, now, loc) } }
            rv.setIcon(R.id.analog, "setDial", dl, dd)
            val (hl, hd) = pair { p -> analogLayer(ctx, ar) { c, r -> Analog.drawHand(c, r, 0f, "hour", s, p) } }
            rv.setIcon(R.id.analog, "setHourHand", hl, hd)
            val (ml, md) = pair { p -> analogLayer(ctx, ar) { c, r -> Analog.drawHand(c, r, 0f, "minute", s, p, !s.seconds) } }
            rv.setIcon(R.id.analog, "setMinuteHand", ml, md)
            if (s.seconds) {
                val (sl, sd) = pair { p -> analogLayer(ctx, ar) { c, r -> Analog.drawHand(c, r, 0f, "second", s, p, true) } }
                rv.setIcon(R.id.analog, "setSecondHand", sl, sd)
            }
        } else rv.setViewVisibility(R.id.analog, View.GONE)

        // tasti della musica
        SlotIds.ZONES.forEach { rv.setViewVisibility(it, View.GONE) }
        light.drawn.zones.take(3).forEachIndexed { i, (r, action) ->
            val vid = SlotIds.ZONES[i]
            rv.setViewVisibility(vid, View.VISIBLE)
            place(rv, vid, r)
            val pi = PendingIntent.getBroadcast(
                ctx, id * 10 + i, Intent(ctx, MediaKeyReceiver::class.java).setAction("com.marco.mlwidget.KEY.$action").putExtra("key", action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(vid, pi)
        }

        rv.setOnClickPendingIntent(R.id.root, tapIntent(ctx, t, id, data))
        return rv
    }

    private fun tapIntent(ctx: Context, t: Template, id: Int, data: WData): PendingIntent {
        val pf = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (t.kind == "weather" || t.kind == "glance") {
            // toccando il meteo si aggiorna subito
            return PendingIntent.getBroadcast(ctx, id, Intent(ctx, RefreshReceiver::class.java).setAction(RefreshReceiver.WEATHER), pf)
        }
        val i: Intent? = when (t.kind) {
            "digital", "analog", "flip", "lcd" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            "device" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "calendar", "tearoff" -> if (!data.calendarAllowed) Intent(open).putExtra("open", "calendar")
                else Intent(Intent.ACTION_VIEW).setData(
                    CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
                )
            "music", "ipod" -> {
                if (!data.musicAllowed) Intent(open).putExtra("open", "music")
                else data.track?.pkg?.takeIf { it.isNotBlank() }?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
                    ?: Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            }
            "note" -> Intent(open).putExtra("open", "edit:${t.id}")
            "search" -> Intent(Intent.ACTION_WEB_SEARCH)
            else -> null
        }
        val target = i?.takeIf { it.resolveActivity(ctx.packageManager) != null } ?: Intent(open).putExtra("open", "edit:${t.id}")
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, id, target, pf)
    }
}

fun gravityFor(align: String) = when (align) {
    "left" -> Gravity.START or Gravity.CENTER_VERTICAL
    "right" -> Gravity.END or Gravity.CENTER_VERTICAL
    else -> Gravity.CENTER
}

// ---------------------------------------------------------------------------------------------
//  Aggiornamenti
//  - meteo: ogni 15/30/60 minuti (WorkManager)
//  - batteria: quando cambia (con l'app in memoria) e comunque ogni 10 minuti
//  - data: a mezzanotte; calendario: quando cambia un appuntamento
// ---------------------------------------------------------------------------------------------

object Refresh {
    fun schedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        )
        scheduleMidnight(ctx)
        scheduleBattery(ctx)
    }

    /** Aggiorna subito (scarica il meteo se serve), appena c'è la rete. */
    fun now(ctx: Context, forceWeather: Boolean = false) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "refresh_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(androidx.work.Data.Builder().putBoolean("force", forceWeather).build())
                .build()
        )
    }

    private fun pi(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, RefreshReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
        }
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi(ctx, RefreshReceiver.MIDNIGHT, 1)) } catch (e: Exception) { }
    }

    /** Ogni 10 minuti ridisegna batteria e dispositivo (solo se sulla Home ce n'è uno). */
    fun scheduleBattery(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val p = pi(ctx, RefreshReceiver.BATTERY, 2)
        if (!WidgetUpdater.anyBattery(ctx)) { am.cancel(p); return }
        try { am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 10 * 60_000L, p) } catch (e: Exception) { }
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val force = inputData.getBoolean("force", false)
        if (force || (WidgetUpdater.anyWeather(ctx) && WeatherRepo.isOld(ctx))) WeatherRepo.fetch(ctx)
        WidgetUpdater.updateAll(ctx)
        Refresh.scheduleMidnight(ctx)
        Refresh.scheduleBattery(ctx)
        return Result.success()
    }
}

/** Mezzanotte, batteria, cambio di ora/fuso/lingua, calendario, riavvio: ridisegna i widget. */
class RefreshReceiver : BroadcastReceiver() {
    companion object {
        const val MIDNIGHT = "com.marco.mlwidget.MIDNIGHT"
        const val BATTERY = "com.marco.mlwidget.BATTERY"
        const val WEATHER = "com.marco.mlwidget.WEATHER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            WEATHER -> {
                android.widget.Toast.makeText(app, "Aggiorno il meteo…", android.widget.Toast.LENGTH_SHORT).show()
                Refresh.now(app, true)
                return
            }
            BATTERY -> {
                Refresh.scheduleBattery(app)
                WidgetUpdater.updateKindAsync(app, setOf("battery", "device"))
                return
            }
            Intent.ACTION_PROVIDER_CHANGED -> {
                WidgetUpdater.updateKindAsync(app, setOf("calendar", "tearoff"))
                return
            }
        }
        val pending = goAsync()
        Thread {
            try {
                WidgetUpdater.updateAll(app)
                Refresh.schedule(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Con l'app in memoria la batteria si ridisegna a ogni punto percentuale e quando si collega il caricatore. */
object BatteryWatcher {
    private var lastLevel = -1
    private var lastCharging: Boolean? = null

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        val rec = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val sc = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                val level = if (l >= 0 && sc > 0) l * 100 / sc else -1
                val charging = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (level != lastLevel || charging != lastCharging) {
                    lastLevel = level; lastCharging = charging
                    WidgetUpdater.updateKindAsync(app, setOf("battery", "device"))
                }
            }
        }
        try {
            app.registerReceiver(rec, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val bt = IntentFilter().apply {
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            androidx.core.content.ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    try {
                        @Suppress("DEPRECATION")
                        val dev = i.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                        val lv = i.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                        if (dev != null && lv in 0..100 && hasBtPermission(app)) AccessoryCache.levels[dev.address] = lv
                    } catch (e: Throwable) { }
                    WidgetUpdater.updateKindAsync(app, setOf("battery"))
                }
            }, bt, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            AppLog.e("Batteria", e)
        }
    }
}

/** Tasti della musica sui widget. */
class MediaKeyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MusicRepo.key(context, intent.getStringExtra("key") ?: "play")
        WidgetUpdater.updateKindAsync(context, setOf("music", "ipod"))
    }
}

/** Arriva quando un widget aggiunto dall'app ("Aggiungi alla Home") è stato messo sulla Home. */
class PinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val tpl = intent.getStringExtra("template") ?: return
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        Store.bind(context, id, tpl)
        WidgetUpdater.update(context, AppWidgetManager.getInstance(context), id)
        Refresh.schedule(context)
        if (Templates.byId(tpl)?.usesWeather == true && WeatherRepo.isOld(context)) Refresh.now(context, true)
    }
}

object Pin {
    fun request(ctx: Context, t: Template): Boolean {
        val m = AppWidgetManager.getInstance(ctx)
        if (!m.isRequestPinAppWidgetSupported) return false
        val cb = PendingIntent.getBroadcast(
            ctx, t.id.hashCode(), Intent(ctx, PinReceiver::class.java).putExtra("template", t.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return m.requestPinAppWidget(ComponentName(ctx, WidgetUpdater.providerFor(t)), null, cb)
    }
}

/** Un appuntamento è cambiato: ridisegna i calendari. */
class CalendarReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdater.updateKindAsync(context, setOf("calendar", "tearoff"))
    }
}
