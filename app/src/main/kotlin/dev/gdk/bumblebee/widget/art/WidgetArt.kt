package dev.gdk.bumblebee.widget.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import dev.gdk.bumblebee.ui.sharecard.CardFonts
import dev.gdk.bumblebee.ui.sharecard.ShareText
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Colours and type every widget shares, matching the app. */
data class WidgetLook(val accent: Int, val fonts: CardFonts, val density: Float)

data class BikeWidget(
    val name: String,
    val regNo: String?,
    val fuelBars: Int?,
    val rangeKm: Int?,
    val odoKm: Int?,
    val linked: Boolean,
    val health: Int?,
    val liveKm: Double?,
    val bike: Bitmap?,
)

data class FuelWidget(val bars: Int?, val rangeKm: Int?, val kmPerL: Double?, val sinceFill: String?)

data class TodayWidget(
    val todayKm: Double,
    /** Last seven days, oldest first, today last. */
    val week: List<Double>,
    val dayLetters: List<String>,
    val streak: Int,
    val monthKm: Double,
    val goalKm: Double?,
)

data class CareLine(val title: String, val caption: String, val fraction: Float, val urgent: Boolean)

data class CareWidget(val lines: List<CareLine>)

data class LastRideWidget(
    val label: String,
    val when_: String,
    val km: Double,
    val minutes: Int,
    val maxKmh: Int,
    /** Track squashed into 0..1 on both axes, y down. */
    val route: List<Pair<Float, Float>>,
)

data class PlacesWidget(val places: List<String>)

/**
 * Home-screen widgets drawn as one image each, in the app's dark, colour-blocked style: chunky
 * Anton numbers, Rajdhani labels and the rider's accent. Sizes are in pixels; [WidgetLook.density]
 * converts the app's dp so text keeps its size on any phone.
 */
object WidgetArt {
    private const val BASE = 0xFF171717.toInt()
    private const val RAISED = 0xFF2A2A2A.toInt()
    private const val INK = 0xFF111111.toInt()
    private const val TEXT = 0xFFF4F5F7.toInt()
    private const val MUTED = 0xFF9A9A9A.toInt()
    const val YELLOW = 0xFFF7DD48.toInt()
    const val LAVENDER = 0xFFA39CE9.toInt()
    const val MINT = 0xFF82D6C6.toInt()
    const val CORAL = 0xFFE17A57.toInt()
    private const val DANGER = 0xFFE06B6B.toInt()

    private class Pad(val c: Canvas, val w: Float, val h: Float, val look: WidgetLook) {
        val u = look.density
        fun text(size: Float, color: Int, display: Boolean = false, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * u
            this.color = color
            typeface = if (display) look.fonts.display else look.fonts.body
            letterSpacing = spacing
        }
        fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        fun width(p: TextPaint, s: String) = p.measureText(s) - p.letterSpacing * p.textSize
        fun fit(p: TextPaint, s: String, max: Float, min: Float) = ShareText.fit(p, s, max, min * u)
    }

    private fun card(w: Int, h: Int, look: WidgetLook, color: Int, draw: Pad.() -> Unit): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = Pad(c, w.toFloat(), h.toFloat(), look)
        val r = 24f * look.density
        val shape = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(shape)
        c.drawColor(color)
        pad.draw()
        c.restore()
        return bmp
    }

    private fun Pad.label(s: String, x: Float, y: Float, color: Int = MUTED, size: Float = 11f) {
        c.drawText(s.uppercase(Locale.ENGLISH), x, y, text(size, color, spacing = 0.18f))
    }

    /** Six cluster bars, filled from the left. */
    private fun Pad.bars(bars: Int?, l: Float, t: Float, r: Float, b: Float, on: Int, off: Int) {
        val gap = 3f * u
        val bw = (r - l - gap * 5) / 6
        for (i in 0 until 6) {
            val x = l + i * (bw + gap)
            c.drawRoundRect(RectF(x, t, x + bw, b), 3f * u, 3f * u, fill(if (bars != null && i < bars) on else off))
        }
    }

    private fun fuelTone(bars: Int?, accent: Int) = when {
        bars == null -> MUTED
        bars <= 1 -> DANGER
        bars == 2 -> CORAL
        else -> accent
    }

    // ---- Bike status: 2x2 to 4x2 ----

    fun bike(w: Int, h: Int, look: WidgetLook, d: BikeWidget): Bitmap = card(w, h, look, BASE) {
        val m = 16f * u
        // Accent glow behind the bike.
        c.drawRect(0f, 0f, this.w, this.h, Paint().apply {
            shader = RadialGradient(this@card.w * 0.72f, this@card.h * 0.45f, max(this@card.w, this@card.h) * 0.6f,
                intArrayOf(withAlpha(look.accent, 70), withAlpha(look.accent, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        })
        val wide = this.w > this.h * 1.4f
        d.bike?.let { img ->
            val box = if (wide) RectF(this.w * 0.42f, m * 0.6f, this.w - m * 0.5f, this.h * 0.7f) else RectF(this.w * 0.3f, m + 42f * u, this.w - m * 0.3f, this.h - m - 54f * u)
            val s = min(box.width() / img.width, box.height() / img.height)
            val dw = img.width * s
            val dh = img.height * s
            c.drawBitmap(img, null, RectF(box.right - dw, box.bottom - dh, box.right, box.bottom), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        // Live dot and name.
        c.drawCircle(m + 4f * u, m + 5f * u, 4f * u, fill(if (d.linked) look.accent else MUTED))
        label(if (d.liveKm != null) "Riding" else if (d.linked) "Linked" else "Parked", m + 14f * u, m + 9f * u, if (d.linked) look.accent else MUTED)
        val name = text(if (wide) 22f else 18f, TEXT, display = true)
        c.drawText(fit(name, d.name.uppercase(Locale.ENGLISH), if (wide) this.w * 0.42f else this.w - 2 * m, 13f), m, m + 36f * u, name)
        d.regNo?.takeIf { it.isNotBlank() && wide }?.let { reg ->
            val p = text(11f, INK, display = true, spacing = 0.06f)
            val rw = width(p, reg) + 12f * u
            val top = m + 44f * u
            c.drawRoundRect(RectF(m, top, m + rw, top + 18f * u), 4f * u, 4f * u, fill(TEXT))
            c.drawText(reg, m + 6f * u, top + 13.5f * u, p)
        }
        // Range big, bars under it.
        val big = text(if (wide) 40f else 34f, TEXT, display = true)
        val value = d.liveKm?.let { "%.1f".format(Locale.ENGLISH, it) } ?: d.rangeKm?.toString() ?: d.fuelBars?.let { "$it/6" } ?: "—"
        val unit = if (d.liveKm != null) "KM THIS RIDE" else if (d.rangeKm != null) "KM RANGE" else "FUEL"
        val baseY = this.h - m - 22f * u
        c.drawText(value, m, baseY, big)
        label(unit, m + width(big, value) + 6f * u, baseY, MUTED, 10f)
        bars(d.fuelBars, m, this.h - m - 10f * u, if (wide) this.w * 0.5f else this.w - m, this.h - m, fuelTone(d.fuelBars, look.accent), RAISED)
        // Odo and health on the right when there is room.
        if (wide) {
            val rx = this.w - m
            d.health?.let { score ->
                val ring = RectF(rx - 46f * u, this.h - m - 46f * u, rx, this.h - m)
                c.drawArc(ring, 0f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f * u; color = RAISED })
                c.drawArc(ring, -90f, 360f * score / 100f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 5f * u; strokeCap = Paint.Cap.ROUND
                    color = when { score >= 85 -> look.accent; score >= 65 -> CORAL; else -> DANGER }
                })
                val n = text(16f, TEXT, display = true)
                val s = "$score"
                c.drawText(s, ring.centerX() - width(n, s) / 2, ring.centerY() + 6f * u, n)
            }
            d.odoKm?.let { odo ->
                val p = text(13f, TEXT, display = true)
                val s = "%,d KM".format(Locale.ENGLISH, odo)
                c.drawText(s, rx - (if (d.health != null) 56f * u else 0f) - width(p, s), this.h - m - 4f * u, p)
            }
        }
    }

    // ---- Fuel: 1x1 to 2x2 ----

    fun fuel(w: Int, h: Int, look: WidgetLook, d: FuelWidget): Bitmap = card(w, h, look, fuelTone(d.bars, look.accent).let { if (it == MUTED) RAISED else it }) {
        val m = 14f * u
        val ink = if (d.bars == null) TEXT else INK
        label("Fuel", m, m + 9f * u, withAlpha(ink, 170))
        val roomy = this.h > 150 * u
        val big = text(if (roomy) 44f else 28f, ink, display = true)
        val value = d.rangeKm?.toString() ?: d.bars?.let { "$it/6" } ?: "—"
        // Small: number just above the bars. Roomy: number mid-card with details under it.
        val y = if (roomy) this.h * 0.48f else this.h - m - 16f * u
        c.drawText(fit(big, value, this.w - 2 * m, 18f), m, y, big)
        if (roomy) {
            if (d.rangeKm != null) label("km left", m, y + 16f * u, withAlpha(ink, 170), 10f)
            listOfNotNull(d.kmPerL?.let { "%.1f km/l".format(Locale.ENGLISH, it) }, d.sinceFill).forEachIndexed { i, s ->
                c.drawText(s, m, y + (38f + i * 17f) * u, text(13f, ink))
            }
        }
        bars(d.bars, m, this.h - m - 8f * u, this.w - m, this.h - m, ink, withAlpha(ink, 50))
    }

    // ---- Start ride: 1x1 ----

    fun start(w: Int, h: Int, look: WidgetLook, liveKm: Double?): Bitmap = card(w, h, look, BASE) {
        val cx = this.w / 2
        val cy = this.h * 0.42f
        val r = min(this.w, this.h) * 0.29f
        c.drawCircle(cx, cy, r, fill(look.accent))
        if (liveKm == null) {
            // A play triangle.
            val t = r * 0.42f
            val p = Path().apply {
                moveTo(cx - t * 0.6f, cy - t)
                lineTo(cx + t, cy)
                lineTo(cx - t * 0.6f, cy + t)
                close()
            }
            c.drawPath(p, fill(INK))
            val l = text(11f, MUTED, spacing = 0.2f)
            val s = "START RIDE"
            c.drawText(fit(l, s, this.w - 8f * u, 8f), cx - width(l, s) / 2, this.h - 9f * u, l)
        } else {
            val n = text(16f, INK, display = true)
            val s = "%.1f".format(Locale.ENGLISH, liveKm)
            c.drawText(fit(n, s, r * 1.7f, 10f), cx - width(n, s) / 2, cy + 6f * u, n)
            val live = text(11f, look.accent, spacing = 0.2f)
            val t = "LIVE · KM"
            c.drawText(t, cx - width(live, t) / 2, this.h - 9f * u, live)
        }
    }

    // ---- Today and week: 2x2 to 4x2 ----

    fun today(w: Int, h: Int, look: WidgetLook, d: TodayWidget): Bitmap = card(w, h, look, LAVENDER) {
        val m = 16f * u
        label("Today", m, m + 9f * u, withAlpha(INK, 170))
        val big = text(38f, INK, display = true)
        val v = "%.0f".format(Locale.ENGLISH, d.todayKm)
        c.drawText(v, m, m + 50f * u, big)
        label("km", m + width(big, v) + 5f * u, m + 50f * u, withAlpha(INK, 170), 11f)
        if (d.streak >= 2) {
            val s = "${d.streak}-DAY STREAK"
            val p = text(10f, INK, spacing = 0.15f)
            val sw = width(p, s) + 14f * u
            c.drawRoundRect(RectF(this.w - m - sw, m - 2f * u, this.w - m, m + 16f * u), 9f * u, 9f * u, fill(withAlpha(INK, 30)))
            c.drawText(s, this.w - m - sw + 7f * u, m + 10.5f * u, p)
        }
        // Seven bars.
        val top = m + 66f * u
        val bottom = this.h - m - 14f * u
        val wide = this.w > this.h * 1.4f
        val right = if (wide && d.goalKm != null) this.w - m - 92f * u else this.w - m
        val peak = (d.week.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val gap = 6f * u
        val bw = (right - m - gap * 6) / 7
        d.week.forEachIndexed { i, km ->
            val x = m + i * (bw + gap)
            val bh = ((bottom - top) * (km / peak)).toFloat().coerceAtLeast(3f * u)
            c.drawRoundRect(RectF(x, bottom - bh, x + bw, bottom), 4f * u, 4f * u, fill(if (i == d.week.lastIndex) INK else withAlpha(INK, 70)))
            d.dayLetters.getOrNull(i)?.let { l ->
                val p = text(10f, withAlpha(INK, 150))
                c.drawText(l, x + bw / 2 - width(p, l) / 2, this.h - m + 1f * u, p)
            }
        }
        // Monthly goal ring.
        if (wide && d.goalKm != null) {
            val size = 70f * u
            val ring = RectF(this.w - m - size, top, this.w - m, top + size)
            val stroke = 8f * u
            ring.inset(stroke / 2, stroke / 2)
            c.drawArc(ring, 0f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = withAlpha(INK, 40) })
            val f = (d.monthKm / d.goalKm).toFloat().coerceIn(0f, 1f)
            c.drawArc(ring, -90f, 360f * f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = INK })
            val p = text(15f, INK, display = true)
            val s = "${(f * 100).toInt()}%"
            c.drawText(s, ring.centerX() - width(p, s) / 2, ring.centerY() + 5f * u, p)
            val l = text(10f, withAlpha(INK, 170))
            val g = "of %.0f km".format(Locale.ENGLISH, d.goalKm)
            c.drawText(g, ring.centerX() - width(l, g) / 2, ring.bottom + 18f * u, l)
        }
    }

    // ---- Care: 2x1 to 4x2 ----

    fun care(w: Int, h: Int, look: WidgetLook, d: CareWidget): Bitmap {
        val urgent = d.lines.any { it.urgent }
        return card(w, h, look, if (urgent) CORAL else YELLOW) {
            val m = 16f * u
            label(if (urgent) "Needs attention" else "Coming up", m, m + 9f * u, withAlpha(INK, 170))
            if (d.lines.isEmpty()) {
                c.drawText("All good", m, m + 40f * u, text(24f, INK, display = true))
                c.drawText("Nothing due soon", m, m + 60f * u, text(13f, INK))
                return@card
            }
            val rowH = 46f * u
            val rows = ((this.h - m * 2 - 14f * u) / rowH).toInt().coerceIn(1, d.lines.size)
            d.lines.take(rows).forEachIndexed { i, line ->
                val y = m + 22f * u + i * rowH
                val t = text(17f, INK, display = true)
                c.drawText(fit(t, line.title.uppercase(Locale.ENGLISH), this.w * 0.55f, 12f), m, y + 14f * u, t)
                val cap = text(13f, INK)
                val cs = fit(cap, line.caption, this.w * 0.4f, 10f)
                c.drawText(cs, this.w - m - width(cap, cs), y + 14f * u, cap)
                val bt = y + 22f * u
                c.drawRoundRect(RectF(m, bt, this.w - m, bt + 6f * u), 3f * u, 3f * u, fill(withAlpha(INK, 40)))
                c.drawRoundRect(RectF(m, bt, m + (this.w - 2 * m) * line.fraction.coerceIn(0.03f, 1f), bt + 6f * u), 3f * u, 3f * u, fill(INK))
            }
        }
    }

    // ---- Last ride: 4x2 ----

    fun lastRide(w: Int, h: Int, look: WidgetLook, d: LastRideWidget?): Bitmap = card(w, h, look, BASE) {
        val m = 16f * u
        if (d == null) {
            label("Last ride", m, m + 9f * u)
            c.drawText("No rides yet", m, m + 40f * u, text(22f, TEXT, display = true))
            return@card
        }
        // Route on the right half, glowing in the accent.
        val box = RectF(this.w * 0.5f, m, this.w - m, this.h - m)
        if (d.route.size >= 2) {
            val s = min(box.width(), box.height())
            val ox = box.left + (box.width() - s) / 2
            val oy = box.top + (box.height() - s) / 2
            val p = Path()
            d.route.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(ox + x * s, oy + y * s) else p.lineTo(ox + x * s, oy + y * s) }
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 4f * u; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                color = look.accent
                setShadowLayer(8f * u, 0f, 0f, withAlpha(look.accent, 160))
            })
            val (sx, sy) = d.route.first()
            val (ex, ey) = d.route.last()
            c.drawCircle(ox + sx * s, oy + sy * s, 4f * u, fill(TEXT))
            c.drawCircle(ox + ex * s, oy + ey * s, 5f * u, fill(look.accent))
        }
        label("Last ride · ${d.when_}", m, m + 9f * u)
        val t = text(20f, TEXT, display = true)
        c.drawText(fit(t, d.label.uppercase(Locale.ENGLISH), this.w * 0.46f, 13f), m, m + 34f * u, t)
        val big = text(36f, look.accent, display = true)
        val km = "%.1f".format(Locale.ENGLISH, d.km)
        c.drawText(km, m, this.h - m - 26f * u, big)
        label("km", m + width(big, km) + 5f * u, this.h - m - 26f * u, MUTED, 11f)
        val time = if (d.minutes >= 60) "${d.minutes / 60} h ${d.minutes % 60} m" else "${d.minutes} min"
        c.drawText("$time · top ${d.maxKmh} km/h", m, this.h - m, text(13f, TEXT))
    }

    // ---- Places: 4x1 ----

    /** Up to three place chips; each third of the widget opens Maps to one. */
    fun places(w: Int, h: Int, look: WidgetLook, d: PlacesWidget): Bitmap = card(w, h, look, BASE) {
        val m = 10f * u
        val slots = 3
        val cw = (this.w - m * (slots + 1)) / slots
        for (i in 0 until slots) {
            val x = m + i * (cw + m)
            val r = RectF(x, m, x + cw, this.h - m)
            val name = d.places.getOrNull(i)
            c.drawRoundRect(r, 16f * u, 16f * u, fill(if (name != null && i == 0) look.accent else RAISED))
            val ink = if (name != null && i == 0) INK else TEXT
            if (name == null) {
                val p = text(13f, MUTED)
                val s = if (i == 0) "Add places in Journey" else "+"
                val fs = fit(p, s, cw - 12f * u, 10f)
                c.drawText(fs, r.centerX() - width(p, fs) / 2, r.centerY() + 5f * u, p)
                continue
            }
            // Pin.
            val px = r.left + 16f * u
            val py = r.centerY() - 2f * u
            c.drawCircle(px, py, 6f * u, fill(ink))
            c.drawCircle(px, py, 2.5f * u, fill(if (i == 0) look.accent else RAISED))
            val p = text(15f, ink, display = true)
            c.drawText(fit(p, name.uppercase(Locale.ENGLISH), cw - 38f * u, 10f), px + 12f * u, r.centerY() + 5f * u, p)
        }
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    /** Squashes a track into the unit square, keeping its shape, with y pointing down. */
    fun normalise(points: List<Pair<Double, Double>>): List<Pair<Float, Float>> {
        if (points.size < 2) return emptyList()
        // Longitude lines get closer away from the equator; scale x so the shape isn't stretched.
        val k = kotlin.math.cos(Math.toRadians(points.map { it.first }.average()))
        val xs = points.map { it.second * k }
        val ys = points.map { -it.first }
        val minX = xs.min()
        val minY = ys.min()
        val span = max(xs.max() - minX, ys.max() - minY).coerceAtLeast(1e-9)
        val padX = (1 - (xs.max() - minX) / span) / 2
        val padY = (1 - (ys.max() - minY) / span) / 2
        return xs.indices.map { i -> ((xs[i] - minX) / span + padX).toFloat() to ((ys[i] - minY) / span + padY).toFloat() }
    }
}
