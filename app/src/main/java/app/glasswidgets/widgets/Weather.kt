package app.glasswidgets.widgets

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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Weather from Open-Meteo (free, no API key) for the device's coarse location, cached in prefs. */
internal object Weather {
    private const val TAG = "SidebarWeather"
    private const val STALE_MS = 25 * 60 * 1000L

    data class Day(val date: String, val code: Int, val max: Double, val min: Double) {
        val shortName: String
            get() = runCatching {
                LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }.getOrDefault(date)
    }

    data class Now(
        val temp: Double, val code: Int, val isDay: Boolean,
        val max: Double, val min: Double,
        val days: List<Day>, val city: String, val updated: Long
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    fun hasLocationPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasCoordinates(ctx: Context) = prefs(ctx).contains(Prefs.WEATHER_LAT)

    fun isStale(ctx: Context) =
        System.currentTimeMillis() - prefs(ctx).getLong(Prefs.WEATHER_UPDATED, 0) > STALE_MS

    // ── Location ─────────────────────────────────────────────────────────────

    /** Newest last-known fix from any enabled provider (instant, no GPS wake-up). */
    @SuppressLint("MissingPermission")
    fun lastKnownLocation(ctx: Context): Location? {
        if (!hasLocationPermission(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * Fresh coarse fix; only call while the app is in the foreground (settings screen or a
     * widget tap). Blocks for up to [timeoutMs]; falls back to the last known fix.
     */
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
        val executor = Executors.newSingleThreadExecutor()
        try {
            lm.getCurrentLocation(provider, null, executor) { loc ->
                result = loc
                latch.countDown()
            }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation failed", e)
        } finally {
            executor.shutdown()
        }
        return result ?: last
    }

    /** Stores coordinates (and a city name when the on-device geocoder knows one). */
    fun saveLocation(ctx: Context, loc: Location) {
        val city = runCatching {
            if (!Geocoder.isPresent()) return@runCatching ""
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea } ?: ""
        }.getOrDefault("")
        val editor = prefs(ctx).edit()
            .putFloat(Prefs.WEATHER_LAT, loc.latitude.toFloat())
            .putFloat(Prefs.WEATHER_LON, loc.longitude.toFloat())
        if (city.isNotEmpty()) editor.putString(Prefs.WEATHER_CITY, city)
        editor.apply()
    }

    // ── Fetch & cache ────────────────────────────────────────────────────────

    /** Downloads weather for the stored coordinates. Blocking; never call on the main thread. */
    fun fetch(ctx: Context): Now? {
        val p = prefs(ctx)
        if (!p.contains(Prefs.WEATHER_LAT)) {
            // Background refresh: try a cached fix once so first-time setup works without the app.
            lastKnownLocation(ctx)?.let { saveLocation(ctx, it) } ?: return cached(ctx)
        }
        val lat = p.getFloat(Prefs.WEATHER_LAT, 0f)
        val lon = p.getFloat(Prefs.WEATHER_LON, 0f)
        val unit = if (p.getString(Prefs.WEATHER_UNITS, "c") == "f") "fahrenheit" else "celsius"
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=5&temperature_unit=$unit"
        )
        return try {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            JSONObject(body) // validate before caching
            p.edit()
                .putString(Prefs.WEATHER_JSON, body)
                .putLong(Prefs.WEATHER_UPDATED, System.currentTimeMillis())
                .apply()
            cached(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed", e)
            cached(ctx)
        }
    }

    fun cached(ctx: Context): Now? {
        val p = prefs(ctx)
        val json = p.getString(Prefs.WEATHER_JSON, null) ?: return null
        return try {
            val o = JSONObject(json)
            val cur = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val codes = daily.getJSONArray("weather_code")
            val maxs = daily.getJSONArray("temperature_2m_max")
            val mins = daily.getJSONArray("temperature_2m_min")
            val days = (0 until dates.length()).map { i ->
                Day(dates.getString(i), codes.optInt(i), maxs.optDouble(i), mins.optDouble(i))
            }
            Now(
                temp = cur.getDouble("temperature_2m"),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                max = days.firstOrNull()?.max ?: Double.NaN,
                min = days.firstOrNull()?.min ?: Double.NaN,
                days = days,
                city = p.getString(Prefs.WEATHER_CITY, "") ?: "",
                updated = p.getLong(Prefs.WEATHER_UPDATED, 0)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Bad cached weather", e)
            null
        }
    }

    fun toMap(now: Now): Map<String, Any> = mapOf(
        "temp" to now.temp.roundToInt(),
        "code" to now.code,
        "isDay" to now.isDay,
        "condition" to label(now.code),
        "max" to now.max.roundToInt(),
        "min" to now.min.roundToInt(),
        "city" to now.city,
        "updated" to now.updated.toDouble(),
    )

    // ── WMO weather codes ────────────────────────────────────────────────────

    fun label(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "—"
    }

    /** Material Icons glyph for a weather code (from the bundled MaterialIcons font). */
    fun glyph(code: Int, isDay: Boolean): String = when (code) {
        0, 1 -> if (isDay) "" else ""          // sunny / bedtime
        2 -> if (isDay) "" else ""             // filter_drama / nights_stay
        3 -> ""                                      // cloud
        45, 48 -> ""                                 // foggy
        51, 53, 55, 56, 57 -> ""                     // grain
        61, 63, 65, 66, 67, 80, 81, 82 -> ""         // water_drop
        71, 73, 75, 77, 85, 86 -> ""                 // cloudy_snowing
        95, 96, 99 -> ""                             // thunderstorm
        else -> ""
    }
}
