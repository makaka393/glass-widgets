package app.glasswidgets.engine

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.WindowManager
import app.glasswidgets.Prefs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The shell's own smooth wallpaper blur (Desktop.qml blurs the wallpaper once and widgets sample
 * it). Android can't blur behind a widget, so we blur the wallpaper ourselves and hand each widget
 * the piece under it. Reading the wallpaper needs "All files access" on Android 11+.
 */
object WallpaperBlur {
    private const val TAG = "WallpaperBlur"
    private const val DOWNSCALE = 8

    private var cached: Bitmap? = null
    private var cachedKey = ""

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun screenSize(ctx: Context): Pair<Int, Int> {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else ctx.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    }

    /** Where a widget of [wPx]×[hPx] probably sits until its first tap tells us: centred, upper third. */
    fun estimate(ctx: Context, wPx: Int, hPx: Int): Rect {
        val (sw, sh) = screenSize(ctx)
        val l = (sw - wPx) / 2
        val t = (sh * 0.2f).toInt()
        return Rect(l, t, l + wPx, t + hPx)
    }

    /** Blurred wallpaper under [screen] (screen pixels), small — it's stretched when drawn. */
    fun under(ctx: Context, screen: Rect): Bitmap? {
        val wall = blurred(ctx) ?: return null
        val (sw, _) = screenSize(ctx)
        val k = wall.width / sw.toFloat()
        val src = Rect(
            (screen.left * k).roundToInt(), (screen.top * k).roundToInt(),
            (screen.right * k).roundToInt(), (screen.bottom * k).roundToInt(),
        )
        if (!src.intersect(0, 0, wall.width, wall.height) || src.width() < 1 || src.height() < 1) return null
        return Bitmap.createBitmap(wall, src.left, src.top, src.width(), src.height())
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun blurred(ctx: Context): Bitmap? {
        if (!hasAccess(ctx)) return null
        val radiusDp = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).getInt(Prefs.BLUR_RADIUS, 48)
        val wm = WallpaperManager.getInstance(ctx)
        val (sw, sh) = screenSize(ctx)
        val key = "${wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)}:$radiusDp:${sw}x$sh"
        cached?.let { if (key == cachedKey) return it }
        val drawable = try { wm.drawable } catch (e: Exception) { Log.w(TAG, "wallpaper not readable", e); null } ?: return null
        val bw = max(1, sw / DOWNSCALE)
        val bh = max(1, sh / DOWNSCALE)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val dw = drawable.intrinsicWidth.takeIf { it > 0 } ?: bw
        val dh = drawable.intrinsicHeight.takeIf { it > 0 } ?: bh
        val scale = max(bw / dw.toFloat(), bh / dh.toFloat())
        val w = (dw * scale).roundToInt()
        val h = (dh * scale).roundToInt()
        drawable.setBounds((bw - w) / 2, (bh - h) / 2, (bw - w) / 2 + w, (bh - h) / 2 + h)
        drawable.draw(Canvas(bmp))
        val r = (radiusDp * ctx.resources.displayMetrics.density / DOWNSCALE / 2).roundToInt().coerceIn(1, 40)
        boxBlur(bmp, r)
        cached = bmp
        cachedKey = key
        return bmp
    }

    /** Three box passes ≈ gaussian, smooth and grain-free. */
    private fun boxBlur(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        val tmp = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            pass(px, tmp, w, h, radius, true)
            pass(tmp, px, w, h, radius, false)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val win = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int { val c = i.coerceIn(0, len - 1); return if (horizontal) line * w + c else c * w + line }
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) { val p = src[idx(i)]; a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255 }
            for (i in 0 until len) {
                dst[idx(i)] = ((a / win) shl 24) or ((rr / win) shl 16) or ((g / win) shl 8) or (b / win)
                val o = src[idx(i - r)]; val n = src[idx(i + r + 1)]
                a += (n ushr 24) - (o ushr 24)
                rr += ((n shr 16) and 255) - ((o shr 16) and 255)
                g += ((n shr 8) and 255) - ((o shr 8) and 255)
                b += (n and 255) - (o and 255)
            }
        }
    }
}
