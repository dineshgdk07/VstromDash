package dev.gdk.bumblebee.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.LimeDeep
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import kotlin.math.roundToInt

enum class ChunkyStyle {
    Neutral,
    Lime,
    Danger,
    Coral,
    Outline,
}

private fun dangerSlab(): Color = lerp(Danger, Color.Black, 0.3f)

private fun coralSlab(): Color = lerp(BlockCoral, Color.Black, 0.3f)

@Composable
private fun rememberChunkyOffsetPx(
    interactionSource: MutableInteractionSource,
    depth: Dp,
    enabled: Boolean,
): Float {
    val density = LocalDensity.current
    val depthPx = with(density) { depth.toPx() }
    val motionOn = motionAnimationsEnabled()
    val haptic = LocalHapticFeedback.current
    val isPressed = interactionSource.collectIsPressedAsState().value
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(isPressed, enabled, depthPx, motionOn) {
        val target = if (isPressed && enabled) depthPx else 0f
        if (!enabled) {
            offsetY.snapTo(0f)
            return@LaunchedEffect
        }
        if (!motionOn) {
            offsetY.snapTo(target)
        } else if (isPressed) {
            offsetY.animateTo(target, tween(durationMillis = 80))
        } else {
            offsetY.animateTo(target, spring(dampingRatio = 0.6f))
        }
    }

    return offsetY.value
}

/** Face travel on press; slab stays fixed under the resting face (§2.5). */
@Composable
internal fun Modifier.chunky(
    slabColor: Color?,
    depth: Dp,
    faceHeight: Dp,
    shape: Shape,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    modifierRole: Role = Role.Button,
): Modifier {
    val density = LocalDensity.current
    val depthPx = with(density) { depth.toPx() }
    val faceHeightPx = with(density) { faceHeight.toPx() }
    val offsetPx = rememberChunkyOffsetPx(interactionSource, depth, enabled)

    val clickMod =
        if (onClick != null && enabled) {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                role = modifierRole,
                onClick = onClick,
            )
        } else {
            Modifier
        }

    return this
        .then(clickMod)
        .offset { IntOffset(0, offsetPx.roundToInt()) }
}

@Composable
internal fun ChunkySlot(
    modifier: Modifier,
    faceHeight: Dp,
    depth: Dp,
    shape: Shape,
    slabColor: Color?,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    faceModifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.height(faceHeight + depth)) {
        if (enabled && slabColor != null) {
            Box(
                Modifier
                    .padding(top = depth)
                    .height(faceHeight)
                    .fillMaxWidth()
                    .background(slabColor, shape),
            )
        }
        Box(
            Modifier
                .height(faceHeight)
                .fillMaxWidth()
                .then(faceModifier)
                .chunky(
                    slabColor = null,
                    depth = depth,
                    faceHeight = faceHeight,
                    shape = shape,
                    interactionSource = interactionSource,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

/** Signature chunky CTA (§4.1). Ref: `empty-state.jpeg` (Outline). */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChunkyStyle = ChunkyStyle.Neutral,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) {
    val faceHeight = if (compact) 44.dp else 56.dp
    val depth = if (compact) 4.dp else 5.dp
    val fallbackSource = remember { MutableInteractionSource() }
    val source = interactionSource ?: fallbackSource

    val (face, slab, borderW, borderC, textColor) =
        if (!enabled) {
            StyleColors(SurfaceRaised, null, 0.dp, Color.Transparent, TextDisabled)
        } else {
            when (style) {
                ChunkyStyle.Neutral -> StyleColors(SurfaceRaised, SurfaceSunken, 0.dp, Color.Transparent, TextPrimary)
                ChunkyStyle.Lime -> StyleColors(Lime, LimeDeep, 0.dp, Color.Transparent, OnBlock)
                ChunkyStyle.Danger -> StyleColors(Danger, dangerSlab(), 0.dp, Color.Transparent, TextPrimary)
                ChunkyStyle.Coral -> StyleColors(BlockCoral, coralSlab(), 0.dp, Color.Transparent, OnBlock)
                ChunkyStyle.Outline -> StyleColors(Canvas, Lime, 1.5.dp, Lime, TextPrimary)
            }
        }

    val borderMod =
        if (borderW > 0.dp) Modifier.border(borderW, borderC, ShapeMd) else Modifier

    ChunkySlot(
        modifier = modifier.fillMaxWidth(),
        faceHeight = faceHeight,
        depth = depth,
        shape = ShapeMd,
        slabColor = slab,
        interactionSource = source,
        enabled = enabled,
        onClick = if (enabled) onClick else null,
        faceModifier = Modifier.background(face, ShapeMd).then(borderMod),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Back, close and top-bar icon actions (§4.2). Ref: `menu-sheet.jpeg`. */
@Composable
fun ChunkyIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    wide: Boolean = false,
    style: ChunkyStyle = ChunkyStyle.Neutral,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val faceHeight = 48.dp
    val depth = 4.dp
    val shape = if (wide) ShapePill else ShapeSm
    val widthMod = if (wide) Modifier.width(56.dp) else Modifier.size(48.dp)
    val (face, slab, iconTint) = when {
        !enabled -> Triple(SurfaceRaised, null, TextDisabled)
        style == ChunkyStyle.Lime -> Triple(Lime, LimeDeep, OnBlock)
        style == ChunkyStyle.Coral -> Triple(BlockCoral, coralSlab(), OnBlock)
        else -> Triple(SurfaceRaised, SurfaceSunken, TextPrimary)
    }

    ChunkySlot(
        modifier = modifier.then(widthMod),
        faceHeight = faceHeight,
        depth = depth,
        shape = shape,
        slabColor = slab,
        interactionSource = interactionSource,
        enabled = enabled,
        onClick = if (enabled) onClick else null,
        faceModifier = Modifier.background(face, shape),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
    }
}

private data class StyleColors(
    val face: Color,
    val slab: Color?,
    val borderWidth: Dp,
    val borderColor: Color,
    val text: Color,
)
