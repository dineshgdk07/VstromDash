package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
import dev.gdk.bumblebee.ui.theme.Warning

@Composable
private fun PreviewCanvas(content: @Composable () -> Unit) {
    BumblebeeTheme {
        Column(
            Modifier
                .background(Canvas)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun ChunkyButtonPreview() {
    PreviewCanvas {
        ChunkyStyle.entries.forEach { style ->
            ChunkyButton(text = "Add friends", onClick = {}, style = style)
        }
        ChunkyButton(text = "Compact", onClick = {}, compact = true)
        ChunkyButton(text = "Disabled", onClick = {}, enabled = false)
        ChunkyButton(text = "With icon", onClick = {}, style = ChunkyStyle.Lime, icon = Icons.Rounded.Add)
    }
}

@Preview(fontScale = 1.3f)
@Composable
private fun ChunkyButtonLongPreview() {
    PreviewCanvas {
        ChunkyButton(
            text = "Very long label that should ellipsize at the end",
            onClick = {},
            style = ChunkyStyle.Outline,
        )
    }
}

@Preview
@Composable
private fun ChunkyIconButtonPreview() {
    PreviewCanvas {
        ChunkyIconButton(icon = Icons.Rounded.Close, onClick = {}, contentDescription = "Back")
        ChunkyIconButton(icon = Icons.Rounded.Close, onClick = {}, wide = true, contentDescription = "Close sheet")
        ChunkyIconButton(icon = Icons.Rounded.Close, onClick = {}, enabled = false)
        ChunkyIconButton(
            icon = Icons.Rounded.ChevronRight,
            onClick = {},
            style = ChunkyStyle.Lime,
            contentDescription = "Open",
        )
    }
}

@Preview
@Composable
private fun BlockTilePreview() {
    PreviewCanvas {
        BlockTile(
            title = "Insights",
            color = BlockColor.Lavender,
            onClick = {},
            badge = { BlockBadge("▲ rating") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
        BlockTile(
            title = "Tips and tricks for long highway rides",
            color = BlockColor.Mint,
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        )
    }
}

@Preview
@Composable
private fun SectionCardPreview() {
    PreviewCanvas {
        SectionCard(
            header = { SectionLabel(text = "Friends", count = 0, showHairline = true) },
        ) {
            Text("Body", style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(
            header = {
                Text("PLAY", style = MaterialTheme.typography.headlineMedium)
            },
        ) {
            Text("Tiles go here", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
private fun IconTilePreview() {
    PreviewCanvas {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon = Icons.Rounded.DirectionsBike, label = "Math", onClick = {}, selected = true)
            IconTile(icon = Icons.Rounded.Insights, label = "Stats", onClick = {}, selected = false)
        }
    }
}

@Preview
@Composable
private fun PillChipPreview() {
    PreviewCanvas {
        PillChipRow {
            PillChip(text = "Overview", selected = true, onClick = {})
            PillChip(text = "Speed", selected = false, onClick = {})
            PillChip(text = "Disabled", selected = false, onClick = {}, enabled = false)
        }
    }
}

@Preview
@Composable
private fun ChunkyPressedPreview() {
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(Unit) {
        source.emit(PressInteraction.Press(Offset.Zero))
    }
    PreviewCanvas {
        ChunkySlot(
            modifier = Modifier.fillMaxWidth(),
            faceHeight = 56.dp,
            depth = 5.dp,
            shape = ShapeMd,
            slabColor = SurfaceSunken,
            interactionSource = source,
            enabled = true,
            onClick = {},
            faceModifier = Modifier.background(SurfaceRaised, ShapeMd),
        ) {
            Text("PRESSED", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun DropdownLabelPreview() {
    PreviewCanvas {
        DropdownLabel(text = "Math", onClick = {})
        Spacer(Modifier.width(16.dp))
        DropdownLabel(text = "Sprint duel", onClick = {})
    }
}

@Preview
@Composable
private fun StatPillPreview() {
    PreviewCanvas {
        StatPill(icon = Icons.Rounded.Star, value = "275")
        StatPill(icon = Icons.Rounded.LocalGasStation, value = "5 / 7", valueTint = Warning)
    }
}

@Preview
@Composable
private fun StatTriplePreview() {
    PreviewCanvas {
        StatTriple(
            headlineLabel = "Overview",
            headlineValue = "63.64%",
            columns = listOf(
                StatTripleColumn("124 km", "Distance", StatTone.Good),
                StatTripleColumn("2:14", "Time", StatTone.Neutral),
                StatTripleColumn("18%", "In ties", StatTone.Bad),
            ),
        )
    }
}

@Preview
@Composable
private fun ProgressPillPreview() {
    PreviewCanvas {
        ProgressPill(progress = 0.35f, current = 2, total = 7, trailingIcon = Icons.Rounded.Star)
        ProgressPill(progress = 0f, current = 0, total = 7)
    }
}

@Preview
@Composable
private fun ChunkyDialogLimePreview() {
    BumblebeeTheme {
        ChunkyDialog(
            title = "Hold on!",
            body = "Are you sure you want to leave this duel?",
            dismissLabel = "Cancel",
            confirmLabel = "Continue",
            onDismiss = {},
            onConfirm = {},
            confirmStyle = ChunkyDialogConfirmStyle.Lime,
        )
    }
}

@Preview
@Composable
private fun ChunkyDialogDangerPreview() {
    BumblebeeTheme {
        ChunkyDialog(
            title = "Exit",
            body = "This will end your session.",
            dismissLabel = "Stay",
            confirmLabel = "Exit",
            onDismiss = {},
            onConfirm = {},
            confirmStyle = ChunkyDialogConfirmStyle.Danger,
        )
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    PreviewCanvas {
        EmptyState(
            headline = "No rides yet",
            subtitle = "The road is not going to ride itself.",
            primaryLabel = "Add friends",
            onPrimary = {},
            linkLabel = "Learn more",
            linkIcon = Icons.Rounded.Explore,
            onLink = {},
            art = {
                Icon(Icons.Rounded.DirectionsBike, contentDescription = null, modifier = Modifier.size(120.dp))
            },
        )
    }
}

@Preview
@Composable
private fun ForgeTopBarPreview() {
    PreviewCanvas {
        ForgeTopBar(
            title = "Insights",
            onBack = {},
            trailing = {
                StatPill(icon = Icons.Rounded.Star, value = "434 XP")
            },
        )
        ForgeTopBar(
            title = "Fuel log",
            onBack = {},
            trailing = {
                ChunkyButton(text = "Log", onClick = {}, compact = true, style = ChunkyStyle.Lime)
            },
        )
    }
}

@Preview
@Composable
private fun ForgeBottomNavPreview() {
    PreviewCanvas {
        ForgeBottomNav(
            items = listOf(
                ForgeNavItem(Icons.Rounded.Home, "Cockpit", selected = true),
                ForgeNavItem(Icons.Rounded.Route, "Journey", badge = ForgeNavBadge.Dot),
                ForgeNavItem(Icons.Rounded.Insights, "Ride"),
                ForgeNavItem(Icons.Rounded.Garage, "Garage", badge = ForgeNavBadge.Count(3)),
                ForgeNavItem(Icons.Rounded.Share, "More"),
            ),
            onItemClick = {},
        )
    }
}
