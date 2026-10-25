package app.glasswidgets.ui

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.glasswidgets.engine.WallpaperBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** The real wallpaper behind previews (when we may read it), else a soft gradient of the theme. */
@Composable
fun WallpaperBackdrop(modifier: Modifier = Modifier, key: Any = Unit) {
    val ctx = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) { bmp = withContext(Dispatchers.IO) { wallpaper(ctx) } }
    val cs = MaterialTheme.colorScheme
    Box(modifier.background(Brush.linearGradient(listOf(cs.tertiaryContainer, cs.primary, cs.secondaryContainer)))) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

private var cache: Bitmap? = null

@SuppressLint("MissingPermission")
private fun wallpaper(ctx: Context): Bitmap? {
    if (!WallpaperBlur.hasAccess(ctx)) return null
    cache?.let { return it }
    val d = runCatching { WallpaperManager.getInstance(ctx).drawable }.getOrNull() ?: return null
    val (sw, sh) = WallpaperBlur.screenSize(ctx)
    val w = sw / 3
    val h = sh / 3
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val dw = d.intrinsicWidth.takeIf { it > 0 } ?: w
    val dh = d.intrinsicHeight.takeIf { it > 0 } ?: h
    val s = max(w / dw.toFloat(), h / dh.toFloat())
    val rw = (dw * s).roundToInt()
    val rh = (dh * s).roundToInt()
    d.setBounds((w - rw) / 2, (h - rh) / 2, (w - rw) / 2 + rw, (h - rh) / 2 + rh)
    d.draw(Canvas(bmp))
    cache = bmp
    return bmp
}
