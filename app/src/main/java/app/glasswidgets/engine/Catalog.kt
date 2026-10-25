package app.glasswidgets.engine

import android.content.Context
import android.graphics.Rect
import app.glasswidgets.Prefs
import org.json.JSONObject

/**
 * Widgets.qml catalog: each widget's defaults. [w]/[h] are the default size in Linux grid
 * cells; [tone] and [bg] are the look before "Customize".
 */
enum class WType(
    val id: String, val ru: String, val en: String, val icon: String,
    val tone: String, val bg: String, val w: Int, val h: Int,
    val weather: Boolean = false,
) {
    CLOCK("clock", "Часы", "Clock", "schedule", "neutral", "blur", 2, 2),
    GLANCE("glance", "Коротко о главном", "At a glance", "wb_sunny", "primary", "none", 4, 1, weather = true),
    CALENDAR("calendar", "Календарь", "Calendar", "calendar_month", "neutral", "blur", 3, 3),
    PROGRESS("progress", "День и год", "Day & year", "data_usage", "primary", "blur", 2, 2),
    WEATHER("weather", "Погода", "Weather", "partly_cloudy_day", "secondary", "blur", 2, 2, weather = true),
    FORECAST("forecast", "Прогноз", "Forecast", "show_chart", "tertiary", "blur", 4, 2, weather = true),
    SUN("sun", "Солнце", "Sun", "wb_twilight", "tertiary", "blur", 2, 2);

    val label get() = I18n.t(ru, en)

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** AOD clock faces (AodClock.qml styles), in the order the Linux tuner shows them. */
val CLOCK_FACES = listOf(
    "bubble" to ("Формы" to "Shapes"),
    "analog" to ("Стрелки" to "Analog"),
    "orbit" to ("Орбита" to "Orbit"),
    "dots" to ("Точки" to "Dots"),
    "planets" to ("Планеты" to "Planets"),
    "horizon" to ("Горизонт" to "Horizon"),
    "segments" to ("Сегменты" to "Segments"),
)

/** One placed widget: its type and its own settings (Widgets.qml "extra"). */
data class WidgetCfg(
    val type: WType,
    val look: Look = Look(),
    val face: String? = null,     // clock
    val mode: String? = null,     // progress: day | month | year
) {
    fun toJson(): String = JSONObject().apply {
        put("type", type.id)
        look.bg?.let { put("bg", it) }
        look.tone?.let { put("tone", it) }
        look.alpha?.let { put("alpha", it.toDouble()) }
        face?.let { put("face", it) }
        mode?.let { put("mode", it) }
    }.toString()

    companion object {
        fun fromJson(s: String?, fallback: WType): WidgetCfg {
            if (s == null) return WidgetCfg(fallback)
            return try {
                val o = JSONObject(s)
                WidgetCfg(
                    type = WType.byId(o.optString("type")) ?: fallback,
                    look = Look(
                        bg = o.optString("bg").ifEmpty { null },
                        tone = o.optString("tone").ifEmpty { null },
                        alpha = if (o.has("alpha")) o.getDouble("alpha").toFloat() else null,
                    ),
                    face = o.optString("face").ifEmpty { null },
                    mode = o.optString("mode").ifEmpty { null },
                )
            } catch (e: Exception) {
                WidgetCfg(fallback)
            }
        }
    }
}

/** Per-widget settings and on-screen bounds, by appWidgetId. */
object WidgetStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    fun get(ctx: Context, id: Int, type: WType) = WidgetCfg.fromJson(p(ctx).getString("w_$id", null), type)
    fun put(ctx: Context, id: Int, cfg: WidgetCfg) = p(ctx).edit().putString("w_$id", cfg.toJson()).apply()

    fun bounds(ctx: Context, id: Int): Rect? = p(ctx).getString("b_$id", null)?.let { Rect.unflattenFromString(it) }
    fun putBounds(ctx: Context, id: Int, r: Rect) = p(ctx).edit().putString("b_$id", r.flattenToString()).apply()

    fun forget(ctx: Context, id: Int) = p(ctx).edit().remove("w_$id").remove("b_$id").apply()
}
