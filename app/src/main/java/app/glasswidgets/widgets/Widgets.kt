package app.glasswidgets.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import app.glasswidgets.M3Scheme
import app.glasswidgets.Prefs
import app.glasswidgets.R
import app.glasswidgets.ThemeColors
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Every home-screen widget the app offers. Ids are shared with the settings screen. */
enum class WidgetKind(val id: String, val layout: Int, val weather: Boolean) {
    CLOCK_STACK("clock_stack", R.layout.widget_clock_stack, false),
    CLOCK_THIN("clock_thin", R.layout.widget_clock_thin, false),
    CLOCK_PILL("clock_pill", R.layout.widget_clock_pill, false),
    CLOCK_ANALOG("clock_analog", R.layout.widget_clock_analog, false),
    CLOCK_TILES("clock_tiles", R.layout.widget_clock_tiles, false),
    CLOCK_DATE("clock_date", R.layout.widget_clock_date, false),
    WEATHER("weather", R.layout.widget_weather, true),
    WEATHER_FORECAST("weather_forecast", R.layout.widget_weather_forecast, true),
    CLOCK_WEATHER("clock_weather", R.layout.widget_clock_weather, true);

    val provider: Class<out AppWidgetProvider>
        get() = when (this) {
            CLOCK_STACK -> ClockStackWidget::class.java
            CLOCK_THIN -> ClockThinWidget::class.java
            CLOCK_PILL -> ClockPillWidget::class.java
            CLOCK_ANALOG -> ClockAnalogWidget::class.java
            CLOCK_TILES -> ClockTilesWidget::class.java
            CLOCK_DATE -> ClockDateWidget::class.java
            WEATHER -> WeatherWidget::class.java
            WEATHER_FORECAST -> WeatherForecastWidget::class.java
            CLOCK_WEATHER -> ClockWeatherWidget::class.java
        }

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** Widget look settings, edited from the Widgets tab in the app. */
data class WidgetSettings(val solidity: Int, val blur: Boolean, val blurRadius: Int, val units: String) {
    companion object {
        fun load(ctx: Context): WidgetSettings {
            val p = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            return WidgetSettings(
                solidity = p.getInt(Prefs.WIDGET_SOLIDITY, 100),
                blur = p.getBoolean(Prefs.WIDGET_BLUR, false),
                blurRadius = p.getInt(Prefs.WIDGET_BLUR_RADIUS, 24),
                units = p.getString(Prefs.WEATHER_UNITS, "c") ?: "c",
            )
        }
    }
}

// ── Providers (one per widget in the launcher's widget list) ─────────────────

abstract class SidebarWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WidgetUpdater.executor.execute {
            try {
                if (kind.weather && Weather.isStale(ctx)) Weather.fetch(ctx)
                ids.forEach { WidgetUpdater.update(ctx, mgr, it, kind) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        WidgetUpdater.executor.execute { WidgetUpdater.update(ctx, mgr, id, kind) }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { WidgetBackground.forget(ctx, it) }
    }
}

class ClockStackWidget : SidebarWidgetProvider(WidgetKind.CLOCK_STACK)
class ClockThinWidget : SidebarWidgetProvider(WidgetKind.CLOCK_THIN)
class ClockPillWidget : SidebarWidgetProvider(WidgetKind.CLOCK_PILL)
class ClockAnalogWidget : SidebarWidgetProvider(WidgetKind.CLOCK_ANALOG)
class ClockTilesWidget : SidebarWidgetProvider(WidgetKind.CLOCK_TILES)
class ClockDateWidget : SidebarWidgetProvider(WidgetKind.CLOCK_DATE)
class WeatherWidget : SidebarWidgetProvider(WidgetKind.WEATHER)
class WeatherForecastWidget : SidebarWidgetProvider(WidgetKind.WEATHER_FORECAST)
class ClockWeatherWidget : SidebarWidgetProvider(WidgetKind.CLOCK_WEATHER)

// ── Updating ─────────────────────────────────────────────────────────────────

object WidgetUpdater {
    private const val TAG = "SidebarWidgets"
    internal val executor = Executors.newSingleThreadExecutor()

    /** Re-renders every placed widget (colors, background, weather). Safe to call from any thread. */
    @JvmStatic
    fun updateAll(ctx: Context, weatherOnly: Boolean = false) {
        val app = ctx.applicationContext
        executor.execute {
            val mgr = AppWidgetManager.getInstance(app)
            for (kind in WidgetKind.entries) {
                if (weatherOnly && !kind.weather) continue
                for (id in mgr.getAppWidgetIds(ComponentName(app, kind.provider))) update(app, mgr, id, kind)
            }
        }
    }

    fun placedCount(ctx: Context): Int {
        val mgr = AppWidgetManager.getInstance(ctx)
        return WidgetKind.entries.sumOf { mgr.getAppWidgetIds(ComponentName(ctx, it.provider)).size }
    }

    internal fun update(ctx: Context, mgr: AppWidgetManager, id: Int, kind: WidgetKind) {
        try {
            mgr.updateAppWidget(id, WidgetRenderer.render(ctx, mgr, id, kind))
        } catch (e: Exception) {
            Log.w(TAG, "Widget $id (${kind.id}) update failed", e)
        }
    }
}

// ── Rendering ────────────────────────────────────────────────────────────────

internal object WidgetRenderer {
    private var iconFont: Typeface? = null
    private val glyphCache = HashMap<String, Bitmap>()

    /** [preview] = drawn inside the app's gallery: no click handling, estimated blur position. */
    fun render(ctx: Context, mgr: AppWidgetManager, id: Int, kind: WidgetKind, preview: Boolean = false): RemoteViews {
        val rv = RemoteViews(ctx.packageName, kind.layout)
        val (light, dark, follow) = ThemeColors.resolveBoth(ctx)
        val c = Colors(ctx, rv, light, dark, follow)
        val settings = WidgetSettings.load(ctx)

        // Background: optional blurred wallpaper, then the surface tint whose alpha is the
        // "glass ↔ solid" slider. Over real blur the glass end can be more transparent.
        val blur = WidgetBackground.blurFor(ctx, mgr, id, settings)
        if (blur != null) {
            rv.setViewVisibility(R.id.bg_blur, View.VISIBLE)
            rv.setImageViewBitmap(R.id.bg_blur, blur)
        } else {
            rv.setViewVisibility(R.id.bg_blur, View.GONE)
        }
        val s = settings.solidity / 100f
        val alpha = if (blur != null) 0.30f + 0.70f * s else 0.15f + 0.85f * s
        c.tint(R.id.bg_tint) { it.surfaceContainer }
        rv.setInt(R.id.bg_tint, "setImageAlpha", (alpha * 255).roundToInt())

        when (kind) {
            WidgetKind.CLOCK_STACK -> {
                c.text(R.id.hours) { it.primary }
                c.text(R.id.minutes) { it.tertiary }
            }
            WidgetKind.CLOCK_THIN -> {
                c.text(R.id.time) { it.onSurface }
                c.text(R.id.date) { it.primary }
            }
            WidgetKind.CLOCK_PILL -> {
                c.tint(R.id.pill_bg) { it.primaryContainer }
                c.text(R.id.time) { it.onPrimaryContainer }
                c.text(R.id.weekday) { it.onSurface }
                c.text(R.id.date) { it.onSurfaceVariant }
            }
            WidgetKind.CLOCK_ANALOG -> {
                c.clockTint(R.id.analog, "setDialTintList") { it.primaryContainer }
                c.clockTint(R.id.analog, "setHourHandTintList") { it.primary }
                c.clockTint(R.id.analog, "setMinuteHandTintList") { it.onPrimaryContainer }
                c.clockTint(R.id.analog, "setSecondHandTintList") { it.tertiary }
            }
            WidgetKind.CLOCK_TILES -> {
                c.tint(R.id.tile_bg1) { it.primaryContainer }
                c.tint(R.id.tile_bg2) { it.tertiaryContainer }
                c.text(R.id.tile1) { it.onPrimaryContainer }
                c.text(R.id.tile2) { it.onTertiaryContainer }
                c.text(R.id.date) { it.onSurfaceVariant }
            }
            WidgetKind.CLOCK_DATE -> {
                c.text(R.id.weekday) { it.primary }
                c.text(R.id.day) { it.onSurface }
                c.text(R.id.month) { it.onSurface }
                c.text(R.id.time) { it.onSurfaceVariant }
            }
            WidgetKind.WEATHER -> {
                weatherNow(ctx, rv, c, onAccent = false)
            }
            WidgetKind.WEATHER_FORECAST -> {
                weatherNow(ctx, rv, c, onAccent = false)
                c.tint(R.id.days_bg) { it.primaryContainer }
                forecast(ctx, rv, c)
            }
            WidgetKind.CLOCK_WEATHER -> {
                c.text(R.id.time) { it.onSurface }
                c.text(R.id.date) { it.primary }
                c.tint(R.id.pill_bg) { it.primaryContainer }
                weatherNow(ctx, rv, c, onAccent = true)
            }
        }

        if (!preview) rv.setOnClickPendingIntent(android.R.id.background, clickIntent(ctx, id, kind))
        return rv
    }

    private fun weatherNow(ctx: Context, rv: RemoteViews, c: Colors, onAccent: Boolean) {
        val now = Weather.cached(ctx)
        val main: (M3Scheme) -> Int = if (onAccent) { s -> s.onPrimaryContainer } else { s -> s.onSurface }
        val sub: (M3Scheme) -> Int = if (onAccent) { s -> s.onPrimaryContainer } else { s -> s.onSurfaceVariant }
        c.text(R.id.temp, main)
        c.text(R.id.condition, main)
        rv.setImageViewBitmap(R.id.icon, glyph(ctx, if (now != null) Weather.glyph(now.code, now.isDay) else ""))
        c.tint(R.id.icon) { if (onAccent) it.onPrimaryContainer else it.primary }
        if (now == null) {
            rv.setTextViewText(R.id.temp, "--°")
            rv.setTextViewText(R.id.condition, if (onAccent) "" else "Weather")
            if (!onAccent) {
                rv.setTextViewText(R.id.details, "Tap to set up")
                c.text(R.id.details, sub)
            }
            return
        }
        rv.setTextViewText(R.id.temp, "${now.temp.roundToInt()}°")
        rv.setTextViewText(R.id.condition, Weather.label(now.code))
        if (!onAccent) {
            val range = if (now.max.isNaN()) "" else "↑${now.max.roundToInt()}° ↓${now.min.roundToInt()}°"
            rv.setTextViewText(R.id.details, listOf(now.city, range).filter { it.isNotEmpty() }.joinToString(" · "))
            c.text(R.id.details, sub)
        }
    }

    private fun forecast(ctx: Context, rv: RemoteViews, c: Colors) {
        val days = Weather.cached(ctx)?.days?.drop(1)?.take(4) ?: emptyList()
        val ids = listOf(
            intArrayOf(R.id.d0_name, R.id.d0_icon, R.id.d0_max, R.id.d0_min),
            intArrayOf(R.id.d1_name, R.id.d1_icon, R.id.d1_max, R.id.d1_min),
            intArrayOf(R.id.d2_name, R.id.d2_icon, R.id.d2_max, R.id.d2_min),
            intArrayOf(R.id.d3_name, R.id.d3_icon, R.id.d3_max, R.id.d3_min),
        )
        ids.forEachIndexed { i, (name, icon, max, min) ->
            val d = days.getOrNull(i)
            rv.setTextViewText(name, d?.shortName ?: "—")
            rv.setTextViewText(max, d?.let { "${it.max.roundToInt()}°" } ?: "")
            rv.setTextViewText(min, d?.let { "${it.min.roundToInt()}°" } ?: "")
            rv.setImageViewBitmap(icon, glyph(ctx, if (d != null) Weather.glyph(d.code, true) else ""))
            c.text(name) { it.onPrimaryContainer }
            c.text(max) { it.onPrimaryContainer }
            c.text(min) { it.onPrimaryContainer }
            c.tint(icon) { it.primary }
        }
    }

    private fun clickIntent(ctx: Context, id: Int, kind: WidgetKind): PendingIntent {
        val intent = Intent(ctx, WidgetActivity::class.java).apply {
            action = WidgetActivity.ACTION_CLICK
            data = Uri.parse("sidebar-widget://$id") // keeps one PendingIntent per widget
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(WidgetActivity.EXTRA_KIND, kind.id)
        }
        // Mutable so the launcher can fill in the tap's source bounds (used to align the blur).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getActivity(ctx, id, intent, flags)
    }

    /** White glyph from the bundled Material Icons font; tinted per view via a color filter. */
    @Synchronized
    private fun glyph(ctx: Context, glyph: String): Bitmap = glyphCache.getOrPut(glyph) {
        val font = iconFont ?: Typeface.createFromAsset(ctx.assets, "fonts/MaterialIcons-Regular.ttf").also { iconFont = it }
        val size = (48 * ctx.resources.displayMetrics.density).roundToInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = size.toFloat()
        }
        val fm = paint.fontMetrics
        Canvas(bmp).drawText(glyph, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint)
        bmp
    }

    /**
     * Applies scheme colors. With theme "Auto" on Android 12+, both light and dark colors are
     * sent and the launcher picks per its night mode, so widgets flip with the system theme.
     */
    private class Colors(
        private val ctx: Context,
        private val rv: RemoteViews,
        private val light: M3Scheme,
        private val dark: M3Scheme,
        private val follow: Boolean
    ) {
        private val dayNight = follow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        private val current: M3Scheme
            get() {
                if (!follow) return light
                val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                return if (night) dark else light
            }

        fun text(id: Int, role: (M3Scheme) -> Int) {
            if (dayNight) rv.setColorInt(id, "setTextColor", role(light), role(dark))
            else rv.setTextColor(id, role(current))
        }

        fun tint(id: Int, role: (M3Scheme) -> Int) {
            if (dayNight) rv.setColorInt(id, "setColorFilter", role(light), role(dark))
            else rv.setInt(id, "setColorFilter", role(current))
        }

        fun clockTint(id: Int, method: String, role: (M3Scheme) -> Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            if (dayNight) rv.setColorStateList(id, method,
                ColorStateList.valueOf(role(light)), ColorStateList.valueOf(role(dark)))
            else rv.setColorStateList(id, method, ColorStateList.valueOf(role(current)))
        }
    }
}
