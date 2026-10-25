package app.glasswidgets.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** AodShape.qml: Material 3 Expressive shapes as radius-by-angle. */
object Shapes {
    fun r(kind: String, a: Double, lobes: Int = 9): Double = when (kind) {
        "cookie" -> 0.94 + 0.06 * cos(a * lobes)
        "clover" -> 0.82 + 0.18 * cos(a * 4)
        "hex" -> 0.93 + 0.07 * cos(a * 6)
        "squircle" -> 1.0 / (abs(cos(a)).pow(4) + abs(sin(a)).pow(4)).pow(0.25)
        else -> 1.0
    }

    /** Path of [kind] filling [r] ("rect" → rounded rectangle of [radius]). */
    fun path(kind: String, r: RectF, radius: Float = 0f, lobes: Int = 9): Path {
        val p = Path()
        if (kind == "rect") {
            val rr = min(radius, min(r.width(), r.height()) / 2)
            p.addRoundRect(r, rr, rr, Path.Direction.CW)
            return p
        }
        val n = 240
        val big = min(r.width(), r.height()) / 2
        val cx = r.centerX()
        val cy = r.centerY()
        for (i in 0..n) {
            val a = i.toDouble() / n * Math.PI * 2
            val k = r(kind, a, lobes)
            val x = (cx + big * k * cos(a)).toFloat()
            val y = (cy + big * k * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }
}

/**
 * The blurred wallpaper under a widget, already aligned to the widget's bitmap (null when the
 * wallpaper can't be read). [avg] gives the wallpaper colour under a part of the widget.
 */
class Backdrop(val bitmap: Bitmap?, private val fallback: Int, private val widgetW: Float, private val widgetH: Float) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, clip: Path) {
        val b = bitmap ?: return
        c.save()
        c.clipPath(clip)
        c.drawBitmap(b, null, RectF(0f, 0f, widgetW, widgetH), paint)
        c.restore()
    }

    /** Average wallpaper colour under [r] (widget coordinates). */
    fun avg(r: RectF): Int {
        val b = bitmap ?: return fallback
        val sx = b.width / widgetW
        val sy = b.height / widgetH
        val box = Rect(
            (r.left * sx).toInt().coerceIn(0, b.width - 1), (r.top * sy).toInt().coerceIn(0, b.height - 1),
            (r.right * sx).toInt().coerceIn(1, b.width), (r.bottom * sy).toInt().coerceIn(1, b.height),
        )
        var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
        val step = maxOf(1, maxOf(box.width(), box.height()) / 24)
        var y = box.top
        while (y < box.bottom) {
            var x = box.left
            while (x < box.right) {
                val p = b.getPixel(x, y)
                rs += Color.red(p); gs += Color.green(p); bs += Color.blue(p); n++
                x += step
            }
            y += step
        }
        if (n == 0) return fallback
        return Color.rgb((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
    }

    val available get() = bitmap != null
}

/**
 * WidgetSurface + shaders/glass.frag on a Canvas:
 *   blur  — blurred wallpaper mixed with the theme tint by [Ink.alpha], a soft sheen at the top
 *           and a thin light rim (brighter at the top);
 *   solid — the tint with its alpha straight over the wallpaper;
 *   none  — nothing.
 */
object SurfacePainter {
    fun draw(
        c: Canvas, r: RectF, ink: Ink, backdrop: Backdrop,
        shape: String = "rect", radius: Float = 44f, lobes: Int = 9,
    ) {
        if (ink.bare || r.width() < 1 || r.height() < 1) return
        val path = Shapes.path(shape, r, radius, lobes)
        val sheen = if (ink.t.m3) 0.3f else 1f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        if (ink.bg == "blur" && backdrop.available) {
            backdrop.draw(c, path)
            fill.color = Col.alpha(ink.tint, ink.alpha.coerceIn(0f, 1f))
            c.drawPath(path, fill)
            sheenTop(c, path, r, 0.07f * sheen)
            rim(c, path, r, 0.30f * sheen)
        } else if (ink.bg == "blur") {
            // no wallpaper to blur (no access yet): just the colour, never fully clear
            fill.color = Col.alpha(ink.tint, maxOf(0.3f, ink.alpha))
            c.drawPath(path, fill)
            sheenTop(c, path, r, 0.06f * sheen)
            rim(c, path, r, 0.22f * sheen)
        } else {
            val a = maxOf(0.05f, ink.alpha)
            fill.color = Col.alpha(ink.tint, a)
            c.drawPath(path, fill)
            sheenTop(c, path, r, 0.06f * sheen * a)
            rim(c, path, r, 0.22f * sheen)
        }
    }

    // highlight over the top 42%
    private fun sheenTop(c: Canvas, path: Path, r: RectF, strength: Float) {
        if (strength <= 0f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.42f,
            Col.alpha(Color.WHITE, strength), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
    }

    // thin light edge, strongest along the top
    private fun rim(c: Canvas, path: Path, r: RectF, strength: Float) {
        if (strength <= 0f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        p.shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.5f,
            Col.alpha(Color.WHITE, strength), Col.alpha(Color.WHITE, strength * 0.35f), Shader.TileMode.CLAMP)
        c.save()
        c.clipPath(path)
        c.drawPath(path, p)
        c.restore()
    }
}
