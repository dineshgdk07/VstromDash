package dev.gdk.bumblebee.ui.hud.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.ForgeBlack
import dev.gdk.bumblebee.ui.theme.ForgeCardTop
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import kotlin.math.min

/** Pixel layout shared by chassis, stacks, and the dial. */
data class ClusterLayout(val w: Float, val h: Float) {
    val gaugeD = min(h * 0.74f, w * 0.44f)
    val cx = w * 0.50f
    val cy = h * 0.435f
    val biteR = gaugeD * 0.50f
    val stackTop = h * 0.115f
    val stackH = h * 0.62f
    val stackPadX = w * 0.026f
    val stackW = (cx - biteR * 1.04f - stackPadX).coerceAtLeast(w * 0.20f)
    val dockTop = h * 0.775f
    val dockH = h * 0.145f

    fun gaugeHole(): Path = Path().apply {
        addOval(Rect(cx - biteR, cy - biteR, cx + biteR, cy + biteR))
    }

    fun leftPanel(): Path {
        val cut = min(w, h) * 0.032f
        val L = w * 0.016f
        val T = h * 0.098f
        val R = cx + biteR * 0.22f
        val B = h * 0.745f
        return Path().apply {
            moveTo(L + cut, T)
            lineTo(R - cut, T)
            lineTo(R, T + cut * 1.6f)
            lineTo(R, B - cut * 1.6f)
            lineTo(R - cut, B)
            lineTo(L + cut, B)
            lineTo(L, B - cut)
            lineTo(L, T + cut)
            close()
        }
    }

    fun rightPanel(): Path {
        val cut = min(w, h) * 0.032f
        val L = cx - biteR * 0.22f
        val T = h * 0.098f
        val R = w * 0.984f
        val B = h * 0.745f
        return Path().apply {
            moveTo(L + cut, T)
            lineTo(R - cut, T)
            lineTo(R, T + cut)
            lineTo(R, B - cut)
            lineTo(R - cut, B)
            lineTo(L + cut, B)
            lineTo(L, B - cut * 1.6f)
            lineTo(L, T + cut * 1.6f)
            close()
        }
    }

    /** Raised hexagonal pod that carries the centre button in the dock. */
    fun centrePod(): Path {
        val hw = w * 0.108f
        val top = dockTop - h * 0.058f
        val bot = dockTop + dockH + h * 0.012f
        val notch = (bot - top) * 0.34f
        return Path().apply {
            moveTo(cx - hw + notch * 0.6f, top)
            lineTo(cx + hw - notch * 0.6f, top)
            lineTo(cx + hw, top + notch)
            lineTo(cx + hw, bot - notch)
            lineTo(cx + hw - notch * 0.6f, bot)
            lineTo(cx - hw + notch * 0.6f, bot)
            lineTo(cx - hw, bot - notch)
            lineTo(cx - hw, top + notch)
            close()
        }
    }

    fun outerFrame(): Path {
        val inset = min(w, h) * 0.012f
        val cut = min(w, h) * 0.055f
        val l = inset
        val t = inset
        val r = w - inset
        val b = h - inset
        return Path().apply {
            moveTo(l + cut, t)
            lineTo(r - cut, t)
            lineTo(r, t + cut)
            lineTo(r, b - cut)
            lineTo(r - cut, b)
            lineTo(l + cut, b)
            lineTo(l, b - cut)
            lineTo(l, t + cut)
            close()
        }
    }

    fun dock(): Path {
        val cut = min(w, h) * 0.02f
        return Path().apply {
            moveTo(w * 0.03f + cut, dockTop)
            lineTo(w * 0.97f - cut, dockTop)
            lineTo(w * 0.97f, dockTop + cut)
            lineTo(w * 0.955f, dockTop + dockH)
            lineTo(w * 0.045f, dockTop + dockH)
            lineTo(w * 0.03f, dockTop + cut)
            close()
        }
    }

    /** Top bar whose bottom edge drops into a centre tab for the clock and date. */
    fun statusWell(): Path {
        val t = h * 0.010f
        val side = h * 0.070f
        val tab = h * 0.108f
        val cut = min(w, h) * 0.016f
        return Path().apply {
            moveTo(w * 0.030f + cut, t)
            lineTo(w * 0.970f - cut, t)
            lineTo(w * 0.970f, t + cut)
            lineTo(w * 0.970f, side)
            lineTo(w * 0.632f, side)
            lineTo(w * 0.606f, tab)
            lineTo(w * 0.394f, tab)
            lineTo(w * 0.368f, side)
            lineTo(w * 0.030f, side)
            lineTo(w * 0.030f, t + cut)
            close()
        }
    }
}

@Composable
fun ClusterChassis(layout: ClusterLayout, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val hole = layout.gaugeHole()
        backdrop(layout)
        drawPath(
            layout.outerFrame(),
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f), Color.Black),
            ),
            style = Stroke(1.6.dp.toPx()),
        )
        metalPanel(layout.statusWell(), dock = true)
        clipPath(hole, ClipOp.Difference) {
            metalPanel(layout.leftPanel())
            metalPanel(layout.rightPanel())
        }
        metalPanel(layout.dock(), dock = true)
        metalPanel(layout.centrePod())
    }
}

/** Deep base, a soft pool of light behind the dial, then a corner vignette. */
private fun DrawScope.backdrop(g: ClusterLayout) {
    drawRect(ForgeBlack)
    val pool = g.biteR * 2.2f
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF1E232C).copy(alpha = 0.6f), Color.Transparent),
            center = Offset(g.cx, g.cy),
            radius = pool,
        ),
        radius = pool,
        center = Offset(g.cx, g.cy),
    )
    drawRect(
        Brush.radialGradient(
            0.42f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.75f),
            center = Offset(g.cx, g.cy),
            radius = maxOf(g.w, g.h) * 0.70f,
        ),
    )
}

/** Fine diagonal brush marks, clipped to a panel. */
private fun DrawScope.brushedTexture(path: Path, alpha: Float) {
    val spacing = 4.dp.toPx()
    clipPath(path) {
        var x = 0f
        val end = size.width + size.height
        while (x < end) {
            drawLine(
                Color.White.copy(alpha = alpha),
                Offset(x, 0f),
                Offset(x - size.height, size.height),
                strokeWidth = 1f,
            )
            x += spacing
        }
    }
}

private fun DrawScope.metalPanel(path: Path, dock: Boolean = false) {
    val fill = Brush.linearGradient(
        0f to Color(0xFF181A1E),
        0.16f to Color(0xFF101114),
        0.55f to Color(0xFF090A0C),
        1f to ForgeBlack,
        start = Offset.Zero,
        end = Offset(size.width * 0.18f, size.height),
    )
    drawPath(path, fill)
    drawPath(
        path,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dock) 0.07f else 0.12f),
            0.22f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width * 0.5f, size.height * 0.4f),
        ),
    )
    drawPath(
        path,
        Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.26f),
            0.45f to Color.White.copy(alpha = 0.045f),
            1f to Color.Black.copy(alpha = 0.7f),
        ),
        style = Stroke(2.2.dp.toPx()),
    )
}

fun clusterPanelShape(cutFrac: Float = 0.11f) = GenericShape { size, _ ->
    val cut = size.minDimension * cutFrac
    moveTo(cut, 0f)
    lineTo(size.width - cut, 0f)
    lineTo(size.width, cut)
    lineTo(size.width, size.height - cut)
    lineTo(size.width - cut, size.height)
    lineTo(cut, size.height)
    lineTo(0f, size.height - cut)
    lineTo(0f, cut)
    close()
}

fun panelPath(w: Float, h: Float, cutFrac: Float): Path {
    val cut = minOf(w, h) * cutFrac
    return Path().apply {
        moveTo(cut, 0f)
        lineTo(w - cut, 0f)
        lineTo(w, cut)
        lineTo(w, h - cut)
        lineTo(w - cut, h)
        lineTo(cut, h)
        lineTo(0f, h - cut)
        lineTo(0f, cut)
        close()
    }
}

/** Brushed, bevelled card face used by the side stacks. */
fun Modifier.hudCardSurface(cutFrac: Float = 0.05f): Modifier = this
    .clip(clusterPanelShape(cutFrac))
    .drawWithCache {
        val path = panelPath(size.width, size.height, cutFrac)
        val fill = Brush.verticalGradient(
            listOf(Color(0xFF17191D), Color(0xFF101114), Color(0xFF090A0C)),
        )
        val topSheen = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.07f),
            0.12f to Color.Transparent,
        )
        val footShadow = Brush.verticalGradient(
            0.80f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.5f),
        )
        val rim = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.035f),
                Color.Black.copy(alpha = 0.55f),
            ),
        )
        onDrawBehind {
            drawPath(path, fill)
            clipPath(path) {
                drawRect(topSheen)
                drawRect(footShadow)
            }
            drawPath(path, rim, style = Stroke(1.4.dp.toPx()))
        }
    }

fun Modifier.forgePanel(cutFrac: Float = 0.11f): Modifier = this
    .clip(clusterPanelShape(cutFrac))
    .drawWithCache {
        val path = panelPath(size.width, size.height, cutFrac)
        onDrawBehind {
            drawPath(
                path,
                Brush.linearGradient(listOf(ForgeCardTop, ForgeBlack), Offset.Zero, Offset(size.width * 0.15f, size.height)),
            )
            drawPath(path, Color.White.copy(alpha = 0.18f), style = Stroke(1.6.dp.toPx()))
        }
    }

@Composable
fun GlowBar(modifier: Modifier = Modifier) {
    Box(modifier) {
        if (Build.VERSION.SDK_INT >= 31) {
            Box(
                Modifier
                    .fillMaxSize()
                    .blur(10.dp)
                    .background(SuzukiYellow.copy(alpha = 0.6f), RoundedCornerShape(3.dp)),
            )
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(SuzukiYellow))
    }
}
