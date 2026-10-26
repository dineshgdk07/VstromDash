package dev.gdk.bumblebee.ui.hud.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.LabelMute
import dev.gdk.bumblebee.ui.theme.SuzukiYellow

data class HudRow(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val sub: String,
    val accent: Color = ForgeWhite,
    val iconAccent: Color = ForgeWhite.copy(alpha = 0.72f),
    val fuelBars: Int? = null,
    val large: Boolean = false,
    /** Draws the yellow edge light down the inner side of this row. */
    val railed: Boolean = false,
    /** Lifts the row to the mock's selected-cell tone. */
    val highlighted: Boolean = false,
    /** Daily high and low, shown as a trailing column when present. */
    val hiLo: Pair<Int, Int>? = null,
)

@Composable
fun HudStack(
    rows: List<HudRow>,
    modifier: Modifier = Modifier,
    railOnStart: Boolean = false,
) {
    Box(modifier.hudCardSurface()) {
        Column(Modifier.fillMaxSize()) {
            rows.forEachIndexed { i, row ->
                HudCell(
                    row = row,
                    shaded = i % 2 == 1,
                    railOnStart = railOnStart,
                    modifier = Modifier.weight(1f),
                )
                if (i != rows.lastIndex) {
                    Divider(accent = row.highlighted)
                }
            }
        }
    }
}

@Composable
private fun Divider(accent: Boolean) {
    val tint = if (accent) SuzukiYellow.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.10f)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (accent) 1.5.dp else 1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, tint, Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.Black.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun HudCell(
    row: HudRow,
    shaded: Boolean,
    railOnStart: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val tight = maxHeight < 86.dp
        val valueSize = when {
            row.large && tight -> 27.sp
            row.large -> 33.sp
            tight -> 19.sp
            else -> 23.sp
        }
        when {
            row.highlighted -> Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.045f), Color.White.copy(alpha = 0.012f)),
                    ),
                ),
            )
            shaded -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                row.icon,
                contentDescription = null,
                tint = row.iconAccent,
                modifier = Modifier.size(if (row.large) 26.dp else 21.dp),
            )
            Column(
                Modifier.weight(1f).padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    row.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = LabelMute,
                    maxLines = 1,
                )
                Text(
                    row.value,
                    style = if (row.large) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                    color = row.accent,
                    fontSize = valueSize,
                    lineHeight = valueSize * 1.05f,
                    maxLines = 1,
                )
                if (row.fuelBars != null) {
                    FuelBars(row.fuelBars, Modifier.padding(top = 3.dp, bottom = 2.dp))
                }
                Text(
                    row.sub,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = if (tight) 11.sp else 12.sp,
                    color = LabelMute,
                    maxLines = 1,
                )
            }
            row.hiLo?.let { (hi, lo) ->
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        "↑ $hi°",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = if (tight) 11.sp else 12.sp,
                        color = ForgeWhite.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                    Text(
                        "↓ $lo°",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = if (tight) 11.sp else 12.sp,
                        color = LabelMute,
                        maxLines = 1,
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = ForgeWhite.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp),
            )
        }
        if (row.railed) {
            Box(
                Modifier
                    .align(if (railOnStart) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to SuzukiYellow.copy(alpha = 0.35f),
                            0.2f to SuzukiYellow,
                            0.8f to SuzukiYellow,
                            1f to SuzukiYellow.copy(alpha = 0.35f),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun FuelBars(filled: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(6) { i ->
            Box(
                Modifier
                    .width(18.dp)
                    .height(5.dp)
                    .background(
                        if (i < filled) {
                            Brush.linearGradient(
                                listOf(SuzukiYellow, Color(0xFFE0A80E)),
                                Offset.Zero,
                                Offset(0f, 12f),
                            )
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF2A2C31), Color(0xFF212328)))
                        },
                    ),
            )
        }
    }
}
