package app.glasswidgets.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Updater
import app.glasswidgets.engine.Weather
import app.glasswidgets.engine.WidgetStore
import app.glasswidgets.engine.WType
import app.glasswidgets.engine.painters.ProgressPainter

/**
 * Invisible trampoline for widget taps. The tap tells us where the widget is (its blur lines up
 * with the wallpaper from then on), then does the widget's action:
 * clock → alarms, glance/calendar → calendar, day & year → next period, weather → refresh.
 */
class TapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val type = WType.byId(intent.getStringExtra(EXTRA_TYPE))
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) intent.sourceBounds?.let { WidgetStore.putBounds(this, id, it) }

        when (type) {
            WType.CLOCK -> open(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            WType.GLANCE, WType.CALENDAR ->
                open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
            WType.PROGRESS -> {
                val cfg = WidgetStore.get(this, id, type)
                val (w, h) = Updater.sizeDp(AppWidgetManager.getInstance(this), id, type)
                val area = w * h * app.glasswidgets.engine.Renderer.DESIGN_PER_DP * app.glasswidgets.engine.Renderer.DESIGN_PER_DP
                val cur = cfg.mode ?: if (area > 260 * 260) "year" else "month"
                val next = ProgressPainter.MODES[(ProgressPainter.MODES.indexOf(cur) + 1) % ProgressPainter.MODES.size]
                WidgetStore.put(this, id, cfg.copy(mode = next))
            }
            WType.WEATHER, WType.FORECAST, WType.SUN -> refreshWeather()
            null -> {}
        }
        Updater.all(applicationContext)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun open(i: Intent) {
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun refreshWeather() {
        if (!Weather.hasLocationPermission(this)) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        Toast.makeText(this, I18n.t("Обновляю погоду…", "Updating weather…"), Toast.LENGTH_SHORT).show()
        // take the location now, while this activity counts as foreground
        val loc = Weather.lastKnownLocation(this)
        val app = applicationContext
        Thread {
            loc?.let { Weather.saveLocation(app, it) }
            Weather.fetch(app)
            Updater.all(app)
        }.start()
    }

    companion object {
        const val EXTRA_TYPE = "type"
    }
}
