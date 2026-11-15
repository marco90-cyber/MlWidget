package com.marco.mlwidget

import android.app.ActivityManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import java.io.File
import java.util.Calendar

// ---------------------------------------------------------------------------------------------
//  Batteria (telefono + accessori Bluetooth)
// ---------------------------------------------------------------------------------------------

class Accessory(val name: String, val level: Int, val kind: String)

class Battery(val level: Int, val charging: Boolean, val temp: Float, val accessories: List<Accessory>)

fun hasBtPermission(ctx: Context) =
    ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

/** Livelli degli accessori arrivati dai broadcast del Bluetooth (indirizzo -> %). */
object AccessoryCache {
    val levels = HashMap<String, Int>()
}

@android.annotation.SuppressLint("MissingPermission")
fun readAccessories(ctx: Context): List<Accessory> {
    if (!hasBtPermission(ctx)) return emptyList()
    val out = ArrayList<Accessory>()
    try {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        for (d in adapter.bondedDevices ?: emptySet()) {
            val level = try {
                BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(d) as Int
            } catch (e: Throwable) {
                AccessoryCache.levels[d.address] ?: -1
            }
            if (level !in 0..100) continue
            val name = try { d.alias ?: d.name } catch (e: Exception) { null } ?: continue
            val kind = when (d.bluetoothClass?.majorDeviceClass) {
                0x0400 -> "audio"
                0x0700 -> "watch"
                0x0500 -> "input"
                else -> "other"
            }
            out += Accessory(name, level, kind)
        }
    } catch (e: Throwable) { }
    return out
}

fun readBattery(ctx: Context): Battery {
    val i = try { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (e: Exception) { null }
    val bm = ctx.getSystemService(BatteryManager::class.java)
    var level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: -1
    if (i != null) {
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (l >= 0 && sc > 0) level = l * 100 / sc
    }
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL ||
        (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    return Battery(level.coerceIn(0, 100).let { if (level < 0) 100 else it }, charging, temp, readAccessories(ctx))
}

// ---------------------------------------------------------------------------------------------
//  Dispositivo
// ---------------------------------------------------------------------------------------------

class Device(val storageUsed: Long, val storageTotal: Long, val ramUsed: Long, val ramTotal: Long)

fun readDevice(ctx: Context): Device {
    val st = try { StatFs(Environment.getDataDirectory().path) } catch (e: Exception) { null }
    val total = st?.totalBytes ?: 1L
    val free = st?.availableBytes ?: 0L
    val mi = ActivityManager.MemoryInfo()
    try { ctx.getSystemService(ActivityManager::class.java)?.getMemoryInfo(mi) } catch (e: Exception) { }
    val rt = mi.totalMem.coerceAtLeast(1L)
    return Device(total - free, total, rt - mi.availMem, rt)
}

fun gb(b: Long): String {
    val g = b / 1_000_000_000.0
    return if (g >= 100) "%.0f GB".format(g) else "%.1f GB".format(g).replace('.', ',')
}

// ---------------------------------------------------------------------------------------------
//  Calendario
// ---------------------------------------------------------------------------------------------

class Event(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

object CalendarRepo {
    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Prossimi appuntamenti (da adesso a 14 giorni). */
    fun upcoming(ctx: Context, max: Int = 8): List<Event> {
        if (!hasPermission(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startOfDay.toString()).appendPath((now + 14L * 86_400_000L).toString()).build()
        val out = ArrayList<Event>()
        try {
            ctx.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR),
                "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext() && out.size < max) {
                    val allDay = c.getInt(3) == 1
                    val end = c.getLong(2)
                    // quelli già finiti non servono (quelli di tutto il giorno restano fino a sera)
                    if (!allDay && end < now) continue
                    if (allDay && end < startOfDay + 1) continue
                    out += Event(c.getString(0) ?: "(senza titolo)", c.getLong(1), end, allDay, c.getInt(4))
                }
            }
        } catch (e: Exception) {
            AppLog.e("Calendario", e)
        }
        return out
    }
}

// ---------------------------------------------------------------------------------------------
//  Musica: si legge il brano dalle notifiche dei lettori (serve l'accesso alle notifiche)
// ---------------------------------------------------------------------------------------------

class Track(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val pkg: String,
    val art: Bitmap?
)

object MusicRepo {
    @Volatile var current: Track? = null
    private var lastKey = ""

    fun hasAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(ctx, MediaListener::class.java).flattenToString())
    }

    fun openAccessSettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun artFile(ctx: Context) = File(ctx.cacheDir, "art.png")

    /** Brano attuale: dalla memoria o, se l'app era chiusa, dalle sessioni attive. */
    fun read(ctx: Context): Track? {
        current?.let { return it }
        if (hasAccess(ctx)) {
            try {
                val msm = ctx.getSystemService(MediaSessionManager::class.java)
                val list = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
                pick(list)?.let { return fromController(ctx, it, save = false) }
            } catch (e: Exception) { }
        }
        val p = ctx.getSharedPreferences("music", Context.MODE_PRIVATE)
        val title = p.getString("title", null) ?: return null
        val art = try { BitmapFactory.decodeFile(artFile(ctx).path) } catch (e: Exception) { null }
        return Track(title, p.getString("artist", "") ?: "", false, p.getString("pkg", "") ?: "", art).also { current = it }
    }

    fun pick(list: List<MediaController>): MediaController? =
        list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()

    fun fromController(ctx: Context, c: MediaController, save: Boolean = true): Track? {
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val small = art?.let { scaleArt(it) }
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val t = Track(title, artist, playing, c.packageName, small)
        current = t
        if (save) {
            val key = "$title|$artist|${c.packageName}"
            if (key != lastKey) {
                lastKey = key
                ctx.getSharedPreferences("music", Context.MODE_PRIVATE).edit()
                    .putString("title", title).putString("artist", artist).putString("pkg", c.packageName).apply()
                try {
                    if (small != null) artFile(ctx).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    else artFile(ctx).delete()
                } catch (e: Exception) { }
            }
        }
        return t
    }

    private fun scaleArt(b: Bitmap): Bitmap {
        val max = 360
        if (b.width <= max && b.height <= max) return b.copy(Bitmap.Config.ARGB_8888, false)
        val k = max.toFloat() / maxOf(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    /** Tasti: funzionano con qualsiasi lettore, anche senza l'accesso alle notifiche. */
    fun key(ctx: Context, action: String) {
        val code = when (action) {
            "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(android.media.AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        // il disegno del tasto cambia subito, poi arriva lo stato vero dal lettore
        if (action == "play") current?.let { current = Track(it.title, it.artist, !it.playing, it.pkg, it.art) }
    }

    /** Colore medio della copertina (per lo sfondo dello stile iOS). */
    fun averageColor(b: Bitmap?): Int? {
        if (b == null) return null
        return try {
            val one = Bitmap.createScaledBitmap(b, 1, 1, true)
            one.getPixel(0, 0)
        } catch (e: Exception) { null }
    }
}

/** Ascolta i lettori musicali e aggiorna i widget della musica. */
class MediaListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val pending = Runnable { WidgetUpdater.updateKindAsync(this, setOf("music", "ipod")) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = attach()
    }

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { attach() }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            msm = getSystemService(MediaSessionManager::class.java)
            msm?.addOnActiveSessionsChangedListener(sessions, ComponentName(this, MediaListener::class.java))
        } catch (e: Exception) {
            AppLog.e("Musica", e)
        }
        attach()
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessions) } catch (e: Exception) { }
        controller?.unregisterCallback(callback)
        controller = null
        super.onListenerDisconnected()
    }

    private fun attach() {
        try {
            val list = msm?.getActiveSessions(ComponentName(this, MediaListener::class.java)) ?: emptyList()
            val c = MusicRepo.pick(list)
            if (c?.sessionToken != controller?.sessionToken) {
                controller?.unregisterCallback(callback)
                controller = c
                c?.registerCallback(callback, handler)
            }
        } catch (e: Exception) {
            AppLog.e("Musica", e)
        }
        refresh()
    }

    private fun refresh() {
        controller?.let { MusicRepo.fromController(this, it) } ?: run {
            MusicRepo.current?.let { MusicRepo.current = Track(it.title, it.artist, false, it.pkg, it.art) }
        }
        // più cambi di fila (titolo, copertina, stato) diventano un solo aggiornamento
        handler.removeCallbacks(pending)
        handler.postDelayed(pending, 400)
    }
}
