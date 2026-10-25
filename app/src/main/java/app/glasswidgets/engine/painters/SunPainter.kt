package app.glasswidgets.engine.painters

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.glasswidgets.engine.Col
import app.glasswidgets.engine.Draw
import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Painter
import app.glasswidgets.engine.Scene
import app.glasswidgets.engine.Sun
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * WSun.qml — the day's arc from sunrise to sunset with the sun on it now; at night the moon and
 * time to sunrise. One row — only the next event. Computed locally, no network.
 */
object SunPainter : Painter {
    private fun dur(x: Double): String {
        val m = (x * 60).roundToInt()
        val hh = m / 60
        val mm = m % 60
        return if (hh > 0) "$hh" + I18n.t(" ч ", "h ") + "$mm" + I18n.t(" мин", "m") else "$mm" + I18n.t(" мин", " min")
    }

    override fun paint(s: Scene) {
        val (lat, lon) = s.place
        val t = Sun.times(s.now, lat, lon)
        val h = Sun.hourOf(s.now)
        val day = h >= t[0] && h < t[1]
        val frac = ((h - t[0]) / (t[1] - t[0])).coerceIn(0.0, 1.0)
        val row = s.H < 150
        val until = if (day) t[1] - h else if (h < t[0]) t[0] - h else t[0] + 24 - h

        val ws = s.ink()
        s.surface(s.full, ws, radius = min(44f, s.H / 2))
        val d = s.d
        val c = s.c

        if (row) {
            val cy = s.H / 2
            d.icon(c, if (day) "wb_twilight" else "wb_sunny", 22f + 17, cy, 34f, ws.accent)
            val x = 22f + 34 + 12
            val h1 = d.lineHeight(17f, 700)
            val h2 = d.lineHeight(13f, 550)
            val top = cy - (h1 + h2) / 2
            d.text(c, if (day) I18n.t("Закат ", "Sunset ") + Sun.hm(t[1]) else I18n.t("Восход ", "Sunrise ") + Sun.hm(t[0]),
                x, top + h1 / 2, 17f, 700, ws.fg)
            d.text(c, I18n.t("через ", "in ") + dur(until), x, top + h1 + h2 / 2, 13f, 550, ws.fgDim)
            return
        }

        // arc
        val left = 22f
        val top = 26f
        val width = s.W - 44f
        val height = s.H - 92f
        val cx = left + width / 2
        val by = top + height
        val rx = width / 2
        val ry = min(height - 8, width / 2)
        val oval = RectF(cx - rx, by - ry, cx + rx, by + ry)
        val dashed = s.paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f; color = Col.alpha(ws.fg, 0.28f)
            pathEffect = DashPathEffect(floatArrayOf(2 * 2.5f, 3 * 2.5f), 0f)
        }
        c.drawArc(oval, 180f, 180f, false, dashed)
        if (day && frac > 0) s.stroke(Path().apply { addArc(oval, 180f, (180 * frac).toFloat()) }, ws.accent, 4f)
        s.roundRect(RectF(left - 6, by - 1, left + width + 6, by + 1), 1f, Col.alpha(ws.fg, 0.35f))

        val (bx, byy) = if (day) {
            val a = PI * (1 - frac)
            (cx + rx * cos(a)).toFloat() to (by - ry * sin(a)).toFloat()
        } else cx to by - ry * 0.55f
        if (day) {
            s.circle(bx, byy, 27f, Col.alpha(ws.accent, 0.22f))
            s.circle(bx, byy, 13f, ws.accent)
        } else {
            d.icon(c, "bedtime", bx, byy, 30f, ws.accent)
        }

        // bottom: sunrise — time left — sunset
        val bTop = s.H - 16f - 40f
        val l1 = d.lineHeight(15f, 700)
        val l2 = d.lineHeight(11f, 550)
        d.text(c, Sun.hm(t[0]), 20f, bTop + l1 / 2, 15f, 700, ws.fg)
        d.text(c, I18n.t("восход", "sunrise"), 20f, bTop + l1 + l2 / 2, 11f, 550, ws.fgDim)
        d.text(c, Sun.hm(t[1]), s.W - 20f, bTop + l1 / 2, 15f, 700, ws.fg, Draw.Align.RIGHT)
        d.text(c, I18n.t("закат", "sunset"), s.W - 20f, bTop + l1 + l2 / 2, 11f, 550, ws.fgDim, Draw.Align.RIGHT)
        if (s.W >= 330) {
            val m1 = d.lineHeight(13f, 700)
            d.text(c, dur(until), s.W / 2, bTop + m1 / 2, 13f, 700, ws.accent, Draw.Align.CENTER)
            d.text(c, if (day) I18n.t("до заката", "to sunset") else I18n.t("до восхода", "to sunrise"), s.W / 2, bTop + m1 + l2 / 2, 11f, 550, ws.fgDim, Draw.Align.CENTER)
        }
    }
}
