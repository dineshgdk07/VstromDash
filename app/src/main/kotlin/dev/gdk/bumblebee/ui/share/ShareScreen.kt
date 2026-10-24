package dev.gdk.bumblebee.ui.share

import android.app.Application
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.map.OsmTileStore
import dev.gdk.bumblebee.ride.ShareAccent
import dev.gdk.bumblebee.ride.ShareBackground
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.ShareLook
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareMetrics
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ride.TileId
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.sharecard.ShareCardGallery
import dev.gdk.bumblebee.ui.sharecard.ShareCardMapTransform
import dev.gdk.bumblebee.ui.sharecard.ShareCardPainter
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapeXs
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun ShareScreen(subject: ShareSubject, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: ShareViewModel = viewModel(
        key = subject.toString(),
        factory = ShareViewModel.Factory(app, subject),
    )
    val ui by vm.ui.collectAsState()
    val card = ui.previews[ui.switches.look]
    val scope = rememberCoroutineScope()
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var statHint by remember { mutableStateOf(false) }
    var sawPlaces by remember { mutableStateOf(false) }
    var sawHighlight by remember { mutableStateOf(false) }
    var sawWeather by remember { mutableStateOf(false) }
    if (card?.fromPlace != null && card.toPlace != null) sawPlaces = true
    if (card?.highlight != null) sawHighlight = true
    if (card?.weatherLine != null) sawWeather = true

    val tiles by produceState(TileLoad(), card?.needsTiles, card?.route, card?.shape) {
        val model = card
        if (model == null || !model.needsTiles) {
            value = TileLoad(ready = true, tiles = emptyMap())
        } else {
            value = TileLoad(ready = false, tiles = emptyMap())
            val cover = ShareCardMapTransform.posterCover(model.route, model.shape != ShareShape.SQUARE)
            val loaded = if (cover == null) {
                emptyMap()
            } else {
                withContext(Dispatchers.IO) { OsmTileStore(context).load(cover) }
            }
            value = TileLoad(ready = true, tiles = loaded)
        }
    }
    val mapNotice = card != null && card.needsTiles && tiles.ready && !tilesComplete(card, tiles.tiles)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val decoded = ShareCardPainter.decodePhoto(context, uri, 1080, 1920)
        if (decoded == null) {
            vm.setPhoto(null)
            photoError = "Couldn't use that photo"
            vm.update { it.copy(background = ShareBackground.OWN) }
        } else {
            vm.setPhoto(decoded)
            photoError = null
            vm.update { it.copy(background = ShareBackground.PHOTO) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Canvas).padding(top = 12.dp),
        ) {
            Header(
                title = if (ui.selected == null) {
                    if (subject is ShareSubject.Day) "Share day" else "Share ride"
                } else {
                    ui.selected!!.label()
                },
                showBack = ui.selected != null,
                onBack = vm::back,
                onClose = onDismiss,
            )
            if (ui.selected == null || card == null) {
                PickStep(
                    modifier = Modifier.weight(1f),
                    ui = ui,
                    tiles = tiles.tiles,
                    nameLabel = if (subject is ShareSubject.Day) "Name this day" else "Ride name",
                    onRename = vm::rename,
                    onCommit = vm::commitName,
                    onPick = vm::pick,
                    onShape = { shape -> vm.update { it.copy(shape = shape) } },
                )
            } else {
                CustomiseStep(
                    modifier = Modifier.weight(1f),
                    ui = ui,
                    card = card,
                    tiles = tiles.tiles,
                    mapNotice = mapNotice,
                    photoError = photoError,
                    saveMessage = saveMessage,
                    statHint = statHint,
                    showPlaces = sawPlaces,
                    showHighlight = sawHighlight,
                    showWeather = sawWeather,
                    onBackground = { background ->
                        if (background == ShareBackground.PHOTO && ui.photo == null) {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            vm.update { it.copy(background = background) }
                        }
                    },
                    onPickPhoto = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClearPhoto = {
                        vm.setPhoto(null)
                        vm.update { it.copy(background = ShareBackground.OWN) }
                    },
                    onAccent = { accent -> vm.update { it.copy(accent = accent) } },
                    onStat = { kind ->
                        val shown = card.stats.map { it.kind }
                        val current = ui.switches.stats ?: shown
                        if (kind !in current && current.size >= ShareMetrics.MAX_STATS) {
                            statHint = true
                        } else {
                            statHint = false
                            val next = if (kind in current) current.filter { it != kind } else current + kind
                            vm.update { it.copy(stats = next) }
                        }
                    },
                    onPlaces = { vm.update { it.copy(showPlaces = !it.showPlaces) } },
                    onHighlight = { vm.update { it.copy(showHighlight = !it.showHighlight) } },
                    onWeather = { vm.update { it.copy(showWeather = !it.showWeather) } },
                    onSave = {
                        scope.launch {
                            val bmp = render(context, card, ui.photo, tiles.tiles, sticker = false)
                            val name = when (subject) {
                                is ShareSubject.Ride -> "bumblebee-ride-${subject.rideId}.png"
                                is ShareSubject.Day -> "bumblebee-day-${subject.date}.png"
                            }
                            val ok = withContext(Dispatchers.IO) { ShareCardGallery.savePng(context, bmp, name) }
                            saveMessage = if (ok.isSuccess) "Saved to Gallery" else "Could not save image"
                        }
                    },
                    onSticker = {
                        vm.commitName()
                        scope.launch {
                            val bmp = render(context, card, ui.photo, tiles.tiles, sticker = true)
                            val file = withContext(Dispatchers.IO) { ShareCardPainter.writePng(context, bmp, fileId(subject)) }
                            shareSticker(context, file)
                        }
                    },
                    onShare = {
                        vm.commitName()
                        scope.launch {
                            val bmp = render(context, card, ui.photo, tiles.tiles, sticker = false)
                            val file = withContext(Dispatchers.IO) { ShareCardPainter.writePng(context, bmp, fileId(subject)) }
                            shareCardImage(context, file, card.sentence)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, showBack: Boolean, onBack: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            ChunkyIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        ChunkyIconButton(
            icon = Icons.Rounded.Close,
            onClick = onClose,
            contentDescription = "Close",
        )
    }
}

@Composable
private fun PickStep(
    modifier: Modifier,
    ui: ShareUi,
    tiles: Map<TileId, Bitmap>,
    nameLabel: String,
    onRename: (String) -> Unit,
    onCommit: () -> Unit,
    onPick: (ShareLook) -> Unit,
    onShape: (ShareShape) -> Unit,
) {
    val context = LocalContext.current
    val thumbs by produceState<Map<ShareLook, Bitmap>>(emptyMap(), ui.previews, ui.photo, tiles) {
        value = withContext(Dispatchers.Default) {
            ui.previews.mapValues { (_, model) ->
                val full = render(context, model, ui.photo, tiles, sticker = false)
                // Half size: a grid cell on a phone is about 500 px wide, so a quarter-size thumb looks soft.
                val w = max(1, full.width / 2)
                val h = max(1, full.height / 2)
                val scaled = Bitmap.createScaledBitmap(full, w, h, true)
                if (scaled != full) full.recycle()
                scaled
            }
        }
    }
    var focused by remember { mutableStateOf(false) }
    val placeholder = ui.previews[ui.switches.look]?.headline.orEmpty()
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ui.canName) {
            OutlinedTextField(
                value = ui.name,
                onValueChange = { onRename(it.take(30)) },
                label = { Text(nameLabel) },
                placeholder = { Text(placeholder) },
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    if (focused && !it.isFocused) onCommit()
                    focused = it.isFocused
                },
                singleLine = true,
                shape = ShapeMd,
                colors = fieldColors(),
            )
        }
        val looks = ShareLook.entries
        looks.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (look in row) {
                    Thumb(look, thumbs[look], ui.switches.shape, Modifier.weight(1f)) { onPick(look) }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        SectionLabel(text = "Size")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip("Story", ui.switches.shape == ShareShape.STORY, onClick = { onShape(ShareShape.STORY) })
            PillChip("4:5", ui.switches.shape == ShareShape.FEED, onClick = { onShape(ShareShape.FEED) })
            PillChip("Square", ui.switches.shape == ShareShape.SQUARE, onClick = { onShape(ShareShape.SQUARE) })
        }
    }
}

@Composable
private fun Thumb(look: ShareLook, bitmap: Bitmap?, shape: ShareShape, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(shape.ratio())
                .clip(ShapeLg)
                .background(Surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap == null) {
                Text("Drawing…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = look.label(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Text(look.label(), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomiseStep(
    modifier: Modifier,
    ui: ShareUi,
    card: ShareCardModel,
    tiles: Map<TileId, Bitmap>,
    mapNotice: Boolean,
    photoError: String?,
    saveMessage: String?,
    statHint: Boolean,
    showPlaces: Boolean,
    showHighlight: Boolean,
    showWeather: Boolean,
    onBackground: (ShareBackground) -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onAccent: (ShareAccent) -> Unit,
    onStat: (ShareMetricKind) -> Unit,
    onPlaces: () -> Unit,
    onHighlight: () -> Unit,
    onWeather: () -> Unit,
    onSave: () -> Unit,
    onSticker: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val rendered by produceState<Bitmap?>(null, card, ui.photo, tiles) {
        value = withContext(Dispatchers.Default) {
            render(context, card, ui.photo, tiles, sticker = false)
        }
    }
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = rendered
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(card.shape.ratio())
                    .heightIn(max = 520.dp)
                    .clip(ShapeLg)
                    .background(Surface),
                contentAlignment = Alignment.Center,
            ) {
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
            if (mapNotice) {
                Text(
                    "Map unavailable offline, showing route only",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Warning,
                )
            }
            SectionLabel(text = "Background")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip(card.look.label(), ui.switches.background == ShareBackground.OWN, onClick = {
                    onBackground(ShareBackground.OWN)
                })
                PillChip("Map", ui.switches.background == ShareBackground.MAP, onClick = { onBackground(ShareBackground.MAP) })
                PillChip("Photo", ui.switches.background == ShareBackground.PHOTO, onClick = { onBackground(ShareBackground.PHOTO) })
                PillChip("Transparent", ui.switches.background == ShareBackground.TRANSPARENT, onClick = {
                    onBackground(ShareBackground.TRANSPARENT)
                })
            }
            if (ui.switches.background == ShareBackground.PHOTO) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.photo?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Selected photo",
                            modifier = Modifier.size(40.dp).clip(ShapeXs),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    ChunkyButton(
                        text = if (ui.photo == null) "Choose photo" else "Change photo",
                        onClick = onPickPhoto,
                        style = ChunkyStyle.Outline,
                        compact = true,
                    )
                }
                if (ui.photo != null) {
                    ChunkyButton(text = "Remove photo", onClick = onClearPhoto, style = ChunkyStyle.Neutral, compact = true)
                }
            }
            photoError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Warning) }
            SectionLabel(text = "Accent")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShareAccent.entries.forEach { accent ->
                    val on = ui.switches.accent == accent
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(accent.argb))
                            .border(if (on) 2.dp else 1.dp, if (on) TextPrimary else Stroke, CircleShape)
                            .clickable { onAccent(accent) },
                    )
                }
            }
            SectionLabel(text = "Stats")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val selected = ui.switches.stats ?: card.stats.map { it.kind }
                card.available.forEach { metric ->
                    val index = selected.indexOf(metric.kind)
                    val on = index >= 0
                    val caption = if (on) "${index + 1}  ${metric.title}" else metric.title
                    PillChip(caption, on, onClick = { onStat(metric.kind) })
                }
            }
            if (statHint) {
                Text("Up to 3", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (showPlaces || showHighlight || showWeather) {
                SectionLabel(text = "Details")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showPlaces) PillChip("Places", ui.switches.showPlaces, onClick = onPlaces)
                    if (showHighlight) PillChip("Highlight", ui.switches.showHighlight, onClick = onHighlight)
                    if (showWeather) PillChip("Weather", ui.switches.showWeather, onClick = onWeather)
                }
            }
            saveMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        }
        Column(
            Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChunkyButton(text = "Save", onClick = onSave, style = ChunkyStyle.Outline, compact = true)
            ChunkyButton(text = "Sticker", onClick = onSticker, style = ChunkyStyle.Outline, compact = true)
            ChunkyButton(text = "Share", onClick = onShare, style = ChunkyStyle.Lime, compact = true)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = Lime,
    unfocusedBorderColor = Stroke,
    focusedLabelColor = Lime,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Lime,
    focusedPlaceholderColor = TextDisabled,
    unfocusedPlaceholderColor = TextDisabled,
)

private fun render(
    context: Context,
    model: ShareCardModel,
    photo: Bitmap?,
    tiles: Map<TileId, Bitmap>,
    sticker: Boolean,
): Bitmap = ShareCardPainter.paint(
    context,
    model,
    photo.takeIf { model.background == ShareBackground.PHOTO },
    tiles,
    sticker,
)

private fun tilesComplete(model: ShareCardModel, tiles: Map<TileId, Bitmap>): Boolean {
    val cover = ShareCardMapTransform.posterCover(model.route, model.shape != ShareShape.SQUARE) ?: return false
    return cover.tiles().all { tiles.containsKey(it) }
}

private fun fileId(subject: ShareSubject): Long = when (subject) {
    is ShareSubject.Ride -> subject.rideId
    is ShareSubject.Day -> subject.date.toEpochDay()
}

private fun shareSticker(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val installed = StickerShare.instagramInstalled(context)
    if (installed) {
        context.grantUriPermission(StickerShare.IG_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(StickerShare.intent(context, uri, installed))
}

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

private fun ShareLook.label(): String = when (this) {
    ShareLook.NIGHT_GLOW -> "Night Glow"
    ShareLook.POSTER -> "Poster"
    ShareLook.COCKPIT -> "Cockpit"
    ShareLook.RIDE_PASS -> "Ride Pass"
}

private fun ShareShape.ratio(): Float = when (this) {
    ShareShape.STORY -> 9f / 16f
    ShareShape.FEED -> 4f / 5f
    ShareShape.SQUARE -> 1f
}

private data class TileLoad(
    val ready: Boolean = false,
    val tiles: Map<TileId, Bitmap> = emptyMap(),
)
