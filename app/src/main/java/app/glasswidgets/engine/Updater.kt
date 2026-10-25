package app.glasswidgets.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import app.glasswidgets.R
import app.glasswidgets.ui.TapActivity
import java.util.concurrent.Executors

// ── Providers: one per widget in the launcher's list ─────────────────────────

abstract class GlassProvider(private val type: WType) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Updater.executor.execute {
            try {
                if (type.weather && Weather.isStale(ctx)) Weather.fetch(ctx)
                ids.forEach { Updater.update(ctx, mgr, it, type) }
                Ticker.schedule(ctx)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        Updater.executor.execute { Updater.update(ctx, mgr, id, type) }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { WidgetStore.forget(ctx, it) }
    }

    override fun onDisabled(ctx: Context) = Ticker.schedule(ctx)
}

class ClockWidget : GlassProvider(WType.CLOCK)
class GlanceWidget : GlassProvider(WType.GLANCE)
class CalendarWidget : GlassProvider(WType.CALENDAR)
class ProgressWidget : GlassProvider(WType.PROGRESS)
class WeatherWidget : GlassProvider(WType.WEATHER)
class ForecastWidget : GlassProvider(WType.FORECAST)
class SunWidget : GlassProvider(WType.SUN)

val WType.provider: Class<out AppWidgetProvider>
    get() = when (this) {
        WType.CLOCK -> ClockWidget::class.java
        WType.GLANCE -> GlanceWidget::class.java
        WType.CALENDAR -> CalendarWidget::class.java
        WType.PROGRESS -> ProgressWidget::class.java
        WType.WEATHER -> WeatherWidget::class.java
        WType.FORECAST -> ForecastWidget::class.java
        WType.SUN -> SunWidget::class.java
    }

// ── Updating ─────────────────────────────────────────────────────────────────

object Updater {
    private const val TAG = "GlassUpdater"
    val executor = Executors.newSingleThreadExecutor()

    fun typeOf(ctx: Context, id: Int): WType? {
        val cls = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider?.className ?: return null
        return WType.entries.firstOrNull { it.provider.name == cls }
    }

    fun placed(ctx: Context): List<Pair<Int, WType>> {
        val mgr = AppWidgetManager.getInstance(ctx)
        return WType.entries.flatMap { t -> mgr.getAppWidgetIds(ComponentName(ctx, t.provider)).map { it to t } }
    }

    /** Repaints every placed widget, off the main thread. */
    fun all(ctx: Context, weatherOnly: Boolean = false) {
        val app = ctx.applicationContext
        executor.execute { renderAll(app, weatherOnly) }
    }

    fun renderAll(ctx: Context, weatherOnly: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for ((id, type) in placed(ctx)) if (!weatherOnly || type.weather) update(ctx, mgr, id, type)
    }

    /** Widget size in dp, as the launcher shows it in portrait. */
    fun sizeDp(mgr: AppWidgetManager, id: Int, type: WType): Pair<Float, Float> {
        val o = mgr.getAppWidgetOptions(id)
        var w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toFloat()
        var h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).toFloat()
        if (w <= 0f) w = defaultDp(type.w)
        if (h <= 0f) h = defaultDp(type.h)
        return w to h
    }

    /** A Linux grid size in cells → dp (a cell is 96 px + a 16 px gap). */
    fun defaultDp(cells: Int) = (cells * 112 - 16) / Renderer.DESIGN_PER_DP

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, type: WType) {
        try {
            val cfg = WidgetStore.get(ctx, id, type)
            val (w, h) = sizeDp(mgr, id, type)
            val bmp = Renderer.render(ctx, cfg, w, h, WidgetStore.bounds(ctx, id))
            val rv = RemoteViews(ctx.packageName, R.layout.widget_bitmap)
            rv.setImageViewBitmap(R.id.image, bmp)
            rv.setContentDescription(R.id.image, type.label)
            rv.setOnClickPendingIntent(R.id.image, tapIntent(ctx, id, type))
            mgr.updateAppWidget(id, rv)
        } catch (e: Exception) {
            Log.w(TAG, "widget $id (${type.id}) failed", e)
        }
    }

    private fun tapIntent(ctx: Context, id: Int, type: WType): PendingIntent {
        val intent = Intent(ctx, TapActivity::class.java).apply {
            data = Uri.parse("glasswidgets://$id")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(TapActivity.EXTRA_TYPE, type.id)
        }
        // Mutable so the launcher can fill in where the tap was (the widget's bounds — for the blur).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getActivity(ctx, id, intent, flags)
    }
}

// ── Once a minute, while the screen is on ────────────────────────────────────

/**
 * Clocks are bitmaps, so they're redrawn on every minute. The alarm is not a wake-up one: while
 * the phone sleeps it waits, and fires (once) as soon as the screen comes back.
 */
object Ticker {
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, TickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Updater.placed(ctx).isEmpty()) {
            am.cancel(pi)
            return
        }
        val next = (System.currentTimeMillis() / 60_000 + 1) * 60_000 + 200
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setWindow(AlarmManager.RTC, next, 30_000, pi)
        } else {
            am.setExact(AlarmManager.RTC, next, pi)
        }
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Updater.executor.execute {
            try {
                if (Updater.placed(ctx).any { it.second.weather } && Weather.isStale(ctx)) Weather.fetch(ctx)
                Updater.renderAll(ctx)
                Ticker.schedule(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Boot, time or time-zone change, language change, app update: redraw and restart the minute tick. */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Updater.executor.execute {
            try {
                Updater.renderAll(ctx)
                Ticker.schedule(ctx)
            } finally {
                pending.finish()
            }
        }
    }
}
