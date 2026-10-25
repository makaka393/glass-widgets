package app.glasswidgets.engine.painters

import android.graphics.RectF
import app.glasswidgets.engine.Col
import app.glasswidgets.engine.Draw
import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Painter
import app.glasswidgets.engine.Scene
import java.util.Calendar
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * WCalendar.qml + CalendarCard.qml: small — today's date large (weekday, day, month) like on
 * Pixel; from 3×3 — the month as a grid, today highlighted, weeks from Monday.
 */
object CalendarPainter : Painter {
    override fun paint(s: Scene) {
        val month = s.W >= 300 && s.H >= 300
        val ws = s.ink()
        s.surface(s.full, ws, radius = 40f)
        if (month) monthGrid(s, ws) else today(s, ws)
    }

    private fun today(s: Scene, ws: app.glasswidgets.engine.Ink) {
        val k = min(s.W, s.H) / 208f
        val dz = (18 * k).roundToInt().toFloat()
        val nz = (96 * k).roundToInt().toFloat()
        val mz = (16 * k).roundToInt().toFloat()
        val dh = s.d.lineHeight(dz, 700)
        val nh = s.d.lineHeight(nz, 620)
        val mh = s.d.lineHeight(mz, 600)
        val total = dh + nh + mh - 8
        var y = (s.H - total) / 2
        val cx = s.W / 2
        s.d.text(s.c, I18n.dayName(s.now), cx, y + dh / 2, dz, 700, ws.accent, Draw.Align.CENTER)
        y += dh - 4
        s.d.text(s.c, s.now.get(Calendar.DAY_OF_MONTH).toString(), cx, y + nh / 2, nz, 620, ws.fg, Draw.Align.CENTER)
        y += nh - 4
        s.d.text(s.c, I18n.monthName(s.now), cx, y + mh / 2, mz, 600, ws.fgDim, Draw.Align.CENTER)
    }

    private fun monthGrid(s: Scene, ws: app.glasswidgets.engine.Ink) {
        val ink = ws.fg
        val inkDim = ws.fgDim
        val accent = ws.accent
        val weekend = Col.legible(s.t.tertiary, ws.seen, 4.5)
        val todayInk = ws.fillInk

        val left = 16f
        val innerW = s.W - 32f
        var y = 12f
        // title
        s.d.text(s.c, I18n.monthYear(s.now), left + 6, y + 18, 17f, 700, ink)
        y += 36 + 6
        // weekdays, Monday first
        val colW = innerW / 7
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
        for (i in 0 until 7) {
            s.d.text(s.c, I18n.dayShort(cal), left + colW * (i + 0.5f), y + 11, 12f, 600,
                if (i >= 5) weekend else inkDim, Draw.Align.CENTER)
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        y += 22 + 6
        // days
        val first = (s.now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = s.now.get(Calendar.DAY_OF_MONTH)
        val cell = 38f
        val rows = if (lead + days > 35) 6 else 5
        for (i in 0 until rows * 7) {
            val n = i - lead + 1
            if (n < 1 || n > days) continue
            val cx = left + colW * (i % 7 + 0.5f)
            val cy = y + cell * (i / 7) + cell / 2
            val isToday = n == today
            if (isToday) s.roundRect(RectF(cx - 17, cy - 17, cx + 17, cy + 17), 12f, accent)
            s.d.text(s.c, n.toString(), cx, cy, 14f, if (isToday) 750 else 550,
                if (isToday) todayInk else if (i % 7 >= 5) weekend else ink, Draw.Align.CENTER)
        }
    }
}
