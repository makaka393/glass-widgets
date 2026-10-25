package app.glasswidgets.engine.painters

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.glasswidgets.engine.CLOCK_FACES
import app.glasswidgets.engine.Col
import app.glasswidgets.engine.Draw
import app.glasswidgets.engine.I18n
import app.glasswidgets.engine.Ink
import app.glasswidgets.engine.Painter
import app.glasswidgets.engine.Scene
import app.glasswidgets.engine.Shapes
import app.glasswidgets.engine.Sun
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * WClock.qml + AodClock.qml and its faces: the AOD clock faces as a desktop widget.
 * Plates: "Analog" and "Shapes" carry their own shaped surfaces; "Orbit" and "Planets" sit on a
 * circle; the rest on a rounded plate sized to the face. Everything fits the widget whole.
 */
object ClockPainter : Painter {
    private const val K = 0.5f

    /** Colours of the face (AodClock ink). */
    private class Pal(
        val fg: Int, val dim: Int, val primary: Int, val tertiary: Int, val base: Int,
        val fill: Int, val onFill: Int, val date: Int, val primaryFg: Int,
    )

    override fun paint(s: Scene) {
        val face = s.cfg.face?.takeIf { f -> CLOCK_FACES.any { it.first == f } } ?: "analog"
        val dated = s.H >= 300 && s.W >= 300
        val bare = s.cfg.look.bg == "none"
        val inner = face == "analog" || face == "bubble"
        val round = face == "orbit" || face == "planets"

        val ws = s.ink(tone = "neutral", bg = "blur")
        val t = s.t
        val mono = t.mono
        val pal = Pal(
            fg = if (mono) Color.WHITE else ws.fg,
            dim = if (mono) Color.WHITE else ws.fgDim,
            primary = if (mono) Color.WHITE else Col.legible(t.primary, ws.seen, 3.0),
            tertiary = if (mono) Color.WHITE else Col.legible(t.tertiary, ws.seen, 3.0),
            base = ws.seen,
            fill = if (mono) Color.WHITE else t.primaryContainer,
            onFill = if (mono) t.primaryFg else t.primaryContainerFg,
            date = if (mono) Color.WHITE else if (bare || inner) Col.alpha(ws.wallInk, 0.85f) else ws.fgDim,
            primaryFg = t.primaryFg,
        )

        val (iw, ih) = size(s, face, dated)
        val inset = if (bare || inner) 8f else if (round) 22f else 34f
        val scale = min((s.W - 2 * inset) / iw, (s.H - 2 * inset) / ih)

        if (!bare && !inner) {
            val fw = iw * scale + if (round) 18f else 40f
            val fh = ih * scale + if (round) 18f else 32f
            val pw = if (round) min(s.W, max(fw, fh)) else min(s.W, fw)
            val ph = if (round) pw else min(s.H, fh)
            val r = RectF((s.W - pw) / 2, (s.H - ph) / 2, (s.W + pw) / 2, (s.H + ph) / 2)
            s.surface(r, ws, shape = if (round) "circle" else "rect", radius = min(40f, ph / 2))
        }

        val c = s.c
        c.save()
        c.translate((s.W - iw * scale) / 2, (s.H - ih * scale) / 2)
        c.scale(scale, scale)
        val surfaced = !bare && inner
        when (face) {
            "bubble" -> bubble(s, pal, iw, dated, surfaced)
            "orbit" -> orbit(s, pal, iw, dated)
            "dots" -> dots(s, pal, iw, dated)
            "planets" -> planets(s, pal, iw, dated)
            "horizon" -> horizon(s, pal, iw, dated)
            "segments" -> segments(s, pal, iw, dated)
            else -> analog(s, pal, iw, dated, surfaced, ws)
        }
        c.restore()
    }

    // ── layout ────────────────────────────────────────────────────────────────

    private fun hh(s: Scene) = "%02d".format(s.now.get(Calendar.HOUR_OF_DAY))
    private fun mm(s: Scene) = "%02d".format(s.now.get(Calendar.MINUTE))

    private fun dateSize(face: String) = max(8f, (if (face == "bubble") 24f else 26f) * K)
    private fun dateHeight(s: Scene, face: String) = s.d.lineHeight(dateSize(face), 550)
    private fun dateWidth(s: Scene, face: String) = s.d.width(I18n.shortDate(s.now), dateSize(face), 550)

    /** Implicit size of the whole face column (main part + date), like AodClock's implicitWidth/Height. */
    private fun size(s: Scene, face: String, dated: Boolean): Pair<Float, Float> {
        val (mw, mh, spacing) = when (face) {
            "bubble" -> Triple(290 * K, 290 * K, 22 * K)
            "orbit" -> Triple(400 * K, 400 * K, 30 * K)
            "dots" -> Triple(4 * (4 * 29 * K + 22 * K) + 22 * K + 4 * 24 * K, 6 * 29 * K + 22 * K, 34 * K)
            "planets" -> Triple(444 * K, 444 * K, 30 * K)
            "horizon" -> Triple(600 * K, 250 * K + 20 * K + 100 * K * 0.92f, 20 * K)
            "segments" -> Triple(4 * 118 * K + 30 * K + 4 * 26 * K, 216 * K, 34 * K)
            else -> Triple(330 * K, 330 * K, 26 * K)
        }
        if (!dated) return mw to mh
        return max(mw, dateWidth(s, face)) to mh + spacing + dateHeight(s, face)
    }

    private fun date(s: Scene, pal: Pal, face: String, colW: Float, top: Float) {
        val z = dateSize(face)
        s.d.text(s.c, I18n.shortDate(s.now), colW / 2, top + s.d.lineHeight(z, 550) / 2, z, 550, pal.date, Draw.Align.CENTER)
    }

    private fun digitRow(s: Scene, chars: List<Pair<String, Int>>, cx: Float, cy: Float, size: Float, weight: Int) {
        val total = chars.sumOf { s.d.glyphWidth(it.first, size, weight).toDouble() }.toFloat()
        var x = cx - total / 2
        for ((ch, color) in chars) {
            val w = s.d.glyphWidth(ch, size, weight)
            s.d.glyph(s.c, ch, x + w / 2, cy, size, weight, color)
            x += w
        }
    }

    // ── Analog: hands on a scalloped "cookie" ────────────────────────────────

    private fun analog(s: Scene, pal: Pal, colW: Float, dated: Boolean, surfaced: Boolean, ws: Ink) {
        val d = 330 * K
        val ox = (colW - d) / 2
        val cx = ox + d / 2
        val cy = d / 2
        val dial = RectF(ox, 0f, ox + d, d)
        if (surfaced) {
            s.surface(dial, ws, shape = "cookie", lobes = 12)
        } else {
            val p = Path()
            val n = 240
            for (i in 0..n) {
                val a = i.toDouble() / n * PI * 2
                val r = d / 2 * (0.95 + 0.05 * cos(a * 12))
                val x = (cx + r * cos(a)).toFloat()
                val y = (cy + r * sin(a)).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            s.fill(p, Col.alpha(pal.primary, 0.16f))
        }
        // hour dots
        for (i in 0 until 12) {
            val a = i / 12.0 * PI * 2
            val r = d * 0.36f
            val w = (if (i % 3 == 0) 10 else 6) * K
            s.circle((cx + r * sin(a)).toFloat(), (cy - r * cos(a)).toFloat(), w / 2,
                if (i % 3 == 0) pal.primary else Col.alpha(pal.fg, 0.4f))
        }
        val h = s.now.get(Calendar.HOUR_OF_DAY)
        val m = s.now.get(Calendar.MINUTE)
        hand(s, cx, cy, 22 * K, d * 0.27f, ((h % 12) * 30 + m * 0.5f), pal.primary)
        hand(s, cx, cy, 12 * K, d * 0.39f, m * 6f, pal.fg)
        // centre: ring of primary around the base colour
        s.circle(cx, cy, 13 * K, pal.primary)
        s.circle(cx, cy, 13 * K - 6 * K, pal.base)
        if (dated) date(s, pal, "analog", colW, d + 26 * K)
    }

    /** A rounded hand from just below the centre to [len] above it, turned [deg] clockwise. */
    private fun hand(s: Scene, cx: Float, cy: Float, w: Float, len: Float, deg: Float, color: Int) {
        val c = s.c
        c.save()
        c.rotate(deg, cx, cy)
        s.roundRect(RectF(cx - w / 2, cy - len, cx + w / 2, cy + w / 2), w / 2, color)
        c.restore()
    }

    // ── Shapes: each digit in its own M3 Expressive shape ────────────────────

    private fun bubble(s: Scene, pal: Pal, colW: Float, dated: Boolean, surfaced: Boolean) {
        val t = s.t
        val hh = hh(s)
        val mm = mm(s)
        data class Cell(val ch: String, val bg: Int, val fg: Int, val shape: String)
        val cells = if (t.mono) listOf(
            Cell(hh[0].toString(), pal.fill, pal.onFill, "cookie"), Cell(hh[1].toString(), pal.fill, pal.onFill, "squircle"),
            Cell(mm[0].toString(), pal.fill, pal.onFill, "clover"), Cell(mm[1].toString(), pal.fill, pal.onFill, "hex"),
        ) else listOf(
            Cell(hh[0].toString(), t.primaryContainer, t.primaryContainerFg, "cookie"),
            Cell(hh[1].toString(), t.tertiaryContainer, t.tertiaryContainerFg, "squircle"),
            Cell(mm[0].toString(), t.secondaryContainer, t.secondaryContainerFg, "clover"),
            Cell(mm[1].toString(), t.primary, t.primaryFg, "hex"),
        )
        val cell = 140 * K
        val gap = 10 * K
        val gridW = 2 * cell + gap
        val ox = (colW - gridW) / 2
        cells.forEachIndexed { i, cc ->
            val x = ox + (i % 2) * (cell + gap)
            val y = (i / 2) * (cell + gap)
            val r = RectF(x, y, x + cell, y + cell)
            var digit = cc.fg
            if (surfaced) {
                val ink = s.ink(tone = "neutral", bg = "blur", tintOverride = cc.bg)
                s.surface(r, ink, shape = cc.shape)
                digit = Col.legible(cc.fg, ink.seen, 4.5)
            } else {
                s.fill(Shapes.path(cc.shape, r), Col.alpha(cc.bg, 0.92f))
            }
            s.d.glyph(s.c, cc.ch, r.centerX(), r.centerY(), 92 * K, 640, digit)
        }
        if (dated) date(s, pal, "bubble", colW, 2 * cell + gap + 22 * K)
    }

    // ── Orbit: time inside a ring, a wavy arc of the hour's minutes ──────────

    private fun orbit(s: Scene, pal: Pal, colW: Float, dated: Boolean) {
        val d = 400 * K
        val ox = (colW - d) / 2
        val cx = ox + d / 2
        val cy = d / 2
        val rad = d / 2 - 34 * K
        val m = s.now.get(Calendar.MINUTE)
        val h = s.now.get(Calendar.HOUR_OF_DAY)
        val sweep = max(0.004, m / 60.0) * PI * 2
        val sweepDeg = (sweep * 180 / PI).toFloat()

        // rest of the hour — thin track
        val track = Path().apply {
            addArc(RectF(cx - rad, cy - rad, cx + rad, cy + rad), -90 + sweepDeg + 7, max(0f, 360 - sweepDeg - 14))
        }
        s.stroke(track, Col.alpha(pal.fg, 0.14f), 6 * K)

        // minutes gone — a wave that settles at both ends
        val amp = 7 * K
        val waves = 20
        val n = max(2, ceil(sweep / (PI * 2) * 360).toInt())
        val wave = Path()
        for (i in 0..n) {
            val a = -PI / 2 + sweep * i / n
            val edge = min(1.0, min(i, n - i) / 6.0)
            val r = rad + amp * edge * sin((a + PI / 2) * waves)
            val x = (cx + r * cos(a)).toFloat()
            val y = (cy + r * sin(a)).toFloat()
            if (i == 0) wave.moveTo(x, y) else wave.lineTo(x, y)
        }
        s.stroke(wave, pal.primary, 11 * K)

        // hour — a dot on the outer orbit
        val ha = ((h % 12) + m / 60.0) / 12 * PI * 2
        val hr = d / 2 - 9 * K
        s.circle((cx + hr * sin(ha)).toFloat(), (cy - hr * cos(ha)).toFloat(), 9 * K, pal.tertiary)

        val hh = hh(s)
        val mm = mm(s)
        digitRow(s, listOf(hh[0].toString() to pal.fg, hh[1].toString() to pal.fg, ":" to pal.primary,
            mm[0].toString() to pal.fg, mm[1].toString() to pal.fg), cx, cy, 104 * K, 480)
        if (dated) date(s, pal, "orbit", colW, d + 30 * K)
    }

    // ── Dots: 5×7 dot-matrix digits ──────────────────────────────────────────

    private val glyphs = mapOf(
        '0' to intArrayOf(14, 17, 19, 21, 25, 17, 14), '1' to intArrayOf(4, 12, 4, 4, 4, 4, 14),
        '2' to intArrayOf(14, 17, 1, 2, 4, 8, 31), '3' to intArrayOf(14, 17, 1, 6, 1, 17, 14),
        '4' to intArrayOf(2, 6, 10, 18, 31, 2, 2), '5' to intArrayOf(31, 16, 30, 1, 1, 17, 14),
        '6' to intArrayOf(6, 8, 16, 30, 17, 17, 14), '7' to intArrayOf(31, 1, 2, 4, 8, 8, 8),
        '8' to intArrayOf(14, 17, 17, 14, 17, 17, 14), '9' to intArrayOf(14, 17, 17, 15, 1, 2, 12),
    )

    private fun dots(s: Scene, pal: Pal, colW: Float, dated: Boolean) {
        val dot = 22 * K
        val pitch = 29 * K
        val mw = 4 * pitch + dot
        val rowW = 4 * mw + dot + 4 * 24 * K
        var x = (colW - rowW) / 2
        val off = Col.alpha(pal.fg, 0.07f)
        fun matrix(ch: Char?, lit: Int) {
            val cols = if (ch == null) 1 else 5
            for (row in 0 until 7) for (col in 0 until cols) {
                val on = if (ch == null) row == 2 || row == 4 else ((glyphs[ch] ?: glyphs.getValue('0'))[row] shr (4 - col) and 1) == 1
                val cx = x + col * pitch + dot / 2
                val cy = row * pitch + dot / 2
                s.circle(cx, cy, dot / 2 * if (on) 1f else 0.5f, if (on) lit else off)
            }
            x += (if (ch == null) dot else mw) + 24 * K
        }
        val hh = hh(s)
        val mm = mm(s)
        matrix(hh[0], pal.primary)
        matrix(hh[1], pal.primary)
        matrix(null, pal.tertiary)
        matrix(mm[0], pal.fg)
        matrix(mm[1], pal.fg)
        if (dated) date(s, pal, "dots", colW, 6 * pitch + dot + 34 * K)
    }

    // ── Planets: the hour in a "sun", minutes a planet on the outer orbit ────

    private fun planets(s: Scene, pal: Pal, colW: Float, dated: Boolean) {
        val d = 444 * K
        val ox = (colW - d) / 2
        val cx = ox + d / 2
        val cy = d / 2
        val rOut = 176 * K
        val rIn = 110 * K
        val m = s.now.get(Calendar.MINUTE)
        val h = s.now.get(Calendar.HOUR_OF_DAY)
        val minDeg = m * 6f
        val hourDeg = ((h % 12) + m / 60f) * 30f
        fun px(deg: Float, r: Float) = cx + r * sin(deg * PI / 180).toFloat()
        fun py(deg: Float, r: Float) = cy - r * cos(deg * PI / 180).toFloat()

        val outer = Path().apply { addCircle(cx, cy, rOut, Path.Direction.CW) }
        val innerP = Path().apply { addCircle(cx, cy, rIn, Path.Direction.CW) }
        s.stroke(outer, Col.alpha(pal.fg, 0.13f), 3 * K)
        s.stroke(innerP, Col.alpha(pal.fg, 0.09f), 3 * K)
        if (minDeg % 360 > 0) {
            val gone = Path().apply { addArc(RectF(cx - rOut, cy - rOut, cx + rOut, cy + rOut), -90f, minDeg % 360) }
            s.stroke(gone, Col.alpha(pal.primary, 0.55f), 5 * K)
        }

        // the sun: hour
        val c = s.c
        c.save()
        c.rotate(minDeg / 12, cx, cy)
        val sun = 150 * K
        s.fill(Shapes.path("cookie", RectF(cx - sun / 2, cy - sun / 2, cx + sun / 2, cy + sun / 2), lobes = 12), pal.fill)
        c.restore()
        val hh = hh(s)
        digitRow(s, listOf(hh[0].toString() to pal.onFill, hh[1].toString() to pal.onFill), cx, cy, 66 * K, 680)

        // the moon: where the hour is
        s.circle(px(hourDeg, rIn), py(hourDeg, rIn), 13 * K, pal.tertiary)

        // the planet: minutes
        val pxm = px(minDeg, rOut)
        val pym = py(minDeg, rOut)
        s.circle(pxm, pym, 44 * K, pal.primary)
        val mm = mm(s)
        digitRow(s, listOf(mm[0].toString() to pal.primaryFg, mm[1].toString() to pal.primaryFg), pxm, pym, 38 * K, 720)
        if (dated) date(s, pal, "planets", colW, d + 30 * K)
    }

    // ── Horizon: sun or moon on an arc over the horizon ──────────────────────

    private fun horizon(s: Scene, pal: Pal, colW: Float, dated: Boolean) {
        val (lat, lon) = s.place
        val sun = Sun.times(s.now, lat, lon)
        val hourNow = Sun.hourOf(s.now)
        val day = hourNow >= sun[0] && hourNow < sun[1]
        val t = if (day) (hourNow - sun[0]) / (sun[1] - sun[0])
                else ((hourNow - sun[1] + 24) % 24) / (24 - (sun[1] - sun[0]))
        val w = 600 * K
        val rx = 250 * K
        val ry = 190 * K
        val baseY = 236 * K
        val ox = (colW - w) / 2
        val cx = ox + w / 2
        val ang = PI * (1 - t)
        val body = if (day) pal.primary else pal.tertiary

        if (!day) {
            val stars = listOf(0.16 to 0.30, 0.27 to 0.12, 0.41 to 0.36, 0.52 to 0.08, 0.63 to 0.30, 0.74 to 0.14, 0.86 to 0.34, 0.35 to 0.60, 0.68 to 0.58)
            stars.forEachIndexed { i, (sx, sy) ->
                val sz = (if (i % 3 == 0) 7 else 4) * K
                s.circle(ox + sx.toFloat() * w + sz / 2, sy.toFloat() * baseY + sz / 2, sz / 2,
                    Col.alpha(pal.fg, if (i % 3 == 0) 0.55f else 0.32f))
            }
        }
        val oval = RectF(cx - rx, baseY - ry, cx + rx, baseY + ry)
        // whole path — dotted
        val dotted = s.paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 4 * K; strokeCap = Paint.Cap.ROUND
            color = Col.alpha(pal.fg, 0.22f)
            pathEffect = DashPathEffect(floatArrayOf(0.1f * 4 * K, 3.2f * 4 * K), 0f)
        }
        s.c.drawArc(oval, 180f, 180f, false, dotted)
        // the part already travelled
        s.stroke(Path().apply { addArc(oval, 180f, max(0.5f, 180f * t.toFloat())) }, body, 6 * K)
        // horizon
        s.stroke(Path().apply { moveTo(ox + 14 * K, baseY); lineTo(ox + w - 14 * K, baseY) }, Col.alpha(pal.fg, 0.30f), 4 * K)

        val bx = (cx + rx * cos(ang)).toFloat()
        val by = (baseY - ry * sin(ang)).toFloat()
        val bd = 76 * K
        s.circle(bx, by, bd * 1.7f / 2, Col.alpha(body, 0.16f))
        if (day) s.fill(Shapes.path("cookie", RectF(bx - bd / 2, by - bd / 2, bx + bd / 2, by + bd / 2), lobes = 10), pal.primary)
        else s.d.icon(s.c, "bedtime", bx, by, 70 * K, pal.tertiary)

        // under the horizon: behind — now — ahead
        val rowTop = 250 * K + 20 * K
        val rowH = 100 * K * 0.92f
        val rowCy = rowTop + rowH / 2
        val hh = hh(s)
        val mm = mm(s)
        digitRow(s, listOf(hh[0].toString() to pal.fg, hh[1].toString() to pal.fg, ":" to body,
            mm[0].toString() to pal.fg, mm[1].toString() to pal.fg), cx, rowCy, 100 * K, 520)
        val iz = max(8f, 26 * K)
        val lz = max(8f, 21 * K)
        val colH = iz + s.d.lineHeight(lz, 600)
        fun side(icon: String, time: Double, left: Boolean) {
            val x = if (left) ox + 8 * K else ox + w - 8 * K
            val top = rowCy - colH / 2
            val label = Sun.hm(time)
            val lw = s.d.width(label, lz, 600)
            val iconX = if (left) x + iz / 2 else x - iz / 2
            s.d.icon(s.c, icon, iconX, top + iz / 2, iz, pal.dim)
            s.d.text(s.c, label, if (left) x else x - lw, top + iz + s.d.lineHeight(lz, 600) / 2, lz, 600, pal.dim)
        }
        side(if (day) "wb_twilight" else "bedtime", if (day) sun[0] else sun[1], true)
        side(if (day) "bedtime" else "wb_twilight", if (day) sun[1] else sun[0], false)
        if (dated) date(s, pal, "horizon", colW, rowTop + rowH + 20 * K)
    }

    // ── Segments: seven-segment digits from soft pills ───────────────────────

    private val segs = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
        '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abfgcd",
    )

    private fun segments(s: Scene, pal: Pal, colW: Float, dated: Boolean) {
        val w = 118 * K
        val h = 216 * K
        val th = 30 * K
        val gap = 5 * K
        val vlen = (h - th) / 2 - 2 * gap
        val rowW = 4 * w + 30 * K + 4 * 26 * K
        var x = (colW - rowW) / 2
        val off = Col.alpha(pal.fg, 0.07f)
        fun digit(ch: Char, lit: Int) {
            val on = segs[ch] ?: ""
            val parts = listOf(
                'a' to RectF(th / 2 + gap, 0f, w - th / 2 - gap, th),
                'g' to RectF(th / 2 + gap, (h - th) / 2, w - th / 2 - gap, (h + th) / 2),
                'd' to RectF(th / 2 + gap, h - th, w - th / 2 - gap, h),
                'f' to RectF(0f, th / 2 + gap, th, th / 2 + gap + vlen),
                'b' to RectF(w - th, th / 2 + gap, w, th / 2 + gap + vlen),
                'e' to RectF(0f, h / 2 + gap, th, h / 2 + gap + vlen),
                'c' to RectF(w - th, h / 2 + gap, w, h / 2 + gap + vlen),
            )
            for ((id, r) in parts) {
                val lit2 = id in on
                val rr = RectF(r).apply { offset(x, 0f) }
                if (!lit2) {   // dim segments are a touch smaller
                    val dx = rr.width() * 0.05f
                    val dy = rr.height() * 0.05f
                    rr.inset(dx, dy)
                }
                s.roundRect(rr, min(rr.width(), rr.height()) / 2, if (lit2) lit else off)
            }
            x += w + 26 * K
        }
        val hh = hh(s)
        val mm = mm(s)
        digit(hh[0], pal.primary)
        digit(hh[1], pal.primary)
        // colon
        for (f in listOf(0.31f, 0.69f)) s.circle(x + 15 * K, h * f, 15 * K, pal.tertiary)
        x += 30 * K + 26 * K
        digit(mm[0], pal.fg)
        digit(mm[1], pal.fg)
        if (dated) date(s, pal, "segments", colW, h + 34 * K)
    }
}
