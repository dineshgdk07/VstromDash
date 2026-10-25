package dev.gdk.bumblebee.ui.garage

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.R
import dev.gdk.bumblebee.ble.ConnectionState
import dev.gdk.bumblebee.ride.BikeHealth
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.StatTone
import dev.gdk.bumblebee.ui.components.StatTriple
import dev.gdk.bumblebee.ui.components.StatTripleColumn
import dev.gdk.bumblebee.ui.components.motionAnimationsEnabled
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import java.time.LocalDate

internal fun healthColor(score: Int): Color = when {
    score >= 85 -> Lime
    score >= 65 -> Warning
    else -> Danger
}

/**
 * The top of the Garage: the bike with a live pulse while connected, its odometer and trips,
 * a fuel gauge drawn as the cluster's six bars with the range left, the health ring, and the
 * pre-ride check.
 */
@Composable
internal fun BikeHero(
    state: GarageUiState,
    onOpenHealth: () -> Unit,
    onOpenCheck: () -> Unit,
) {
    val ready = state.connection is ConnectionState.Ready
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(ShapeSm).background(OnBlock)) {
                Image(
                    painter = painterResource(R.drawable.vstrom_hero),
                    contentDescription = "V-Strom 250 SX",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("V-Strom 250 SX", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                LiveChip(state.connection)
            }
            state.extras?.health?.let { HealthRing(it, onOpenHealth) }
        }
        StatTriple(
            modifier = Modifier.padding(top = 16.dp),
            columns = listOf(
                StatTripleColumn(
                    (state.odometerKm ?: state.storedOdometerKm)?.let { "%,d".format(it) } ?: "—",
                    if (state.odometerKm == null && state.storedOdometerKm != null) "Last odo" else "Odo",
                    StatTone.Plain,
                ),
                StatTripleColumn(state.tripAKm?.let { "%.1f".format(it) } ?: "—", "Trip A", StatTone.Plain),
                StatTripleColumn(state.tripBKm?.let { "%.1f".format(it) } ?: "—", "Trip B", StatTone.Plain),
            ),
        )
        state.fuelBars?.let { bars ->
            FuelGauge(bars, state.rangeKm, live = ready, modifier = Modifier.padding(top = 14.dp))
        }
        CheckRow(state.extras, onOpenCheck, Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun LiveChip(connection: ConnectionState) {
    val ready = connection is ConnectionState.Ready
    val label = when (connection) {
        ConnectionState.Ready -> "Live"
        ConnectionState.Connecting, ConnectionState.Discovering -> "Connecting"
        else -> "Not connected"
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "glow",
    )
    val moving = ready && motionAnimationsEnabled()
    Row(
        Modifier
            .border(1.dp, if (ready) Lime else Stroke, ShapePill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (ready) {
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(if (moving) glow else 1f)
                    .background(Lime, ShapePill),
            )
        }
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (ready) Lime else TextSecondary)
    }
}

@Composable
private fun HealthRing(health: BikeHealth, onClick: () -> Unit) {
    val sweep by animateFloatAsState(health.score / 100f, tween(900), label = "health")
    val tone = healthColor(health.score)
    // Only the number sits inside the ring; the label goes underneath so "100" never crowds it.
    Column(
        Modifier
            .clip(ShapeMd)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 5.dp.toPx()
                val inset = w / 2
                val arc = Size(size.width - w, size.height - w)
                drawArc(Stroke, -90f, 360f, false, Offset(inset, inset), arc, style = DrawStroke(w))
                drawArc(tone, -90f, 360f * sweep, false, Offset(inset, inset), arc, style = DrawStroke(w, cap = StrokeCap.Round))
            }
            Text(
                "${health.score}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text("HEALTH", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
    }
}

@Composable
private fun FuelGauge(bars: Int, rangeKm: Double?, live: Boolean, modifier: Modifier = Modifier) {
    val filled = bars.coerceIn(0, 6)
    val tone = when {
        filled <= 1 -> Danger
        filled == 2 -> Warning
        else -> Lime
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("FUEL", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(
                rangeKm?.let { "≈ ${"%,.0f".format(it)} km left" } ?: "$filled of 6 bars",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
            )
        }
        val pulse = rememberInfiniteTransition(label = "fuel")
        val blink by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "blink")
        val lowBlink = live && filled <= 1 && motionAnimationsEnabled()
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val gap = 5.dp.toPx()
            val w = (size.width - gap * 5) / 6
            for (i in 0 until 6) {
                val on = i < filled
                val alpha = if (on && lowBlink) blink else 1f
                drawRoundRect(
                    color = if (on) tone.copy(alpha = alpha) else Stroke,
                    topLeft = Offset(i * (w + gap), 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun CheckRow(extras: GarageExtras?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val done = extras?.checkedToday == true
    val streak = extras?.checkStreak ?: 0
    Row(
        modifier
            .fillMaxWidth()
            .clip(ShapeMd)
            .background(SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) Lime else TextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(if (done) "Checked today" else "Pre-ride check", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                if (done) "Tyres, chain, lights, brakes, fluids, papers" else "30 seconds · six things",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        if (streak > 0) {
            Text(
                "Streak $streak",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
            )
        }
    }
}

private val CHECKS = listOf(
    "Tyres" to "Pressure feels right, no cuts or nails",
    "Chain" to "A thumb of slack, not dry or rusty",
    "Lights & horn" to "Head, tail, brake light, indicators, horn",
    "Brakes" to "Both levers firm, no squeal",
    "Fluids" to "No oil or coolant drips under the bike",
    "Papers & kit" to "Licence, RC on phone, helmet strap",
)

/** A tap-through pre-ride check. Finishing it saves today and grows the streak. */
@Composable
fun PreRideCheckSheet(onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val ticked = remember { mutableStateListOf<Int>() }
    val all = ticked.size == CHECKS.size
    ForgeSheet(
        title = "Pre-ride check",
        onDismiss = onDismiss,
        primaryLabel = if (all) "Ready to ride" else "${CHECKS.size - ticked.size} to go",
        primaryEnabled = all,
        onPrimary = {
            GaragePrefs(context).markChecked(LocalDate.now())
            onDone()
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CHECKS.forEachIndexed { i, (title, hint) ->
                val on = i in ticked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(ShapeMd)
                        .background(if (on) Lime.copy(alpha = 0.14f) else SurfaceRaised)
                        .clickable { if (on) ticked.remove(i) else ticked.add(i) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (on) Lime else TextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** What the health score is made of. */
@Composable
internal fun HealthSheet(health: BikeHealth, onDismiss: () -> Unit) {
    ForgeSheet(title = "Bike health", onDismiss = onDismiss, primaryLabel = "Close", onPrimary = onDismiss) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${health.score}", style = MaterialTheme.typography.displaySmall, color = healthColor(health.score))
            Text(
                "  ${health.label}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (health.reasons.isEmpty()) {
            Text(
                "Nothing is pulling it down. Care, papers, parts and fuel efficiency all look good.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                "Starts at 100. These take points off:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                health.reasons.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(ShapeMd).background(SurfaceRaised).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.text, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text(
                            "−${r.points}",
                            style = MaterialTheme.typography.labelLarge,
                            color = OnBlock,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(44.dp)
                                .background(healthColor(100 - r.points * 3), ShapePill)
                                .padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
