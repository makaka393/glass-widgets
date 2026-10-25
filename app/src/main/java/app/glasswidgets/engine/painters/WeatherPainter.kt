package app.glasswidgets.engine.painters

import android.graphics.RectF
import app.glasswidgets.engine.Draw
import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Painter
import app.glasswidgets.engine.Scene
import app.glasswidgets.engine.Weather
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * WWeather.qml — weather like on Pixel. One row — a pill "icon · temperature · description";
 * square — big temperature, icon and the day's range; wider — hourly too; three rows — daily.
 */
object WeatherPainter : Painter {
    override fun paint(s: Scene) {
        val row = s.H < 150
        val wide = !row && s.W >= 420
        val tall = !row && s.H >= 300
        val ws = s.ink()
        s.surface(s.full, ws, radius = min(44f, s.H / 2))
        val w = s.weather
        val d = s.d
        val c = s.c

        if (row) {
            var x = 22f
            val cy = s.H / 2
            d.icon(c, if (w != null) Weather.icon(w.code, w.isDay) else "cloud_off", x + 18, cy, 36f, ws.accent)
            x += 36 + 12
            x += d.text(c, if (w != null) "${w.temp.roundToInt()}°" else "—", x, cy, 34f, 500, ws.fg) + 12
            if (s.W >= 300) {
                val h1 = d.lineHeight(15f, 650)
                val h2 = if (w != null) d.lineHeight(13f, 550) else 0f
                val top = cy - (h1 + h2) / 2
                d.text(c, if (w != null) Weather.describe(w.code) else I18n.t("Нет данных", "No data"), x, top + h1 / 2, 15f, 650, ws.fg, maxWidth = s.W - x - 16)
                if (w != null) d.text(c, "↑${w.tMax.roundToInt()}°  ↓${w.tMin.roundToInt()}°", x, top + h1 + h2 / 2, 13f, 550, ws.fgDim)
            }
            return
        }

        // current weather (the square, or the left part of a wide one)
        val nowW = if (wide) 210f else s.W
        val nowH = if (tall) 208f else s.H
        d.icon(c, if (w != null) Weather.icon(w.code, w.isDay) else "cloud_off", 20f + 23, 18f + 23, 46f, ws.accent)
        d.text(c, if (w != null) "${w.temp.roundToInt()}°" else "—", nowW - 20f, 14f + d.lineHeight(50f, 450) / 2, 50f, 450, ws.fg, Draw.Align.RIGHT)
        val l1 = d.lineHeight(15f, 650)
        val l2 = d.lineHeight(13f, 550)
        val bottom = nowH - 18f
        val city = w?.city ?: ""
        if (w != null) {
            d.text(c, "↑${w.tMax.roundToInt()}°  ↓${w.tMin.roundToInt()}°" + (if (city.isNotEmpty()) "  ·  $city" else ""),
                20f, bottom - l2 / 2, 13f, 550, ws.fgDim, maxWidth = nowW - 40)
            d.text(c, Weather.describe(w.code), 20f, bottom - l2 - 2 - l1 / 2, 15f, 650, ws.fg, maxWidth = nowW - 40)
        } else {
            d.text(c, I18n.t("Нет данных", "No data"), 20f, bottom - l1 / 2, 15f, 650, ws.fg, maxWidth = nowW - 40)
        }

        // hourly (right side of a wide widget)
        if (wide && w != null && w.hourly.isNotEmpty()) {
            val left = nowW
            val width = s.W - 18f - left
            val n = floor(width / 64).toInt().coerceIn(2, 6)
            val colW = width / n
            w.hourly.take(n).forEachIndexed { i, h ->
                val cx = left + colW * (i + 0.5f)
                val top = 22f
                val lh = d.lineHeight(13f, 600)
                d.text(c, h.hour, cx, top + lh / 2, 13f, 600, ws.fgDim, Draw.Align.CENTER)
                val chipTop = top + lh + 8
                s.roundRect(RectF(cx - 23, chipTop, cx + 23, chipTop + 72), 23f, ws.chip)
                d.icon(c, Weather.icon(h.code, h.day), cx, chipTop + 8 + 13, 26f, ws.accent)
                d.text(c, "${h.temp.roundToInt()}°", cx, chipTop + 72 - 10 - d.lineHeight(15f, 650) / 2, 15f, 650, ws.fg, Draw.Align.CENTER)
            }
        }

        // daily (bottom of a tall widget)
        if (tall && w != null && w.days.size > 1) {
            val left = 18f
            val width = s.W - 36f
            val n = floor(width / 58).toInt().coerceIn(2, 7)
            val colW = width / n
            val h1 = d.lineHeight(12f, 650)
            val h3 = d.lineHeight(14f, 650)
            val h4 = d.lineHeight(12f, 550)
            val colH = h1 + 4 + 24 + 4 + h3 + 4 + h4
            var y0 = s.H - 20f - colH
            w.days.take(n).forEachIndexed { i, day ->
                val cx = left + colW * (i + 0.5f)
                var y = y0
                d.text(c, if (i == 0) I18n.t("Сег.", "Today") else I18n.dayShort(day.date), cx, y + h1 / 2, 12f, 650,
                    if (i == 0) ws.accent else ws.fgDim, Draw.Align.CENTER)
                y += h1 + 4
                d.icon(c, Weather.icon(day.code, true), cx, y + 12, 24f, ws.accent)
                y += 24 + 4
                d.text(c, "${day.max.roundToInt()}°", cx, y + h3 / 2, 14f, 650, ws.fg, Draw.Align.CENTER)
                y += h3 + 4
                d.text(c, "${day.min.roundToInt()}°", cx, y + h4 / 2, 12f, 550, ws.fgDim, Draw.Align.CENTER)
            }
            y0 = max(y0, 0f)
        }
    }
}
