package dev.gdk.bumblebee.ui.hud.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.SuzukiYellow

object HudIcons {
    val Shield: ImageVector by lazy { stroke("hud_shield") {
        moveTo(12f, 3f); lineTo(5f, 6.2f); verticalLineTo(12.2f)
        curveTo(5f, 16.1f, 7.7f, 19.6f, 12f, 21f)
        curveTo(16.3f, 19.6f, 19f, 16.1f, 19f, 12.2f); verticalLineTo(6.2f); close()
        moveTo(9.2f, 12.1f); lineTo(11.1f, 14f); lineTo(15.2f, 9.6f)
    } }
    val Fuel: ImageVector by lazy { stroke("hud_fuel") {
        moveTo(7f, 20f); verticalLineTo(8f); curveTo(7f, 6.9f, 7.9f, 6f, 9f, 6f)
        horizontalLineTo(13f); curveTo(14.1f, 6f, 15f, 6.9f, 15f, 8f); verticalLineTo(20f); close()
        moveTo(15f, 10f); lineTo(18f, 12f); verticalLineTo(17f)
        curveTo(18f, 18.1f, 18.9f, 19f, 20f, 19f)
        moveTo(9f, 10f); horizontalLineTo(13f)
    } }
    val Road: ImageVector by lazy { stroke("hud_road") {
        moveTo(4f, 20f); lineTo(8f, 4f); lineTo(16f, 4f); lineTo(20f, 20f)
        moveTo(12f, 7f); verticalLineTo(9.2f); moveTo(12f, 11.4f); verticalLineTo(13.6f)
        moveTo(12f, 15.8f); verticalLineTo(18f)
    } }
    val Weather: ImageVector by lazy { stroke("hud_weather") {
        moveTo(7.5f, 16f); curveTo(5.6f, 16f, 4f, 14.4f, 4f, 12.5f)
        curveTo(4f, 10.8f, 5.2f, 9.4f, 6.8f, 9.1f)
        curveTo(7.3f, 7.3f, 9f, 6f, 11f, 6f)
        curveTo(13.4f, 6f, 15.4f, 7.8f, 15.9f, 10.1f)
        curveTo(17.7f, 10.4f, 19f, 12f, 19f, 13.9f)
        curveTo(19f, 16f, 17.3f, 17.7f, 15.2f, 17.7f); horizontalLineTo(7.5f)
    } }
    val Heart: ImageVector by lazy { stroke("hud_heart") {
        moveTo(3f, 12f); lineTo(7f, 12f); lineTo(9f, 8f); lineTo(12f, 16f)
        lineTo(14.5f, 11f); lineTo(16.5f, 12f); lineTo(21f, 12f)
    } }
    val Wrench: ImageVector by lazy { stroke("hud_wrench") {
        moveTo(14.7f, 6.3f); curveTo(16.5f, 4.5f, 19.3f, 4.5f, 21.1f, 6.3f)
        lineTo(17.5f, 9.9f); lineTo(14.1f, 6.5f)
        moveTo(9.9f, 17.5f); lineTo(3.5f, 23.9f); lineTo(2.1f, 22.5f); lineTo(8.5f, 16.1f)
        curveTo(6.7f, 14.3f, 6.7f, 11.5f, 8.5f, 9.7f)
    } }
    val Warning: ImageVector by lazy { stroke("hud_warning") {
        moveTo(12f, 4f); lineTo(22f, 20f); lineTo(2f, 20f); close()
        moveTo(12f, 10f); verticalLineTo(14.5f); moveTo(12f, 17.2f); verticalLineTo(17.8f)
    } }
    val Nav: ImageVector by lazy { stroke("hud_nav") {
        moveTo(12f, 3f); lineTo(20f, 21f); lineTo(12f, 17f); lineTo(4f, 21f); close()
    } }
    val Locate: ImageVector by lazy { stroke("hud_locate") {
        circle(12f, 12f, 4f)
        moveTo(12f, 3f); verticalLineTo(6f)
        moveTo(12f, 18f); verticalLineTo(21f)
        moveTo(3f, 12f); horizontalLineTo(6f)
        moveTo(18f, 12f); horizontalLineTo(21f)
    } }
    val Share: ImageVector by lazy { stroke("hud_share") {
        circle(18f, 8f, 2.4f)
        circle(6f, 12f, 2.4f)
        circle(18f, 16f, 2.4f)
        moveTo(8.4f, 13.2f); lineTo(15.6f, 16.6f)
        moveTo(8.4f, 10.8f); lineTo(15.6f, 7.4f)
    } }
    val Mountains: ImageVector by lazy { stroke("hud_explore") {
        moveTo(3f, 18f); lineTo(9f, 8f); lineTo(13f, 14f); lineTo(16f, 10f); lineTo(21f, 18f); close()
        moveTo(14.2f, 6.2f); lineTo(16.6f, 4f); lineTo(19f, 6.2f)
    } }
    val Bluetooth: ImageVector by lazy { stroke("hud_bt") {
        moveTo(7f, 17f); lineTo(17f, 7f); lineTo(12f, 3f); verticalLineTo(21f); lineTo(17f, 17f); lineTo(7f, 7f)
    } }
    val Gps: ImageVector by lazy { stroke("hud_gps") {
        circle(12f, 12f, 7f)
        circle(12f, 12f, 2.4f)
        moveTo(12f, 3f); verticalLineTo(5.5f)
        moveTo(12f, 18.5f); verticalLineTo(21f)
        moveTo(3f, 12f); horizontalLineTo(5.5f)
        moveTo(18.5f, 12f); horizontalLineTo(21f)
    } }
    val Sun: ImageVector by lazy { stroke("hud_sun") {
        circle(12f, 12f, 3.4f)
        moveTo(12f, 3.5f); verticalLineTo(5.5f)
        moveTo(12f, 18.5f); verticalLineTo(20.5f)
        moveTo(3.5f, 12f); horizontalLineTo(5.5f)
        moveTo(18.5f, 12f); horizontalLineTo(20.5f)
        moveTo(6.1f, 6.1f); lineTo(7.5f, 7.5f)
        moveTo(16.5f, 16.5f); lineTo(17.9f, 17.9f)
        moveTo(17.9f, 6.1f); lineTo(16.5f, 7.5f)
        moveTo(7.5f, 16.5f); lineTo(6.1f, 17.9f)
    } }
    val Battery: ImageVector by lazy { stroke("hud_battery") {
        moveTo(4f, 8f); horizontalLineTo(17f); verticalLineTo(16f); horizontalLineTo(4f); close()
        moveTo(17f, 10.5f); horizontalLineTo(19.5f); verticalLineTo(13.5f); horizontalLineTo(17f)
    } }
    val Temp: ImageVector by lazy { stroke("hud_temp") {
        moveTo(10f, 14.2f); verticalLineTo(6.2f)
        curveTo(10f, 5.1f, 10.9f, 4.2f, 12f, 4.2f)
        curveTo(13.1f, 4.2f, 14f, 5.1f, 14f, 6.2f); verticalLineTo(14.2f)
        curveTo(15.1f, 14.8f, 15.8f, 16f, 15.8f, 17.4f)
        curveTo(15.8f, 19.5f, 14.1f, 21.2f, 12f, 21.2f)
        curveTo(9.9f, 21.2f, 8.2f, 19.5f, 8.2f, 17.4f)
        curveTo(8.2f, 16f, 8.9f, 14.8f, 10f, 14.2f); close()
    } }
    val Headlight: ImageVector by lazy { stroke("hud_headlight") {
        moveTo(4f, 5.5f); horizontalLineTo(9f)
        curveTo(13.4f, 5.5f, 16f, 8.4f, 16f, 12f)
        curveTo(16f, 15.6f, 13.4f, 18.5f, 9f, 18.5f)
        horizontalLineTo(4f); close()
        moveTo(18.5f, 8f); lineTo(22f, 6.4f)
        moveTo(18.5f, 12f); horizontalLineTo(22f)
        moveTo(18.5f, 16f); lineTo(22f, 17.6f)
    } }

    /** Stylised angular S in the spirit of the tank badge. */
    val SuzukiMark: ImageVector by lazy { filled("hud_suzuki") {
        moveTo(21f, 3.5f); lineTo(9.5f, 3.5f); lineTo(3f, 9f); lineTo(12.5f, 9f)
        lineTo(3f, 20.5f); lineTo(14.5f, 20.5f); lineTo(21f, 15f); lineTo(11.5f, 15f)
        close()
    } }
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
}

private fun filled(name: String, body: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(SuzukiYellow)) { body() }
    }.build()

private fun stroke(name: String, body: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = null,
            stroke = SolidColor(SuzukiYellow),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) { body() }
    }.build()
