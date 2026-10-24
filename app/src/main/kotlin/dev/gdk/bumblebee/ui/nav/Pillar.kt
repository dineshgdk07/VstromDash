package dev.gdk.bumblebee.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector

enum class Pillar(val route: String, val label: String, val icon: ImageVector) {
    COCKPIT("cockpit", "Cockpit", Icons.Filled.Speed),
    JOURNEY("journey", "Journey", Icons.Filled.Map),
    RIDE("ride", "Ride", Icons.Filled.DirectionsBike),
    EXPLORER("explorer", "Explorer", Icons.Filled.Explore),
    GARAGE("garage", "Garage", Icons.Filled.Garage),
}
