package dev.gdk.bumblebee.video

import kotlin.math.cos

/**
 * Flat metres around a centre point: x east, y north. Good to well under 0.5% across a few hundred
 * kilometres, which is far below what a video frame can show.
 */
class LocalFrame(val lat0: Double, val lng0: Double) {
    private val mPerDegLat = 111_132.0
    private val mPerDegLng = 111_320.0 * cos(Math.toRadians(lat0))

    fun x(lng: Double) = (lng - lng0) * mPerDegLng
    fun y(lat: Double) = (lat - lat0) * mPerDegLat
    fun lng(x: Double) = lng0 + x / mPerDegLng
    fun lat(y: Double) = lat0 + y / mPerDegLat
}
