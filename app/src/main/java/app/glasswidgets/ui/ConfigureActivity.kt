@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.glasswidgets.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glasswidgets.ThemeColors
import app.glasswidgets.engine.CLOCK_FACES
import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Look
import app.glasswidgets.engine.Renderer
import app.glasswidgets.engine.Ticker
import app.glasswidgets.engine.Updater
import app.glasswidgets.engine.WTheme
import app.glasswidgets.engine.WType
import app.glasswidgets.engine.WidgetCfg
import app.glasswidgets.engine.WidgetStore
import kotlin.math.roundToInt

/**
 * "Customize widget" — WidgetTuner.qml as a screen, like widget settings on Samsung: style (clock
 * face, period), background (blur / colour / none), theme colour and transparency. Everything
 * applies to the live preview at once; Done saves, Reset goes back to the defaults.
 */
class ConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // leaving without "Done" keeps the widget (with its current look)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val type = Updater.typeOf(this, id)
        if (type == null) { finish(); return }
        val mgr = AppWidgetManager.getInstance(this)
        val (w, h) = Updater.sizeDp(mgr, id, type)
        setContent {
            val ctx = LocalContext.current
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            GlassTheme(remember(dark) { ThemeColors.resolve(ctx) }) {
                Tuner(type, WidgetStore.get(this, id, type), w, h, onDone = { cfg ->
                    WidgetStore.put(this, id, cfg)
                    Updater.executor.execute { Updater.update(this, mgr, id, type); Ticker.schedule(this) }
                    finish()
                })
            }
        }
    }
}

@Composable
private fun Tuner(type: WType, initial: WidgetCfg, wDp: Float, hDp: Float, onDone: (WidgetCfg) -> Unit) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(initial) }
    val theme = remember { WTheme.load(ctx) }
    val bg = cfg.look.bg ?: type.bg
    val tone = cfg.look.tone ?: type.tone
    val alpha = cfg.look.alpha ?: theme.defaultAlpha
    fun look(l: Look) { cfg = cfg.copy(look = l) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(WindowInsets.systemBars.asPaddingValues()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // live preview over the wallpaper
            val k = minOf(1f, 340f / wDp)
            Box(
                Modifier.fillMaxWidth().height((hDp * k + 48).dp).clip(RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center,
            ) {
                WallpaperBackdrop(Modifier.fillMaxSize())
                BitmapOf(cfg, Modifier.size((wDp * k).dp, (hDp * k).dp)) { Renderer.render(ctx, cfg, wDp, hDp, null) }
            }

            // header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Sym(type.icon, 24.sp, MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column {
                    Text(type.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight(750))
                    Text(I18n.t("Настроить виджет", "Customize widget"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Section(null) {
                // style: clock face
                if (type == WType.CLOCK) Tile {
                    Label(I18n.t("Циферблат", "Clock face"))
                    val face = cfg.face ?: "analog"
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 4) {
                        CLOCK_FACES.forEach { (id, names) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                                MorphChoice(face == id, 72.dp, { cfg = cfg.copy(face = id) }) {
                                    Box(Modifier.fillMaxSize().background(Color(0x8C000000)), contentAlignment = Alignment.Center) {
                                        BitmapOf(id, Modifier.fillMaxSize()) {
                                            Renderer.render(ctx, WidgetCfg(WType.CLOCK, Look(bg = "none"), face = id), 150f, 150f, null, dark = true)
                                        }
                                    }
                                }
                                Text(I18n.t(names.first, names.second), style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight(if (face == id) 750 else 550),
                                    color = if (face == id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // style: period of "Day & year"
                if (type == WType.PROGRESS) Tile {
                    Label(I18n.t("Период", "Period"))
                    Connected(listOf(
                        Triple("day", I18n.t("День", "Day"), null),
                        Triple("month", I18n.t("Месяц", "Month"), null),
                        Triple("year", I18n.t("Год", "Year"), null),
                    ), cfg.mode ?: "") { cfg = cfg.copy(mode = it) }
                }
                // background
                Tile {
                    Label(I18n.t("Фон", "Background"))
                    Connected(listOf(
                        Triple("blur", I18n.t("Размытие", "Blur"), "blur_on"),
                        Triple("solid", I18n.t("Цвет", "Color"), "format_color_fill"),
                        Triple("none", I18n.t("Без фона", "None"), "hide_source"),
                    ), bg) { look(cfg.look.copy(bg = it)) }
                }
                // theme colour: halves of the container and the colour itself, like Pixel and Galaxy
                Tile {
                    Label(I18n.t("Цвет", "Color"))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            Triple("primary", theme.primaryContainer to theme.primary, I18n.t("Основной", "Primary")),
                            Triple("secondary", theme.secondaryContainer to theme.secondary, I18n.t("Второй", "Secondary")),
                            Triple("tertiary", theme.tertiaryContainer to theme.tertiary, I18n.t("Третий", "Tertiary")),
                            Triple("neutral", theme.surfaceHigh to theme.fgVariant, I18n.t("Нейтральный", "Neutral")),
                        ).forEach { (id, cols, _) ->
                            MorphChoice(tone == id, 56.dp, { look(cfg.look.copy(tone = id)) }) {
                                Box(Modifier.fillMaxSize().background(Color(cols.first))) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(Color(cols.second)))
                                }
                            }
                        }
                    }
                }
                // transparency (nothing to make transparent without a background)
                if (bg != "none") Tile {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(I18n.t("Прозрачность", "Transparency"))
                        Spacer(Modifier.weight(1f))
                        Text("${((1 - alpha) * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight(700))
                    }
                    Slider(value = 1 - alpha, onValueChange = { look(cfg.look.copy(alpha = ((1 - it) * 100).roundToInt() / 100f)) })
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { cfg = WidgetCfg(type) }) {
                    Sym("restart_alt", 18.sp, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(I18n.t("Сбросить", "Reset"))
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onDone(cfg) }) {
                    Sym("check", 18.sp, MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(I18n.t("Готово", "Done"), fontWeight = FontWeight(750))
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight(700), color = MaterialTheme.colorScheme.primary)
