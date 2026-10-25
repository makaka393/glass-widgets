package app.glasswidgets.engine.painters

import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Painter
import app.glasswidgets.engine.Scene
import app.glasswidgets.engine.Sun
import app.glasswidgets.engine.Weather
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * WGlance.qml — "At a glance" like on Pixel: the date and weather right on the wallpaper (a
 * surface can be turned on in Customize). Taller widget — bigger text; two rows add the next
 * sunrise or sunset.
 */
object GlancePainter : Painter {
    override fun paint(s: Scene) {
        val k = (s.H / 96f).coerceIn(0.8f, 1.7f)
        val ws = s.ink()
        s.surface(s.full, ws, radius = min(40f, s.H / 2))

        val left = if (ws.bare) 8f else 26f
        val width = s.W - left - 12f
        val gap = 4 * k
        val w = s.weather

        val dateText = I18n.longDate(s.now)
        val dateSize = s.d.fit(dateText, min(30 * k, s.W / 11).roundToInt().toFloat(), 600, width, 16f)
        val dateH = s.d.lineHeight(dateSize, 600)

        val wxIcon = (24 * k).roundToInt().toFloat()
        val wxSize = (18 * k).roundToInt().toFloat()
        val wxH = if (w != null) max(wxIcon, s.d.lineHeight(wxSize, 550)) else 0f

        val (lat, lon) = s.place
        val sun = Sun.times(s.now, lat, lon)
        val hourNow = Sun.hourOf(s.now)
        val showSun = s.H >= 180
        val sunIcon = (20 * k).roundToInt().toFloat()
        val sunSize = (16 * k).roundToInt().toFloat()
        val sunH = if (showSun) max(sunIcon, s.d.lineHeight(sunSize, 550)) else 0f

        val total = dateH + (if (w != null) gap + wxH else 0f) + (if (showSun) gap + sunH else 0f)
        var y = (s.H - total) / 2

        s.d.text(s.c, dateText, left, y + dateH / 2, dateSize, 600, ws.fg, maxWidth = width)
        y += dateH

        if (w != null) {
            y += gap
            val cy = y + wxH / 2
            s.d.icon(s.c, Weather.icon(w.code, w.isDay), left + wxIcon / 2, cy, wxIcon, ws.fgDim)
            s.d.text(s.c, "${w.temp.roundToInt()}°  ·  ${Weather.describe(w.code)}", left + wxIcon + 8 * k, cy, wxSize, 550, ws.fgDim,
                maxWidth = width - wxIcon - 8 * k)
            y += wxH
        }
        if (showSun) {
            y += gap
            val cy = y + sunH / 2
            val toSet = hourNow >= sun[0] && hourNow < sun[1]
            s.d.icon(s.c, if (toSet) "wb_twilight" else "wb_sunny", left + sunIcon / 2, cy, sunIcon, ws.accent)
            val label = if (toSet) I18n.t("Закат в ", "Sunset at ") + Sun.hm(sun[1]) else I18n.t("Восход в ", "Sunrise at ") + Sun.hm(sun[0])
            s.d.text(s.c, label, left + sunIcon + 8 * k, cy, sunSize, 550, ws.fgDim)
        }
    }
}
