package app.glasswidgets.engine.painters

import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import app.glasswidgets.engine.Col
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
 * WForecast.qml — hourly forecast as a smooth temperature curve (like Pixel weather): degrees
 * over the points, icons and hours under them; how many hours — by width; three rows add days.
 */
object ForecastPainter : Painter {
    override fun paint(s: Scene) {
        val tall = s.H >= 300
        val n = floor((s.W - 40) / 62).toInt().coerceIn(4, 12)
        val step = if (s.W >= 700) 1 else 2
        val w = s.weather
        val pts = w?.hours?.filterIndexed { i, _ -> i % step == 0 }?.take(n) ?: emptyList()
        val tMin = pts.minOfOrNull { it.temp } ?: 0.0
        val tMax = pts.maxOfOrNull { it.temp } ?: 1.0

        val ws = s.ink()
        s.surface(s.full, ws)
        val d = s.d
        val c = s.c

        // head
        val headH = max(22f, d.lineHeight(15f, 700))
        val headCy = 16f + headH / 2
        d.icon(c, if (w != null) Weather.icon(w.code, w.isDay) else "cloud_off", 22f + 11, headCy, 22f, ws.accent)
        d.text(c, if (w != null) "${w.temp.roundToInt()}° · ${Weather.describe(w.code)}" else I18n.t("Нет данных", "No data"),
            22f + 22 + 8, headCy, 15f, 700, ws.fg, maxWidth = s.W - 120)
        if (w != null && s.W >= 380) d.text(c, "↑${w.tMax.roundToInt()}°  ↓${w.tMin.roundToInt()}°", s.W - 22f, headCy, 13f, 600, ws.fgDim, Draw.Align.RIGHT)

        if (pts.size < 2) {
            d.text(c, I18n.t("Прогноз загружается…", "Loading forecast…"), s.W / 2, s.H / 2, 14f, 500, ws.fgDim, Draw.Align.CENTER)
            return
        }

        // the curve
        val left = 20f
        val top = 16f + headH + 6f
        val width = s.W - 40f
        val height = if (tall) s.H - headH - 140 else s.H - headH - 28
        val colW = width / pts.size
        val yTop = top + 26
        val yBot = top + height - 52
        fun px(i: Int) = left + colW * (i + 0.5f)
        fun py(t: Double) = if (tMax - tMin < 0.5) (yTop + yBot) / 2 else (yBot - (t - tMin) / (tMax - tMin) * (yBot - yTop)).toFloat()

        // Catmull-Rom → smooth polyline
        val p = pts.mapIndexed { i, q -> PointF(px(i), py(q.temp)) }
        val curve = Path()
        curve.moveTo(p[0].x, p[0].y)
        for (i in 0 until p.size - 1) {
            val p0 = p[max(0, i - 1)]; val p1 = p[i]; val p2 = p[i + 1]; val p3 = p[min(p.size - 1, i + 2)]
            for (k in 1..12) {
                val t = k / 12f; val t2 = t * t; val t3 = t2 * t
                curve.lineTo(
                    0.5f * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
                    0.5f * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
                )
            }
        }
        // fill under the curve
        val area = Path(curve).apply {
            lineTo(px(pts.size - 1), yBot + 10)
            lineTo(px(0), yBot + 10)
            close()
        }
        val fill = s.paint().apply {
            shader = LinearGradient(0f, yTop, 0f, yBot + 10, Col.alpha(ws.accent, 0.32f), Col.alpha(ws.accent, 0f), Shader.TileMode.CLAMP)
        }
        c.drawPath(area, fill)
        s.stroke(curve, ws.accent, 3f)

        pts.forEachIndexed { i, q ->
            val x = px(i)
            val y0 = py(q.temp)
            s.circle(x, y0, 5f, ws.accent)
            if (i != 0) s.circle(x, y0, 5f - 2.5f, ws.container)
            d.text(c, "${q.temp.roundToInt()}°", x, y0 - 30 + d.lineHeight(14f, 700) / 2, 14f, 700, ws.fg, Draw.Align.CENTER)
            d.icon(c, Weather.icon(q.code, q.day), x, yBot + 12 + 10, 20f, ws.accent)
            d.text(c, if (i == 0) I18n.t("Сейчас", "Now") else q.hour, x, yBot + 34 + d.lineHeight(12f, 600) / 2, 12f, 600, ws.fgDim, Draw.Align.CENTER)
        }

        // days (three rows)
        if (tall && w != null && w.days.size > 1) {
            val l = 20f
            val wd = s.W - 40f
            val nd = floor(wd / 64).toInt().coerceIn(2, 7)
            val cw = wd / nd
            val boxTop = s.H - 18f - 100f
            w.days.take(nd).forEachIndexed { i, day ->
                val bx = l + cw * i
                s.roundRect(RectF(bx, boxTop, bx + cw - 6, boxTop + 100), 22f, if (i == 0) Col.alpha(ws.accent, 0.18f) else ws.chip)
                val cx = bx + (cw - 6) / 2
                val h1 = d.lineHeight(12f, 650)
                val h3 = d.lineHeight(13f, 650)
                val colH = h1 + 4 + 24 + 4 + h3
                var y = boxTop + (100 - colH) / 2
                d.text(c, if (i == 0) I18n.t("Сег.", "Today") else I18n.dayShort(day.date), cx, y + h1 / 2, 12f, 650,
                    if (i == 0) ws.accent else ws.fgDim, Draw.Align.CENTER)
                y += h1 + 4
                d.icon(c, Weather.icon(day.code, true), cx, y + 12, 24f, ws.accent)
                y += 28
                d.text(c, "${day.max.roundToInt()}° ${day.min.roundToInt()}°", cx, y + h3 / 2, 13f, 650, ws.fg, Draw.Align.CENTER)
            }
        }
    }
}
