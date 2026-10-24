package dev.gdk.bumblebee.ui.hud.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.RedlineGlow
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import dev.gdk.bumblebee.ui.theme.WarningAmber
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val START_DEG = 135f
private const val SWEEP_DEG = 270f
private const val SPEED_MAX = 140
private const val SPEED_MAJOR = 20

@Composable
fun SpeedDial(
    digital: String,
    speedKmh: Int,
    odometerKm: Int?,
    fuelBars: Int?,
    rangeKm: Double?,
    modifier: Modifier = Modifier,
    tripAKm: Double? = null,
    tripBKm: Double? = null,
    navLabel: String? = null,
) {
    val fuelFrac = ((fuelBars ?: 0).coerceIn(0, 6)) / 6f
    val speedFrac = (speedKmh.coerceIn(0, SPEED_MAX)).toFloat() / SPEED_MAX
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val d = min(maxWidth.value, maxHeight.value)
        Canvas(Modifier.matchParentSize()) {
            val half = min(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)

            bezel(c, half)
            dialFace(c, half)
            speedBand(c, half, speedFrac)
            subDial(
                center = c + Offset(0f, half * 0.42f),
                r = half * 0.155f,
                frac = fuelFrac,
                accent = SuzukiYellow,
                topLabel = "F",
                bottomLabel = "E",
                start = 200f,
                sweep = 140f,
                labelSide = 0f,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (navLabel != null) {
                Text(
                    navLabel,
                    color = SuzukiYellow,
                    fontSize = (d * 0.032f).sp,
                    maxLines = 1,
                )
            }
            Text(
                digital,
                color = ForgeWhite,
                fontSize = (d * 0.215f).sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                "km/h",
                style = MaterialTheme.typography.titleMedium,
                fontSize = (d * 0.046f).sp,
                color = ForgeWhite.copy(alpha = 0.5f),
            )
            Text(
                rangeKm?.let { "RANGE  ${it.toInt()} km" } ?: "RANGE  —",
                style = MaterialTheme.typography.titleSmall,
                fontSize = (d * 0.032f).sp,
                color = ForgeWhite.copy(alpha = 0.66f),
                modifier = Modifier.padding(top = (d * 0.008f).dp),
            )
            Text(
                "ODO  ${odometerKm?.let { "%,d".format(java.util.Locale.ROOT, it) } ?: "—"} km",
                style = MaterialTheme.typography.titleSmall,
                fontSize = (d * 0.036f).sp,
                color = ForgeWhite.copy(alpha = 0.66f),
                modifier = Modifier.padding(top = (d * 0.012f).dp),
            )
            Text(
                "A ${tripAKm?.let { java.lang.String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—"}   B ${tripBKm?.let { java.lang.String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—"}",
                style = MaterialTheme.typography.titleSmall,
                fontSize = (d * 0.032f).sp,
                color = ForgeWhite.copy(alpha = 0.66f),
            )
        }
    }
}

/** Thin dark rim with a soft top highlight, like the mock's recessed pod. */
private fun DrawScope.bezel(c: Offset, half: Float) {
    drawCircle(Color.Black.copy(alpha = 0.6f), half, c)
    drawCircle(
        Brush.sweepGradient(
            listOf(
                Color(0xFF303338),
                Color(0xFF6E7278),
                Color(0xFF15171B),
                Color(0xFF3C4046),
                Color(0xFF0D0E11),
                Color(0xFF52565C),
                Color(0xFF303338),
            ),
            center = c,
        ),
        half * 0.975f,
        c,
        style = Stroke(half * 0.045f),
    )
    drawCircle(Color.Black.copy(alpha = 0.75f), half * 0.945f, c, style = Stroke(half * 0.03f))
}

private fun DrawScope.dialFace(c: Offset, half: Float) {
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF15171C), Color(0xFF0A0B0E), Color(0xFF060607)),
            center = c.copy(y = c.y - half * 0.25f),
            radius = half * 1.15f,
        ),
        half * 0.93f,
        c,
    )
    drawCircle(Color.White.copy(alpha = 0.05f), half * 0.93f, c, style = Stroke(1.dp.toPx()))
}

private fun DrawScope.speedBand(c: Offset, half: Float, speedFrac: Float) {
    val tickOuter = half * 0.735f
    val majorInner = half * 0.600f
    val minorInner = half * 0.662f
    val numberR = half * 0.885f
    val bandR = (tickOuter + majorInner) / 2f

    drawArc(
        Color.White.copy(alpha = 0.04f),
        START_DEG,
        SWEEP_DEG,
        false,
        topLeft = Offset(c.x - bandR, c.y - bandR),
        size = Size(bandR * 2, bandR * 2),
        style = Stroke(half * 0.135f, cap = StrokeCap.Butt),
    )
    if (speedFrac > 0f) {
        drawArc(
            SuzukiYellow.copy(alpha = 0.55f),
            START_DEG,
            SWEEP_DEG * speedFrac,
            false,
            topLeft = Offset(c.x - bandR, c.y - bandR),
            size = Size(bandR * 2, bandR * 2),
            style = Stroke(half * 0.08f, cap = StrokeCap.Butt),
        )
    }

    val numberPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = half * 0.072f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val unitPaint = Paint().apply {
        color = android.graphics.Color.argb(150, 190, 195, 202)
        textAlign = Paint.Align.CENTER
        textSize = half * 0.044f
        isAntiAlias = true
    }
    val nc = drawContext.canvas.nativeCanvas
    nc.drawText("km/h", c.x, c.y - half * 0.615f, unitPaint)

    var v = 0
    while (v <= SPEED_MAX) {
        val major = v % SPEED_MAJOR == 0
        val rad = Math.toRadians((START_DEG + SWEEP_DEG * (v.toFloat() / SPEED_MAX)).toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        drawLine(
            if (major) ForgeWhite else ForgeWhite.copy(alpha = 0.45f),
            Offset(c.x + (if (major) majorInner else minorInner) * cosA, c.y + (if (major) majorInner else minorInner) * sinA),
            Offset(c.x + tickOuter * cosA, c.y + tickOuter * sinA),
            strokeWidth = if (major) half * 0.030f else half * 0.011f,
            cap = StrokeCap.Butt,
        )
        if (major) {
            nc.drawText(
                "$v",
                c.x + numberR * cosA,
                c.y + numberR * sinA + half * 0.035f,
                numberPaint,
            )
        }
        v += 10
    }
}

private fun DrawScope.subDial(
    center: Offset,
    r: Float,
    frac: Float,
    accent: Color,
    topLabel: String,
    bottomLabel: String,
    start: Float,
    sweep: Float,
    labelSide: Float,
) {
    val tl = Offset(center.x - r, center.y - r)
    val sz = Size(r * 2, r * 2)
    val st = Stroke(width = r * 0.17f, cap = StrokeCap.Butt)
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF15181D), Color.Transparent),
            center = center,
            radius = r * 1.45f,
        ),
        r * 1.45f,
        center,
    )
    drawArc(Color(0xFF23262B), start, sweep, false, style = st, topLeft = tl, size = sz)
    if (frac > 0f) {
        drawArc(accent, start, sweep * frac, false, style = st, topLeft = tl, size = sz)
    }

    // Graduation ticks sitting just inside the arc.
    val tickOut = r * 0.86f
    val tickIn = r * 0.66f
    repeat(5) { i ->
        val rad = Math.toRadians((start + sweep * (i / 4f)).toDouble())
        val ca = cos(rad).toFloat()
        val sa = sin(rad).toFloat()
        drawLine(
            ForgeWhite.copy(alpha = if (i % 2 == 0) 0.75f else 0.42f),
            Offset(center.x + tickIn * ca, center.y + tickIn * sa),
            Offset(center.x + tickOut * ca, center.y + tickOut * sa),
            strokeWidth = if (i % 2 == 0) r * 0.055f else r * 0.032f,
        )
    }

    val paint = Paint().apply {
        color = android.graphics.Color.argb(185, 225, 228, 232)
        textAlign = Paint.Align.CENTER
        textSize = r * 0.46f
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val nc = drawContext.canvas.nativeCanvas
    val lx = center.x + labelSide * r * 1.02f
    nc.drawText(topLabel, lx, center.y - r * 0.62f, paint)
    nc.drawText(bottomLabel, lx, center.y + r * 1.05f, paint)
}
