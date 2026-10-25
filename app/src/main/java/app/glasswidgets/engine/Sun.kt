package app.glasswidgets.engine

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Sun.qml: sunrise and sunset for a place, computed locally (NOAA approximation, minutes accuracy). */
object Sun {
    /** [sunrise, sunset] in local hours. */
    fun times(now: Calendar, lat: Double, lon: Double): DoubleArray {
        val rad = PI / 180
        val n = now.get(Calendar.DAY_OF_YEAR)
        val decl = 23.44 * rad * sin(2 * PI * (284 + n) / 365)
        val x = (sin(-0.833 * rad) - sin(lat * rad) * sin(decl)) / (cos(lat * rad) * cos(decl))
        val w = acos(x.coerceIn(-1.0, 1.0)) / rad / 15
        val b = 2 * PI * (n - 81) / 364
        val eot = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val tzHours = TimeZone.getDefault().getOffset(now.timeInMillis) / 3_600_000.0
        val noon = 12 - (lon - tzHours * 15) / 15 - eot / 60
        return doubleArrayOf(noon - w, noon + w)
    }

    fun hourOf(now: Calendar) = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0

    /** 18.82 → "18:49". */
    fun hm(h: Double): String {
        val m = ((((h % 24) + 24) % 24) * 60).roundToInt()
        return "%02d:%02d".format(floor(m / 60.0).toInt() % 24, m % 60)
    }
}
