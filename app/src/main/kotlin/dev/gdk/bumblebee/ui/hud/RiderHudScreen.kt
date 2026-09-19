package dev.gdk.bumblebee.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ui.cockpit.ReadinessBand
import dev.gdk.bumblebee.ui.hud.components.HudSideCard
import dev.gdk.bumblebee.ui.hud.components.SpeedDial
import dev.gdk.bumblebee.ui.theme.ForgeBezel
import dev.gdk.bumblebee.ui.theme.ForgeBlack
import dev.gdk.bumblebee.ui.theme.ForgeCard
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import dev.gdk.bumblebee.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun RiderHudScreen(model: HudViewModel = viewModel()) {
    val state by model.ui.collectAsStateWithLifecycle()
    val digital = if (state.speed.source == SpeedSource.NONE) "—" else state.speed.speedKmh.toString()
    val readyColor = when (state.readiness.band) {
        ReadinessBand.READY -> SuccessGreen
        ReadinessBand.CAUTION -> WarningAmber
        else -> SuzukiYellow
    }
    val clock = produceState(initialValue = clockNow()) {
        while (true) {
            value = clockNow()
            delay(15_000)
        }
    }
    val date = DateTimeFormatter.ofPattern("EEE, d MMM yyyy").format(LocalDate.now())
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(ForgeBlack)
                .border(1.dp, ForgeBezel, RoundedCornerShape(28.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LiveDot("BLE", state.bleReady)
                    LiveDot("GPS", state.gpsOn)
                    LiveDot("Internet", false)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(clock.value, style = MaterialTheme.typography.titleLarge)
                    Text(date, style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.45f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("—", style = MaterialTheme.typography.titleMedium)
                        Text("no forecast", style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.45f))
                    }
                    Text("${state.phoneBatteryPercent ?: "—"}%", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (state.navStreet != null) {
                Text(
                    "${state.navDist ?: ""} · ${state.navStreet}",
                    color = SuzukiYellow,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    Modifier
                        .width(248.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HudSideCard(
                        "Ride readiness",
                        if (state.readiness.band == ReadinessBand.COMPUTING) "…" else "${state.readiness.percent}%",
                        state.readiness.label,
                        Icons.Outlined.Security,
                        readyColor,
                        stripe = true,
                        modifier = Modifier.weight(1f),
                    )
                    HudSideCard(
                        "Fuel",
                        state.fuelBars?.let { "$it bars" } ?: "—",
                        "est. range —",
                        Icons.Outlined.LocalGasStation,
                        modifier = Modifier.weight(1f),
                    )
                    HudSideCard(
                        "Today's ride",
                        "—",
                        "no ride yet",
                        Icons.Outlined.Route,
                        modifier = Modifier.weight(1f),
                    )
                }
                SpeedDial(
                    speedKmh = state.speed.speedKmh,
                    source = state.speed.source,
                    digital = digital,
                    heading = state.heading,
                    odometerKm = state.odometerKm,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Column(
                    Modifier
                        .width(248.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HudSideCard("Weather", "—", "no forecast", Icons.Outlined.Cloud, modifier = Modifier.weight(1f))
                    HudSideCard(
                        "Link & fuel",
                        if (state.bleReady) "OK" else "Check",
                        "not engine health",
                        Icons.Outlined.FavoriteBorder,
                        if (state.bleReady) SuccessGreen else WarningAmber,
                        modifier = Modifier.weight(1f),
                    )
                    HudSideCard("Service due", "—", "not set", Icons.Outlined.Build, modifier = Modifier.weight(1f))
                    HudSideCard(
                        "Warnings",
                        if (state.bleReady) "None" else "Link",
                        "app & connection",
                        Icons.Outlined.Warning,
                        if (state.bleReady) SuccessGreen else WarningAmber,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ForgeCard),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HudAction(Icons.Outlined.Navigation, "Navigate", "Plan a route")
                    HudAction(Icons.Outlined.MyLocation, "Find Bike", "Locate bike")
                    Chevron()
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(ForgeBlack)
                            .border(3.dp, SuzukiYellow, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("S", style = MaterialTheme.typography.headlineMedium, color = SuzukiYellow)
                    }
                    Chevron(flip = true)
                    HudAction(Icons.Outlined.Share, "Share", "Share location")
                    HudAction(Icons.Outlined.Explore, "Explore", "Riding journal")
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (state.bleReady) "LINK READY" else "NO LINK",
                    color = if (state.bleReady) SuccessGreen else WarningAmber,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text("LONG PRESS CENTER FOR QUICK MENU", style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.35f))
                Text("☎ ${state.phoneBatteryPercent ?: "—"}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LiveDot(label: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (on) SuccessGreen else ForgeGraphite),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) ForgeWhite else ForgeWhite.copy(alpha = 0.4f))
    }
}

@Composable
private fun HudAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = title, tint = SuzukiYellow, modifier = Modifier.size(22.dp))
        Column {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = ForgeWhite)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = ForgeWhite.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun Chevron(flip: Boolean = false) {
    val shape = GenericShape { size, _ ->
        if (!flip) {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
            close()
        } else {
            moveTo(size.width, 0f)
            lineTo(0f, size.height / 2f)
            lineTo(size.width, size.height)
            close()
        }
    }
    Box(Modifier.size(18.dp, 28.dp).clip(shape).background(SuzukiYellow))
}

private fun clockNow(): String = DateTimeFormatter.ofPattern("hh:mm a").format(LocalTime.now())
