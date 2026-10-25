package app.glasswidgets.engine

import android.content.Context
import app.glasswidgets.M3Scheme
import app.glasswidgets.Prefs
import app.glasswidgets.ThemeColors

/**
 * Theme.qml for widgets: Material 3 roles plus the shell's look settings.
 *   style "glass" — frosted surfaces over the blurred wallpaper (One UI-like);
 *   style "m3"    — tonal Material You surfaces.
 */
class WTheme(
    val s: M3Scheme,
    val m3: Boolean,
    val glassOpacity: Float,
    val m3Opacity: Float,
) {
    val primary = s.primary
    val primaryFg = s.onPrimary
    val primaryContainer = s.primaryContainer
    val primaryContainerFg = s.onPrimaryContainer
    val secondary = s.secondary
    val secondaryContainer = s.secondaryContainer
    val secondaryContainerFg = s.onSecondaryContainer
    val tertiary = s.tertiary
    val tertiaryContainer = s.tertiaryContainer
    val tertiaryContainerFg = s.onTertiaryContainer
    val surface = s.surface
    val surfaceLowest = s.roles["surfaceContainerLowest"] ?: s.surface
    val surfaceHigh = s.surfaceContainerHigh
    val surfaceHighest = s.surfaceContainerHighest
    val fg = s.onSurface
    val fgVariant = s.onSurfaceVariant

    /** Monochrome palette: clock faces go pure white, like the AOD. */
    val mono = listOf(primary, secondary, tertiary).all { Col.hsl(it)[1] < 0.05f }

    /** Glass is matte like One UI's floating panels: the high surface, lifted a touch. */
    val frost = Col.tint(surfaceHigh, Col.alpha(0xFFFFFFFF.toInt(), 0.08f))

    /** Default surface density when a widget doesn't set its own. */
    val defaultAlpha get() = if (m3) m3Opacity else glassOpacity

    val primaryText = Col.legible(primary, surface)

    companion object {
        fun load(ctx: Context, dark: Boolean = ThemeColors.isDark(ctx)): WTheme {
            val p = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            return WTheme(
                s = ThemeColors.scheme(ctx, dark),
                m3 = p.getString(Prefs.STYLE, "glass") == "m3",
                glassOpacity = p.getFloat(Prefs.GLASS_OPACITY, 0.34f).coerceAtLeast(0.02f),
                m3Opacity = p.getFloat(Prefs.M3_OPACITY, 1f).coerceAtLeast(0.02f),
            )
        }
    }
}
