package dev.gdk.bumblebee.ui.hud

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ride.SpeedSource
import dev.gdk.bumblebee.ui.cockpit.ReadinessBand
import dev.gdk.bumblebee.ui.hud.components.ClusterChassis
import dev.gdk.bumblebee.ui.hud.components.ClusterLayout
import dev.gdk.bumblebee.ui.hud.components.HudIcons
import dev.gdk.bumblebee.ui.hud.components.HudRow
import dev.gdk.bumblebee.ui.hud.components.HudStack
import dev.gdk.bumblebee.ui.hud.components.SpeedDial
import dev.gdk.bumblebee.ui.theme.ForgeBlack
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.LabelMute
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.SuzukiYellow
import dev.gdk.bumblebee.ui.theme.WarningAmber
import dev.gdk.bumblebee.weather.WeatherSnapshot
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun RiderHudScreen(model: HudViewModel = viewModel()) {
    val state by model.ui.collectAsStateWithLifecycle()
    val digital = if (state.speed.source == SpeedSource.NONE) "0" else state.speed.speedKmh.toString()
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
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(ForgeBlack),
    ) {
        val density = LocalDensity.current
        val layout = ClusterLayout(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })
        fun px(v: Float) = with(density) { v.toDp() }
        ClusterChassis(layout, Modifier.fillMaxSize())
        StatusBar(
            ble = state.bleReady,
            gps = state.gpsOn,
            clock = clock.value,
            date = date,
            phonePercent = state.phoneBatteryPercent,
            weather = state.weather,
            statusSideHeight = px(layout.h * 0.050f),
            modifier = Modifier
                .offset { IntOffset(0, (layout.h * 0.014f).roundToInt()) }
                .fillMaxWidth()
                .padding(horizontal = px(layout.w * 0.05f)),
        )
        if (state.navStreet != null) {
            Text(
                "${state.navDist ?: ""} · ${state.navStreet}",
                color = SuzukiYellow,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = px(layout.h * 0.09f)),
            )
        }
        HudStack(
            modifier = Modifier
                .offset {
                    IntOffset(layout.stackPadX.roundToInt(), layout.stackTop.roundToInt())
                }
                .width(px(layout.stackW))
                .height(px(layout.stackH))
                .zIndex(1f),
            rows = listOf(
                HudRow(
                    HudIcons.Shield,
                    "Ride readiness",
                    if (state.readiness.band == ReadinessBand.COMPUTING) "…" else "${state.readiness.percent}%",
                    state.readiness.label,
                    readyColor,
                    large = true,
                    railed = true,
                    highlighted = true,
                ),
                HudRow(
                    HudIcons.Fuel,
                    "Fuel",
                    state.fuelBars?.let { "$it Bars" } ?: "—",
                    state.rangeKm?.let { "Est. ${it.toInt()} km" } ?: "range after a few fills",
                    fuelBars = state.fuelBars ?: 0,
                ),
                HudRow(
                    HudIcons.Road,
                    "Trip A",
                    state.tripAKm?.let { String.format("%.1f km", it) } ?: "—",
                    "cluster",
                ),
            ),
        )
        HudStack(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (layout.w - layout.stackPadX - layout.stackW).roundToInt(),
                        layout.stackTop.roundToInt(),
                    )
                }
                .width(px(layout.stackW))
                .height(px(layout.stackH))
                .zIndex(1f),
            rows = listOf(
                HudRow(
                    HudIcons.Weather,
                    "Weather",
                    state.weather?.let { "${it.tempC.roundToInt()}°C" } ?: "—",
                    state.weather?.label ?: "no forecast",
                    hiLo = state.weather?.let { w ->
                        val hi = w.highC?.roundToInt()
                        val lo = w.lowC?.roundToInt()
                        if (hi != null && lo != null) hi to lo else null
                    },
                ),
                HudRow(
                    HudIcons.Heart,
                    "Trip B",
                    state.tripBKm?.let { String.format("%.1f km", it) } ?: "—",
                    "cluster",
                ),
                HudRow(
                    HudIcons.Wrench,
                    "Service due",
                    run {
                        val due = state.serviceDueKm
                        when {
                            due == null -> "—"
                            due <= 0 -> "Overdue"
                            else -> "%,d km".format(due)
                        }
                    },
                    state.serviceEta ?: if (state.serviceDueKm == null) "set in Garage" else "from odo interval",
                ),
                HudRow(
                    HudIcons.Warning,
                    "Warnings",
                    if (state.bleReady) "None" else "Waiting",
                    if (state.bleReady) "All systems normal" else "link after ignition",
                    if (state.bleReady) SuccessGreen else LabelMute,
                ),
            ),
        )
        SpeedDial(
            digital = digital,
            speedKmh = state.speed.speedKmh,
            odometerKm = state.odometerKm,
            fuelBars = state.fuelBars,
            rangeKm = state.rangeKm,
            tripAKm = state.tripAKm,
            tripBKm = state.tripBKm,
            navLabel = listOfNotNull(state.navDist, state.navStreet).joinToString(" · ").ifBlank { null },
            modifier = Modifier
                .offset {
                    IntOffset(
                        (layout.cx - layout.gaugeD / 2f).roundToInt(),
                        (layout.cy - layout.gaugeD / 2f).roundToInt(),
                    )
                }
                .size(px(layout.gaugeD))
                .zIndex(2f),
        )
        StatusDock(
            linked = state.bleReady,
            gps = state.gpsOn,
            heading = state.heading,
            nav = listOfNotNull(state.navDist, state.navStreet).joinToString(" · ").ifBlank { null },
            centerGap = px(layout.w * 0.26f),
            modifier = Modifier
                .offset { IntOffset(0, layout.dockTop.roundToInt()) }
                .fillMaxWidth()
                .height(px(layout.dockH))
                .padding(horizontal = px(layout.w * 0.045f))
                .zIndex(3f),
        )
        Footer(
            linked = state.bleReady,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = px(layout.w * 0.05f), vertical = px(layout.h * 0.030f)),
        )
    }
}

@Composable
private fun StatusBar(
    ble: Boolean,
    gps: Boolean,
    clock: String,
    date: String,
    phonePercent: Int?,
    weather: WeatherSnapshot?,
    statusSideHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(statusSideHeight),
        ) {
            LiveDot(HudIcons.Bluetooth, "BLE", ble)
            LiveDot(HudIcons.Gps, "GPS", gps)
            LiveDot(HudIcons.Weather, "Internet", weather != null)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(clock.uppercase(), style = MaterialTheme.typography.titleMedium, fontSize = 18.sp)
            Text(date, style = MaterialTheme.typography.labelSmall, color = LabelMute)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(statusSideHeight),
        ) {
            Icon(weatherIcon(weather), contentDescription = null, tint = SuzukiYellow, modifier = Modifier.size(16.dp))
            Column {
                Text(
                    weather?.let { "${it.tempC.roundToInt()}°C" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    weather?.label ?: "no forecast",
                    style = MaterialTheme.typography.labelSmall,
                    color = LabelMute,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(HudIcons.Battery, contentDescription = null, tint = ForgeWhite, modifier = Modifier.size(16.dp))
                Text("${phonePercent ?: "—"}%", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun LiveDot(icon: ImageVector, label: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(icon, contentDescription = label, tint = if (on) ForgeWhite else ForgeWhite.copy(alpha = 0.35f), modifier = Modifier.size(13.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) ForgeWhite else LabelMute)
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) SuccessGreen else ForgeGraphite))
    }
}

@Composable
private fun StatusDock(
    linked: Boolean,
    gps: Boolean,
    heading: String?,
    nav: String?,
    centerGap: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockItem(HudIcons.Bluetooth, if (linked) "Linked" else "Waiting", if (linked) "Bike cluster" else "After ignition")
                DockItem(HudIcons.Gps, if (gps) "GPS" else "No GPS", heading ?: "waiting for a fix")
            }
            Spacer(Modifier.width(centerGap))
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockItem(HudIcons.Nav, "Turn", nav ?: "Maps is quiet")
                DockItem(HudIcons.Fuel, "Range", "from fuel bars")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HexChevron(pointInwardRight = true)
            Box(Modifier.offset(y = (-8).dp).size(84.dp), contentAlignment = Alignment.Center) {
                if (Build.VERSION.SDK_INT >= 31) {
                    Box(
                        Modifier.size(74.dp).blur(18.dp).background(SuzukiYellow.copy(alpha = 0.18f), CircleShape),
                    )
                }
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF1A1C20), ForgeBlack)))
                        .border(1.6.dp, SuzukiYellow.copy(alpha = 0.85f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "S",
                        fontSize = 32.sp,
                        color = SuzukiYellow,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }
            HexChevron(pointInwardRight = false)
        }
    }
}

private fun weatherIcon(weather: WeatherSnapshot?) = when (weather?.suzukiCode) {
    0x01 -> HudIcons.Sun
    0x02 -> HudIcons.Weather
    0x03 -> HudIcons.Weather
    0x04, 0x05 -> HudIcons.Warning
    else -> HudIcons.Sun
}

@Composable
private fun Footer(linked: Boolean, modifier: Modifier = Modifier) {
    Text(
        if (linked) "Linked" else "Waiting for the bike",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = LabelMute,
    )
}

@Composable
private fun DockItem(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = title, tint = SuzukiYellow, modifier = Modifier.size(22.dp))
        Column {
            Text(title.uppercase(), style = MaterialTheme.typography.labelLarge)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = LabelMute)
        }
    }
}

@Composable
private fun HexChevron(pointInwardRight: Boolean) {
    Canvas(Modifier.size(20.dp, 38.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (pointInwardRight) {
                moveTo(0f, 0f)
                lineTo(w * 0.52f, 0f)
                lineTo(w, h / 2f)
                lineTo(w * 0.52f, h)
                lineTo(0f, h)
                lineTo(w * 0.42f, h / 2f)
                close()
            } else {
                moveTo(w, 0f)
                lineTo(w * 0.48f, 0f)
                lineTo(0f, h / 2f)
                lineTo(w * 0.48f, h)
                lineTo(w, h)
                lineTo(w * 0.58f, h / 2f)
                close()
            }
        }
        drawPath(path, SuzukiYellow)
    }
}

private fun formatMoving(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun clockNow(): String = DateTimeFormatter.ofPattern("hh:mm a").format(LocalTime.now())
