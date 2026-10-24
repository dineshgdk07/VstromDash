package dev.gdk.bumblebee.ui.sharecard

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import dev.gdk.bumblebee.R

data class CardFonts(
    val body: Typeface,
    val display: Typeface,
    val script: Typeface,
    val serif: Typeface,
    val mono: Typeface,
)

object ShareText {
    /** Shrinks [paint] down to [minSize], then ellipsizes. Mutates `paint.textSize`. */
    fun fit(paint: TextPaint, text: String, maxWidth: Float, minSize: Float): String {
        var size = paint.textSize
        while (size > minSize && paint.measureAt(size, text) > maxWidth) {
            size = (size - 1f).coerceAtLeast(minSize)
        }
        paint.textSize = size.coerceAtLeast(minSize)
        if (paint.measureText(text) <= maxWidth) return text
        paint.textSize = minSize
        val ellipsis = "…"
        var end = text.length
        while (end > 0) {
            val candidate = text.substring(0, end).trimEnd() + ellipsis
            if (paint.measureText(candidate) <= maxWidth) return candidate
            end--
        }
        return ellipsis
    }

    fun fonts(context: Context): CardFonts = CardFonts(
        body = face(context, R.font.rajdhani_semibold, Typeface.DEFAULT_BOLD),
        display = face(context, R.font.anton_regular, Typeface.DEFAULT_BOLD),
        script = face(context, R.font.permanent_marker_regular, Typeface.DEFAULT),
        serif = face(context, R.font.fraunces_extrabold, Typeface.SERIF),
        mono = face(context, R.font.jetbrains_mono_medium, Typeface.MONOSPACE),
    )

    private fun face(context: Context, res: Int, fallback: Typeface): Typeface =
        ResourcesCompat.getFont(context, res) ?: fallback

    private fun TextPaint.measureAt(size: Float, text: String): Float {
        textSize = size
        return measureText(text)
    }
}
