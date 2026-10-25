package app.glasswidgets.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
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
 * Imitated blur for home-screen widgets. Android can't blur what's behind a widget (the
 * launcher owns that window), so we blur the wallpaper ourselves and show the piece that
 * sits under the widget. Reading the wallpaper needs "All files access" on Android 11+.
 *
 * Where a widget sits isn't reported by the launcher, so the crop is estimated until the
 * user taps the widget once: the tap carries its exact on-screen bounds (see [saveBounds]).
 */
internal object WidgetBackground {
    private const val TAG = "WidgetBackground"
    /** The wallpaper is blurred at 1/DOWNSCALE of the screen size; it's going to be soft anyway. */
    private const val DOWNSCALE = 8

    private var cached: Bitmap? = null
    private var cachedKey = ""

    fun hasWallpaperAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun saveBounds(ctx: Context, widgetId: Int, bounds: Rect) {
        prefs(ctx).edit().putString(Prefs.WIDGET_BOUNDS + widgetId, bounds.flattenToString()).apply()
    }

    fun forget(ctx: Context, widgetId: Int) {
        prefs(ctx).edit().remove(Prefs.WIDGET_BOUNDS + widgetId).apply()
    }

    /** The blurred wallpaper piece under [widgetId], or null when blur is off or unavailable. */
    fun blurFor(ctx: Context, mgr: AppWidgetManager, widgetId: Int, settings: WidgetSettings): Bitmap? {
        if (!settings.blur) return null
        val wallpaper = blurredWallpaper(ctx, settings.blurRadius) ?: return null
        val (sw, sh) = screenSize(ctx)
        val bounds = prefs(ctx).getString(Prefs.WIDGET_BOUNDS + widgetId, null)
            ?.let { Rect.unflattenFromString(it) }
            ?: estimateBounds(ctx, mgr, widgetId, sw, sh)
        val k = wallpaper.width / sw.toFloat()
        val src = Rect(
            (bounds.left * k).roundToInt(), (bounds.top * k).roundToInt(),
            (bounds.right * k).roundToInt(), (bounds.bottom * k).roundToInt()
        )
        if (!src.intersect(0, 0, wallpaper.width, wallpaper.height) || src.width() < 1 || src.height() < 1) return null
        return Bitmap.createBitmap(wallpaper, src.left, src.top, src.width(), src.height())
    }

    // Until calibrated: horizontally centred, upper part of the screen (where clocks usually go).
    private fun estimateBounds(ctx: Context, mgr: AppWidgetManager, id: Int, sw: Int, sh: Int): Rect {
        val opts = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull() ?: android.os.Bundle()
        val density = ctx.resources.displayMetrics.density
        val w = (opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) * density).toInt().coerceAtMost(sw)
        val h = (opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180) * density).toInt().coerceAtMost(sh)
        val left = (sw - w) / 2
        val top = (sh * 0.18f).toInt()
        return Rect(left, top, left + w, top + h)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun blurredWallpaper(ctx: Context, radiusDp: Int): Bitmap? {
        if (!hasWallpaperAccess(ctx)) return null
        val wm = WallpaperManager.getInstance(ctx)
        val (sw, sh) = screenSize(ctx)
        val key = "${wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)}:$radiusDp:${sw}x$sh"
        cached?.let { if (key == cachedKey) return it }
        val drawable = try { wm.drawable } catch (e: Exception) {
            Log.w(TAG, "Wallpaper not readable", e); null
        } ?: return null

        val bw = max(1, sw / DOWNSCALE)
        val bh = max(1, sh / DOWNSCALE)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val dw = drawable.intrinsicWidth.takeIf { it > 0 } ?: bw
        val dh = drawable.intrinsicHeight.takeIf { it > 0 } ?: bh
        // Centre-crop like the launcher does for a non-scrolling wallpaper.
        val scale = max(bw / dw.toFloat(), bh / dh.toFloat())
        val w = (dw * scale).roundToInt()
        val h = (dh * scale).roundToInt()
        val left = (bw - w) / 2
        val top = (bh - h) / 2
        drawable.setBounds(left, top, left + w, top + h)
        drawable.draw(Canvas(bmp))

        val radiusPx = (radiusDp * ctx.resources.displayMetrics.density / DOWNSCALE).roundToInt().coerceIn(1, 40)
        boxBlur(bmp, radiusPx)
        cached = bmp
        cachedKey = key
        return bmp
    }

    private fun screenSize(ctx: Context): Pair<Int, Int> {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = ctx.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    /** Three box-blur passes ≈ gaussian. The bitmap is tiny (1/8 screen), so this is quick. */
    private fun boxBlur(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        val tmp = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            pass(px, tmp, w, h, radius, horizontal = true)
            pass(tmp, px, w, h, radius, horizontal = false)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val p = src[idx(i)]
                a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until len) {
                dst[idx(i)] = ((a / window) shl 24) or ((rr / window) shl 16) or ((g / window) shl 8) or (b / window)
                val out = src[idx(i - r)]
                val inn = src[idx(i + r + 1)]
                a += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
}
