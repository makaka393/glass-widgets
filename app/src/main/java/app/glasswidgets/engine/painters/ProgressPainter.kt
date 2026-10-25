package app.glasswidgets.engine.painters

import app.glasswidgets.engine.Col
import app.glasswidgets.engine.Draw
import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Painter
import app.glasswidgets.engine.Scene
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * WProgress.qml — the day, month or year as dots: past in the theme colour, today (or this
 * hour) glowing, ahead faint. A tap switches the period.
 */
object ProgressPainter : Painter {
    val MODES = listOf("year", "month", "day")

    fun mode(s: Scene) = s.cfg.mode?.takeIf { it in MODES } ?: if (s.W * s.H > 260 * 260) "year" else "month"

    override fun paint(s: Scene) {
        val mode = mode(s)
        val now = s.now
        val year = now.get(Calendar.YEAR)
        val yearDays = now.getActualMaximum(Calendar.DAY_OF_YEAR)
        val dayOfYear = now.get(Calendar.DAY_OF_YEAR)
        val monthDays = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val total = when (mode) { "year" -> yearDays; "month" -> monthDays; else -> 24 }
        val current = when (mode) { "year" -> dayOfYear; "month" -> now.get(Calendar.DAY_OF_MONTH); else -> now.get(Calendar.HOUR_OF_DAY) + 1 }
        val pct = if (mode == "day") minutes / 1440.0 else (current - 1 + minutes / 1440.0) / total
        val title = when (mode) { "year" -> year.toString(); "month" -> I18n.monthName(now); else -> I18n.t("Сегодня", "Today") }
        val remain = if (mode == "day") "${24 - current}" + I18n.t(" ч осталось", "h left")
                     else "${total - current}" + I18n.t(" дн. осталось", " days left")

        val ws = s.ink()
        s.surface(s.full, ws, radius = 40f)

        // head
        val headCy = 16f + 13f
        s.d.text(s.c, title, 20f, headCy, 15f, 750, ws.fg)
        s.d.text(s.c, "${floor(pct * 100).toInt()}%", s.W - 20f, headCy, 15f, 750, ws.accent, Draw.Align.RIGHT)

        val footH = if (s.H >= 200) 20f else 0f
        if (footH > 0) s.d.text(s.c, remain, 20f, s.H - 12f - footH / 2, 12f, 600, ws.fgDim)

        // field: as many columns as make the dots biggest
        val fl = 18f
        val ft = 16f + 26f + 10f
        val fr = s.W - 18f
        val fb = s.H - 12f - footH - 6f
        val fw = fr - fl
        val fh = fb - ft
        var bestC = 1; var bestR = total; var bestP = 0f
        for (c in 1..total) {
            val r = ceil(total / c.toDouble()).toInt()
            val p = min(fw / c, fh / r)
            if (p > bestP) { bestP = p; bestC = c; bestR = r }
        }
        val gx = fl + (fw - bestC * bestP) / 2
        val gy = ft + (fh - bestR * bestP) / 2
        for (i in 0 until total) {
            val n = i + 1
            val cx = gx + (i % bestC + 0.5f) * bestP
            val cy = gy + (i / bestC + 0.5f) * bestP
            if (n == current) s.circle(cx, cy, bestP * 1.05f / 2, Col.alpha(ws.accent, 0.28f))
            val d = bestP * if (n == current) 0.78f else 0.62f
            s.circle(cx, cy, d / 2, when {
                n < current -> ws.accent
                n == current -> ws.fg
                else -> Col.alpha(ws.fg, 0.16f)
            })
        }
    }
}
