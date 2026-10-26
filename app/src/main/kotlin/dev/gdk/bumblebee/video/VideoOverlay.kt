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

/** The share-card fonts: tall numbers and titles, labels, and a monospace for the dash. */
class VideoFonts(val display: Typeface, val label: Typeface, val labelBold: Typeface, val mono: Typeface = labelBold)

/** Pictures the overlay draws: the rider's bike (facing right) and their photos by id. */
class VideoAssets(val bike: Bitmap? = null, val photos: Map<String, Bitmap> = emptyMap()) {
    fun release() {
        bike?.recycle()
        photos.values.forEach { it.recycle() }
    }
}

/**
 * Paints the text layer of each frame into one reused bitmap: the title in the intro, the HUD and
 * elevation strip during the ride, moment cards, and the closing stats. Layout is in pixels at a
 * 1080-pixel short side and scaled from there, so every shape keeps the same proportions.
 */
class VideoOverlay(
    private val plan: RideVideoPlan,
    private val fonts: VideoFonts,
    private val accent: Int,
    /** Defaults to the video's size; the Studio preview draws smaller. */
    val width: Int = plan.shape.width,
    val height: Int = plan.shape.height,
    private val assets: VideoAssets = VideoAssets(),
) {
    private val cfg = plan.config
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

    /** When each chapter's card appears: as the marker reaches the chapter's start. */
    private val chapterTimes = plan.chapters.map { plan.timeline.timeAt(it.startM) }

    fun draw(t: Double): Bitmap {
        bitmap.eraseColor(Color.TRANSPARENT)
        val cue = plan.timeline.cue(t)
        if (markerShown(cue)) bikeMarker(t, cue)
        when (cue.shot) {
            Shot.COLD -> cue.moment?.let { teaser(it, cue.progress) }
            Shot.INTRO -> if (cfg.titleCard) intro(cue.progress)
            Shot.RIDE, Shot.HOLD -> {
                if (plan.labels.isNotEmpty()) placeLabels(t)
                // The HUD fades in over the first half second of the ride.
                val since = t - plan.timeline.rideStartS
                hud(cue, (since / 0.5).coerceIn(0.0, 1.0).toFloat())
                if (cfg.moments) cue.moment?.let { if (cue.shot == Shot.HOLD) highlight(it, cue.progress) }
                if (cue.shot == Shot.RIDE) chapterCard(t)
            }
            Shot.FINISH -> {
                hud(cue, (1 - cue.progress / 0.25).coerceIn(0.0, 1.0).toFloat())
                if (cfg.finishCard) finish(cue.progress)
            }
        }
        flash(t)
        return bitmap
    }

    /**
     * Towns along the route, pinned where the road passes them: a short stalk up from the ground
     * and the name on a dark tab. Shown only while in view, near enough to read, and not behind a
     * ridge, fading in and out at the frame's edges.
     */
    private fun placeLabels(t: Double) {
        val pose = plan.camera(t)
        val vm = ViewMath(pose, width.toDouble() / height, 5.0, 1e7)
        val reach = plan.director.radius * 0.9
        for (l in plan.labels) {
            val dist = (l.anchor - pose.eye).length
            if (dist > reach * 1.4) continue
            val p = vm.project(l.anchor, width, height) ?: continue
            val x = p[0].toFloat()
            val y = p[1].toFloat()
            val edge = minOf(x, width - x, y - 140 * u, height - 360 * u - y) / (90 * u)
            var a = edge.coerceIn(0f, 1f) * (1 - ((dist - reach) / (reach * 0.4)).coerceIn(0.0, 1.0)).toFloat()
            if (a <= 0.02f || !plan.canSee(pose.eye, l.anchor + Vec3.UP * 30.0)) continue
            a = a.coerceIn(0f, 1f)
            val stalk = 70 * u
            stroke.strokeWidth = 3 * u
            stroke.color = Color.WHITE
            stroke.alpha = (a * 220).toInt()
            canvas.drawLine(x, y, x, y - stalk, stroke)
            fill.color = Color.WHITE
            fill.alpha = (a * 255).toInt()
            canvas.drawCircle(x, y, 6 * u, fill)
            text.typeface = fonts.labelBold
            text.textSize = 30 * u
            text.letterSpacing = 0.06f
            val name = l.name.uppercase(Locale.ENGLISH)
            val tw = text.measureText(name)
            text.letterSpacing = 0f
            val pad = 16 * u
            val top = y - stalk - 52 * u
            fill.color = Color.argb((a * 190).toInt(), 12, 12, 14)
            canvas.drawRoundRect(RectF(x - tw / 2 - pad, top, x + tw / 2 + pad, top + 52 * u), 26 * u, 26 * u, fill)
            text.textAlign = Paint.Align.CENTER
            label(name, x, top + 37 * u, 30 * u, Color.WHITE, a, fonts.labelBold, tracking = 0.06f)
            text.textAlign = Paint.Align.LEFT
        }
    }

    /** A white flash across the cut from the cold open into the title. */
    private fun flash(t: Double) {
        val cut = plan.timeline.coldS
        if (cut <= 0) return
        val a = when {
            t in (cut - 0.12)..cut -> (t - (cut - 0.12)) / 0.12
            t in cut..(cut + 0.35) -> 1 - (t - cut) / 0.35
            else -> return
        }.coerceIn(0.0, 1.0)
        fill.color = Color.argb((a * 230).toInt(), 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
    }

    /** A moment's card, or the rider's photo as a postcard when the moment is a photo. */
    private fun highlight(m: VideoMoment, p: Double, alphaOverride: Float? = null) {
        val photo = m.photoId?.let { assets.photos[it] }
        if (photo != null) postcard(m, photo, p, alphaOverride) else momentCard(m, p, alphaOverride)
    }

    /**
     * A photo as a postcard: a white-bordered print, tilted a few degrees (always the same way for
     * the same photo), that drops in, holds, and lifts away; the caption is written on the border.
     */
    private fun postcard(m: VideoMoment, photo: Bitmap, p: Double, alphaOverride: Float?) {
        val inOut = min(p, 1 - p)
        val a = alphaOverride ?: (inOut / 0.16).coerceIn(0.0, 1.0).toFloat()
        if (a <= 0f) return
        val maxW = (if (wide) width * 0.42f else width * 0.74f)
        val maxH = height * (if (wide) 0.62f else 0.44f)
        val scale = min(maxW / photo.width, maxH / photo.height)
        val pw = photo.width * scale
        val ph = photo.height * scale
        val border = 18 * u
        val bottom = 74 * u
        val cw = pw + border * 2
        val ch = ph + border + bottom
        val cx = width / 2f
        val cy = height * (if (wide) 0.45f else 0.38f) + (1 - a) * 60 * u
        val tilt = ((m.photoId.hashCode() % 7) - 3) * 1.2f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tilt)
        val s = 0.92f + 0.08f * a
        canvas.scale(s, s)
        // Shadow, then the print.
        fill.color = Color.argb((a * 110).toInt(), 0, 0, 0)
        canvas.drawRoundRect(RectF(-cw / 2 + 8 * u, -ch / 2 + 14 * u, cw / 2 + 8 * u, ch / 2 + 14 * u), 10 * u, 10 * u, fill)
        fill.color = Color.argb((a * 255).toInt(), 250, 248, 242)
        canvas.drawRoundRect(RectF(-cw / 2, -ch / 2, cw / 2, ch / 2), 6 * u, 6 * u, fill)
        val img = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (a * 255).toInt() }
        canvas.drawBitmap(photo, null, RectF(-cw / 2 + border, -ch / 2 + border, -cw / 2 + border + pw, -ch / 2 + border + ph), img)
        text.textAlign = Paint.Align.CENTER
        text.typeface = fonts.labelBold
        text.textSize = 32 * u
        text.letterSpacing = 0.04f
        text.color = Color.rgb(40, 40, 44)
        text.alpha = (a * 255).toInt()
        text.clearShadowLayer()
        canvas.drawText(m.title, 0f, ch / 2 - bottom / 2 + 11 * u, text)
        text.textAlign = Paint.Align.LEFT
        text.letterSpacing = 0f
        canvas.restore()
    }

    /** When the rider's marker is on screen, matching the renderer's head of the trail. */
    private fun markerShown(cue: Cue): Boolean = cfg.marker == MarkerStyle.BIKE && assets.bike != null && when (cue.shot) {
        Shot.INTRO -> cue.progress > 0.55
        Shot.FINISH -> cue.progress < 0.40
        else -> true
    }

    /**
     * The rider's own bike riding the route: its photo at the head of the trail, turned to face the
     * way the road goes on screen, on a soft shadow, hidden when a ridge is in the way.
     */
    private fun bikeMarker(t: Double, cue: Cue) {
        val bike = assets.bike ?: return
        val pose = plan.camera(t)
        val vm = ViewMath(pose, width.toDouble() / height, 2.0, 1e7)
        val d = cue.distanceM
        val at = plan.path.at(d)
        val p = vm.project(at, width, height) ?: return
        if (!plan.canSee(pose.eye, at + Vec3.UP * 10.0)) return
        val ahead = vm.project(plan.path.at(kotlin.math.min(plan.path.length, d + 40)), width, height)
        val behind = vm.project(plan.path.at(kotlin.math.max(0.0, d - 40)), width, height)
        val faceLeft = if (ahead != null && behind != null) ahead[0] < behind[0] else false
        val w = (190 * u * cfg.markerSize).toFloat()
        val h = w * bike.height / bike.width
        val x = p[0].toFloat()
        val y = p[1].toFloat() - 4 * u
        fill.color = Color.argb(90, 0, 0, 0)
        canvas.drawOval(RectF(x - w * 0.42f, y - h * 0.06f, x + w * 0.42f, y + h * 0.08f), fill)
        canvas.save()
        if (faceLeft) canvas.scale(-1f, 1f, x, y)
        canvas.drawBitmap(bike, null, RectF(x - w / 2, y - h, x + w / 2, y), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /** The cold open: the best moment's card (or photo), big, with a hint of what's coming. */
    private fun teaser(m: VideoMoment, p: Double) {
        val a = (min(p, 1 - p) / 0.15).coerceIn(0.0, 1.0).toFloat()
        highlight(m, 0.5, alphaOverride = a)
        label("TODAY'S RIDE", 56 * u, height - 120 * u, 28 * u, Color.WHITE, a * 0.8f, fonts.labelBold, tracking = 0.25f)
    }

    /** "Chapter 2 · The climb", top left, for a couple of seconds as each act begins. */
    private fun chapterCard(t: Double) {
        val i = chapterTimes.indexOfLast { it <= t }
        if (i < 0) return
        val since = t - chapterTimes[i]
        if (since > CHAPTER_S) return
        val a = when {
            since < 0.3 -> since / 0.3
            since > CHAPTER_S - 0.4 -> (CHAPTER_S - since) / 0.4
            else -> 1.0
        }.toFloat()
        val c = plan.chapters[i]
        val left = 56 * u
        val slide = (1 - a) * 30 * u
        var y = 150 * u
        label("CHAPTER ${c.number}", left - slide, y, 26 * u, accent, a, fonts.labelBold, tracking = 0.3f)
        y += 70 * u
        label(c.title.uppercase(Locale.ENGLISH), left - slide, y, fitSize(c.title.uppercase(Locale.ENGLISH), fonts.display, 76 * u, width - left * 2), Color.WHITE, a, fonts.display)
        c.detail?.let { label(it, left - slide, y + 44 * u, 28 * u, Color.WHITE, a * 0.8f, fonts.label) }
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
        val titleSize = fitSize(plan.title.uppercase(Locale.ENGLISH), fonts.display, (if (wide) 96 else 112) * u, width - left * 2)
        label(plan.title.uppercase(Locale.ENGLISH), left, y, titleSize, Color.WHITE, a, fonts.display)
        y -= titleSize * 1.05f
        label(plan.dateLine.uppercase(Locale.ENGLISH), left, y, 28 * u, accent, a, fonts.labelBold, tracking = 0.08f)
        plan.tagsLine?.let { tags ->
            y -= 48 * u
            label(tags.uppercase(Locale.ENGLISH), left, y, fitSize(tags.uppercase(Locale.ENGLISH), fonts.labelBold, 24 * u, width - left * 2), Color.WHITE, a * 0.75f, fonts.labelBold, tracking = 0.08f)
        }
    }

    private fun hud(cue: Cue, a: Float) {
        if (a <= 0f) return
        if (cfg.clock) clock(cue.distanceM, a)
        if (!cfg.hud && !cfg.elevationStrip) return
        val d = cue.distanceM
        val stripH = if (cfg.elevationStrip) 40 * u else 0f
        val bottom = height - 54 * u
        scrimBottom(height - (if (!cfg.hud) 140 else if (cfg.hudStyle == HudStyle.CLUSTER) 400 else 330) * u, 185, a)
        val left = 56 * u
        if (cfg.hud && cfg.hudStyle == HudStyle.CLUSTER) {
            clusterPanel(d, left, bottom - stripH - (if (cfg.elevationStrip) 24 else 0) * u, a)
        } else if (cfg.hud) {
            val cols = listOf(
                String.format(Locale.ENGLISH, "%.1f", plan.odoKm(d)) to "KM",
                plan.speedAt(d).toString() to "KM/H",
                String.format(Locale.ENGLISH, "%,d", plan.altitudeAt(d).toInt()) to "M ALT",
            )
            val colW = (width - left * 2) / 3
            val numY = bottom - stripH - 66 * u
            cols.forEachIndexed { i, (v, l) ->
                val x = left + i * colW
                label(v, x, numY, 84 * u, Color.WHITE, a, fonts.display)
                label(l, x + 3 * u, numY + 36 * u, 24 * u, Color.WHITE, a * 0.75f, fonts.labelBold, tracking = 0.1f)
            }
        }
        if (cfg.elevationStrip) elevationStrip(left, bottom - stripH, width - left * 2, stripH, d, a)
    }

    /**
     * A dash styled on the bike's own LCD: dark glass with a hairline, a big monospace speed, the
     * fuel gauge's six bars (when the ride has gauge readings), odometer and altitude.
     */
    private fun clusterPanel(d: Double, left: Float, bottom: Float, a: Float) {
        val panelH = 210 * u
        val top = bottom - panelH
        val right = width - left
        val rect = RectF(left - 12 * u, top, right + 12 * u, bottom)
        fill.color = Color.argb((a * 200).toInt(), 8, 9, 12)
        canvas.drawRoundRect(rect, 28 * u, 28 * u, fill)
        stroke.strokeWidth = 2 * u
        stroke.color = accent
        stroke.alpha = (a * 160).toInt()
        canvas.drawRoundRect(rect, 28 * u, 28 * u, stroke)
        // Speed, the hero.
        val speed = plan.speedAt(d).toString()
        label(speed, left + 18 * u, bottom - 56 * u, 132 * u, Color.WHITE, a, fonts.mono)
        text.typeface = fonts.mono
        text.textSize = 132 * u
        val sw = text.measureText(speed)
        label("km/h", left + 26 * u + sw, bottom - 60 * u, 30 * u, Color.WHITE, a * 0.7f, fonts.mono)
        val colX = left + (right - left) * 0.52f
        // Fuel bars, filled from the left like the gauge.
        val bars = plan.fuelAt(d)
        if (bars != null) {
            label("E", colX, top + 66 * u, 24 * u, Color.WHITE, a * 0.6f, fonts.mono)
            val bx = colX + 30 * u
            val bw = (right - bx - 40 * u) / 6
            for (i in 0 until 6) {
                val on = i < bars
                fill.color = if (on) accent else Color.WHITE
                fill.alpha = (a * if (on) 255 else 40).toInt()
                canvas.drawRoundRect(RectF(bx + i * bw + 3 * u, top + 36 * u, bx + (i + 1) * bw - 3 * u, top + 70 * u), 4 * u, 4 * u, fill)
            }
            label("F", right - 28 * u, top + 66 * u, 24 * u, Color.WHITE, a * 0.6f, fonts.mono)
        }
        label("ODO", colX, top + 122 * u, 22 * u, accent, a, fonts.mono)
        label(String.format(Locale.ENGLISH, "%.1f km", plan.odoKm(d)), colX + 70 * u, top + 122 * u, 34 * u, Color.WHITE, a, fonts.mono)
        label("ALT", colX, top + 172 * u, 22 * u, accent, a, fonts.mono)
        label(String.format(Locale.ENGLISH, "%,d m", plan.altitudeAt(d).toInt()), colX + 70 * u, top + 172 * u, 34 * u, Color.WHITE, a, fonts.mono)
    }

    /** Time of day, top right, small. */
    private fun clock(d: Double, a: Float) {
        text.typeface = fonts.labelBold
        text.textSize = 30 * u
        text.textAlign = Paint.Align.RIGHT
        text.color = Color.WHITE
        text.alpha = (a * 200).toInt()
        text.setShadowLayer(6 * u, 0f, 2 * u, Color.argb((a * 140).toInt(), 0, 0, 0))
        canvas.drawText(plan.clockAt(d), width - 48 * u, 84 * u, text)
        text.textAlign = Paint.Align.LEFT
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

    private fun momentCard(m: VideoMoment, p: Double, alphaOverride: Float? = null) {
        // Slides in, holds, slides out, inside the hold.
        val inOut = min(p, 1 - p)
        val a = alphaOverride ?: (inOut / 0.18).coerceIn(0.0, 1.0).toFloat()
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
        // After the map reveal has drawn the route, if it's on.
        val from = if (cfg.mapReveal) 0.78 else 0.45
        val a = ((p - from) / 0.14).coerceIn(0.0, 1.0).toFloat()
        if (a <= 0f) return
        fill.color = Color.argb((a * 120).toInt(), 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val left = 64 * u
        val rise = (1 - a) * 30 * u
        var y = height * (if (wide) 0.26f else 0.30f) + rise
        val title = plan.title.uppercase(Locale.ENGLISH)
        val ts = fitSize(title, fonts.display, 96 * u, width - left * 2)
        label(title, left, y, ts, Color.WHITE, a, fonts.display)
        y += 40 * u
        plan.placesLine?.let { label(it, left, y, 30 * u, Color.WHITE, a * 0.8f, fonts.label) }
        plan.tagsLine?.let { tags ->
            y += 44 * u
            label(tags.uppercase(Locale.ENGLISH), left, y, fitSize(tags.uppercase(Locale.ENGLISH), fonts.labelBold, 24 * u, width - left * 2), accent, a, fonts.labelBold, tracking = 0.08f)
        }
        y += 150 * u
        val colW = (width - left * 2) / 2
        stats.forEachIndexed { i, (v, l) ->
            val x = left + (i % 2) * colW
            val yy = y + (i / 2) * 200 * u
            label(v, x, yy, 120 * u, Color.WHITE, a, fonts.display)
            label(l, x + 4 * u, yy + 44 * u, 26 * u, accent, a, fonts.labelBold, tracking = 0.1f)
        }
        label("BUMBLEBEE", left, height - 92 * u, 26 * u, Color.WHITE, a * 0.7f, fonts.labelBold, tracking = 0.25f)
        label(Terrarium.ATTRIBUTION + if (plan.water != null) "  ·  Water © OpenStreetMap contributors" else "", left, height - 54 * u, 18 * u, Color.WHITE, a * 0.45f, fonts.label)
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

    /** Frees the overlay's own bitmap; [VideoAssets] belong to whoever made them. */
    fun release() = bitmap.recycle()

    private companion object {
        const val PROFILE_POINTS = 160
        const val CHAPTER_S = 2.4
    }
}
