package com.marco.mlwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.AlarmClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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
//  Quando se ne aggiunge uno si sceglie quale dei nostri widget mostrare.
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

    fun anyWeather(ctx: Context) = allIds(ctx).any { Store.boundTemplate(ctx, it)?.usesWeather == true }

    /** Aggiorna tutti i widget (fuori dal thread principale). */
    fun updateAllAsync(ctx: Context) {
        val app = ctx.applicationContext
        io.execute { updateAll(app) }
    }

    fun updateAll(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        allIds(ctx).forEach { update(ctx, m, it) }
    }

    fun updateTemplateAsync(ctx: Context, templateId: String) {
        val app = ctx.applicationContext
        io.execute {
            val m = AppWidgetManager.getInstance(app)
            Store.widgetsUsing(app, templateId).forEach { update(app, m, it) }
        }
    }

    /** Grandezza del widget in dp (verticale). */
    fun sizeDp(ctx: Context, m: AppWidgetManager, id: Int, t: Template?): Pair<Float, Float> {
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
            val rv = if (t == null) placeholder(ctx, id) else build(ctx, m, id, t)
            m.updateAppWidget(id, rv)
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

    private fun dpPx(ctx: Context) = ctx.resources.displayMetrics.density

    /** Immagine del widget in chiaro o scuro, con le misure degli orologi vivi. */
    private class Pic(val bmp: Bitmap, val drawn: Drawn, val pal: Pal)

    private fun picture(ctx: Context, t: Template, cfg: Cfg, w: Float, h: Float, dark: Boolean, data: WData): Pic {
        val d = dpPx(ctx)
        val scale = min(d, 820f / max(w, h))
        val bmp = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(4), (h * scale).toInt().coerceAtLeast(4), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val pal = Palettes.palette(ctx, cfg.style, dark)
        val drawn = Renderer.draw(ctx, c, t, cfg, w, h, pal, data, drawLive = false, skipAnalog = true)
        return Pic(bmp, drawn, pal)
    }

    /** Quadrante o lancetta: un quadrato grande quanto l'orologio, che Android fa girare da solo. */
    private fun analogLayer(ctx: Context, rect: RectF, draw: (Canvas, RectF) -> Unit): Icon {
        val d = dpPx(ctx)
        val viewPx = rect.width() * d
        val px = min(viewPx, 460f).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        // densità scelta in modo che il disegno occupi esattamente tutto l'orologio
        bmp.density = (ctx.resources.displayMetrics.densityDpi * px / viewPx).toInt().coerceAtLeast(1)
        val c = Canvas(bmp)
        val s = px / rect.width()
        c.scale(s, s)
        draw(c, RectF(0f, 0f, rect.width(), rect.height()))
        return Icon.createWithBitmap(bmp)
    }

    fun build(ctx: Context, m: AppWidgetManager, id: Int, t: Template): RemoteViews {
        val cfg = Store.load(ctx, t)
        val (w, h) = sizeDp(ctx, m, id, t)
        val data = WData.load(ctx)
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
            rv.setViewLayoutMargin(vid, RemoteViews.MARGIN_LEFT, sl.rect.left, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutMargin(vid, RemoteViews.MARGIN_TOP, sl.rect.top, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutWidth(vid, sl.rect.width() + 2f, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(vid, sl.rect.height(), TypedValue.COMPLEX_UNIT_DIP)
        }

        // orologio analogico di sistema con il nostro quadrante e le nostre lancette
        val ar = light.drawn.analog
        if (ar != null && cfg.el("analog")?.visible == true) {
            val s = cfg.style
            val now = java.util.Date()
            val loc = java.util.Locale.getDefault()
            fun dial(p: Pal) = analogLayer(ctx, ar) { c, r -> Analog.drawDial(c, r, s.dial, p, now, loc) }
            fun hand(p: Pal, kind: String, cap: Boolean) = analogLayer(ctx, ar) { c, r -> Analog.drawHand(c, r, 0f, kind, s, p, cap) }
            rv.setViewVisibility(R.id.analog, View.VISIBLE)
            rv.setViewLayoutMargin(R.id.analog, RemoteViews.MARGIN_LEFT, ar.left, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutMargin(R.id.analog, RemoteViews.MARGIN_TOP, ar.top, TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutWidth(R.id.analog, ar.width(), TypedValue.COMPLEX_UNIT_DIP)
            rv.setViewLayoutHeight(R.id.analog, ar.height(), TypedValue.COMPLEX_UNIT_DIP)
            val sameLook = dark === light
            fun pair(make: (Pal) -> Icon): Pair<Icon, Icon> {
                val a = make(light.pal)
                return a to (if (sameLook) a else make(dark.pal))
            }
            val (dl, dd) = pair { dial(it) }
            rv.setIcon(R.id.analog, "setDial", dl, dd)
            val (hl, hd) = pair { hand(it, "hour", false) }
            rv.setIcon(R.id.analog, "setHourHand", hl, hd)
            val (ml, md) = pair { hand(it, "minute", !s.seconds) }
            rv.setIcon(R.id.analog, "setMinuteHand", ml, md)
            if (s.seconds) {
                val (sl, sd) = pair { hand(it, "second", true) }
                rv.setIcon(R.id.analog, "setSecondHand", sl, sd)
            }
        } else {
            rv.setViewVisibility(R.id.analog, View.GONE)
        }

        rv.setOnClickPendingIntent(R.id.root, tapIntent(ctx, t, id))
        return rv
    }

    private fun tapIntent(ctx: Context, t: Template, id: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val i: Intent = when (t.tap) {
            "clock" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "weather" -> ctx.packageManager.getLaunchIntentForPackage("com.marco.mlmeteo") ?: open.putExtra("open", "weather")
            "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            "note" -> open.putExtra("open", "note:${t.id}")
            "search" -> Intent("android.search.action.GLOBAL_SEARCH")
            else -> open
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = i.resolveActivity(ctx.packageManager) != null
        return PendingIntent.getActivity(ctx, id, if (resolved) i else open.putExtra("open", "edit:${t.id}"), flags)
    }
}

// ---------------------------------------------------------------------------------------------
//  Aggiornamenti: ogni 15 minuti (meteo e batteria) e a mezzanotte (data)
// ---------------------------------------------------------------------------------------------

object Refresh {
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork(
            "refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        )
        scheduleMidnight(ctx)
    }

    /** Aggiorna subito (scarica il meteo se serve). */
    fun now(ctx: Context, forceWeather: Boolean = false) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "refresh_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>().setInputData(
                androidx.work.Data.Builder().putBoolean("force", forceWeather).build()
            ).build()
        )
    }

    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, RefreshReceiver::class.java).setAction("com.marco.mlwidget.MIDNIGHT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: Exception) {
            AppLog.e("Sveglia di mezzanotte", e)
        }
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val force = inputData.getBoolean("force", false)
        if (WidgetUpdater.anyWeather(ctx) && (force || WeatherRepo.isOld(ctx))) WeatherRepo.fetch(ctx)
        WidgetUpdater.updateAll(ctx)
        Refresh.scheduleMidnight(ctx)
        return Result.success()
    }
}

/** Mezzanotte, cambio di ora/fuso/lingua, riavvio: ridisegna i widget. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                WidgetUpdater.updateAll(context.applicationContext)
                Refresh.schedule(context.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
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
    }
}

object Pin {
    fun supported(ctx: Context) = AppWidgetManager.getInstance(ctx).isRequestPinAppWidgetSupported

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

/** Gravità dei testi (usata dall'anteprima per allinearsi come il widget vero). */
fun gravityFor(align: String) = when (align) {
    "left" -> Gravity.START or Gravity.CENTER_VERTICAL
    "right" -> Gravity.END or Gravity.CENTER_VERTICAL
    else -> Gravity.CENTER
}
