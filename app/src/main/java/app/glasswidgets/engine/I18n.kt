package app.glasswidgets.engine

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** I18n.qml: Russian when the phone is in Russian, English otherwise. */
object I18n {
    val ru get() = Locale.getDefault().language == "ru"
    fun t(ru: String, en: String) = if (this.ru) ru else en

    private fun cap(s: String) = s.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    private fun fmt(pattern: String, c: Calendar) = SimpleDateFormat(pattern, Locale.getDefault()).format(c.time)

    /** "Суббота, 25 октября" / "Saturday, October 25". */
    fun longDate(c: Calendar) = cap(fmt(if (ru) "EEEE, d MMMM" else "EEEE, MMMM d", c))
    /** "Сб, 25 октября" / "Sat, October 25" (under clock faces). */
    fun shortDate(c: Calendar) = cap(fmt(if (ru) "EEE, d MMMM" else "EEE, MMMM d", c))
    fun dayName(c: Calendar) = cap(fmt("cccc", c))
    fun dayShort(c: Calendar) = cap(fmt("ccc", c))
    fun monthName(c: Calendar) = cap(fmt("LLLL", c))
    fun monthYear(c: Calendar) = cap(fmt("LLLL yyyy", c))
}
