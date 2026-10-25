package app.glasswidgets.engine

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The colour maths of the Linux shell (Theme.qml / Qt), so widgets pick the same colours:
 * Qt.tint, Qt.alpha, Qt.hsla, Qt.darker and Theme.legible / lum / contrast.
 */
object Col {
    fun alpha(c: Int, a: Float): Int = ColorUtils.setAlphaComponent(c, (a.coerceIn(0f, 1f) * 255).roundToInt())

    /** Qt.tint(base, overlay): the overlay (with its alpha) laid over an opaque base. */
    fun tint(base: Int, overlay: Int): Int {
        val a = Color.alpha(overlay) / 255f
        fun ch(b: Int, o: Int) = (b + (o - b) * a).roundToInt().coerceIn(0, 255)
        return Color.rgb(
            ch(Color.red(base), Color.red(overlay)),
            ch(Color.green(base), Color.green(overlay)),
            ch(Color.blue(base), Color.blue(overlay)),
        )
    }

    /** HSL of a colour as Qt reports it: hue 0…1 (−1 for greys), saturation and lightness 0…1. */
    fun hsl(c: Int): FloatArray {
        val out = FloatArray(3)
        ColorUtils.colorToHSL(c, out)
        val hue = if (out[1] < 1e-4f) -1f else out[0] / 360f
        return floatArrayOf(hue, out[1], out[2])
    }

    /** Qt.hsla — hue 0…1. */
    fun hsla(h: Float, s: Float, l: Float, a: Float = 1f): Int {
        val c = ColorUtils.HSLToColor(floatArrayOf((max(0f, h) * 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
        return alpha(c, a)
    }

    /** Qt.darker(c, factor): HSV value divided by factor. */
    fun darker(c: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] = hsv[2] / factor
        return Color.HSVToColor(Color.alpha(c), hsv)
    }

    fun lum(c: Int): Double {
        fun f(x: Int): Double {
            val v = x / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * f(Color.red(c)) + 0.7152 * f(Color.green(c)) + 0.0722 * f(Color.blue(c))
    }

    fun contrast(a: Int, b: Int): Double {
        val x = lum(a)
        val y = lum(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** Theme.legible: the same hue, lightened or darkened until it reads on [bg] with contrast [need]. */
    fun legible(c: Int, bg: Int, need: Double = 3.4): Int {
        if (contrast(c, bg) >= need) return c
        val up = lum(bg) < 0.18
        val (h, s, l0) = hsl(c)
        var l = l0
        while (if (up) l <= 0.97f else l >= 0.03f) {
            val t = hsla(max(0f, h), min(1f, s * 1.1f), l)
            if (contrast(t, bg) >= need) return t
            l += if (up) 0.04f else -0.04f
        }
        return if (up) Color.WHITE else Color.BLACK
    }

    fun opaque(c: Int) = c or (0xFF shl 24)
}
