package dev.gdk.bumblebee.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.max

/**
 * The photos a rider added to a ride's video. Each picked photo is copied into the app, scaled
 * down and turned upright, together with what it says about itself (when and where it was taken),
 * so renders don't depend on the picker's short-lived access. Blocking; use off the main thread.
 */
class VideoPhotoStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "video-photos")

    private fun dir(rideId: Long) = File(root, rideId.toString())
    private fun index(rideId: Long) = File(dir(rideId), "photos.json")
    fun file(rideId: Long, id: String) = File(dir(rideId), "$id.jpg")

    fun list(rideId: Long): List<RidePhoto> = runCatching {
        val arr = JSONArray(index(rideId).readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RidePhoto(
                id = o.getString("id"),
                takenTs = o.optLong("ts", 0L).takeIf { o.has("ts") },
                lat = o.optDouble("lat").takeIf { o.has("lat") && !it.isNaN() },
                lng = o.optDouble("lng").takeIf { o.has("lng") && !it.isNaN() },
                manualM = o.optDouble("manual").takeIf { o.has("manual") && !it.isNaN() },
                caption = o.optString("caption").takeIf { o.has("caption") && it.isNotBlank() },
            )
        }.filter { file(rideId, it.id).exists() }
    }.getOrDefault(emptyList())

    private fun save(rideId: Long, photos: List<RidePhoto>) {
        dir(rideId).mkdirs()
        val arr = JSONArray()
        photos.forEach { p ->
            arr.put(
                JSONObject().put("id", p.id).apply {
                    p.takenTs?.let { put("ts", it) }
                    p.lat?.let { put("lat", it) }
                    p.lng?.let { put("lng", it) }
                    p.manualM?.let { put("manual", it) }
                    p.caption?.let { put("caption", it) }
                },
            )
        }
        index(rideId).writeText(arr.toString())
    }

    /** Copies picked photos in. Returns how many couldn't be read. */
    fun add(rideId: Long, uris: List<Uri>, zone: ZoneId = ZoneId.systemDefault()): Int {
        val current = list(rideId).toMutableList()
        var failed = 0
        for (uri in uris) {
            if (current.size >= MAX_PHOTOS) break
            val photo = runCatching { import(rideId, uri, zone) }.getOrNull()
            if (photo == null) failed++ else current += photo
        }
        save(rideId, current)
        return failed
    }

    fun remove(rideId: Long, id: String) {
        file(rideId, id).delete()
        save(rideId, list(rideId).filter { it.id != id })
    }

    /** Sets where on the ride a photo goes, in metres from the start; null goes back to automatic. */
    fun setManual(rideId: Long, id: String, metres: Double?) =
        save(rideId, list(rideId).map { if (it.id == id) it.copy(manualM = metres) else it })

    fun setCaption(rideId: Long, id: String, caption: String?) =
        save(rideId, list(rideId).map { if (it.id == id) it.copy(caption = caption?.trim()?.take(40)?.ifBlank { null }) else it })

    /** The stored photo, decoded at most [maxSide] pixels on its longer side. */
    fun bitmap(rideId: Long, id: String, maxSide: Int = 1600): Bitmap? = decodeScaled(file(rideId, id).path, maxSide)

    private fun import(rideId: Long, uri: Uri, zone: ZoneId): RidePhoto {
        val resolver = app.contentResolver
        val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }
        val latLong = FloatArray(2)
        val hasPlace = exif?.getLatLong(latLong) == true && !(latLong[0] == 0f && latLong[1] == 0f)
        val ts = exif?.let { exifTime(it, zone) } ?: dateTaken(uri)
        val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        // Decode small enough to keep, then turn upright.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= KEEP_SIDE) sample *= 2
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("Couldn't read the photo")
        val upright = if (rotation == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation) }, true)
        val id = UUID.randomUUID().toString().take(12)
        dir(rideId).mkdirs()
        file(rideId, id).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (upright !== raw) upright.recycle()
        raw.recycle()
        return RidePhoto(id, ts, if (hasPlace) latLong[0].toDouble() else null, if (hasPlace) latLong[1].toDouble() else null)
    }

    /** When the photo was taken, from EXIF; the camera's own offset if recorded, else [zone]. */
    private fun exifTime(exif: ExifInterface, zone: ZoneId): Long? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME) ?: return null
        val local = runCatching { LocalDateTime.parse(raw.trim(), EXIF_TIME) }.getOrNull() ?: return null
        val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
        val withOffset = offset?.let { o -> runCatching { local.atOffset(ZoneOffset.of(o.trim())).toInstant().toEpochMilli() }.getOrNull() }
        return withOffset ?: local.atZone(zone).toInstant().toEpochMilli()
    }

    /** The gallery's own "date taken", for photos whose files carry no EXIF time. */
    private fun dateTaken(uri: Uri): Long? = runCatching {
        app.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
        }
    }.getOrNull()

    companion object {
        const val MAX_PHOTOS = 12
        private const val KEEP_SIDE = 1600
        private val EXIF_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        fun decodeScaled(path: String, maxSide: Int): Bitmap? {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, b)
            if (b.outWidth <= 0) return null
            var sample = 1
            while (max(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
