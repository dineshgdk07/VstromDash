package dev.gdk.bumblebee.ui.ride.replay

import androidx.compose.ui.graphics.Color
import dev.gdk.bumblebee.ride.SpeedBand
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BlockPink
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.TextDisabled

internal fun bandColor(band: SpeedBand): Color = when (band) {
    SpeedBand.IDLE -> TextDisabled
    SpeedBand.B1_30 -> BlockMint
    SpeedBand.B30_50 -> BlockLavender
    SpeedBand.B50_70 -> BlockYellow
    SpeedBand.B70_90 -> BlockCoral
    SpeedBand.B90_PLUS -> BlockPink
}
