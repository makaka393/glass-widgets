package app.glasswidgets

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant

/**
 * Material 3 color roles used by the widgets and the settings screen.
 */
class M3Scheme(val roles: Map<String, Int>, val dark: Boolean) {
    private fun r(name: String) = roles[name] ?: Color.MAGENTA

    val primary get() = r("primary")
    val onPrimary get() = r("onPrimary")
    val primaryContainer get() = r("primaryContainer")
    val onPrimaryContainer get() = r("onPrimaryContainer")
    val secondary get() = r("secondary")
    val secondaryContainer get() = r("secondaryContainer")
    val onSecondaryContainer get() = r("onSecondaryContainer")
    val tertiary get() = r("tertiary")
    val tertiaryContainer get() = r("tertiaryContainer")
    val onTertiaryContainer get() = r("onTertiaryContainer")
    val surface get() = r("surface")
    val surfaceBright get() = r("surfaceBright")
    val surfaceContainerLow get() = r("surfaceContainerLow")
    val surfaceContainer get() = r("surfaceContainer")
    val surfaceContainerHigh get() = r("surfaceContainerHigh")
    val surfaceContainerHighest get() = r("surfaceContainerHighest")
    val onSurface get() = r("onSurface")
    val onSurfaceVariant get() = r("onSurfaceVariant")
    val outline get() = r("outline")
    val outlineVariant get() = r("outlineVariant")
    val inverseSurface get() = r("inverseSurface")
    val inverseOnSurface get() = r("inverseOnSurface")
}

internal object ThemeColors {

    /** Every role a scheme carries. */
    val ROLE_NAMES = listOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
        "error", "onError", "errorContainer", "onErrorContainer",
        "surface", "surfaceDim", "surfaceBright",
        "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
        "surfaceContainerHigh", "surfaceContainerHighest",
        "onSurface", "onSurfaceVariant", "outline", "outlineVariant",
        "inverseSurface", "inverseOnSurface", "inversePrimary",
    )

    fun isDynamicAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun prefs(context: Context) = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)

    private fun roles(context: Context, dark: Boolean): Map<String, Int> {
        val p = prefs(context)
        val source = p.getString(Prefs.COLOR_SOURCE, "system") ?: "system"
        if (source == "system") system(context, dark)?.let { return it }
        val seed = runCatching { Color.parseColor(p.getString(Prefs.SEED_COLOR, "#6750A4")) }.getOrDefault(0xFF6750A4.toInt())
        return fromSeed(seed, p.getString(Prefs.PALETTE_STYLE, "tonal") ?: "tonal", dark)
    }

    fun isDark(context: Context): Boolean = when (prefs(context).getString(Prefs.THEME_CHOICE, "system")) {
        "light" -> false
        "dark" -> true
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Scheme for the current appearance (settings screen). */
    fun resolve(context: Context): M3Scheme = isDark(context).let { M3Scheme(roles(context, it), it) }

    /**
     * Light and dark schemes for widgets, plus whether they should follow the launcher's
     * night mode (theme "Auto") or stay on one appearance.
     */
    fun resolveBoth(context: Context): Triple<M3Scheme, M3Scheme, Boolean> {
        val light = M3Scheme(roles(context, false), false)
        val dark = M3Scheme(roles(context, true), true)
        return when (prefs(context).getString(Prefs.THEME_CHOICE, "system")) {
            "light" -> Triple(light, light, false)
            "dark" -> Triple(dark, dark, false)
            else -> Triple(light, dark, true)
        }
    }

    // ── Custom seed (Material Color Utilities from the Material library) ────

    @SuppressLint("RestrictedApi")
    fun fromSeed(seed: Int, style: String, dark: Boolean): Map<String, Int> {
        val hct = Hct.fromInt(seed)
        val scheme: DynamicScheme = when (style) {
            "vibrant" -> SchemeVibrant(hct, dark, 0.0)
            "expressive" -> SchemeExpressive(hct, dark, 0.0)
            "fidelity" -> SchemeFidelity(hct, dark, 0.0)
            else -> SchemeTonalSpot(hct, dark, 0.0)
        }
        val m = MaterialDynamicColors()
        val colors: Map<String, DynamicColor> = mapOf(
            "primary" to m.primary(), "onPrimary" to m.onPrimary(),
            "primaryContainer" to m.primaryContainer(), "onPrimaryContainer" to m.onPrimaryContainer(),
            "secondary" to m.secondary(), "onSecondary" to m.onSecondary(),
            "secondaryContainer" to m.secondaryContainer(), "onSecondaryContainer" to m.onSecondaryContainer(),
            "tertiary" to m.tertiary(), "onTertiary" to m.onTertiary(),
            "tertiaryContainer" to m.tertiaryContainer(), "onTertiaryContainer" to m.onTertiaryContainer(),
            "error" to m.error(), "onError" to m.onError(),
            "errorContainer" to m.errorContainer(), "onErrorContainer" to m.onErrorContainer(),
            "surface" to m.surface(), "surfaceDim" to m.surfaceDim(), "surfaceBright" to m.surfaceBright(),
            "surfaceContainerLowest" to m.surfaceContainerLowest(), "surfaceContainerLow" to m.surfaceContainerLow(),
            "surfaceContainer" to m.surfaceContainer(), "surfaceContainerHigh" to m.surfaceContainerHigh(),
            "surfaceContainerHighest" to m.surfaceContainerHighest(),
            "onSurface" to m.onSurface(), "onSurfaceVariant" to m.onSurfaceVariant(),
            "outline" to m.outline(), "outlineVariant" to m.outlineVariant(),
            "inverseSurface" to m.inverseSurface(), "inverseOnSurface" to m.inverseOnSurface(),
            "inversePrimary" to m.inversePrimary(),
        )
        return colors.mapValues { (_, c) -> c.getArgb(scheme) }
    }

    // ── Dynamic (wallpaper) colors ──────────────────────────────────────────

    // Tone (L*) of each step in the Android 12+ system tonal palettes.
    private val STEPS = listOf(
        100f to 0, 99f to 10, 95f to 50, 90f to 100, 80f to 200, 70f to 300, 60f to 400,
        50f to 500, 40f to 600, 30f to 700, 20f to 800, 10f to 900, 0f to 1000,
    )

    @SuppressLint("DiscouragedApi")
    private fun step(context: Context, palette: String, step: Int): Int {
        val id = context.resources.getIdentifier("system_${palette}_$step", "color", "android")
        return context.getColor(id)
    }

    /** Palette color at an arbitrary tone, interpolated between the two nearest system steps. */
    private fun tone(context: Context, palette: String, t: Float): Int {
        for (i in 0 until STEPS.size - 1) {
            val (hiTone, hiStep) = STEPS[i]
            val (loTone, loStep) = STEPS[i + 1]
            if (t <= hiTone && t >= loTone) {
                if (t == hiTone) return step(context, palette, hiStep)
                if (t == loTone) return step(context, palette, loStep)
                val frac = (hiTone - t) / (hiTone - loTone)
                return ColorUtils.blendARGB(step(context, palette, hiStep), step(context, palette, loStep), frac)
            }
        }
        return step(context, palette, 500)
    }

    /** Tonal-spot roles built from the device's wallpaper palettes, or null below Android 12. */
    fun system(context: Context, dark: Boolean): Map<String, Int>? {
        if (!isDynamicAvailable()) return null
        return try {
            val a1 = { t: Float -> tone(context, "accent1", t) }
            val a2 = { t: Float -> tone(context, "accent2", t) }
            val a3 = { t: Float -> tone(context, "accent3", t) }
            val n1 = { t: Float -> tone(context, "neutral1", t) }
            val n2 = { t: Float -> tone(context, "neutral2", t) }
            val err = baseline(dark)
            if (!dark) mapOf(
                "primary" to a1(40f), "onPrimary" to a1(100f),
                "primaryContainer" to a1(90f), "onPrimaryContainer" to a1(10f),
                "secondary" to a2(40f), "onSecondary" to a2(100f),
                "secondaryContainer" to a2(90f), "onSecondaryContainer" to a2(10f),
                "tertiary" to a3(40f), "onTertiary" to a3(100f),
                "tertiaryContainer" to a3(90f), "onTertiaryContainer" to a3(10f),
                "surface" to n1(98f), "surfaceDim" to n1(87f), "surfaceBright" to n1(98f),
                "surfaceContainerLowest" to n1(100f), "surfaceContainerLow" to n1(96f),
                "surfaceContainer" to n1(94f), "surfaceContainerHigh" to n1(92f),
                "surfaceContainerHighest" to n1(90f),
                "onSurface" to n1(10f), "onSurfaceVariant" to n2(30f),
                "outline" to n2(50f), "outlineVariant" to n2(80f),
                "inverseSurface" to n1(20f), "inverseOnSurface" to n1(95f), "inversePrimary" to a1(80f),
            ) + err.filterKeys { it.contains("rror") }
            else mapOf(
                "primary" to a1(80f), "onPrimary" to a1(20f),
                "primaryContainer" to a1(30f), "onPrimaryContainer" to a1(90f),
                "secondary" to a2(80f), "onSecondary" to a2(20f),
                "secondaryContainer" to a2(30f), "onSecondaryContainer" to a2(90f),
                "tertiary" to a3(80f), "onTertiary" to a3(20f),
                "tertiaryContainer" to a3(30f), "onTertiaryContainer" to a3(90f),
                "surface" to n1(6f), "surfaceDim" to n1(6f), "surfaceBright" to n1(24f),
                "surfaceContainerLowest" to n1(4f), "surfaceContainerLow" to n1(10f),
                "surfaceContainer" to n1(12f), "surfaceContainerHigh" to n1(17f),
                "surfaceContainerHighest" to n1(22f),
                "onSurface" to n1(90f), "onSurfaceVariant" to n2(80f),
                "outline" to n2(60f), "outlineVariant" to n2(30f),
                "inverseSurface" to n1(90f), "inverseOnSurface" to n1(20f), "inversePrimary" to a1(40f),
            ) + err.filterKeys { it.contains("rror") }
        } catch (e: Exception) {
            null
        }
    }

    // ── Baseline M3 scheme (seed #6750A4), used when nothing else is available ──

    fun baseline(dark: Boolean): Map<String, Int> {
        val hex = if (!dark) mapOf(
            "primary" to "#6750A4", "onPrimary" to "#FFFFFF",
            "primaryContainer" to "#EADDFF", "onPrimaryContainer" to "#21005D",
            "secondary" to "#625B71", "onSecondary" to "#FFFFFF",
            "secondaryContainer" to "#E8DEF8", "onSecondaryContainer" to "#1D192B",
            "tertiary" to "#7D5260", "onTertiary" to "#FFFFFF",
            "tertiaryContainer" to "#FFD8E4", "onTertiaryContainer" to "#31111D",
            "error" to "#BA1A1A", "onError" to "#FFFFFF",
            "errorContainer" to "#FFDAD6", "onErrorContainer" to "#410002",
            "surface" to "#FEF7FF", "surfaceDim" to "#DED8E1", "surfaceBright" to "#FEF7FF",
            "surfaceContainerLowest" to "#FFFFFF", "surfaceContainerLow" to "#F7F2FA",
            "surfaceContainer" to "#F3EDF7", "surfaceContainerHigh" to "#ECE6F0",
            "surfaceContainerHighest" to "#E6E0E9",
            "onSurface" to "#1D1B20", "onSurfaceVariant" to "#49454F",
            "outline" to "#79747E", "outlineVariant" to "#CAC4D0",
            "inverseSurface" to "#322F35", "inverseOnSurface" to "#F5EFF7", "inversePrimary" to "#D0BCFF",
        ) else mapOf(
            "primary" to "#D0BCFF", "onPrimary" to "#381E72",
            "primaryContainer" to "#4F378B", "onPrimaryContainer" to "#EADDFF",
            "secondary" to "#CCC2DC", "onSecondary" to "#332D41",
            "secondaryContainer" to "#4A4458", "onSecondaryContainer" to "#E8DEF8",
            "tertiary" to "#EFB8C8", "onTertiary" to "#492532",
            "tertiaryContainer" to "#633B48", "onTertiaryContainer" to "#FFD8E4",
            "error" to "#FFB4AB", "onError" to "#690005",
            "errorContainer" to "#93000A", "onErrorContainer" to "#FFDAD6",
            "surface" to "#141218", "surfaceDim" to "#141218", "surfaceBright" to "#3B383E",
            "surfaceContainerLowest" to "#0F0D13", "surfaceContainerLow" to "#1D1B20",
            "surfaceContainer" to "#211F26", "surfaceContainerHigh" to "#2B2930",
            "surfaceContainerHighest" to "#36343B",
            "onSurface" to "#E6E0E9", "onSurfaceVariant" to "#CAC4D0",
            "outline" to "#938F99", "outlineVariant" to "#49454F",
            "inverseSurface" to "#E6E0E9", "inverseOnSurface" to "#322F35", "inversePrimary" to "#6750A4",
        )
        return hex.mapValues { (_, v) -> Color.parseColor(v) }
    }
}
