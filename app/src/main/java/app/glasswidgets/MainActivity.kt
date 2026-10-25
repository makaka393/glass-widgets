package app.glasswidgets

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.glasswidgets.widgets.Weather
import app.glasswidgets.widgets.WidgetBackground
import app.glasswidgets.widgets.WidgetKind
import app.glasswidgets.widgets.WidgetRenderer
import app.glasswidgets.widgets.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    /** Bumped on resume so permission states are re-read after visiting system settings. */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(resumeTick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

private val PRESETS = listOf("#6750A4", "#0B57D0", "#00677F", "#386A20", "#7C5800", "#A63B00", "#B3261E", "#984061")
private val STYLES = listOf("tonal" to "Tonal", "vibrant" to "Vibrant", "expressive" to "Expressive", "fidelity" to "Fidelity")

private enum class PreviewSize(val w: Int, val h: Int) { SMALL(150, 150), WIDE(300, 150), PILL(300, 72) }

private val WidgetKind.previewSize
    get() = when (this) {
        WidgetKind.CLOCK_THIN, WidgetKind.CLOCK_TILES, WidgetKind.WEATHER_FORECAST, WidgetKind.CLOCK_WEATHER -> PreviewSize.WIDE
        WidgetKind.CLOCK_PILL -> PreviewSize.PILL
        else -> PreviewSize.SMALL
    }

private val WidgetKind.label
    get() = when (this) {
        WidgetKind.CLOCK_STACK -> "Stack"
        WidgetKind.CLOCK_THIN -> "Thin"
        WidgetKind.CLOCK_PILL -> "Pill"
        WidgetKind.CLOCK_ANALOG -> "Analog"
        WidgetKind.CLOCK_TILES -> "Tiles"
        WidgetKind.CLOCK_DATE -> "Date"
        WidgetKind.WEATHER -> "Weather"
        WidgetKind.WEATHER_FORECAST -> "Forecast"
        WidgetKind.CLOCK_WEATHER -> "Clock · Weather"
    }

/** Settings in SharedPreferences, mirrored into Compose state; every change repaints the widgets. */
private class SettingsState(private val ctx: Context) {
    private val p = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    var version by mutableIntStateOf(0)
        private set

    fun str(key: String, def: String) = p.getString(key, def) ?: def
    fun int(key: String, def: Int) = p.getInt(key, def)
    fun bool(key: String, def: Boolean) = p.getBoolean(key, def)

    fun set(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        version++
        WidgetUpdater.updateAll(ctx)
    }
}

private fun M3Scheme.toCompose(): ColorScheme {
    fun c(name: String) = Color(roles[name] ?: 0xFFFF00FF.toInt())
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c("primary"), onPrimary = c("onPrimary"),
        primaryContainer = c("primaryContainer"), onPrimaryContainer = c("onPrimaryContainer"),
        secondary = c("secondary"), onSecondary = c("onSecondary"),
        secondaryContainer = c("secondaryContainer"), onSecondaryContainer = c("onSecondaryContainer"),
        tertiary = c("tertiary"), onTertiary = c("onTertiary"),
        tertiaryContainer = c("tertiaryContainer"), onTertiaryContainer = c("onTertiaryContainer"),
        error = c("error"), onError = c("onError"),
        errorContainer = c("errorContainer"), onErrorContainer = c("onErrorContainer"),
        background = c("surface"), onBackground = c("onSurface"),
        surface = c("surface"), onSurface = c("onSurface"),
        surfaceVariant = c("surfaceContainerHighest"), onSurfaceVariant = c("onSurfaceVariant"),
        surfaceDim = c("surfaceDim"), surfaceBright = c("surfaceBright"),
        surfaceContainerLowest = c("surfaceContainerLowest"), surfaceContainerLow = c("surfaceContainerLow"),
        surfaceContainer = c("surfaceContainer"), surfaceContainerHigh = c("surfaceContainerHigh"),
        surfaceContainerHighest = c("surfaceContainerHighest"),
        outline = c("outline"), outlineVariant = c("outlineVariant"),
        inverseSurface = c("inverseSurface"), inverseOnSurface = c("inverseOnSurface"),
        inversePrimary = c("inversePrimary"),
    )
}

@Composable
private fun App(resumeTick: Int) {
    val ctx = LocalContext.current
    val settings = remember { SettingsState(ctx) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = remember(settings.version, systemDark) { ThemeColors.resolve(ctx) }

    MaterialTheme(colorScheme = scheme.toCompose()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = WindowInsets.systemBars.asPaddingValues().let {
                    PaddingValues(start = 16.dp, end = 16.dp, top = it.calculateTopPadding() + 24.dp, bottom = it.calculateBottomPadding() + 24.dp)
                },
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { Header() }
                item { Gallery(settings, resumeTick) }
                item { Appearance(settings) }
                item { Background(settings, resumeTick) }
                item { WeatherSection(settings, resumeTick) }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(horizontal = 8.dp)) {
        Text("Glass Widgets", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            "Material You clocks and weather for your home screen",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceBright)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun Row2(icon: ImageVector, title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

// ── Gallery ──────────────────────────────────────────────────────────────────

@Composable
private fun Gallery(settings: SettingsState, resumeTick: Int) {
    val ctx = LocalContext.current
    Section("Widgets") {
        Text(
            "Tap Add to place one on your home screen. All of them are resizable.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(WidgetKind.entries.toList()) { kind ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetPreview(kind, settings.version + resumeTick)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(kind.label, style = MaterialTheme.typography.labelLarge)
                        FilledTonalButton(onClick = { pin(ctx, kind) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add")
                        }
                    }
                }
            }
        }
    }
}

/** The real widget, rendered from the same RemoteViews the launcher gets. */
@Composable
private fun WidgetPreview(kind: WidgetKind, version: Int) {
    val ctx = LocalContext.current
    val size = kind.previewSize
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
            frame.tag = version
            val rv = WidgetRenderer.render(ctx, AppWidgetManager.getInstance(ctx), Int.MAX_VALUE, kind, preview = true)
            frame.removeAllViews()
            frame.addView(rv.apply(ctx, frame))
        },
        modifier = Modifier.size(size.w.dp, size.h.dp).clip(RoundedCornerShape(24.dp)),
    )
}

private fun pin(ctx: Context, kind: WidgetKind) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val ok = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported &&
            mgr.requestPinAppWidget(ComponentName(ctx, kind.provider), null, null)
    if (!ok) Toast.makeText(ctx, "Long-press your home screen → Widgets → Glass Widgets", Toast.LENGTH_LONG).show()
}

// ── Appearance ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Appearance(settings: SettingsState) {
    val ctx = LocalContext.current
    var pickerOpen by remember { mutableStateOf(false) }
    val theme = settings.str(Prefs.THEME_CHOICE, "system").also { settings.version }
    val source = settings.str(Prefs.COLOR_SOURCE, "system")
    val seed = settings.str(Prefs.SEED_COLOR, "#6750A4").uppercase()
    val style = settings.str(Prefs.PALETTE_STYLE, "tonal")
    val dark = ThemeColors.isDark(ctx)

    Section("Appearance") {
        Row2(Icons.Rounded.Contrast, "Theme")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("system" to "Auto", "light" to "Light", "dark" to "Dark").forEachIndexed { i, (id, label) ->
                SegmentedButton(
                    selected = theme == id,
                    onClick = { settings.set { putString(Prefs.THEME_CHOICE, id) } },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(label) }
            }
        }

        Row2(Icons.Rounded.Palette, "Colors",
            if (source == "system") (if (ThemeColors.isDynamicAvailable()) "Dynamic — follows your wallpaper" else "Dynamic colors need Android 12+")
            else "Custom theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ThemeColors.isDynamicAvailable()) {
                val sys = ThemeColors.system(ctx, dark)
                Swatch(sys, selected = source == "system", badge = Icons.Rounded.AutoAwesome) {
                    settings.set { putString(Prefs.COLOR_SOURCE, "system") }
                }
            }
            PRESETS.forEach { hex ->
                val roles = remember(hex, style, dark) { ThemeColors.fromSeed(android.graphics.Color.parseColor(hex), style, dark) }
                Swatch(roles, selected = source == "custom" && seed == hex) {
                    settings.set { putString(Prefs.COLOR_SOURCE, "custom"); putString(Prefs.SEED_COLOR, hex) }
                }
            }
            val custom = source == "custom" && seed !in PRESETS
            if (custom) {
                val roles = remember(seed, style, dark) { ThemeColors.fromSeed(android.graphics.Color.parseColor(seed), style, dark) }
                Swatch(roles, selected = true) { pickerOpen = true }
            }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { pickerOpen = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Colorize, "Pick any color", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (source == "custom") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STYLES.forEach { (id, label) ->
                    FilterChip(
                        selected = style == id,
                        onClick = { settings.set { putString(Prefs.PALETTE_STYLE, id) } },
                        label = { Text(label) },
                        leadingIcon = if (style == id) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        ColorPickerDialog(initial = seed, onDismiss = { pickerOpen = false }) { hex ->
            settings.set { putString(Prefs.COLOR_SOURCE, "custom"); putString(Prefs.SEED_COLOR, hex) }
            pickerOpen = false
        }
    }
}

/** Wallpaper-picker style swatch; selection morphs the circle into a rounded square. */
@Composable
private fun Swatch(roles: Map<String, Int>?, selected: Boolean, badge: ImageVector? = null, onClick: () -> Unit) {
    val radius by animateDpAsState(if (selected) 16.dp else 26.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "r")
    val ring by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "ring")
    fun c(n: String) = Color(roles?.get(n) ?: 0xFF888888.toInt())
    Box(
        Modifier.size(60.dp).border(2.dp, ring, RoundedCornerShape(radius + 4.dp)).padding(4.dp)
            .clip(RoundedCornerShape(radius)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(c("primary")))
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(c("secondaryContainer")))
                Box(Modifier.weight(1f).fillMaxHeight().background(c("tertiary")))
            }
        }
        if (selected || badge != null) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(c("primaryContainer")), contentAlignment = Alignment.Center) {
                Icon(if (selected) Icons.Rounded.Check else badge!!, null, Modifier.size(14.dp), tint = c("onPrimaryContainer"))
            }
        }
    }
}

/** Any color: hue / saturation / brightness sliders, hex input and a live swatch. */
@Composable
private fun ColorPickerDialog(initial: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(runCatching { android.graphics.Color.parseColor(initial) }.getOrDefault(0xFF6750A4.toInt()), it) }
    }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val hex = String.format("#%06X", 0xFFFFFF and color)
    var hexText by remember(hex) { mutableStateOf(hex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick any color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(20.dp)).background(Color(color)))
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Slider(h, { h = it }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(s, { s = it })
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(v, { v = it })
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        hexText = t.uppercase().take(7)
                        runCatching { android.graphics.Color.parseColor(if (t.startsWith("#")) t else "#$t") }.getOrNull()
                            ?.takeIf { hexText.length == 7 }
                            ?.let { c -> FloatArray(3).also { android.graphics.Color.colorToHSV(c, it) }.let { h = it[0]; s = it[1]; v = it[2] } }
                    },
                    label = { Text("HEX") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                )
            }
        },
        confirmButton = { Button(onClick = { onApply(hex) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Background ───────────────────────────────────────────────────────────────

@Composable
private fun Background(settings: SettingsState, resumeTick: Int) {
    val ctx = LocalContext.current
    val access = remember(resumeTick) { WidgetBackground.hasWallpaperAccess(ctx) }
    val solidity = settings.int(Prefs.WIDGET_SOLIDITY, 100).also { settings.version }
    val blur = settings.bool(Prefs.WIDGET_BLUR, false)
    val radius = settings.int(Prefs.WIDGET_BLUR_RADIUS, 24)
    var sliderValue by remember(solidity) { mutableFloatStateOf(solidity.toFloat()) }
    var radiusValue by remember(radius) { mutableFloatStateOf(radius.toFloat()) }

    Section("Background") {
        Row2(Icons.Rounded.Layers, "Glass ↔ Solid", trailing = {
            Text("${sliderValue.toInt()}%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        })
        Slider(
            value = sliderValue, onValueChange = { sliderValue = it },
            onValueChangeFinished = { settings.set { putInt(Prefs.WIDGET_SOLIDITY, sliderValue.toInt()) } },
            valueRange = 0f..100f,
        )
        Row2(Icons.Rounded.WaterDrop, "Wallpaper blur",
            if (access) "Tap each widget once so its blur lines up with the wallpaper"
            else "Needs “All files access” to read the wallpaper — Android can't blur behind widgets by itself",
            trailing = {
                Switch(checked = blur && access, onCheckedChange = { on ->
                    if (on && !access) openAllFilesAccess(ctx)
                    settings.set { putBoolean(Prefs.WIDGET_BLUR, on) }
                })
            })
        if (blur && access) {
            Row2(Icons.Rounded.BlurOn, "Blur strength", trailing = {
                Text("${radiusValue.toInt()}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            })
            Slider(
                value = radiusValue, onValueChange = { radiusValue = it },
                onValueChangeFinished = { settings.set { putInt(Prefs.WIDGET_BLUR_RADIUS, radiusValue.toInt()) } },
                valueRange = 8f..64f,
            )
        }
    }
}

private fun openAllFilesAccess(ctx: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ── Weather ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherSection(settings: SettingsState, resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var locationOk by remember(resumeTick) {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    var now by remember { mutableStateOf(Weather.cached(ctx)) }
    var refreshing by remember { mutableStateOf(false) }
    val units = settings.str(Prefs.WEATHER_UNITS, "c").also { settings.version }

    fun refresh() {
        refreshing = true
        scope.launch {
            now = withContext(Dispatchers.IO) {
                Weather.currentLocation(ctx)?.let { Weather.saveLocation(ctx, it) }
                Weather.fetch(ctx).also { WidgetUpdater.updateAll(ctx, weatherOnly = true) }
            }
            refreshing = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationOk = granted
        if (granted) refresh()
    }

    Section("Weather") {
        Row2(Icons.Rounded.LocationOn, "Location",
            if (locationOk) "Approximate location for local weather" else "Needed for local weather (Open-Meteo)",
            trailing = {
                if (locationOk) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                else FilledTonalButton(onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text("Grant") }
            })
        Row2(Icons.Rounded.Thermostat, "Units")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("c" to "°C", "f" to "°F").forEachIndexed { i, (id, label) ->
                SegmentedButton(
                    selected = units == id,
                    onClick = {
                        settings.set { putString(Prefs.WEATHER_UNITS, id) }
                        refresh()
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        val n = now
        Row2(
            Icons.Rounded.Cloud,
            if (n != null) "${Math.round(n.temp)}° · ${Weather.label(n.code)}" else "No weather yet",
            if (n != null) listOf(n.city, "updated " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(n.updated)))
                .filter { it.isNotEmpty() }.joinToString(" · ")
            else "Grant location, then refresh",
            trailing = {
                FilledTonalButton(onClick = { refresh() }, enabled = locationOk && !refreshing) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (refreshing) "…" else "Refresh")
                }
            },
        )
    }
}
