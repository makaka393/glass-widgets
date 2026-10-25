@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)

package app.glasswidgets.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glasswidgets.M3Scheme
import app.glasswidgets.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Theme: Material 3 Expressive with the shell's font ───────────────────────

private val weights = listOf(400, 500, 600, 700, 800)

/** Google Sans Flex, rounded (ROND 100) — the shell's Label font. */
val Sans = FontFamily(weights.map { w ->
    Font(R.font.google_sans_flex, FontWeight(w), variationSettings = FontVariation.Settings(
        FontVariation.weight(w), FontVariation.Setting("ROND", 100f),
    ))
})

private val symbolsFilled = FontFamily(Font(R.font.material_symbols_rounded, FontWeight(500),
    variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 1f), FontVariation.weight(500))))
private val symbolsOutlined = FontFamily(Font(R.font.material_symbols_rounded, FontWeight(500),
    variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 0f), FontVariation.weight(500))))

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

fun M3Scheme.toCompose(): ColorScheme {
    fun c(n: String) = Color(roles[n] ?: 0xFFFF00FF.toInt())
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c("primary"), onPrimary = c("onPrimary"),
        primaryContainer = c("primaryContainer"), onPrimaryContainer = c("onPrimaryContainer"),
        secondary = c("secondary"), onSecondary = c("onSecondary"),
        secondaryContainer = c("secondaryContainer"), onSecondaryContainer = c("onSecondaryContainer"),
        tertiary = c("tertiary"), onTertiary = c("onTertiary"),
        tertiaryContainer = c("tertiaryContainer"), onTertiaryContainer = c("onTertiaryContainer"),
        error = c("error"), onError = c("onError"), errorContainer = c("errorContainer"), onErrorContainer = c("onErrorContainer"),
        background = c("surface"), onBackground = c("onSurface"), surface = c("surface"), onSurface = c("onSurface"),
        surfaceVariant = c("surfaceContainerHighest"), onSurfaceVariant = c("onSurfaceVariant"),
        surfaceDim = c("surfaceDim"), surfaceBright = c("surfaceBright"),
        surfaceContainerLowest = c("surfaceContainerLowest"), surfaceContainerLow = c("surfaceContainerLow"),
        surfaceContainer = c("surfaceContainer"), surfaceContainerHigh = c("surfaceContainerHigh"),
        surfaceContainerHighest = c("surfaceContainerHighest"),
        outline = c("outline"), outlineVariant = c("outlineVariant"),
        inverseSurface = c("inverseSurface"), inverseOnSurface = c("inverseOnSurface"), inversePrimary = c("inversePrimary"),
    )
}

@Composable
fun GlassTheme(scheme: M3Scheme, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = scheme.toCompose(),
        motionScheme = MotionScheme.expressive(),
        typography = Typography().withFont(Sans),
        content = content,
    )
}

// ── Pieces ───────────────────────────────────────────────────────────────────

/** Material Symbols Rounded icon by name (same icons as the Linux shell). */
@Composable
fun Sym(name: String, size: TextUnit = 24.sp, color: Color = MaterialTheme.colorScheme.onSurface, fill: Boolean = true, modifier: Modifier = Modifier) {
    Text(name, modifier = modifier, color = color,
        style = TextStyle(fontFamily = if (fill) symbolsFilled else symbolsOutlined, fontSize = size, fontFeatureSettings = "liga"))
}

/** A titled group of tiles with 2 dp gaps: big outer corners, tight inner ones (Android 16 settings). */
@Composable
fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) Text(
            title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight(700), modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        Column(Modifier.clip(RoundedCornerShape(28.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

@Composable
fun Tile(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun TileRow(icon: String, title: String, subtitle: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Sym(icon, 24.sp, MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight(600))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke(this)
    }
}

/** M3 Expressive connected button group (single choice), with optional icons. */
@Composable
fun <T> Connected(options: List<Triple<T, String, String?>>, value: T, onChange: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (id, label, icon) ->
            val shapes = when (i) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(checked = value == id, onCheckedChange = { onChange(id) }, shapes = shapes, modifier = Modifier.weight(1f)) {
                if (icon != null) {
                    Sym(icon, 18.sp, if (value == id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(6.dp))
                }
                Text(label, maxLines = 1)
            }
        }
    }
}

/**
 * Swatch that morphs from a circle to a rounded square when selected and squeezes when pressed
 * (WidgetTuner's colour and face pickers).
 */
@Composable
fun MorphChoice(selected: Boolean, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (selected) size * 0.32f else if (pressed) size * 0.27f else size / 2, label = "r")
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "ring")
    Box(
        Modifier
            .size(size)
            .scale(if (pressed) 0.92f else 1f)
            .border(3.dp, ring, RoundedCornerShape(radius))
            .padding(5.dp)
            .clip(RoundedCornerShape((radius - 5.dp).coerceAtLeast(2.dp)))
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A widget bitmap rendered off the main thread; shows nothing until ready. */
@Composable
fun BitmapOf(key: Any, modifier: Modifier = Modifier, render: suspend () -> Bitmap) {
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) { bmp = withContext(Dispatchers.Default) { render() } }
    bmp?.let { Image(it.asImageBitmap(), null, modifier, contentScale = ContentScale.FillBounds) }
}

fun Context.dpToPx(dp: Float) = dp * resources.displayMetrics.density

@Composable
fun Dot(color: Color, size: Dp) = Box(Modifier.size(size).clip(CircleShape).background(color))
