package com.marco.mlwidget

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import android.os.BatteryManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

class Hour(val time: Long, val temp: Int, val code: Int, val isDay: Boolean)
class Day(val time: Long, val code: Int, val hi: Int, val lo: Int)

class Weather(
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hi: Int,
    val lo: Int,
    val feels: Int,
    val humidity: Int,
    val wind: Int,
    val place: String,
    val fetched: Long,
    val hours: List<Hour>,
    val days: List<Day>,
    /** pioggia ogni 15 minuti (mm) per le prossime 2 ore */
    val rain: List<Float>
) {
    fun toJson(): String = JSONObject().apply {
        put("temp", temp); put("code", code); put("isDay", isDay); put("hi", hi); put("lo", lo)
        put("feels", feels); put("humidity", humidity); put("wind", wind); put("place", place); put("fetched", fetched)
        put("hours", JSONArray().apply { hours.forEach { put(JSONArray().put(it.time).put(it.temp).put(it.code).put(it.isDay)) } })
        put("days", JSONArray().apply { days.forEach { put(JSONArray().put(it.time).put(it.code).put(it.hi).put(it.lo)) } })
        put("rain", JSONArray().apply { rain.forEach { put(it.toDouble()) } })
    }.toString()

    companion object {
        fun fromJson(s: String): Weather? = try {
            val o = JSONObject(s)
            val h = o.getJSONArray("hours"); val d = o.getJSONArray("days"); val r = o.getJSONArray("rain")
            Weather(
                o.getInt("temp"), o.getInt("code"), o.getBoolean("isDay"), o.getInt("hi"), o.getInt("lo"),
                o.optInt("feels"), o.optInt("humidity"), o.optInt("wind"), o.optString("place"), o.getLong("fetched"),
                (0 until h.length()).map { i -> h.getJSONArray(i).let { Hour(it.getLong(0), it.getInt(1), it.getInt(2), it.getBoolean(3)) } },
                (0 until d.length()).map { i -> d.getJSONArray(i).let { Day(it.getLong(0), it.getInt(1), it.getInt(2), it.getInt(3)) } },
                (0 until r.length()).map { r.getDouble(it).toFloat() }
            )
        } catch (e: Exception) {
            null
        }

        /** Dati d'esempio per le anteprime quando il meteo non è ancora arrivato. */
        fun sample(): Weather {
            val now = System.currentTimeMillis() / 3_600_000L * 3_600_000L
            val codes = intArrayOf(1, 2, 3, 61, 80, 2)
            return Weather(
                21, 2, true, 24, 14, 20, 58, 12, "Roma", System.currentTimeMillis(),
                (1..8).map { Hour(now + it * 3_600_000L, 21 - it / 2, codes[it % codes.size], it < 6) },
                (0..5).map { Day(now + it * 86_400_000L, codes[it % codes.size], 24 - it % 3, 14 - it % 2) },
                listOf(0f, 0f, 0.2f, 0.8f, 1.6f, 1.1f, 0.4f, 0f)
            )
        }
    }
}

class Place(val name: String, val lat: Double, val lon: Double, val detail: String = "")

/** Meteo da Open-Meteo, per tutti i widget meteo. */
object WeatherRepo {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(ctx: Context): Weather? = sp(ctx).getString("data", null)?.let { Weather.fromJson(it) }

    /** gps · city */
    fun mode(ctx: Context) = sp(ctx).getString("mode", "gps") ?: "gps"
    fun city(ctx: Context): Place? {
        val p = sp(ctx)
        val n = p.getString("cityName", null) ?: return null
        return Place(n, p.getFloat("cityLat", 0f).toDouble(), p.getFloat("cityLon", 0f).toDouble())
    }

    fun setCity(ctx: Context, pl: Place) = sp(ctx).edit()
        .putString("mode", "city").putString("cityName", pl.name)
        .putFloat("cityLat", pl.lat.toFloat()).putFloat("cityLon", pl.lon.toFloat()).apply()

    fun setGps(ctx: Context) = sp(ctx).edit().putString("mode", "gps").apply()

    /** minuti tra un aggiornamento e l'altro */
    fun interval(ctx: Context) = sp(ctx).getInt("interval", 30)
    fun setInterval(ctx: Context, m: Int) = sp(ctx).edit().putInt("interval", m).apply()

    /** Ultima posizione nota: viene salvata quando l'app è aperta (così non serve la posizione in background). */
    fun savedGps(ctx: Context): Place? {
        val p = sp(ctx)
        if (!p.contains("gpsLat")) return null
        return Place(p.getString("gpsName", "") ?: "", p.getFloat("gpsLat", 0f).toDouble(), p.getFloat("gpsLon", 0f).toDouble())
    }

    fun hasLocationPermission(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Legge la posizione (con l'app aperta) e la salva. */
    @Suppress("MissingPermission", "DEPRECATION")
    fun updateGps(ctx: Context): Place? {
        if (!hasLocationPermission(ctx)) return savedGps(ctx)
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return savedGps(ctx)
        val loc = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
            .maxByOrNull { it.time } ?: return savedGps(ctx)
        val name = try {
            Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: ""
        } catch (e: Exception) {
            ""
        }
        val old = savedGps(ctx)
        val finalName = name.ifBlank { old?.name ?: "" }
        sp(ctx).edit().putFloat("gpsLat", loc.latitude.toFloat()).putFloat("gpsLon", loc.longitude.toFloat())
            .putString("gpsName", finalName).apply()
        return Place(finalName, loc.latitude, loc.longitude)
    }

    fun currentPlace(ctx: Context): Place? = if (mode(ctx) == "city") city(ctx) else savedGps(ctx)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "MlWidget")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun search(name: String): List<Place> {
        val q = URLEncoder.encode(name.trim(), "UTF-8")
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=it&format=json"))
        val r = o.optJSONArray("results") ?: return emptyList()
        return (0 until r.length()).map { i ->
            val p = r.getJSONObject(i)
            Place(
                p.getString("name"), p.getDouble("latitude"), p.getDouble("longitude"),
                listOfNotNull(p.optString("admin1").takeIf { it.isNotBlank() }, p.optString("country").takeIf { it.isNotBlank() }).joinToString(", ")
            )
        }
    }

    fun isOld(ctx: Context): Boolean {
        val w = cached(ctx) ?: return true
        return System.currentTimeMillis() - w.fetched > interval(ctx) * 60_000L - 60_000L
    }

    /** Scarica il meteo. Restituisce null se non c'è una località o la rete non risponde. */
    fun fetch(ctx: Context): Weather? {
        val pl = currentPlace(ctx) ?: return null
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${pl.lat}&longitude=${pl.lon}" +
            "&current=temperature_2m,weather_code,is_day,relative_humidity_2m,apparent_temperature,wind_speed_10m" +
            "&hourly=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&minutely_15=precipitation&forecast_minutely_15=8&timezone=auto&forecast_days=7&timeformat=unixtime"
        return try {
            val o = JSONObject(get(url))
            val cur = o.getJSONObject("current")
            val hourly = o.getJSONObject("hourly")
            val ht = hourly.getJSONArray("time")
            val now = System.currentTimeMillis() / 1000L
            val hours = ArrayList<Hour>()
            for (i in 0 until ht.length()) {
                val t = ht.getLong(i)
                if (t <= now) continue
                hours += Hour(t * 1000L, hourly.getJSONArray("temperature_2m").optDouble(i).roundToInt(),
                    hourly.getJSONArray("weather_code").optInt(i), hourly.getJSONArray("is_day").optInt(i) == 1)
                if (hours.size >= 12) break
            }
            val daily = o.getJSONObject("daily")
            val dt = daily.getJSONArray("time")
            val days = (0 until dt.length()).map { i ->
                Day(dt.getLong(i) * 1000L, daily.getJSONArray("weather_code").optInt(i),
                    daily.getJSONArray("temperature_2m_max").optDouble(i).roundToInt(),
                    daily.getJSONArray("temperature_2m_min").optDouble(i).roundToInt())
            }
            val m = o.optJSONObject("minutely_15")
            val rain = m?.optJSONArray("precipitation")?.let { a -> (0 until a.length()).map { a.optDouble(it, 0.0).toFloat() } } ?: emptyList()
            val w = Weather(
                cur.getDouble("temperature_2m").roundToInt(), cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1,
                days.firstOrNull()?.hi ?: 0, days.firstOrNull()?.lo ?: 0,
                cur.optDouble("apparent_temperature").roundToInt(), cur.optInt("relative_humidity_2m"),
                cur.optDouble("wind_speed_10m").roundToInt(), pl.name, System.currentTimeMillis(), hours, days, rain
            )
            sp(ctx).edit().putString("data", w.toJson()).apply()
            w
        } catch (e: Exception) {
            null
        }
    }
}

class Battery(val level: Int, val charging: Boolean)

fun readBattery(ctx: Context): Battery {
    val bm = ctx.getSystemService(BatteryManager::class.java)
    val lv = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 100
    return Battery(lv, bm?.isCharging == true)
}

fun weatherText(code: Int): String = when (code) {
    0 -> "Sereno"
    1 -> "Quasi sereno"
    2 -> "Poco nuvoloso"
    3 -> "Nuvoloso"
    45, 48 -> "Nebbia"
    51, 53, 55 -> "Pioggerella"
    56, 57, 66, 67 -> "Pioggia gelata"
    61 -> "Pioggia debole"
    63 -> "Pioggia"
    65 -> "Pioggia forte"
    71, 73, 75, 77 -> "Neve"
    80, 81, 82 -> "Rovesci"
    85, 86 -> "Rovesci di neve"
    95, 96, 99 -> "Temporale"
    else -> "Meteo"
}
