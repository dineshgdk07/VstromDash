package dev.gdk.bumblebee.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** The share-card fonts: tall numbers and titles, and labels. */
class VideoFonts(val display: Typeface, val label: Typeface, val labelBold: Typeface)

/**
 * Paints the text layer of each frame into one reused bitmap: the title in the intro, the HUD and
 * elevation strip during the ride, moment cards, and the closing stats. Layout is in pixels at a
 * 1080-pixel short side and scaled from there, so every shape keeps the same proportions.
 */
class VideoOverlay(private val plan: RideVideoPlan, private val fonts: VideoFonts, private val accent: Int) {
    val width = plan.settings.shape.width
    val height = plan.settings.shape.height
    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val u = min(width, height) / 1080f
    private val wide = width > height

    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val profile: FloatArray = FloatArray(PROFILE_POINTS + 1) { plan.altitudeAt(plan.path.length * it / PROFILE_POINTS).toFloat() }
    private val stats = plan.stats()

    fun draw(t: Double): Bitmap {
        bitmap.eraseColor(Color.TRANSPARENT)
        val cue = plan.timeline.cue(t)
        when (cue.shot) {
            Shot.INTRO -> intro(cue.progress)
            Shot.RIDE, Shot.HOLD -> {
                // The HUD fades in over the first half second of the ride.
                val since = t - plan.timeline.introS
                hud(cue, (since / 0.5).coerceIn(0.0, 1.0).toFloat())
                if (cue.shot == Shot.HOLD && cue.moment != null) momentCard(cue.moment, cue.progress)
            }
            Shot.FINISH -> {
                hud(cue, (1 - cue.progress / 0.25).coerceIn(0.0, 1.0).toFloat())
                finish(cue.progress)
            }
        }
        return bitmap
    }

    private fun intro(p: Double) {
        // In over the first 15%, held, out by 70% as the camera dives.
        val a = when {
            p < 0.15 -> p / 0.15
            p < 0.55 -> 1.0
            else -> (1 - (p - 0.55) / 0.15).coerceAtLeast(0.0)
        }.toFloat()
        if (a <= 0f) return
        val rise = (1 - min(1.0, p / 0.15)).toFloat() * 24 * u
        scrimBottom(height * 0.45f, 170, a)
        val left = 56 * u
        var y = height - (if (wide) 120 else 260) * u + rise
        plan.placesLine?.let {
            label(it, left, y, 34 * u, Color.WHITE, a * 0.85f, fonts.label)
        }
        y -= 52 * u
        val titleSize = fitSize(plan.input.title.uppercase(Locale.ENGLISH), fonts.display, (if (wide) 96 else 112) * u, width - left * 2)
        label(plan.input.title.uppercase(Locale.ENGLISH), left, y, titleSize, Color.WHITE, a, fonts.display)
        y -= titleSize * 1.05f
        label(plan.dateLine.uppercase(Locale.ENGLISH), left, y, 28 * u, accent, a, fonts.labelBold, tracking = 0.08f)
    }

    private fun hud(cue: Cue, a: Float) {
        if (a <= 0f) return
        val d = cue.distanceM
        val stripH = 40 * u
        val bottom = height - 54 * u
        scrimBottom(height - 330 * u, 185, a)
        // Clock, top right, small.
        text.typeface = fonts.labelBold
        text.textSize = 30 * u
        text.textAlign = Paint.Align.RIGHT
        text.color = Color.WHITE
        text.alpha = (a * 200).toInt()
        text.setShadowLayer(6 * u, 0f, 2 * u, Color.argb((a * 140).toInt(), 0, 0, 0))
        canvas.drawText(plan.clockAt(d), width - 48 * u, 84 * u, text)
        text.textAlign = Paint.Align.LEFT

        val cols = listOf(
            String.format(Locale.ENGLISH, "%.1f", plan.odoKm(d)) to "KM",
            plan.speedAt(d).toString() to "KM/H",
            String.format(Locale.ENGLISH, "%,d", plan.altitudeAt(d).toInt()) to "M ALT",
        )
        val left = 56 * u
        val colW = (width - left * 2) / 3
        val numY = bottom - stripH - 66 * u
        cols.forEachIndexed { i, (v, l) ->
            val x = left + i * colW
            label(v, x, numY, 84 * u, Color.WHITE, a, fonts.display)
            label(l, x + 3 * u, numY + 36 * u, 24 * u, Color.WHITE, a * 0.75f, fonts.labelBold, tracking = 0.1f)
        }
        elevationStrip(left, bottom - stripH, width - left * 2, stripH, d, a)
    }

    private fun elevationStrip(x: Float, y: Float, w: Float, h: Float, d: Double, a: Float) {
        val lo = profile.min()
        val hi = max(profile.max(), lo + 1f)
        val path = Path()
        for (i in profile.indices) {
            val px = x + w * i / PROFILE_POINTS
            val py = y + h - (profile[i] - lo) / (hi - lo) * h
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        stroke.strokeWidth = 3 * u
        stroke.color = Color.WHITE
        stroke.alpha = (a * 110).toInt()
        canvas.drawPath(path, stroke)
        val head = x + w * (d / plan.path.length).toFloat()
        canvas.save()
        canvas.clipRect(x - 4 * u, y - 4 * u, head, y + h + 4 * u)
        stroke.strokeWidth = 4.5f * u
        stroke.color = accent
        stroke.alpha = (a * 255).toInt()
        canvas.drawPath(path, stroke)
        canvas.restore()
        // Moments as ticks on the strip.
        fill.color = accent
        for (m in plan.timeline.moments) {
            val mx = x + w * (m.distanceM / plan.path.length).toFloat()
            fill.alpha = (a * if (m.distanceM <= d) 255 else 120).toInt()
            canvas.drawCircle(mx, y + h + 12 * u, 4 * u, fill)
        }
        fill.color = Color.WHITE
        fill.alpha = (a * 255).toInt()
        canvas.drawCircle(head, y + h + 12 * u, 6 * u, fill)
    }

    private fun momentCard(m: VideoMoment, p: Double) {
        // Slides in, holds, slides out, inside the hold.
        val inOut = min(p, 1 - p)
        val a = (inOut / 0.18).coerceIn(0.0, 1.0).toFloat()
        if (a <= 0f) return
        val slide = (1 - a) * 40 * u
        val titleSize = 30 * u
        val valueSize = 92 * u
        text.typeface = fonts.display
        text.textSize = valueSize
        val valueW = text.measureText(m.value.ifBlank { " " })
        text.typeface = fonts.labelBold
        text.textSize = titleSize
        val titleW = text.measureText(m.title.uppercase(Locale.ENGLISH)) * 1.1f
        val cw = max(valueW, titleW) + 88 * u
        val ch = (if (m.value.isBlank()) 92 else 196) * u
        val cx = (width - cw) / 2
        val cy = height * (if (wide) 0.14f else 0.16f) + slide
        fill.color = Color.argb((a * 215).toInt(), 12, 12, 14)
        canvas.drawRoundRect(RectF(cx, cy, cx + cw, cy + ch), 30 * u, 30 * u, fill)
        fill.color = accent
        fill.alpha = (a * 255).toInt()
        canvas.drawRoundRect(RectF(cx, cy, cx + 10 * u, cy + ch), 6 * u, 6 * u, fill)
        label(m.title.uppercase(Locale.ENGLISH), cx + 40 * u, cy + 60 * u, titleSize, accent, a, fonts.labelBold, tracking = 0.08f)
        if (m.value.isNotBlank()) label(m.value, cx + 40 * u, cy + 160 * u, valueSize, Color.WHITE, a, fonts.display)
    }

    private fun finish(p: Double) {
        val a = ((p - 0.45) / 0.18).coerceIn(0.0, 1.0).toFloat()
        if (a <= 0f) return
        fill.color = Color.argb((a * 120).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val left = 64 * u
        val rise = (1 - a) * 30 * u
        var y = height * (if (wide) 0.26f else 0.30f) + rise
        val title = plan.input.title.uppercase(Locale.ENGLISH)
        val ts = fitSize(title, fonts.display, 96 * u, width - left * 2)
        label(title, left, y, ts, Color.WHITE, a, fonts.display)
        y += 40 * u
        plan.placesLine?.let { label(it, left, y, 30 * u, Color.WHITE, a * 0.8f, fonts.label) }
        y += 150 * u
        val colW = (width - left * 2) / 2
        stats.forEachIndexed { i, (v, l) ->
            val x = left + (i % 2) * colW
            val yy = y + (i / 2) * 200 * u
            label(v, x, yy, 120 * u, Color.WHITE, a, fonts.display)
            label(l, x + 4 * u, yy + 44 * u, 26 * u, accent, a, fonts.labelBold, tracking = 0.1f)
        }
        label("BUMBLEBEE", left, height - 92 * u, 26 * u, Color.WHITE, a * 0.7f, fonts.labelBold, tracking = 0.25f)
        label(Terrarium.ATTRIBUTION, left, height - 54 * u, 18 * u, Color.WHITE, a * 0.45f, fonts.label)
    }

    private fun scrimBottom(top: Float, maxAlpha: Int, a: Float) {
        fill.shader = LinearGradient(0f, top, 0f, height.toFloat(), Color.TRANSPARENT, Color.argb((maxAlpha * a).toInt(), 0, 0, 0), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
    }

    private fun label(s: String, x: Float, y: Float, size: Float, color: Int, a: Float, face: Typeface, tracking: Float = 0f) {
        text.typeface = face
        text.textSize = size
        text.letterSpacing = tracking
        text.color = color
        text.alpha = (a.coerceIn(0f, 1f) * Color.alpha(color)).toInt()
        text.setShadowLayer(8 * u, 0f, 2 * u, Color.argb((a * 150).toInt(), 0, 0, 0))
        canvas.drawText(s, x, y, text)
        text.letterSpacing = 0f
    }

    /** The largest size up to [max] at which [s] fits in [width]. */
    private fun fitSize(s: String, face: Typeface, max: Float, width: Float): Float {
        text.typeface = face
        text.textSize = max
        val w = text.measureText(s)
        return if (w <= width) max else max * width / w
    }

    fun release() = bitmap.recycle()

    private companion object {
        const val PROFILE_POINTS = 160
    }
}
