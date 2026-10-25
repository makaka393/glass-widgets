package app.glasswidgets

internal object Prefs {
    const val FILE = "glass_widgets"
    const val THEME_CHOICE = "theme_choice"          // system | light | dark
    const val COLOR_SOURCE = "color_source"          // system | custom
    const val SEED_COLOR = "seed_color"              // #RRGGBB
    const val PALETTE_STYLE = "palette_style"        // tonal | vibrant | expressive | fidelity
    /** "glass" — frosted surfaces over the blurred wallpaper (One UI); "m3" — tonal Material You. */
    const val STYLE              = "style"
    const val GLASS_OPACITY      = "glass_opacity"      // 0.02…0.7, default 0.34 (like the Linux shell)
    const val M3_OPACITY         = "m3_opacity"         // 0.5…1, default 1
    const val BLUR_RADIUS        = "blur_radius"        // dp
    const val WEATHER_UNITS      = "weather_units"
    const val WEATHER_LAT        = "weather_lat"
    const val WEATHER_LON        = "weather_lon"
    const val WEATHER_CITY       = "weather_city"
    const val WEATHER_JSON       = "weather_json"
    const val WEATHER_UPDATED    = "weather_updated"
}
