package app.glasswidgets.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import app.glasswidgets.R

/**
 * Label.qml / Icon.qml on a Canvas: Google Sans Flex with rounded terminals (ROND 100, tabular
 * figures) and Material Symbols Rounded icons drawn by their ligature name.
 * Sizes are in the widget's design units (Linux pixels); the canvas is scaled for the screen.
 */
class Draw(ctx: Context) {
    private val sans: Typeface = fonts(ctx).first
    private val symbols: Typeface = fonts(ctx).second
    private val bounds = Rect()

    enum class Align { LEFT, CENTER, RIGHT }

    private fun textPaint(size: Float, weight: Int, color: Int, round: Int = 100): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = sans
            textSize = size
            this.color = color
            fontVariationSettings = "'wght' $weight, 'ROND' $round, 'opsz' ${size.coerceIn(6f, 144f)}"
            fontFeatureSettings = "tnum"
        }

    fun width(text: String, size: Float, weight: Int): Float = textPaint(size, weight, 0).measureText(text)

    /** Height of a single line (what a QML Label occupies). */
    fun lineHeight(size: Float, weight: Int = 500): Float = textPaint(size, weight, 0).fontMetrics.let { it.descent - it.ascent }

    /** Height of capitals and digits. */
    fun capHeight(size: Float, weight: Int = 500): Float {
        textPaint(size, weight, 0).getTextBounds("H", 0, 1, bounds)
        return bounds.height().toFloat()
    }

    /**
     * Text whose capitals are optically centred on [cy] (like Label's cap-centre fix).
     * Returns the drawn width.
     */
    fun text(
        c: Canvas, s: String, x: Float, cy: Float, size: Float, weight: Int, color: Int,
        align: Align = Align.LEFT, maxWidth: Float = Float.MAX_VALUE, round: Int = 100,
    ): Float {
        var str = s
        val p = textPaint(size, weight, color, round)
        if (p.measureText(str) > maxWidth) str = ellipsize(p, str, maxWidth)
        val w = p.measureText(str)
        p.getTextBounds("H", 0, 1, bounds)
        val baseline = cy + bounds.height() / 2f
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2
            Align.RIGHT -> x - w
        }
        c.drawText(str, left, baseline, p)
        return w
    }

    /** Largest size ≤ [size] (and ≥ [min]) at which [s] fits into [maxWidth] — QML's HorizontalFit. */
    fun fit(s: String, size: Float, weight: Int, maxWidth: Float, min: Float = 6f): Float {
        var z = size
        while (z > min && width(s, z, weight) > maxWidth) z -= 0.5f
        return z
    }

    /** One glyph centred on its own ink at ([cx], [cy]) — AodDigit. */
    fun glyph(c: Canvas, ch: String, cx: Float, cy: Float, size: Float, weight: Int, color: Int) {
        val p = textPaint(size, weight, color)
        val w = p.measureText(ch)
        p.getTextBounds("0", 0, 1, bounds)
        c.drawText(ch, cx - w / 2, cy + bounds.height() / 2f, p)
    }

    fun glyphWidth(ch: String, size: Float, weight: Int) = textPaint(size, weight, 0).measureText(ch)

    /** Material Symbols Rounded icon by name, centred on ([cx], [cy]). */
    fun icon(c: Canvas, name: String, cx: Float, cy: Float, size: Float, color: Int, fill: Int = 1, weight: Int = 500) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = symbols
            textSize = size
            this.color = color
            fontVariationSettings = "'FILL' $fill, 'wght' $weight, 'opsz' ${size.coerceIn(20f, 48f)}, 'GRAD' 0"
        }
        p.getTextBounds(name, 0, name.length, bounds)
        val w = p.measureText(name)
        c.drawText(name, cx - w / 2, cy - bounds.exactCenterY(), p)
    }

    private fun ellipsize(p: Paint, s: String, max: Float): String {
        var end = s.length
        while (end > 0 && p.measureText(s.substring(0, end) + "…") > max) end--
        return s.substring(0, end).trimEnd() + "…"
    }

    companion object {
        @Volatile private var cached: Pair<Typeface, Typeface>? = null

        private fun fonts(ctx: Context): Pair<Typeface, Typeface> = cached ?: synchronized(this) {
            cached ?: Pair(
                ctx.resources.getFont(R.font.google_sans_flex),
                ctx.resources.getFont(R.font.material_symbols_rounded),
            ).also { cached = it }
        }
    }
}
