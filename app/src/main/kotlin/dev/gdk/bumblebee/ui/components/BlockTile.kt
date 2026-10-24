package dev.gdk.bumblebee.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BlockPink
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.TextPrimary

enum class BlockColor {
    Yellow,
    Lavender,
    Mint,
    Coral,
    Pink,
}

fun BlockColor.toColor(): Color =
    when (this) {
        BlockColor.Yellow -> BlockYellow
        BlockColor.Lavender -> BlockLavender
        BlockColor.Mint -> BlockMint
        BlockColor.Coral -> BlockCoral
        BlockColor.Pink -> BlockPink
    }

/** Black pill on a pastel block (§4.3). Ref: `menu-sheet.jpeg`. */
@Composable
fun BlockBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextPrimary,
        modifier = modifier
            .background(OnBlock, ShapePill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Pastel bento tile; flat with scale press (§4.3). Ref: `menu-sheet.jpeg`. */
@Composable
fun BlockTile(
    title: String,
    color: BlockColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    art: (@Composable BoxScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState().value
    val motionOff = !motionAnimationsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (motionOff) snap() else spring(dampingRatio = 0.6f),
        label = "blockTileScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(ShapeLg)
            .background(color.toColor(), ShapeLg)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(20.dp),
    ) {
        if (art != null) {
            Box(Modifier.fillMaxSize(), content = art)
        }
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = OnBlock,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart),
        )
        if (badge != null) {
            Box(Modifier.align(Alignment.TopEnd)) { badge() }
        }
    }
}
