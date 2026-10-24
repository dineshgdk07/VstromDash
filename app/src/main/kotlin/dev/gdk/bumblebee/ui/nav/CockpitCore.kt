package dev.gdk.bumblebee.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.gdk.bumblebee.ui.components.ForgeBottomNav
import dev.gdk.bumblebee.ui.components.ForgeNavItem
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas

/** Visual order of the five pillars. [tabIndex] uses this for slide direction. */
val CockpitCoreOrder = listOf(
    Pillar.JOURNEY,
    Pillar.GARAGE,
    Pillar.COCKPIT,
    Pillar.RIDE,
    Pillar.EXPLORER,
)

@Composable
fun CockpitCore(
    currentRoute: String?,
    onSelect: (Pillar) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = CockpitCoreOrder.map { pillar ->
        ForgeNavItem(
            icon = pillar.icon,
            label = pillar.label,
            selected = currentRoute == pillar.route,
        )
    }
    ForgeBottomNav(
        items = items,
        onItemClick = { index -> onSelect(CockpitCoreOrder[index]) },
        modifier = modifier.navigationBarsPadding(),
    )
}

@Preview
@Composable
private fun CockpitCorePreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            CockpitCore(currentRoute = Pillar.COCKPIT.route, onSelect = {})
            CockpitCore(currentRoute = Pillar.GARAGE.route, onSelect = {})
        }
    }
}
