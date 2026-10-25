package app.glasswidgets.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import app.glasswidgets.MainActivity

/**
 * Invisible trampoline for widget taps and "customize widget". A tap records the widget's
 * on-screen bounds (so its blur lines up with the wallpaper), then opens the clock app or
 * refreshes the weather. Being an activity, it may read the location while in the foreground.
 */
class WidgetActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            openSettings()
            finish()
            return
        }

        intent.sourceBounds?.let { if (id != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetBackground.saveBounds(this, id, it) }
        val kind = WidgetKind.byId(intent.getStringExtra(EXTRA_KIND))

        if (kind?.weather == true) {
            if (!Weather.hasLocationPermission(this) && !Weather.hasCoordinates(this)) {
                openSettings()
            } else {
                Toast.makeText(this, "Updating weather…", Toast.LENGTH_SHORT).show()
                // Take the location now, while this activity counts as foreground.
                val location = Weather.lastKnownLocation(this)
                val app = applicationContext
                Thread {
                    location?.let { Weather.saveLocation(app, it) }
                    Weather.fetch(app)
                    WidgetUpdater.updateAll(app, weatherOnly = true)
                }.start()
            }
        } else {
            runCatching {
                startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        // Re-render so a fresh tap position shows up in the blur right away.
        WidgetUpdater.updateAll(applicationContext)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    companion object {
        const val ACTION_CLICK = "app.glasswidgets.CLICK"
        const val EXTRA_KIND = "kind"
    }
}
