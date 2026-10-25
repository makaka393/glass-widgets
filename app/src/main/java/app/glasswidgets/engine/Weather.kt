package app.glasswidgets.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import app.glasswidgets.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Weather.qml: open-meteo.com (no key) for the phone's coarse location, cached in prefs. */
object Weather {
    private const val TAG = "GlassWeather"
    private const val STALE_MS = 15 * 60 * 1000L

    data class Hour(val hour: String, val temp: Double, val code: Int, val day: Boolean)
    data class Day(val date: Calendar, val max: Double, val min: Double, val code: Int)
    data class Now(
        val temp: Double, val feels: Double, val code: Int, val isDay: Boolean,
        val tMax: Double, val tMin: Double,
        /** every 2 hours, 6 of them (Weather.hourly) */
        val hourly: List<Hour>,
        /** every hour for a day (Weather.hours) */
        val hours: List<Hour>,
        val days: List<Day>,
        val city: String,
        val updated: Long,
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    fun hasLocationPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isStale(ctx: Context) = System.currentTimeMillis() - prefs(ctx).getLong(Prefs.WEATHER_UPDATED, 0) > STALE_MS

    /** Where we are, for weather and sunrise/sunset: saved coordinates, else a guess from the time zone. */
    fun place(ctx: Context): Pair<Double, Double> {
        val p = prefs(ctx)
        if (p.contains(Prefs.WEATHER_LAT)) return p.getFloat(Prefs.WEATHER_LAT, 0f).toDouble() to p.getFloat(Prefs.WEATHER_LON, 0f).toDouble()
        val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000.0
        return 45.0 to tz * 15
    }

    @SuppressLint("MissingPermission")
    fun lastKnownLocation(ctx: Context): Location? {
        if (!hasLocationPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** A fresh fix; call only while our UI is in the foreground. Blocks up to [timeoutMs]. */
    @SuppressLint("MissingPermission")
    fun currentLocation(ctx: Context, timeoutMs: Long = 6000): Location? {
        if (!hasLocationPermission(ctx)) return null
        val last = lastKnownLocation(ctx)
        if (last != null && System.currentTimeMillis() - last.time < 30 * 60 * 1000L) return last
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return last
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return last
        val latch = CountDownLatch(1)
        var result: Location? = null
        val ex = Executors.newSingleThreadExecutor()
        try {
            lm.getCurrentLocation(provider, null, ex) { result = it; latch.countDown() }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation failed", e)
        } finally {
            ex.shutdown()
        }
        return result ?: last
    }

    fun saveLocation(ctx: Context, loc: Location) {
        val city = runCatching {
            if (!Geocoder.isPresent()) return@runCatching ""
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: ""
        }.getOrDefault("")
        val e = prefs(ctx).edit()
            .putFloat(Prefs.WEATHER_LAT, loc.latitude.toFloat())
            .putFloat(Prefs.WEATHER_LON, loc.longitude.toFloat())
        if (city.isNotEmpty()) e.putString(Prefs.WEATHER_CITY, city)
        e.apply()
    }

    /** Downloads the forecast. Blocking — never on the main thread. */
    fun fetch(ctx: Context): Now? {
        val p = prefs(ctx)
        if (!p.contains(Prefs.WEATHER_LAT)) lastKnownLocation(ctx)?.let { saveLocation(ctx, it) } ?: return cached(ctx)
        val (lat, lon) = place(ctx)
        val unit = if (p.getString(Prefs.WEATHER_UNITS, "c") == "f") "&temperature_unit=fahrenheit" else ""
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code" +
                "&timezone=auto&forecast_days=7$unit"
        )
        return try {
            val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            JSONObject(body)
            p.edit().putString(Prefs.WEATHER_JSON, body).putLong(Prefs.WEATHER_UPDATED, System.currentTimeMillis()).apply()
            cached(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "fetch failed", e)
            cached(ctx)
        }
    }

    fun cached(ctx: Context): Now? {
        val p = prefs(ctx)
        val json = p.getString(Prefs.WEATHER_JSON, null) ?: return null
        return try {
            val d = JSONObject(json)
            val cur = d.getJSONObject("current")
            val h = d.getJSONObject("hourly")
            val times = h.getJSONArray("time")
            val temps = h.getJSONArray("temperature_2m")
            val codes = h.getJSONArray("weather_code")
            val days = h.getJSONArray("is_day")
            // from the next full hour (by the forecast's own clock)
            val nowKey = cur.getString("time").take(13)
            var start = (0 until times.length()).firstOrNull { times.getString(it).take(13) > nowKey } ?: 0
            if (start < 0) start = 0
            fun hourAt(i: Int) = Hour(times.getString(i).substring(11, 16), temps.getDouble(i), codes.getInt(i), days.getInt(i) == 1)
            val hourly = (start until times.length() step 2).take(6).map(::hourAt)
            val hours = (start until times.length()).take(24).map(::hourAt)
            val daily = d.getJSONObject("daily")
            val dt = daily.getJSONArray("time")
            val dmax = daily.getJSONArray("temperature_2m_max")
            val dmin = daily.getJSONArray("temperature_2m_min")
            val dcode = daily.getJSONArray("weather_code")
            val dayList = (0 until dt.length()).map { i ->
                val (y, m, dd) = dt.getString(i).split("-").map { it.toInt() }
                Day(Calendar.getInstance().apply { set(y, m - 1, dd, 12, 0, 0) }, dmax.getDouble(i), dmin.getDouble(i), dcode.getInt(i))
            }
            Now(
                temp = cur.getDouble("temperature_2m"),
                feels = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                tMax = dayList.firstOrNull()?.max ?: 0.0,
                tMin = dayList.firstOrNull()?.min ?: 0.0,
                hourly = hourly, hours = hours, days = dayList,
                city = p.getString(Prefs.WEATHER_CITY, "") ?: "",
                updated = p.getLong(Prefs.WEATHER_UPDATED, 0),
            )
        } catch (e: Exception) {
            Log.w(TAG, "bad cache", e)
            null
        }
    }

    /** WMO weather code → Material Symbol (Weather.qml icon()). */
    fun icon(c: Int, day: Boolean): String = when {
        c == 0 -> if (day) "clear_day" else "clear_night"
        c <= 2 -> if (day) "partly_cloudy_day" else "partly_cloudy_night"
        c == 3 -> "cloud"
        c == 45 || c == 48 -> "foggy"
        c in 51..57 -> "rainy_light"
        c == 61 || c == 80 -> "rainy_light"
        c == 63 || c == 81 -> "rainy"
        c == 65 || c == 82 -> "rainy_heavy"
        c == 66 || c == 67 -> "rainy_snow"
        c in 71..77 -> "weather_snowy"
        c == 85 || c == 86 -> "snowing"
        c >= 95 -> "thunderstorm"
        else -> "cloud"
    }

    fun describe(c: Int): String = when {
        c == 0 -> I18n.t("Ясно", "Clear")
        c == 1 -> I18n.t("Почти ясно", "Mostly clear")
        c == 2 -> I18n.t("Переменная облачность", "Partly cloudy")
        c == 3 -> I18n.t("Пасмурно", "Overcast")
        c == 45 || c == 48 -> I18n.t("Туман", "Fog")
        c in 51..57 -> I18n.t("Морось", "Drizzle")
        c in 61..67 -> I18n.t("Дождь", "Rain")
        c in 71..77 -> I18n.t("Снег", "Snow")
        c in 80..82 -> I18n.t("Ливень", "Showers")
        c == 85 || c == 86 -> I18n.t("Снегопад", "Snowfall")
        c >= 95 -> I18n.t("Гроза", "Thunderstorm")
        else -> "—"
    }
}
