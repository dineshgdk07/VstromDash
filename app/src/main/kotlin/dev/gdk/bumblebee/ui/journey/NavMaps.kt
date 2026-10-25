package dev.gdk.bumblebee.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import dev.gdk.bumblebee.journey.NavState
import dev.gdk.bumblebee.map.MapTiles
import dev.gdk.bumblebee.ride.MapCamera
import dev.gdk.bumblebee.ride.SlippyMap
import dev.gdk.bumblebee.ui.ride.replay.drawTiles
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.Scrim
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.TextPrimary
import com.google.android.gms.maps.model.LatLng as GLatLng

private const val FOLLOW_ZOOM = 16.5

@Composable
private fun RecenterChip(onClick: () -> Unit, modifier: Modifier) {
    Text(
        "Recenter",
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary,
        modifier = modifier
            .padding(10.dp)
            .clip(ShapePill)
            .background(OnBlock.copy(alpha = 0.8f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * The route on the app's own OpenStreetMap view: what's left in the accent, what's ridden dimmed,
 * following the rider until they pan away.
 */
@Composable
fun OsmRouteMap(state: NavState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val route = state.route ?: return
    val tilePx = with(LocalDensity.current) { 200.dp.toPx() }.toDouble()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var follow by remember { mutableStateOf(true) }
    var camera by remember { mutableStateOf<MapCamera?>(null) }
    val here = state.here ?: route.points.first().let { dev.gdk.bumblebee.nav.LatLng(it.lat, it.lng) }
    LaunchedEffect(here, follow) {
        if (follow) camera = MapCamera(SlippyMap.worldX(here.lng), SlippyMap.worldY(here.lat), camera?.zoom ?: FOLLOW_ZOOM)
    }
    LaunchedEffect(camera, size) {
        val cam = camera ?: return@LaunchedEffect
        if (size.width > 0) MapTiles.request(context, SlippyMap.visibleTiles(cam, size.width.toDouble(), size.height.toDouble(), tilePx))
    }
    val dim = Scrim.copy(alpha = 0.3f)
    Box(modifier.clipToBounds().onSizeChanged { size = it }) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(tilePx) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val cam = camera ?: return@detectTransformGestures
                    follow = false
                    var next = SlippyMap.zoomAbout(cam, zoom.toDouble(), centroid.x.toDouble(), centroid.y.toDouble(), size.width.toDouble(), size.height.toDouble(), tilePx)
                    next = SlippyMap.pan(next, pan.x.toDouble(), pan.y.toDouble(), tilePx)
                    camera = next
                }
            },
        ) {
            val cam = camera ?: return@Canvas
            MapTiles.version.intValue
            drawTiles(cam, tilePx)
            drawRect(dim)
            fun at(lat: Double, lng: Double): Offset {
                val (x, y) = SlippyMap.toScreen(cam, SlippyMap.worldX(lng), SlippyMap.worldY(lat), size.width.toDouble(), size.height.toDouble(), tilePx)
                return Offset(x.toFloat(), y.toFloat())
            }
            val split = (state.progress?.index ?: 0).coerceIn(0, route.points.lastIndex)
            fun path(from: Int, to: Int) = Path().apply {
                for (i in from..to) {
                    val p = at(route.points[i].lat, route.points[i].lng)
                    if (i == from) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
            }
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (split > 0) drawPath(path(0, split), Color.White.copy(alpha = 0.3f), style = stroke)
            drawPath(path(split, route.points.lastIndex), Lime, style = stroke)
            val end = route.points.last()
            drawCircle(Color.White, 7.dp.toPx(), at(end.lat, end.lng))
            drawCircle(Lime, 4.dp.toPx(), at(end.lat, end.lng))
            val me = at(here.lat, here.lng)
            drawCircle(Lime.copy(alpha = 0.25f), 18.dp.toPx(), me)
            drawCircle(Color.White, 9.dp.toPx(), me)
            drawCircle(Lime, 6.dp.toPx(), me)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9.dp.toPx()
                color = android.graphics.Color.argb(170, 255, 255, 255)
            }
            val credit = "© OpenStreetMap contributors · route © openrouteservice.org"
            drawContext.canvas.nativeCanvas.drawText(credit, this.size.width - paint.measureText(credit) - 6.dp.toPx(), this.size.height - 5.dp.toPx(), paint)
        }
        if (!follow) RecenterChip({ follow = true }, Modifier.align(Alignment.TopEnd))
    }
}

/** A quiet dark style for Google's map so the route and the accent carry the screen. */
private const val DARK_STYLE = """[
  {"elementType":"geometry","stylers":[{"color":"#1d1d1f"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#9a9a9a"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#111111"}]},
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2c2c30"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#38383d"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#4a4a50"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1a24"}]}
]"""

/**
 * The route on Google's own map, as Google's terms require for Routes results, restyled dark with
 * the app's accent. Only shown when the app was built with a MAPS_API_KEY.
 */
@Composable
fun GoogleRouteMap(state: NavState, modifier: Modifier = Modifier) {
    val route = state.route ?: return
    val start = state.here ?: route.points.first()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(GLatLng(start.lat, start.lng), FOLLOW_ZOOM.toFloat()) }
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) follow = false
    }
    LaunchedEffect(state.here, follow) {
        val here = state.here ?: return@LaunchedEffect
        if (follow) runCatching { camera.animate(CameraUpdateFactory.newLatLng(GLatLng(here.lat, here.lng)), 700) }
    }
    val split = (state.progress?.index ?: 0).coerceIn(0, route.points.lastIndex)
    val done = remember(route, split) { route.points.take(split + 1).map { GLatLng(it.lat, it.lng) } }
    val ahead = remember(route, split) { route.points.drop(split).map { GLatLng(it.lat, it.lng) } }
    Box(modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(mapStyleOptions = MapStyleOptions(DARK_STYLE)),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false, compassEnabled = false),
        ) {
            if (done.size >= 2) Polyline(points = done, color = Color.White.copy(alpha = 0.35f), width = 16f)
            if (ahead.size >= 2) Polyline(points = ahead, color = Lime, width = 16f)
            route.points.lastOrNull()?.let { Circle(center = GLatLng(it.lat, it.lng), radius = 12.0, fillColor = Lime, strokeColor = Color.White, strokeWidth = 4f) }
            state.here?.let { Circle(center = GLatLng(it.lat, it.lng), radius = 8.0, fillColor = Color.White, strokeColor = Lime, strokeWidth = 8f) }
        }
        if (!follow) RecenterChip({ follow = true }, Modifier.align(Alignment.TopEnd))
    }
}
