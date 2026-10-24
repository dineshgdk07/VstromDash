package dev.gdk.bumblebee.ui.garage.documents

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.garage.DocCard
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun CheckpostViewer(
    pages: List<DocCard>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val previous = window?.attributes?.screenBrightness
        val keptOn = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        window?.let {
            val attrs = it.attributes
            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            it.attributes = attrs
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                val attrs = it.attributes
                attrs.screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = attrs
                if (!keptOn) it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState { pages.size }
        Column(Modifier.fillMaxSize().background(Canvas)) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChunkyIconButton(
                    icon = Icons.Rounded.Close,
                    onClick = onClose,
                    contentDescription = "Close",
                )
                Text(
                    text = pages.getOrNull(pager.currentPage)?.title ?: "Checkpost",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                CheckpostPage(pages[index])
            }
        }
    }
}

@Composable
private fun CheckpostPage(doc: DocCard) {
    var pageIndex by remember(doc.id) { mutableIntStateOf(0) }
    var pageCount by remember(doc.id) { mutableIntStateOf(1) }
    var bitmap by remember(doc.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(doc.filePath, pageIndex) {
        val path = doc.filePath ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.IO) { renderPaper(path, doc.mime, pageIndex) }
        bitmap = rendered.first
        pageCount = rendered.second.coerceAtLeast(1)
    }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = doc.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            }
        }
        if (pageCount > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChunkyIconButton(
                    icon = Icons.Rounded.ChevronLeft,
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    contentDescription = "Previous page",
                    enabled = pageIndex > 0,
                )
                Text(
                    text = "${pageIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                ChunkyIconButton(
                    icon = Icons.Rounded.ChevronRight,
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                    contentDescription = "Next page",
                    enabled = pageIndex < pageCount - 1,
                )
            }
        }
    }
}

private fun renderPaper(path: String, mime: String?, pageIndex: Int): Pair<Bitmap?, Int> {
    val file = File(path)
    if (!file.exists()) return null to 1
    if (mime?.startsWith("image/") == true || looksLikeImage(path)) {
        return decodeThumb(path, maxEdge = 1600) to 1
    }
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val index = pageIndex.coerceIn(0, renderer.pageCount - 1)
                val page = renderer.openPage(index)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap to renderer.pageCount
            }
        }
    }.getOrDefault(null to 1)
}

internal fun decodeThumb(path: String, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

private fun looksLikeImage(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
