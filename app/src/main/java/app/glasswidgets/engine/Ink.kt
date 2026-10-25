package app.glasswidgets.engine

import android.graphics.Color

/** Per-widget look ("Customize", like on Samsung): background, theme colour, density. */
data class Look(
    val bg: String? = null,       // "blur" | "solid" | "none" — null = the widget's default
    val tone: String? = null,     // "primary" | "secondary" | "tertiary" | "neutral"
    val alpha: Float? = null,     // 0…1 — null = the theme's opacity
)

/**
 * WidgetSurface.qml: the colours of one widget surface and of whatever is drawn on it.
 * [under] is the wallpaper colour under the surface (average of the blurred wallpaper there),
 * so text stays readable on light wallpapers and on almost-clear glass.
 */
class Ink(
    val t: WTheme,
    look: Look,
    defaultTone: String,
    defaultBg: String,
    val under: Int,
    tintOverride: Int = Color.TRANSPARENT,
) {
    val bg = look.bg?.takeIf { it in BGS } ?: defaultBg
    val tone = look.tone?.takeIf { it in TONES } ?: defaultTone
    val alpha = look.alpha ?: t.defaultAlpha
    val bare = bg == "none"

    val container = when (tone) {
        "secondary" -> t.secondaryContainer
        "tertiary" -> t.tertiaryContainer
        "neutral" -> t.surfaceHigh
        else -> t.primaryContainer
    }
    val containerInk = when (tone) {
        "secondary" -> t.secondaryContainerFg
        "tertiary" -> t.tertiaryContainerFg
        "neutral" -> t.fg
        else -> t.primaryContainerFg
    }
    val hue = when (tone) {
        "secondary" -> t.secondary
        "tertiary" -> t.tertiary
        "neutral" -> t.fgVariant
        else -> t.primary
    }

    // Glass: the theme's matte surface with the container's hue (its lightness stays the shell's);
    // M3: the container itself.
    private val base = if (Color.alpha(tintOverride) > 0) tintOverride else container
    private val mixed = Col.tint(t.frost, Col.alpha(base, 0.5f))
    val tint: Int = when {
        t.m3 -> base
        Color.alpha(tintOverride) > 0 -> mixed
        else -> Col.hsl(mixed).let { Col.hsla(it[0], it[1], Col.hsl(t.frost)[2]) }
    }

    /** What text and icons actually sit on. */
    val seen: Int = if (bare) under else Col.tint(under, Col.alpha(tint, alpha.coerceIn(0f, 1f)))

    val fg: Int = Col.legible(if (bare) Color.WHITE else if (t.m3) containerInk else t.fg, seen, 4.5)
    val fgDim: Int = Col.alpha(fg, if (bare) 0.82f else 0.72f)
    val accent: Int = Col.legible(
        if (bare) Col.hsl(hue).let { Col.hsla(it[0], maxOf(0.35f, it[1]), 0.84f) } else hue,
        seen, if (bare) 3.0 else 4.5,
    )
    val fill = accent
    val fillInk: Int = if (Col.lum(accent) > 0.3) Col.legible(Col.darker(container, 1.25f), accent, 4.5) else Color.WHITE
    val chip: Int = Col.alpha(fg, if (bare) 0.16f else 0.1f)
    /** Text right on the wallpaper next to the surface (the date under some clock faces). */
    val wallInk: Int = Col.legible(Color.WHITE, under, 4.5)

    companion object {
        val BGS = listOf("blur", "solid", "none")
        val TONES = listOf("primary", "secondary", "tertiary", "neutral")
    }
}
