package dev.gdk.bumblebee.ui.video

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.Accent
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.video.PreviewRenderer
import dev.gdk.bumblebee.video.RideVideoSource
import dev.gdk.bumblebee.video.VideoExportState
import dev.gdk.bumblebee.video.VideoPreset
import dev.gdk.bumblebee.video.VideoShape
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ride Video Studio: pick a style, tune anything, watch a live preview, then render. While it
 * renders the screen shows progress; when it's done it plays the video with Save, Share and
 * "another size". Rendering runs in a service, so leaving the screen doesn't stop it.
 */
@Composable
fun VideoStudioScreen(rideId: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vm: VideoStudioViewModel = viewModel(key = "video-$rideId", factory = VideoStudioViewModel.Factory(context.applicationContext as Application, rideId))
    val ui by vm.ui.collectAsState()
    val export by vm.export.collectAsState()
    val mine = when (val e = export) {
        is VideoExportState.Working -> e.rideId == rideId
        is VideoExportState.Done -> e.rideId == rideId
        is VideoExportState.Failed -> e.rideId == rideId
        VideoExportState.Idle -> false
    }

    // Closing after a finished or failed render clears it, so the ride screen doesn't reopen it.
    val close = {
        if (mine && export !is VideoExportState.Working) dev.gdk.bumblebee.video.VideoExportService.consume()
        onDismiss()
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Canvas).safeDrawingPadding()) {
            ForgeTopBar(title = "Ride video", onBack = close)
            val e = export
            when {
                mine && e is VideoExportState.Working -> Rendering(e, onCancel = vm::cancel, onLeave = close)
                mine && e is VideoExportState.Done -> Result(
                    done = e,
                    savedTo = ui.savedTo,
                    message = ui.message,
                    onSave = { vm.save(e) },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).setType("video/mp4")
                            .putExtra(Intent.EXTRA_STREAM, e.shareUri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Share ride video"))
                    },
                    onAgain = vm::again,
                )
                else -> Setup(
                    ui = ui,
                    failed = (e as? VideoExportState.Failed)?.takeIf { mine }?.reason,
                    vm = vm,
                )
            }
        }
    }
}

@Composable
private fun Setup(ui: StudioUi, failed: String?, vm: VideoStudioViewModel) {
    val config = ui.config
    val plan = ui.plan
    var time by remember { mutableDoubleStateOf(1.0) }
    var playing by remember { mutableStateOf(false) }
    val total = plan?.totalS ?: 1.0
    LaunchedEffect(playing, total) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            time = (time + (now - last) / 1e9).let { if (it > total) 0.0 else it }
            last = now
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Preview(ui, time)
            if (plan != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = TextPrimary,
                        modifier = Modifier.size(40.dp).clip(ShapeMd).clickable { playing = !playing }.padding(6.dp),
                    )
                    Slider(
                        value = time.toFloat().coerceIn(0f, total.toFloat()),
                        onValueChange = {
                            playing = false
                            time = it.toDouble()
                        },
                        valueRange = 0f..total.toFloat(),
                        colors = sliderColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(clock(time) + " / " + clock(total), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            failed?.let { Text("Last render failed: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            (ui.blocked ?: ui.message)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }

            SectionCard(header = { SectionLabel(text = "Style") }) {
                ChipRow(VideoPreset.entries.filter { it != VideoPreset.CUSTOM || config.preset == VideoPreset.CUSTOM }, config.preset, { it.label }) { vm.preset(it) }
                Text(config.preset.blurb, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            }

            SectionCard(header = { SectionLabel(text = "Size and length") }) {
                ChipRow(VideoShape.entries.toList(), config.shape, { "${it.label}  ${shapeUse(it)}" }) { s -> vm.updateKeepingPreset { it.copy(shape = s) } }
                plan?.let { p ->
                    val range = p.length
                    val len = (config.lengthS ?: range.recommended).coerceIn(range.min, range.max)
                    Text(
                        "Length  ${len} s" + if (config.lengthS == null || len == range.recommended) "  (recommended)" else "  · recommended ${range.recommended} s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Slider(
                        value = len.toFloat(),
                        onValueChange = { v -> vm.updateKeepingPreset { it.copy(lengthS = (v / 5).roundToInt() * 5) } },
                        valueRange = range.min.toFloat()..range.max.toFloat(),
                        colors = sliderColors(),
                    )
                    if (len > 60) Text("YouTube Shorts allows 60 s; Reels allows 90 s.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            SectionCard(header = { SectionLabel(text = "Story") }) {
                TitleField(config.title ?: plan?.input?.title.orEmpty()) { t -> vm.updateKeepingPreset { it.copy(title = t) } }
                Toggle("Cold open: tease the best moment first", config.coldOpen) { v -> vm.update { it.copy(coldOpen = v) } }
                Toggle("Title card at the start", config.titleCard) { v -> vm.update { it.copy(titleCard = v) } }
                Toggle("Chapters: the climb, the twisties, after the break", config.chapters) { v -> vm.update { it.copy(chapters = v) } }
                Toggle("From and to place names", config.places) { v -> vm.update { it.copy(places = v) } }
                Toggle("Pause on highlights", config.moments) { v -> vm.update { it.copy(moments = v) } }
                Toggle("Map reveal: redraw the route from above at the end", config.mapReveal) { v -> vm.update { it.copy(mapReveal = v) } }
                Toggle("Stats card at the end", config.finishCard) { v -> vm.update { it.copy(finishCard = v) } }
                ui.plan?.chapters?.takeIf { it.isNotEmpty() && config.chapters }?.let { chapters ->
                    Text(
                        "This ride: " + chapters.joinToString("  ·  ") { it.title },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            SectionCard(header = { SectionLabel(text = "Overlays") }) {
                Toggle("Speed, distance and height", config.hud) { v -> vm.update { it.copy(hud = v) } }
                Toggle("Time of day", config.clock) { v -> vm.update { it.copy(clock = v) } }
                Toggle("Elevation strip", config.elevationStrip) { v -> vm.update { it.copy(elevationStrip = v) } }
            }

            SectionCard(header = { SectionLabel(text = "Camera and land") }) {
                Tune("Camera distance", config.cameraDistance, 0.5..2.0, closer = "Closer", further = "Further") { v -> vm.update { it.copy(cameraDistance = v) } }
                Tune("Lean into turns", config.bank, 0.0..1.0, closer = "Level", further = "Banked") { v -> vm.update { it.copy(bank = v) } }
                Toggle("A different shot for each highlight", config.momentShots) { v -> vm.update { it.copy(momentShots = v) } }
                Toggle("Rise over hairpins", config.hairpinView) { v -> vm.update { it.copy(hairpinView = v) } }
                val auto = config.exaggeration == null
                Toggle("Automatic hill height", auto) { v -> vm.update { it.copy(exaggeration = if (v) null else plan?.exaggeration ?: 1.5) } }
                if (!auto) {
                    Tune("Hill height ×${"%.1f".format(Locale.ENGLISH, config.exaggeration)}", config.exaggeration ?: 1.5, 1.0..4.0, closer = "True", further = "Dramatic") { v -> vm.update { it.copy(exaggeration = v) } }
                }
                Tune("Haze", config.haze, 0.2..2.0, closer = "Clear", further = "Hazy") { v -> vm.update { it.copy(haze = v) } }
                Toggle("Contour lines", config.contours) { v -> vm.update { it.copy(contours = v) } }
            }

            SectionCard(header = { SectionLabel(text = "World") }) {
                Toggle("Light follows the ride's clock", config.timeOfDay) { v -> vm.update { it.copy(timeOfDay = v) } }
                Tune("Clouds", config.clouds, 0.0..1.0, closer = "Clear", further = "Overcast") { v -> vm.update { it.copy(clouds = v) } }
                Toggle("Lakes and sea", config.water) { v -> vm.update { it.copy(water = v) } }
                if (config.water && ui.plan != null && ui.plan.water == null && !ui.plan.flat) {
                    Text("No water found around this route.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Toggle("Town names along the way", config.placeLabels) { v -> vm.update { it.copy(placeLabels = v) } }
                ui.plan?.labels?.takeIf { it.isNotEmpty() && config.placeLabels }?.let { labels ->
                    Text(labels.joinToString("  ·  ") { it.name }, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            SectionCard(header = { SectionLabel(text = "Trail and finish") }) {
                Tune("Trail width", config.pathWidth, 0.5..2.5, closer = "Fine", further = "Bold") { v -> vm.update { it.copy(pathWidth = v) } }
                Tune("Glow", config.glow, 0.0..2.0, closer = "None", further = "Bright") { v -> vm.update { it.copy(glow = v) } }
                Toggle("Vignette", config.vignette) { v -> vm.update { it.copy(vignette = v) } }
                Toggle("Film grain", config.grain) { v -> vm.update { it.copy(grain = v) } }
            }
            Text(
                "More is coming to the Studio: your bike as the marker, a cluster-style HUD, ride tags, ride photos, kinetic type and music.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            ChunkyButton(
                text = when {
                    ui.preparing && plan == null -> "Preparing terrain…"
                    else -> "Make video"
                },
                onClick = vm::render,
                style = ChunkyStyle.Lime,
                enabled = plan != null && ui.blocked == null,
            )
        }
    }
}

/** The live preview at the chosen shape, letterboxed to fit. */
@Composable
private fun Preview(ui: StudioUi, time: Double) {
    val context = LocalContext.current
    val renderer = remember { PreviewRenderer(RideVideoSource.fonts(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    renderer.onError = { error = it.message ?: it.javaClass.simpleName }
    val shape = ui.config.shape
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxH = 420.dp
        val byWidth = maxWidth / shape.aspect.toFloat()
        val h = if (byWidth > maxH) maxH else byWidth
        val w = h * shape.aspect.toFloat()
        Box(Modifier.width(w).height(h).clip(ShapeMd).background(SurfaceSunken), contentAlignment = Alignment.Center) {
            val view = remember { mutableStateOf<android.opengl.GLSurfaceView?>(null) }
            AndroidView(
                factory = { ctx -> PreviewRenderer.view(ctx, renderer).also { view.value = it } },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(Unit) {
                view.value?.onResume()
                onDispose { view.value?.onPause() }
            }
            LaunchedEffect(ui.plan, ui.look, time) {
                renderer.plan = ui.plan
                renderer.look = ui.look
                renderer.accent = Accent.color.toArgb()
                renderer.time = time
                view.value?.requestRender()
            }
            if (ui.plan == null || ui.preparing) {
                Text(if (ui.plan == null) "Preparing terrain…" else "Updating…", style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
            }
            error?.let { Text("Preview failed: $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(12.dp)) }
        }
    }
}

@Composable
private fun Rendering(w: VideoExportState.Working, onCancel: () -> Unit, onLeave: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(w.fraction?.let { "${(it * 100).toInt()}%" } ?: "…", style = MaterialTheme.typography.displayLarge, color = Lime)
        Text(w.step, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        if (w.fraction != null) {
            LinearProgressIndicator(progress = { w.fraction }, color = Lime, trackColor = SurfaceSunken, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(color = Lime, trackColor = SurfaceSunken, modifier = Modifier.fillMaxWidth())
        }
        w.secondsLeft?.let { Text("About ${clock(it.toDouble())} left", style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
        Text("It keeps going if you leave; the notification brings you back.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { ChunkyButton(text = "Cancel", onClick = onCancel, style = ChunkyStyle.Outline) }
            Box(Modifier.weight(1f)) { ChunkyButton(text = "Leave it running", onClick = onLeave, style = ChunkyStyle.Neutral) }
        }
    }
}

@Composable
private fun Result(
    done: VideoExportState.Done,
    savedTo: String?,
    message: String?,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onAgain: (VideoShape?) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val maxH = 460.dp
            val byWidth = maxWidth / done.shape.aspect.toFloat()
            val h = if (byWidth > maxH) maxH else byWidth
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(done.file))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.width(h * done.shape.aspect.toFloat()).height(h).clip(ShapeMd),
            )
        }
        Text(
            "${done.shape.label} · ${"%.1f".format(Locale.ENGLISH, done.file.length() / 1048576.0)} MB · made in ${clock(done.renderS.toDouble())}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        (savedTo ?: message)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (savedTo != null) Lime else TextSecondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { ChunkyButton(text = if (savedTo != null) "Saved" else "Save to gallery", onClick = onSave, style = ChunkyStyle.Outline, enabled = savedTo == null) }
            Box(Modifier.weight(1f)) { ChunkyButton(text = "Share", onClick = onShare, style = ChunkyStyle.Lime) }
        }
        SectionCard(header = { SectionLabel(text = "Make another") }) {
            Text("Same style, another size. The terrain is already downloaded, so it's quicker.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoShape.entries.filter { it != done.shape }.forEach { s ->
                    PillChip(text = "${s.label} ${shapeUse(s)}", selected = false, onClick = { onAgain(s) })
                }
            }
            Box(Modifier.padding(top = 10.dp)) { ChunkyButton(text = "Change the style", onClick = { onAgain(null) }, style = ChunkyStyle.Outline, compact = true) }
        }
    }
}

@Composable
private fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { PillChip(text = label(it), selected = it == selected, onClick = { onPick(it) }) }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Tune(label: String, value: Double, range: ClosedFloatingPointRange<Double>, closer: String, further: String, onChange: (Double) -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            colors = sliderColors(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(closer, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(further, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun TitleField(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = { onChange(it.take(40)) },
        label = { Text("Title") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceSunken,
            unfocusedContainerColor = SurfaceSunken,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedIndicatorColor = Lime,
            cursorColor = Lime,
        ),
    )
}

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = SurfaceSunken)

private fun shapeUse(s: VideoShape) = when (s) {
    VideoShape.VERTICAL -> "Reels"
    VideoShape.LANDSCAPE -> "YouTube"
    VideoShape.SQUARE -> "Feed"
}

private fun clock(seconds: Double): String {
    val s = seconds.roundToInt().coerceAtLeast(0)
    return "%d:%02d".format(Locale.ENGLISH, s / 60, s % 60)
}
