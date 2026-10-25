# Glass Widgets

Home-screen widgets for Android from [Namira OS](https://github.com/makaka393/glass-dots) — the
same widgets as its Quickshell desktop, redrawn for the phone: Material 3 Expressive, colours from
your wallpaper (Material You) and a frosted-glass background like One UI.

## Widgets

| Widget | What it shows | Changes with size |
|---|---|---|
| **Clock** | any of 7 AOD faces: Shapes, Analog, Orbit, Dots, Planets, Horizon, Segments | the date appears from 3×3 |
| **At a glance** | date and weather right on the wallpaper | two rows add sunrise / sunset |
| **Calendar** | today large | from 3×3 — the month as a grid |
| **Day & year** | the day, month or year as dots | tap to switch the period |
| **Weather** | now, hourly, daily (open-meteo.com) | row → square → hourly → hourly + daily |
| **Forecast** | smooth temperature curve | three rows add the week |
| **Sun** | the day's arc, sunrise and sunset (computed on the phone) | one row → arc |

## Look

- **Style** — *Glass* (frosted surfaces over the blurred wallpaper) or *Material You* (tonal surfaces),
  with a global transparency and blur strength, like the shell's settings.
- **Customize each widget** (long-press → Customize, or right after adding) — like widget settings on
  Samsung: background *Blur / Colour / None*, theme colour (primary, secondary, tertiary, neutral),
  transparency, and the clock face or period.
- Colours come from the wallpaper (Android 12+) or a colour you pick; light, dark or automatic theme.

Android can't blur what's behind a widget, so the app blurs the wallpaper itself and gives each
widget the piece under it. That needs **All files access** (Android only shares the wallpaper image
with it), and one tap on each widget so its blur lines up with the wallpaper.

## Build

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Needs JDK 17 and the Android SDK (platform 36). Release builds are signed with the debug key.

## How it works

Every widget is drawn on a `Canvas` by a painter ported from the shell's QML
(`app/src/main/java/app/glasswidgets/engine/painters`), so shapes, fonts and proportions match the
desktop. Clocks are redrawn on the minute with a non-wake-up alarm, so nothing runs while the
screen is off. The settings app uses Jetpack Compose with Material 3 Expressive components.

## Licence

GPL-3.0, like Namira OS (see `LICENSE`). Fonts: Google Sans Flex — SIL Open Font License 1.1
(`app/src/main/assets/licenses`), Material Symbols — Apache 2.0.
