package app.glasswidgets.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import app.glasswidgets.engine.painters.CalendarPainter
import app.glasswidgets.engine.painters.ClockPainter
import app.glasswidgets.engine.painters.ForecastPainter
import app.glasswidgets.engine.painters.GlancePainter
import app.glasswidgets.engine.painters.ProgressPainter
import app.glasswidgets.engine.painters.SunPainter
import app.glasswidgets.engine.painters.WeatherPainter
import java.util.Calendar
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Everything a widget painter needs. Coordinates are Linux pixels ("design units"): the shell's
 * grid cell is 96 px with a 16 px gap, and a widget is [W]×[H] of those, whatever the phone.
 */
class Scene(
    val ctx: Context,
    val c: Canvas,
    val t: WTheme,
    val d: Draw,
    val now: Calendar,
    val weather: Weather.Now?,
    val place: Pair<Double, Double>,
    val backdrop: Backdrop,
    val cfg: WidgetCfg,
    val W: Float,
    val H: Float,
) {
    val full get() = RectF(0f, 0f, W, H)

    /** WidgetSurface for [r]: colours under it come from the wallpaper there. */
    fun ink(r: RectF = full, tone: String = cfg.type.tone, bg: String = cfg.type.bg, tintOverride: Int = Color.TRANSPARENT) =
        Ink(t, cfg.look, tone, bg, backdrop.avg(r), tintOverride)

    fun surface(r: RectF, ink: Ink, shape: String = "rect", radius: Float = 44f, lobes: Int = 9) =
        SurfacePainter.draw(c, r, ink, backdrop, shape, radius, lobes)

    // ── small drawing helpers ────────────────────────────────────────────────

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun circle(cx: Float, cy: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = color
        c.drawCircle(cx, cy, r, p)
    }

    fun roundRect(r: RectF, radius: Float, color: Int) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = color
        c.drawRoundRect(r, radius, radius, p)
    }

    fun stroke(path: Path, color: Int, width: Float, round: Boolean = true) {
        p.style = Paint.Style.STROKE; p.shader = null; p.color = color; p.strokeWidth = width
        p.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    fun fill(path: Path, color: Int) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = color
        c.drawPath(path, p)
    }

    fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)
}

fun interface Painter {
    fun paint(s: Scene)
}

/** Renders widgets to bitmaps. One bitmap per widget, redrawn each minute while the screen is on. */
object Renderer {
    /** Linux pixels per dp: a phone's 2×2 widget (~180 dp) ≈ the shell's 2×2 (208 px). */
    const val DESIGN_PER_DP = 1.15f
    private const val MAX_PIXELS = 1_400_000

    fun painter(type: WType): Painter = when (type) {
        WType.CLOCK -> ClockPainter
        WType.GLANCE -> GlancePainter
        WType.CALENDAR -> CalendarPainter
        WType.PROGRESS -> ProgressPainter
        WType.WEATHER -> WeatherPainter
        WType.FORECAST -> ForecastPainter
        WType.SUN -> SunPainter
    }

    /**
     * [screen] is where the widget sits on the home screen (for its piece of blurred wallpaper);
     * null — estimated.
     */
    fun render(ctx: Context, cfg: WidgetCfg, widthDp: Float, heightDp: Float, screen: Rect?, dark: Boolean? = null): Bitmap {
        val density = ctx.resources.displayMetrics.density
        var pxW = (widthDp * density).toInt().coerceAtLeast(1)
        var pxH = (heightDp * density).toInt().coerceAtLeast(1)
        val shrink = min(1f, sqrt(MAX_PIXELS / (pxW.toFloat() * pxH)))
        val screenW = pxW
        val screenH = pxH
        pxW = (pxW * shrink).toInt().coerceAtLeast(1)
        pxH = (pxH * shrink).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val W = widthDp * DESIGN_PER_DP
        val H = heightDp * DESIGN_PER_DP
        canvas.scale(pxW / W, pxH / H)

        val t = if (dark == null) WTheme.load(ctx) else WTheme.load(ctx, dark)
        val where = screen ?: WallpaperBlur.estimate(ctx, screenW, screenH)
        val under = WallpaperBlur.under(ctx, where)
        val backdrop = Backdrop(under, t.surfaceLowest, W, H)
        val scene = Scene(
            ctx, canvas, t, Draw(ctx), Calendar.getInstance(), Weather.cached(ctx), Weather.place(ctx),
            backdrop, cfg, W, H,
        )
        painter(cfg.type).paint(scene)
        return bmp
    }
}
