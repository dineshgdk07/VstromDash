package dev.gdk.bumblebee.ui.ride

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import dev.gdk.bumblebee.map.OsmTileStore
import dev.gdk.bumblebee.ride.RideInsightInput
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCard
import dev.gdk.bumblebee.ride.ShareExtras
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareMetrics
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ride.ShareSwitches
import dev.gdk.bumblebee.ride.TileId
import dev.gdk.bumblebee.ride.TrackInsights
import dev.gdk.bumblebee.ui.sharecard.ShareCardGallery
import dev.gdk.bumblebee.ui.sharecard.ShareCardMapTransform
import dev.gdk.bumblebee.ui.sharecard.ShareCardPainter
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapeXs
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ShareCardButton(detail: RideDetail) {
    var open by remember(detail.row.id) { mutableStateOf(false) }
    ChunkyButton(text = "Share card", onClick = { open = true }, style = ChunkyStyle.Outline)
    if (open) {
        ShareCardDialog(detail, onDismiss = { open = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareCardDialog(detail: RideDetail, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val input = remember(detail.row.id, detail.row.km, detail.row.movingSec) { detail.toInsight() }
    val climb = remember(detail.track) { TrackInsights.elevationGainM(detail.track) }
    val tank = detail.tankKmPerL?.takeIf { detail.litresFromFills }
    val available = remember(input, climb, tank) { ShareMetrics.of(input, climb, tank) }
    var switches by remember(detail.row.id) {
        mutableStateOf(
            ShareSwitches(
                shape = ShareShape.STORY,
                background = ShareBackground.OWN,
                bigNumber = ShareMetrics.defaultHero(available)?.kind,
                stats = ShareMetrics.defaultStats(available).map { it.kind },
            ),
        )
    }
    var photo by remember(detail.row.id) { mutableStateOf<Bitmap?>(null) }
    var photoError by remember(detail.row.id) { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val model = remember(switches, input, detail.track, climb, tank, detail.demo, detail.row.name) {
        ShareCard.of(
            ride = input,
            track = detail.track,
            switches = switches,
            climbM = climb,
            tankKmPerL = tank,
            extras = ShareExtras(rideName = detail.row.name),
            demo = detail.demo,
        )
    }
    val tiles by produceState(initialValue = emptyMap<TileId, Bitmap>(), model.needsTiles, model.route, model.shape) {
        value = if (!model.needsTiles) {
            emptyMap()
        } else {
            val cover = ShareCardMapTransform.posterCover(model.route, model.shape != ShareShape.SQUARE)
            if (cover == null) emptyMap()
            else withContext(Dispatchers.IO) { OsmTileStore(context).load(cover) }
        }
    }
    val rendered by produceState<Bitmap?>(initialValue = null, model, photo, tiles) {
        value = withContext(Dispatchers.Default) {
            ShareCardPainter.paint(
                context,
                model,
                photo.takeIf { model.background == ShareBackground.PHOTO },
                tiles,
                sticker = false,
            )
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val decoded = ShareCardPainter.decodePhoto(context, uri, 1080, 1920)
        if (decoded == null) {
            photo = null
            photoError = "Couldn't use that photo"
            switches = switches.copy(background = ShareBackground.OWN)
        } else {
            photo = decoded
            photoError = null
            switches = switches.copy(background = ShareBackground.PHOTO)
        }
    }
    val map = switches.background == ShareBackground.MAP
    val ratio = if (switches.shape == ShareShape.STORY) 9f / 16f else 1f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Canvas)
                .padding(top = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Share ride",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                ChunkyIconButton(
                    icon = Icons.Rounded.Close,
                    onClick = onDismiss,
                    contentDescription = "Close",
                )
            }
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                val byWidth = maxWidth
                val hFromW = byWidth / ratio
                val useWidth = hFromW <= maxHeight
                val bmp = rendered
                val preview = if (useWidth) {
                    Modifier.fillMaxWidth().aspectRatio(ratio)
                } else {
                    Modifier.fillMaxHeight().aspectRatio(ratio)
                }
                Box(preview.clip(ShapeLg).background(Surface), contentAlignment = Alignment.Center) {
                    if (bmp == null) {
                        Text("Drawing…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    } else {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Ride card preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel(text = "Customize")
                Label("SHAPE")
                Segment {
                    Seg("Story", switches.shape == ShareShape.STORY) { switches = switches.copy(shape = ShareShape.STORY) }
                    Seg("Square", switches.shape == ShareShape.SQUARE) { switches = switches.copy(shape = ShareShape.SQUARE) }
                }
                Label("BACKGROUND")
                Segment {
                    Seg("Own", switches.background == ShareBackground.OWN) {
                        switches = switches.copy(background = ShareBackground.OWN)
                    }
                    Seg("Map", switches.background == ShareBackground.MAP) {
                        switches = switches.copy(background = ShareBackground.MAP)
                    }
                    Seg("Photo", switches.background == ShareBackground.PHOTO) {
                        if (photo == null) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            switches = switches.copy(background = ShareBackground.PHOTO)
                        }
                    }
                    Seg("Transparent", switches.background == ShareBackground.TRANSPARENT) {
                        switches = switches.copy(background = ShareBackground.TRANSPARENT)
                    }
                }
                if (!map) {
                    if (switches.background == ShareBackground.PHOTO) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            photo?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Selected photo",
                                    modifier = Modifier.size(40.dp).clip(ShapeXs),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            ChunkyButton(
                                text = if (photo == null) "Choose photo" else "Change photo",
                                onClick = {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                style = ChunkyStyle.Outline,
                                compact = true,
                            )
                            if (photo != null) {
                                ChunkyButton(
                                    text = "Remove photo",
                                    onClick = {
                                        photo = null
                                        switches = switches.copy(background = ShareBackground.OWN)
                                    },
                                    style = ChunkyStyle.Neutral,
                                    compact = true,
                                )
                            }
                        }
                    }
                    photoError?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = Warning)
                    }
                }
                Label("BIG NUMBER")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Seg("None", switches.bigNumber == null) { switches = switches.copy(bigNumber = null) }
                    available.forEach { metric ->
                        Seg(metric.detail, switches.bigNumber == metric.kind) {
                            switches = switches.copy(bigNumber = metric.kind)
                        }
                    }
                }
                Label("STATS")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val selected = switches.stats.orEmpty()
                    available.forEach { metric ->
                        val index = selected.indexOf(metric.kind)
                        val on = index >= 0
                        val caption = if (on) "${index + 1}  ${metric.title}" else metric.title
                        Seg(caption, on) {
                            switches = switches.copy(stats = toggleStat(selected, metric.kind))
                        }
                    }
                }
                saveMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Spacer(Modifier.height(4.dp))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChunkyButton(
                    text = "Save to gallery",
                    onClick = {
                        val bmp = rendered
                        if (bmp == null) {
                            saveMessage = "Could not save image"
                        } else {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    ShareCardGallery.savePng(context, bmp, "bumblebee-ride-${detail.row.id}.png")
                                }
                                saveMessage = if (ok.isSuccess) "Saved to Gallery" else "Could not save image"
                            }
                        }
                    },
                    style = ChunkyStyle.Outline,
                )
                ChunkyButton(
                    text = "Share",
                    onClick = {
                        val bmp = rendered
                        if (bmp == null) {
                            shareRide(context, detail.shareText, null, detail.row.id)
                        } else {
                            val file = ShareCardPainter.writePng(context, bmp, detail.row.id)
                            shareCardImage(context, file, model.sentence)
                        }
                    },
                    style = ChunkyStyle.Lime,
                )
            }
        }
    }
}

private fun toggleStat(current: List<ShareMetricKind>, kind: ShareMetricKind): List<ShareMetricKind> {
    if (kind in current) return current.filter { it != kind }
    if (current.size >= ShareMetrics.MAX_STATS) return current
    return current + kind
}

@Composable
private fun Label(text: String) {
    SectionLabel(text = text)
}

@Composable
private fun Segment(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Seg(label: String, on: Boolean, onClick: () -> Unit) {
    PillChip(text = label, selected = on, onClick = onClick)
}

private fun RideDetail.toInsight() = RideInsightInput(
    startTs = row.startTs,
    endTs = row.endTs ?: row.startTs,
    km = row.km,
    movingSec = row.movingSec,
    idleSec = row.idleSec,
    maxSpeedKmh = row.maxSpeedKmh,
    avgMovingSpeedKmh = row.avgMovingSpeedKmh,
)

private fun shareCardImage(context: Context, file: File, sentence: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee ride")
        putExtra(Intent.EXTRA_TEXT, sentence)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("ride-card.png", uri)
    }
    context.startActivity(Intent.createChooser(send, "Share ride"))
}
