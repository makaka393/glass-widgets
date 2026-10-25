@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.glasswidgets.ui

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glasswidgets.Prefs
import app.glasswidgets.ThemeColors
import app.glasswidgets.engine.CLOCK_FACES
import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Renderer
import app.glasswidgets.engine.Ticker
import app.glasswidgets.engine.Updater
import app.glasswidgets.engine.WType
import app.glasswidgets.engine.WallpaperBlur
import app.glasswidgets.engine.Weather
import app.glasswidgets.engine.WidgetCfg
import app.glasswidgets.engine.WidgetStore
import app.glasswidgets.engine.provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    /** Bumped on resume: permissions and the wallpaper may have changed in system settings. */
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(tick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
        Updater.all(this)
        Updater.executor.execute { Ticker.schedule(this) }
    }
}

/** Global settings in SharedPreferences, mirrored into Compose; every change repaints the widgets. */
class AppSettings(private val ctx: Context) {
    private val p = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    var version by mutableIntStateOf(0)
        private set

    fun str(k: String, d: String) = (p.getString(k, d) ?: d).also { version }
    fun float(k: String, d: Float) = p.getFloat(k, d).also { version }
    fun int(k: String, d: Int) = p.getInt(k, d).also { version }

    fun set(block: SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        version++
        Updater.all(ctx)
    }
}

/** A gallery entry: a widget type, optionally one clock face. */
private data class Variant(val type: WType, val face: String? = null) {
    val label get() = face?.let { f -> CLOCK_FACES.first { it.first == f }.second.let { I18n.t(it.first, it.second) } } ?: type.label
    /** Default size like the shell's catalog. */
    val cells: Pair<Int, Int> get() = when (face) {
        "horizon" -> 3 to 2
        "dots", "segments" -> 4 to 2
        else -> type.w to type.h
    }
}

private val GALLERY: List<Variant> =
    CLOCK_FACES.map { Variant(WType.CLOCK, it.first) } + WType.entries.filter { it != WType.CLOCK }.map { Variant(it) }

@Composable
private fun App(tick: Int) {
    val ctx = LocalContext.current
    val s = remember { AppSettings(ctx) }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = remember(s.version, dark) { ThemeColors.resolve(ctx) }
    GlassTheme(scheme) { Home(s, tick) }
}

@Composable
private fun Home(s: AppSettings, tick: Int) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Glass Widgets", fontWeight = FontWeight(700)) },
                subtitle = { Text(I18n.t("Виджеты из Намиры ОС", "Widgets from Namira OS")) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { Gallery(s, tick) }
            item { Look(s, tick) }
            item { Colors(s) }
            item { WeatherSection(s, tick) }
            item {
                Text(
                    I18n.t("Шрифты: Google Sans Flex (SIL OFL 1.1), Material Symbols (Apache 2.0). Погода: open-meteo.com.",
                        "Fonts: Google Sans Flex (SIL OFL 1.1), Material Symbols (Apache 2.0). Weather: open-meteo.com."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

// ── Gallery ──────────────────────────────────────────────────────────────────

@Composable
private fun Gallery(s: AppSettings, tick: Int) {
    val ctx = LocalContext.current
    Section(I18n.t("Виджеты", "Widgets")) {
        Tile {
            Text(
                I18n.t("«Добавить» — и виджет встанет на рабочий стол. Размер меняется; долгое нажатие на виджете → «Настроить»: фон, цвет, прозрачность, стиль.",
                    "Tap Add to place a widget. Resize it freely; long-press it → Customize: background, colour, transparency, style."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                items(GALLERY) { v ->
                    val (cw, ch) = v.cells
                    val wDp = Updater.defaultDp(cw)
                    val hDp = Updater.defaultDp(ch)
                    val k = 0.78f
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size((wDp * k).dp, (hDp * k).dp).clip(RoundedCornerShape(28.dp))) {
                            WallpaperBackdrop(Modifier.fillMaxSize(), tick)
                            BitmapOf(listOf(v, s.version, tick), Modifier.fillMaxSize()) {
                                Renderer.render(ctx, WidgetCfg(v.type, face = v.face), wDp, hDp, null)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(v.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight(700))
                            FilledTonalButton(onClick = { pin(ctx, v) }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                                Sym("add", 18.sp, MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(4.dp))
                                Text(I18n.t("Добавить", "Add"))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun pin(ctx: Context, v: Variant) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val ok = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported && run {
        val cb = PendingIntent.getBroadcast(
            ctx, (v.type.ordinal shl 4) + (CLOCK_FACES.indexOfFirst { it.first == v.face } + 1),
            Intent(ctx, PinReceiver::class.java).putExtra("type", v.type.id).putExtra("face", v.face),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )
        mgr.requestPinAppWidget(ComponentName(ctx, v.type.provider), null, cb)
    }
    if (!ok) Toast.makeText(ctx, I18n.t("Долгое нажатие на рабочем столе → Виджеты → Glass Widgets", "Long-press the home screen → Widgets → Glass Widgets"), Toast.LENGTH_LONG).show()
}

/** The launcher placed a widget we asked for: give it the chosen clock face. */
class PinReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val type = WType.byId(intent.getStringExtra("type")) ?: return
        if (id < 0) return
        WidgetStore.put(ctx, id, WidgetStore.get(ctx, id, type).copy(face = intent.getStringExtra("face")))
        Updater.all(ctx)
        Updater.executor.execute { Ticker.schedule(ctx) }
    }
}

// ── Look: glass or Material You, like the shell's settings ───────────────────

@Composable
private fun Look(s: AppSettings, tick: Int) {
    val ctx = LocalContext.current
    val style = s.str(Prefs.STYLE, "glass")
    val m3 = style == "m3"
    val opacityKey = if (m3) Prefs.M3_OPACITY else Prefs.GLASS_OPACITY
    val stored = s.float(opacityKey, if (m3) 1f else 0.34f)
    var opacity by remember(style, stored) { mutableFloatStateOf(stored) }
    val access = remember(tick) { WallpaperBlur.hasAccess(ctx) }
    val blur = s.int(Prefs.BLUR_RADIUS, 48)
    var blurV by remember(blur) { mutableFloatStateOf(blur.toFloat()) }

    Section(I18n.t("Оформление", "Look")) {
        Tile {
            TileRow("auto_awesome", I18n.t("Стиль", "Style"),
                if (m3) I18n.t("Тональные поверхности Material You", "Tonal Material You surfaces")
                else I18n.t("Матовое стекло поверх размытых обоев, как на One UI", "Frosted glass over the blurred wallpaper, like One UI"))
            Connected(listOf(
                Triple("glass", I18n.t("Стекло", "Glass"), "blur_on"),
                Triple("m3", "Material You", "palette"),
            ), style) { v -> s.set { putString(Prefs.STYLE, v) } }
        }
        Tile {
            TileRow("opacity", I18n.t("Прозрачность", "Transparency"), trailing = {
                Text("${((1 - opacity) * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight(700))
            })
            Slider(
                value = opacity, onValueChange = { opacity = it },
                onValueChangeFinished = { s.set { putFloat(opacityKey, opacity) } },
                valueRange = if (m3) 0.5f..1f else 0.15f..0.7f,
            )
        }
        Tile {
            TileRow("wallpaper", I18n.t("Размытие обоев", "Wallpaper blur"),
                if (access) I18n.t("Виджеты размывают обои под собой. Коснись каждого виджета один раз — размытие совпадёт с обоями.",
                    "Widgets blur the wallpaper under them. Tap each widget once so the blur lines up.")
                else I18n.t("Нужен «Доступ ко всем файлам»: иначе Android не отдаёт картинку обоев.",
                    "Needs “All files access” — otherwise Android won't share the wallpaper."),
                trailing = {
                    if (access) Sym("check_circle", 24.sp, MaterialTheme.colorScheme.primary)
                    else FilledTonalButton(onClick = { openAllFiles(ctx) }) { Text(I18n.t("Разрешить", "Allow")) }
                })
        }
        if (access) Tile {
            TileRow("blur_medium", I18n.t("Сила размытия", "Blur strength"), trailing = {
                Text("${blurV.roundToInt()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight(700))
            })
            Slider(value = blurV, onValueChange = { blurV = it }, onValueChangeFinished = { s.set { putInt(Prefs.BLUR_RADIUS, blurV.roundToInt()) } },
                valueRange = 12f..120f)
        }
    }
}

private fun openAllFiles(ctx: Context) {
    val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ── Colours ──────────────────────────────────────────────────────────────────

private val PRESETS = listOf("#6750A4", "#0B57D0", "#00677F", "#386A20", "#7C5800", "#A63B00", "#B3261E", "#984061")

@Composable
private fun Colors(s: AppSettings) {
    val ctx = LocalContext.current
    val theme = s.str(Prefs.THEME_CHOICE, "system")
    val source = s.str(Prefs.COLOR_SOURCE, "system")
    val seed = s.str(Prefs.SEED_COLOR, "#6750A4").uppercase()
    val palette = s.str(Prefs.PALETTE_STYLE, "tonal")
    val dark = ThemeColors.isDark(ctx)
    var picker by remember { mutableStateOf(false) }

    Section(I18n.t("Цвета", "Colours")) {
        Tile {
            TileRow("contrast", I18n.t("Тема", "Theme"))
            Connected(listOf(
                Triple("system", I18n.t("Авто", "Auto"), "brightness_auto"),
                Triple("light", I18n.t("Светлая", "Light"), "light_mode"),
                Triple("dark", I18n.t("Тёмная", "Dark"), "dark_mode"),
            ), theme) { v -> s.set { putString(Prefs.THEME_CHOICE, v) } }
        }
        Tile {
            TileRow("palette", I18n.t("Цвета темы", "Theme colours"),
                if (source == "system") I18n.t("Из обоев, как на Pixel", "From the wallpaper, like Pixel") else I18n.t("Свой цвет", "Your colour"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ThemeColors.isDynamicAvailable()) {
                    val sys = remember(dark) { ThemeColors.system(ctx, dark) }
                    MorphChoice(source == "system", 60.dp, { s.set { putString(Prefs.COLOR_SOURCE, "system") } }) {
                        Quad(sys)
                        Sym("auto_awesome", 16.sp, Color.White)
                    }
                }
                PRESETS.forEach { hex ->
                    val roles = remember(hex, palette, dark) { ThemeColors.fromSeed(android.graphics.Color.parseColor(hex), palette, dark) }
                    MorphChoice(source == "custom" && seed == hex, 60.dp, {
                        s.set { putString(Prefs.COLOR_SOURCE, "custom"); putString(Prefs.SEED_COLOR, hex) }
                    }) { Quad(roles) }
                }
                MorphChoice(source == "custom" && seed !in PRESETS, 60.dp, { picker = true }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Sym("colorize", 22.sp, MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (source == "custom") Connected(listOf(
                Triple("tonal", I18n.t("Тон", "Tonal"), null),
                Triple("vibrant", I18n.t("Ярко", "Vibrant"), null),
                Triple("expressive", I18n.t("Экспрессия", "Expressive"), null),
            ), palette) { v -> s.set { putString(Prefs.PALETTE_STYLE, v) } }
        }
    }
    if (picker) ColorDialog(seed, { picker = false }) { hex ->
        s.set { putString(Prefs.COLOR_SOURCE, "custom"); putString(Prefs.SEED_COLOR, hex) }
        picker = false
    }
}

/** Swatch quarters like the Pixel wallpaper picker: primary on top, secondary and tertiary below. */
@Composable
private fun Quad(roles: Map<String, Int>?) {
    fun c(n: String) = Color(roles?.get(n) ?: 0xFF888888.toInt())
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(c("primary")))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.weight(1f).fillMaxSize().background(c("secondaryContainer")))
            Box(Modifier.weight(1f).fillMaxSize().background(c("tertiary")))
        }
    }
}

@Composable
private fun ColorDialog(initial: String, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(runCatching { android.graphics.Color.parseColor(initial) }.getOrDefault(0xFF6750A4.toInt()), it) }
    }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    val color = android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v))
    val hex = String.format("#%06X", 0xFFFFFF and color)
    var text by remember(hex) { mutableStateOf(hex) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(I18n.t("Любой цвет", "Any colour")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(20.dp)).background(Color(color)))
                Text(I18n.t("Оттенок", "Hue"), style = MaterialTheme.typography.labelMedium)
                Slider(h, { h = it }, valueRange = 0f..360f)
                Text(I18n.t("Насыщенность", "Saturation"), style = MaterialTheme.typography.labelMedium)
                Slider(sat, { sat = it })
                Text(I18n.t("Яркость", "Brightness"), style = MaterialTheme.typography.labelMedium)
                Slider(v, { v = it })
                OutlinedTextField(text, { t ->
                    text = t.uppercase().take(7)
                    runCatching { android.graphics.Color.parseColor(if (t.startsWith("#")) t else "#$t") }.getOrNull()
                        ?.takeIf { text.length == 7 }
                        ?.let { cc -> FloatArray(3).also { android.graphics.Color.colorToHSV(cc, it) }.let { h = it[0]; sat = it[1]; v = it[2] } }
                }, label = { Text("HEX") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            }
        },
        confirmButton = { Button(onClick = { onApply(hex) }) { Text(I18n.t("Готово", "Done")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(I18n.t("Отмена", "Cancel")) } },
    )
}

// ── Weather ──────────────────────────────────────────────────────────────────

@Composable
private fun WeatherSection(s: AppSettings, tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var locOk by remember(tick) { mutableStateOf(Weather.hasLocationPermission(ctx)) }
    var now by remember(tick) { mutableStateOf(Weather.cached(ctx)) }
    var busy by remember { mutableStateOf(false) }
    val units = s.str(Prefs.WEATHER_UNITS, "c")

    fun refresh() {
        busy = true
        scope.launch {
            now = withContext(Dispatchers.IO) {
                Weather.currentLocation(ctx)?.let { Weather.saveLocation(ctx, it) }
                Weather.fetch(ctx).also { Updater.all(ctx) }
            }
            busy = false
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> locOk = ok; if (ok) refresh() }

    Section(I18n.t("Погода", "Weather")) {
        Tile {
            TileRow("location_on", I18n.t("Местоположение", "Location"),
                if (locOk) I18n.t("Примерное — для погоды, восхода и заката", "Approximate — for weather, sunrise and sunset")
                else I18n.t("Нужно для погоды (open-meteo.com)", "Needed for the weather (open-meteo.com)"),
                trailing = {
                    if (locOk) Sym("check_circle", 24.sp, MaterialTheme.colorScheme.primary)
                    else FilledTonalButton(onClick = { perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text(I18n.t("Разрешить", "Allow")) }
                })
        }
        Tile {
            TileRow("thermostat", I18n.t("Единицы", "Units"))
            Connected(listOf(Triple("c", "°C", null), Triple("f", "°F", null)), units) { v ->
                s.set { putString(Prefs.WEATHER_UNITS, v) }
                refresh()
            }
        }
        Tile {
            val n = now
            TileRow(
                if (n != null) Weather.icon(n.code, n.isDay) else "cloud_off",
                if (n != null) "${n.temp.roundToInt()}° · ${Weather.describe(n.code)}" else I18n.t("Погоды пока нет", "No weather yet"),
                if (n != null) listOf(n.city, I18n.t("обновлено ", "updated ") + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(n.updated)))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                else I18n.t("Разреши местоположение и обнови", "Allow location, then refresh"),
                trailing = {
                    if (busy) LoadingIndicator(Modifier.size(40.dp))
                    else FilledTonalButton(onClick = { refresh() }, enabled = locOk) {
                        Sym("refresh", 18.sp, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                },
            )
        }
    }
}
