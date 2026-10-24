package dev.gdk.bumblebee.ui.explorer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Spacer
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ride.GpsFix
import dev.gdk.bumblebee.ride.ShareCardModel
import dev.gdk.bumblebee.ride.TrackPlot
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.ride.formatClock
import dev.gdk.bumblebee.ui.ride.formatKm
import dev.gdk.bumblebee.ui.sharecard.ShareCardGallery
import dev.gdk.bumblebee.ui.sharecard.ShareCardPainter
import dev.gdk.bumblebee.ui.theme.BlockCoral
import dev.gdk.bumblebee.ui.theme.BlockLavender
import dev.gdk.bumblebee.ui.theme.BlockMint
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val pageColors = listOf(BlockLavender, BlockCoral, BlockYellow, BlockMint)

@Composable
fun RecapScreen(period: RecapPeriod, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as DashApp
    val model: RecapViewModel = viewModel(
        key = "recap-${period.id}",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RecapViewModel(app, period) as T
            }
        },
    )
    val state by model.ui.collectAsStateWithLifecycle()
    RecapBody(state = state, onBack = onBack)
}

@Composable
private fun RecapBody(state: RecapUi, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChunkyIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
            )
        }
        when {
            !state.loaded -> Unit
            state.pages.isEmpty() -> EmptyState(
                headline = "NO RIDES IN ${state.period.label.uppercase()}",
                subtitle = "The road's not going to ride itself.",
                primaryLabel = "Back",
                onPrimary = onBack,
            )
            else -> RecapPager(state)
        }
    }
}

@Composable
private fun ColumnScope.RecapPager(state: RecapUi) {
    val pagerState = rememberPagerState { state.pages.size }
    VerticalPager(
        state = pagerState,
        modifier = Modifier.weight(1f).padding(top = 12.dp),
        pageSpacing = 12.dp,
    ) { index ->
        val page = state.pages[index]
        if (page is RecapPage.Share) {
            SharePage(state.period, state.card)
        } else {
            FactPage(
                fact = factFor(page),
                label = state.period.label,
                position = "${index + 1} / ${state.pages.size}",
                more = index < state.pages.lastIndex,
                color = pageColors[index % pageColors.size],
            )
        }
    }
}

/** One recap fact: an Anton title, one big number, and a few short lines under it. */
private data class RecapFact(
    val title: String,
    val number: String,
    val unit: String? = null,
    val lines: List<String> = emptyList(),
    val track: List<GpsFix> = emptyList(),
)

private fun factFor(page: RecapPage): RecapFact = when (page) {
    is RecapPage.Km -> RecapFact("Kilometres", kmText(page.km), "km", listOf(page.comparison))
    is RecapPage.Rides -> RecapFact(
        "Rides",
        page.count.toString(),
        lines = listOfNotNull(page.movingSec.takeIf { it > 0 }?.let { "${formatClock(it)} moving" }),
    )
    is RecapPage.Longest -> RecapFact(
        "Longest ride",
        formatKm(page.km),
        lines = listOfNotNull(page.label, page.movingSec.takeIf { it > 0 }?.let { "${formatClock(it)} moving" }),
        track = page.track,
    )
    is RecapPage.TopSpeed -> RecapFact("Top speed", page.kmh.toString(), "km/h")
    is RecapPage.BusiestMonth -> RecapFact("Busiest month", kmText(page.km), "km", listOf(page.label))
    is RecapPage.Repeat -> RecapFact(
        "Repeat route",
        page.runs.toString(),
        "runs",
        listOfNotNull(
            page.medianKm?.let { "Median ${"%.1f".format(it)} km" },
            page.litresEst?.let { "est. ${"%.1f".format(it)} L" },
        ),
    )
    is RecapPage.Fuel -> {
        val spend = page.rupees?.let { "₹${"%.0f".format(it)}" + if (page.spendPartial) " logged, partial" else " logged" }
        if (page.litres != null) {
            RecapFact(
                "Fuel",
                "%.0f".format(page.litres),
                if (page.litresPartial) "L est., partial" else "L est.",
                listOfNotNull(spend),
            )
        } else {
            RecapFact(
                "Fuel spend",
                "₹${"%.0f".format(page.rupees ?: 0.0)}",
                lines = listOf(if (page.spendPartial) "Rupees logged, partial" else "Rupees logged"),
            )
        }
    }
    RecapPage.Share -> RecapFact("", "")
}

@Composable
private fun FactPage(fact: RecapFact, label: String, position: String, more: Boolean, color: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .background(color, ShapeLg)
            .padding(24.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = OnBlock.copy(alpha = 0.7f))
        Text(fact.title.uppercase(), style = MaterialTheme.typography.headlineMedium, color = OnBlock)
        Spacer(Modifier.weight(1f))
        Text(fact.number, style = giantNumber(fact.number), color = OnBlock, maxLines = 1)
        fact.unit?.let { Text(it, style = MaterialTheme.typography.titleLarge, color = OnBlock) }
        fact.lines.forEachIndexed { i, line ->
            Text(
                line,
                style = MaterialTheme.typography.bodyLarge,
                color = OnBlock,
                modifier = Modifier.padding(top = if (i == 0) 16.dp else 4.dp),
            )
        }
        if (fact.track.size >= 2) TrackSketch(fact.track)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (more) {
                Text("Swipe up", style = MaterialTheme.typography.labelLarge, color = OnBlock.copy(alpha = 0.7f))
            }
            Spacer(Modifier.weight(1f))
            Text(position, style = MaterialTheme.typography.labelLarge, color = OnBlock.copy(alpha = 0.7f))
        }
    }
}

/** Biggest size that keeps the number on one line across a phone-width card. */
private fun giantNumber(text: String): TextStyle {
    val size = when {
        text.length <= 3 -> 136
        text.length <= 5 -> 112
        text.length <= 7 -> 84
        else -> 64
    }
    return NumberLarge.copy(fontSize = size.sp, lineHeight = (size * 1.05f).sp)
}

@Composable
private fun TrackSketch(track: List<GpsFix>) {
    val points = TrackPlot.normalize(track)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(top = 8.dp),
    ) {
        if (points.size < 2) return@Canvas
        val path = Path()
        points.forEachIndexed { index, point ->
            val at = Offset(point.x * size.width, point.y * size.height)
            if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
        }
        drawPath(
            path,
            OnBlock,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun SharePage(period: RecapPeriod, card: ShareCardModel?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<Bitmap?>(null, card) {
        val model = card ?: return@produceState
        value = withContext(Dispatchers.Default) {
            ShareCardPainter.paint(context, model, photo = null, tiles = null)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image == null) {
                Text("Drawing…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            } else {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "${period.label} recap card",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        ChunkyButton(
            text = "Share",
            onClick = {
                val image = bitmap
                val sentence = card?.sentence.orEmpty()
                if (image == null) {
                    shareRecapText(context, sentence)
                } else {
                    val file = ShareCardPainter.writePng(context, image, period.id)
                    shareRecapImage(context, file, sentence)
                }
            },
            style = ChunkyStyle.Lime,
        )
        ChunkyButton(
            text = "Save to gallery",
            onClick = {
                val image = bitmap ?: return@ChunkyButton
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ShareCardGallery.savePng(context, image, "bumblebee-${period.id}-recap.png")
                    }
                }
            },
            style = ChunkyStyle.Outline,
        )
    }
}

private fun shareRecapText(context: Context, sentence: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee recap")
        putExtra(Intent.EXTRA_TEXT, sentence)
    }
    context.startActivity(Intent.createChooser(send, "Share recap"))
}

private fun shareRecapImage(context: Context, file: java.io.File, sentence: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee recap")
        putExtra(Intent.EXTRA_TEXT, sentence)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("recap-card.png", uri)
    }
    context.startActivity(Intent.createChooser(send, "Share recap"))
}

@Preview
@Composable
private fun RecapPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            RecapBody(
                state = RecapUi(
                    period = RecapPeriod.Year(2026),
                    loaded = true,
                    pages = listOf(
                        RecapPage.Km(4_400.0, "That's Chennai → Delhi ×2.0"),
                        RecapPage.Rides(40, 86_400),
                        RecapPage.TopSpeed(117),
                    ),
                ),
                onBack = {},
            )
        }
    }
}
