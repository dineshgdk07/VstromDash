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
fun YearRecapScreen(year: Int, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as DashApp
    val model: YearRecapViewModel = viewModel(
        key = "recap-$year",
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return YearRecapViewModel(app, year) as T
            }
        },
    )
    val state by model.ui.collectAsStateWithLifecycle()
    YearRecapBody(state = state, onBack = onBack)
}

@Composable
private fun YearRecapBody(state: YearRecapUi, onBack: () -> Unit) {
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
                headline = "NO RIDES IN ${state.year}",
                subtitle = "The road's not going to ride itself.",
                primaryLabel = "Back",
                onPrimary = onBack,
            )
            else -> RecapPager(state)
        }
    }
}

@Composable
private fun ColumnScope.RecapPager(state: YearRecapUi) {
    val pagerState = rememberPagerState { state.pages.size }
    Text(
        "${pagerState.currentPage + 1} / ${state.pages.size}",
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
    )
    VerticalPager(
        state = pagerState,
        modifier = Modifier.weight(1f),
    ) { index ->
        val page = state.pages[index]
        if (page is RecapPage.Share) {
            SharePage(state.year, state.card)
        } else {
            FactPage(page, pageColors[index % pageColors.size])
        }
    }
}

@Composable
private fun FactPage(page: RecapPage, color: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .background(color, ShapeLg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        when (page) {
            is RecapPage.Km -> {
                FactNumber(kmText(page.km))
                FactLabel("Kilometres")
                FactBody(page.comparison)
            }
            is RecapPage.Rides -> {
                FactNumber(page.count.toString())
                FactLabel("Rides")
                if (page.movingSec > 0) FactBody("${formatClock(page.movingSec)} moving")
            }
            is RecapPage.Longest -> {
                FactNumber(formatKm(page.km))
                FactLabel(page.label)
                if (page.movingSec > 0) FactBody("${formatClock(page.movingSec)} moving")
                if (page.track.size >= 2) TrackSketch(page.track)
            }
            is RecapPage.TopSpeed -> {
                FactNumber(page.kmh.toString())
                FactLabel("Top speed")
                FactBody("km/h")
            }
            is RecapPage.BusiestMonth -> {
                FactNumber(kmText(page.km))
                FactLabel(page.label)
                FactBody("Busiest month, km")
            }
            is RecapPage.Repeat -> {
                FactNumber(page.runs.toString())
                FactLabel("Repeat route")
                page.medianKm?.let { FactBody("Median ${"%.1f".format(it)} km") }
                page.litresEst?.let { FactBody("est. ${"%.1f".format(it)} L") }
            }
            is RecapPage.Fuel -> FuelFact(page)
            RecapPage.Share -> Unit
        }
    }
}

@Composable
private fun FuelFact(page: RecapPage.Fuel) {
    page.litres?.let { litres ->
        FactNumber("%.0f".format(litres))
        FactLabel(if (page.litresPartial) "Litres, est., partial" else "Litres, est.")
        FactBody("From fills")
    }
    page.rupees?.let { rupees ->
        val style = if (page.litres == null) NumberLarge else MaterialTheme.typography.titleMedium
        Text("₹${"%.0f".format(rupees)}", style = style, color = OnBlock)
        FactBody(if (page.spendPartial) "Rupees logged, partial" else "Rupees logged")
    }
}

@Composable
private fun FactNumber(text: String) {
    Text(text, style = NumberLarge, color = OnBlock)
}

@Composable
private fun FactLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = OnBlock)
}

@Composable
private fun FactBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = OnBlock)
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
private fun SharePage(year: Int, card: ShareCardModel?) {
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
                    contentDescription = "$year recap card",
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
                    val file = ShareCardPainter.writePng(context, image, year.toLong())
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
                        ShareCardGallery.savePng(context, image, "bumblebee-$year-recap.png")
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
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee year")
        putExtra(Intent.EXTRA_TEXT, sentence)
    }
    context.startActivity(Intent.createChooser(send, "Share year"))
}

private fun shareRecapImage(context: Context, file: java.io.File, sentence: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, "Bumblebee year")
        putExtra(Intent.EXTRA_TEXT, sentence)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("year-card.png", uri)
    }
    context.startActivity(Intent.createChooser(send, "Share year"))
}

@Preview
@Composable
private fun YearRecapPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas)) {
            YearRecapBody(
                state = YearRecapUi(
                    year = 2026,
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
