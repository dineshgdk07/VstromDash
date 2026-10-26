package dev.gdk.bumblebee.ui.share

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.gdk.bumblebee.ui.sharecard.PhotoFrame
import kotlin.math.roundToInt
import dev.gdk.bumblebee.ui.sharecard.ShareCardLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.gdk.bumblebee.ride.ShareLookRules
import dev.gdk.bumblebee.ride.ShareMetricKind
import dev.gdk.bumblebee.ride.ShareShape
import dev.gdk.bumblebee.ride.TileId
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.sharecard.ShareCardGallery
import dev.gdk.bumblebee.ui.sharecard.ShareCardPainter
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
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
import kotlinx.coroutines.delay
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
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableIntStateOf(0) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var statHint by remember { mutableStateOf(false) }
    var sawPlaces by remember { mutableStateOf(false) }
    var sawHighlight by remember { mutableStateOf(false) }
    var sawWeather by remember { mutableStateOf(false) }
    if (card?.fromPlace != null && card.toPlace != null) sawPlaces = true
    if (card?.highlight != null) sawHighlight = true
    if (card?.weatherLine != null) sawWeather = true

    // Each look fits the map to its own route stage, so the grid needs every look's tiles.
    val covers = remember(ui.previews, ui.selected) {
        val shown = if (ui.selected != null) listOfNotNull(card) else ui.previews.values.toList()
        shown.filter { it.needsTiles }.mapNotNull { ShareCardPainter.mapCover(context, it) }.distinct()
    }
    val tiles by produceState(TileLoad(), covers) {
        if (covers.isEmpty()) {
            value = TileLoad(ready = true, tiles = emptyMap())
        } else {
            value = TileLoad(ready = false, tiles = emptyMap())
            val store = OsmTileStore(context)
            val loaded = withContext(Dispatchers.IO) { covers.fold(emptyMap<TileId, Bitmap>()) { all, cover -> all + store.load(cover) } }
            value = TileLoad(ready = true, tiles = loaded)
        }
    }
    val mapNotice = card != null && card.needsTiles && tiles.ready && !tilesComplete(context, card, tiles.tiles)

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

    // The view model outlives the dialog. Start every open on the design grid.
    val close = {
        vm.back()
        onDismiss()
    }
    Dialog(
        onDismissRequest = { if (ui.selected != null) vm.back() else close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(Canvas).safeDrawingPadding().padding(top = 12.dp),
        ) {
            Header(
                title = if (ui.selected == null) {
                    if (subject is ShareSubject.Day) "Share day" else "Share ride"
                } else {
                    ui.selected!!.label()
                },
                showBack = ui.selected != null,
                onBack = vm::back,
                onClose = close,
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
                    saving = saving,
                    saved = saved,
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
                    onFrame = vm::framePhoto,
                    onAccent = { accent -> vm.update { it.copy(accent = accent) } },
                    onBig = { kind ->
                        statHint = false
                        vm.update { it.copy(bigNumber = kind) }
                    },
                    onStat = { kind ->
                        val shown = card.stats.map { it.kind }
                        val next = ShareLookRules.toggle(card.look, ui.switches.stats ?: shown, shown, kind)
                        statHint = next == null
                        if (next != null) vm.update { it.copy(stats = next) }
                    },
                    onPlaces = { vm.update { it.copy(showPlaces = !it.showPlaces) } },
                    onHighlight = { vm.update { it.copy(showHighlight = !it.showHighlight) } },
                    onWeather = { vm.update { it.copy(showWeather = !it.showWeather) } },
                    onSave = {
                        if (!saving) {
                            saving = true
                            scope.launch {
                                val bmp = withContext(Dispatchers.Default) { render(context, card, ui.photo, tiles.tiles, sticker = false, frame = ui.photoFrame) }
                                val name = when (subject) {
                                    is ShareSubject.Ride -> "bumblebee-ride-${subject.rideId}.png"
                                    is ShareSubject.Day -> "bumblebee-day-${subject.date}.png"
                                }
                                val ok = withContext(Dispatchers.IO) { ShareCardGallery.savePng(context, bmp, name) }
                                saving = false
                                if (ok.isSuccess) {
                                    saved++
                                } else {
                                    Toast.makeText(context, "Couldn't save the image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onSticker = {
                        vm.commitName()
                        scope.launch {
                            val bmp = withContext(Dispatchers.Default) { render(context, card, ui.photo, tiles.tiles, sticker = true, frame = ui.photoFrame) }
                            val file = withContext(Dispatchers.IO) { ShareCardPainter.writePng(context, bmp, fileId(subject)) }
                            shareSticker(context, file)
                        }
                    },
                    onShare = {
                        vm.commitName()
                        scope.launch {
                            val bmp = withContext(Dispatchers.Default) { render(context, card, ui.photo, tiles.tiles, sticker = false, frame = ui.photoFrame) }
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
                val full = render(context, model, ui.photo, tiles, sticker = false, frame = ui.photoFrame)
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
                    Thumb(look, thumbs[look], ui.switches.shape, look == ui.switches.look, Modifier.weight(1f)) { onPick(look) }
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
private fun Thumb(
    look: ShareLook,
    bitmap: Bitmap?,
    shape: ShareShape,
    lastUsed: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(shape.ratio())
                .clip(ShapeLg)
                .background(Surface)
                .border(if (lastUsed) 2.dp else 0.dp, if (lastUsed) Lime else Color.Transparent, ShapeLg)
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
        Text(look.label(), style = MaterialTheme.typography.labelMedium, color = if (lastUsed) Lime else TextSecondary)
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
    saving: Boolean,
    saved: Int,
    statHint: Boolean,
    showPlaces: Boolean,
    showHighlight: Boolean,
    showWeather: Boolean,
    onBackground: (ShareBackground) -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onFrame: (PhotoFrame) -> Unit,
    onAccent: (ShareAccent) -> Unit,
    onBig: (ShareMetricKind) -> Unit,
    onStat: (ShareMetricKind) -> Unit,
    onPlaces: () -> Unit,
    onHighlight: () -> Unit,
    onWeather: () -> Unit,
    onSave: () -> Unit,
    onSticker: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val rendered by produceState<Bitmap?>(null, card, ui.photo, ui.photoFrame, tiles) {
        value = withContext(Dispatchers.Default) {
            render(context, card, ui.photo, tiles, sticker = false, frame = ui.photoFrame)
        }
    }
    // Moving and zooming the photo shows just the photo in the card's frame, so it follows the
    // fingers smoothly; the card is drawn over it again on Done.
    var adjusting by remember { mutableStateOf(false) }
    val photo = ui.photo
    val canAdjust = photo != null && ui.switches.background == ShareBackground.PHOTO
    val showAdjust = adjusting && canAdjust
    var showSaved by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(saved) {
        if (saved == 0) return@LaunchedEffect
        showSaved = true
        pulse.snapTo(0.93f)
        pulse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        delay(1_300)
        showSaved = false
    }
    Column(modifier.fillMaxWidth()) {
        // Fit the card inside its slot. Width-first sizing let a tall story spill under the header and options.
        BoxWithConstraints(
            Modifier
                .weight(0.58f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = card.shape.ratio()
            val w = minOf(maxWidth, maxHeight * ratio)
            val bmp = rendered
            Box(
                Modifier
                    .size(w, w / ratio)
                    .graphicsLayer {
                        scaleX = pulse.value
                        scaleY = pulse.value
                    }
                    .clip(ShapeLg)
                    .background(Surface),
                contentAlignment = Alignment.Center,
            ) {
                if (showAdjust && photo != null) {
                    val layout = ShareCardLayout.forShape(card.shape)
                    AdjustPhoto(photo, ui.photoFrame, layout.width, layout.height, onFrame)
                } else if (bmp == null) {
                    Text("Drawing…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Ride card preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                SavedBadge(showSaved)
            }
        }
        Column(
            Modifier
                .weight(0.42f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    ChunkyButton(
                        text = if (adjusting) "Done" else "Move and zoom photo",
                        onClick = { adjusting = !adjusting },
                        style = if (adjusting) ChunkyStyle.Lime else ChunkyStyle.Outline,
                        compact = true,
                    )
                    if (adjusting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Drag to move, pinch to zoom.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                            if (ui.photoFrame != PhotoFrame()) {
                                Text("Reset", style = MaterialTheme.typography.bodySmall, color = Lime, modifier = Modifier.clickable { onFrame(PhotoFrame()) }.padding(8.dp))
                            }
                        }
                    }
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
            if (ShareLookRules.hasBigNumber(card.look) && card.available.isNotEmpty()) {
                SectionLabel(text = "Big number")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.available.forEach { metric ->
                        PillChip(metric.title, card.hero?.kind == metric.kind, onClick = { onBig(metric.kind) })
                    }
                }
            }
            // Only what this look shows in its stat slots: its dial, big number or fixed cells are left out.
            val slots = ShareLookRules.statSlots(card.look)
            val choices = ShareLookRules.choices(card.look, card.available, card.hero?.kind)
            if (choices.isNotEmpty()) {
                SectionLabel(text = if (slots == 1) "Extra stat" else "Stats · up to $slots")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val shown = card.stats.map { it.kind }
                    choices.forEach { metric ->
                        val index = shown.indexOf(metric.kind)
                        val caption = if (index >= 0 && slots > 1) "${index + 1}  ${metric.title}" else metric.title
                        PillChip(caption, index >= 0, onClick = { onStat(metric.kind) })
                    }
                }
            }
            if (statHint) {
                Text("Up to $slots. Tap one to remove it first.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (showPlaces || showHighlight || showWeather) {
                SectionLabel(text = "Details")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showPlaces) PillChip("Places", ui.switches.showPlaces, onClick = onPlaces)
                    if (showHighlight) PillChip("Highlight", ui.switches.showHighlight, onClick = onHighlight)
                    if (showWeather) PillChip("Weather", ui.switches.showWeather, onClick = onWeather)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChunkyButton(
                text = if (saving) "Saving…" else "Save",
                onClick = onSave,
                modifier = Modifier.weight(1f),
                style = ChunkyStyle.Outline,
                enabled = !saving,
                compact = true,
            )
            ChunkyButton(text = "Sticker", onClick = onSticker, modifier = Modifier.weight(1f), style = ChunkyStyle.Outline, compact = true)
            ChunkyButton(text = "Share", onClick = onShare, modifier = Modifier.weight(1f), style = ChunkyStyle.Lime, compact = true)
        }
    }
}

/** A check that pops over the preview when the card lands in the gallery. */
@Composable
private fun SavedBadge(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = OnBlock, modifier = Modifier.size(40.dp))
                }
                Text("Saved to Gallery", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
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
    frame: PhotoFrame = PhotoFrame(),
): Bitmap = ShareCardPainter.paint(
    context,
    model,
    photo.takeIf { model.background == ShareBackground.PHOTO },
    tiles,
    sticker,
    frame,
)

/**
 * The photo alone in the card's frame, moved by dragging and zoomed by pinching, with a
 * thirds grid to line things up. [cardW]×[cardH] is the card's own size, so the framing matches
 * the saved image exactly.
 */
@Composable
private fun AdjustPhoto(photo: Bitmap, frame: PhotoFrame, cardW: Float, cardH: Float, onFrame: (PhotoFrame) -> Unit) {
    val image = remember(photo) { photo.asImageBitmap() }
    val current by rememberUpdatedState(frame)
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxSize().pointerInput(photo, cardW, cardH) {
            detectTransformGestures { _, pan, zoom, _ ->
                val k = cardW / size.width
                onFrame(current.moved(photo.width, photo.height, cardW, cardH, zoom, pan.x * k, pan.y * k))
            }
        },
    ) {
        val k = size.width / cardW
        val r = frame.rect(photo.width, photo.height, cardW, cardH)
        drawImage(
            image,
            dstOffset = IntOffset((r[0] * k).roundToInt(), (r[1] * k).roundToInt()),
            dstSize = IntSize(((r[2] - r[0]) * k).roundToInt(), ((r[3] - r[1]) * k).roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
        val line = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            drawLine(line, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.dp.toPx())
            drawLine(line, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.dp.toPx())
        }
    }
}

private fun tilesComplete(context: Context, model: ShareCardModel, tiles: Map<TileId, Bitmap>): Boolean {
    val cover = ShareCardPainter.mapCover(context, model) ?: return false
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
