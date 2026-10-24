package dev.gdk.bumblebee.ui.ride

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.TrackPlot
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.Stroke as Hairline
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

/** Recent-ride row shared with Explorer. Thumbnail is a stroke, not a tiled map. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RideArchiveRow(
    row: RideRow,
    track: List<GpsFix>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val title = row.name?.takeIf { it.isNotBlank() } ?: formatWhen(row.startTs)
    val stats = buildString {
        append(formatKm(row.km))
        append(" · ")
        append(formatClock(row.movingSec))
        if (row.maxSpeedKmh > 0) append(" · ${row.maxSpeedKmh} km/h")
    }
    val odo = odoRange(row.odoStartKm, row.odoEndKm)
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .then(if (selected) Modifier.background(SurfaceRaised) else Modifier)
                .then(
                    if (onLongClick != null) {
                        Modifier.combinedClickable(
                            role = Role.Button,
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    } else {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .width(4.dp)
                        .height(36.dp)
                        .background(Lime, ShapePill),
                )
            }
            RouteThumb(track)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (odo != null) {
                    Text(
                        text = odo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Open ride",
                tint = TextSecondary,
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Hairline),
            )
        }
    }
}

@Composable
private fun RouteThumb(track: List<GpsFix>) {
    val points = remember(track) { TrackPlot.normalize(track) }
    Box(
        Modifier
            .size(56.dp)
            .background(SurfaceRaised, ShapeSm),
    ) {
        if (points.size < 2) return@Box
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val path = Path()
            points.forEachIndexed { index, point ->
                val at = Offset(point.x * size.width, point.y * size.height)
                if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
            }
            drawPath(
                path,
                TextPrimary,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun odoRange(start: Int?, end: Int?): String? {
    if (start == null || end == null) return null
    return "%,d → %,d".format(start, end)
}

@Preview(showBackground = true, backgroundColor = 0xFF171717)
@Composable
private fun RideArchiveRowPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas).padding(16.dp)) {
            RideArchiveRow(
                row = RideRow(
                    id = 1,
                    startTs = 1_700_000_000_000,
                    endTs = 1_700_003_600_000,
                    km = 47.0,
                    movingSec = 2400,
                    idleSec = 90,
                    maxSpeedKmh = 72,
                    avgMovingSpeedKmh = 48.0,
                    headline = "Morning ride",
                    name = "Morning ride",
                    odoStartKm = 12_340,
                    odoEndKm = 12_387,
                ),
                track = listOf(
                    GpsFix(0, 12.97, 77.59),
                    GpsFix(60_000, 12.99, 77.61),
                ),
                onClick = {},
                showDivider = false,
            )
        }
    }
}
