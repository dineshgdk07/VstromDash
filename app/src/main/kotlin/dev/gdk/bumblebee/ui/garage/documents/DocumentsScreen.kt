package dev.gdk.bumblebee.ui.garage.documents

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.garage.GarageFiles
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyDialog
import dev.gdk.bumblebee.ui.components.ChunkyDialogConfirmStyle
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.DateField
import dev.gdk.bumblebee.ui.components.EmptyState
import dev.gdk.bumblebee.ui.components.ForgeSheet
import dev.gdk.bumblebee.ui.components.ForgeTopBar
import dev.gdk.bumblebee.ui.components.ListRow
import dev.gdk.bumblebee.ui.components.ListRowIcon
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.PillChipRow
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.StatusChip
import dev.gdk.bumblebee.ui.components.StatusChipTone
import dev.gdk.bumblebee.ui.garage.DOC_KINDS
import dev.gdk.bumblebee.ui.garage.DocCard
import dev.gdk.bumblebee.ui.garage.GarageField
import dev.gdk.bumblebee.ui.garage.KindChips
import dev.gdk.bumblebee.ui.garage.RefreshOnResume
import dev.gdk.bumblebee.ui.garage.prettyKind
import dev.gdk.bumblebee.ui.theme.BlockYellow
import dev.gdk.bumblebee.ui.theme.BumblebeeTheme
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val CHECKPOST_KINDS = listOf("RC", "INSURANCE", "PUC")

@Composable
fun DocumentsScreen(
    onBack: () -> Unit,
    model: DocumentsViewModel = viewModel(),
) {
    val state by model.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<String?>(null) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<DocCard?>(null) }
    var checkpost by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { model.pickDocFile(it) }
    RefreshOnResume(model::refresh)

    val visible = state.documents.filter { filter == null || it.kind == filter }
    val detail = state.documents.firstOrNull { it.id == detailId }
    val checkpostPages = CHECKPOST_KINDS.mapNotNull { kind ->
        state.documents.firstOrNull { it.kind == kind && !it.filePath.isNullOrBlank() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ForgeTopBar(
                title = "Documents",
                onBack = onBack,
                trailing = {
                    Box(Modifier.width(112.dp)) {
                        ChunkyButton(
                            text = "Add",
                            onClick = model::beginAdd,
                            compact = true,
                            style = ChunkyStyle.Lime,
                        )
                    }
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.status.isNotBlank()) {
                    Text(state.status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                if (state.documents.any { !it.expiryIso.isNullOrBlank() }) {
                    ExpiryTimeline(state.documents)
                }
                PillChipRow {
                    PillChip(text = "All", selected = filter == null, onClick = { filter = null })
                    DOC_KINDS.forEach { kind ->
                        PillChip(
                            text = prettyKind(kind),
                            selected = filter == kind,
                            onClick = { filter = kind },
                        )
                    }
                }
                if (visible.isEmpty()) {
                    EmptyState(
                        headline = if (state.documents.isEmpty()) "No papers yet" else "Nothing here",
                        subtitle = if (state.documents.isEmpty()) {
                            "RC, insurance, PUC — keep them where the bike is."
                        } else {
                            "No papers of this kind yet."
                        },
                        primaryLabel = "Add a paper",
                        onPrimary = model::beginAdd,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                } else {
                    SectionCard {
                        visible.forEachIndexed { index, doc ->
                            val count = documentCountdown(doc.expiryIso, LocalDate.now(), state.remindDays)
                            val subtitle = listOfNotNull(
                                doc.number?.takeIf { it.isNotBlank() },
                                count.line,
                            ).joinToString(" · ")
                            ListRow(
                                title = doc.title,
                                subtitle = subtitle,
                                onClick = { detailId = doc.id },
                                showDivider = index < visible.lastIndex,
                                leading = { ListRowIcon(kindIcon(doc.kind)) },
                                trailing = { StatusChip(count.tone()) },
                            )
                        }
                    }
                }
                if (checkpostPages.isNotEmpty()) {
                    ChunkyButton(
                        text = "Show at checkpost",
                        onClick = { checkpost = true },
                        style = ChunkyStyle.Outline,
                    )
                }
            }
        }

        if (state.editing) {
            ForgeSheet(
                title = if (state.docId == null) "Add paper" else "Edit paper",
                onDismiss = model::dismissEditor,
                primaryLabel = if (state.docId == null) "Save" else "Update",
                onPrimary = model::saveDocument,
            ) {
                KindChips(DOC_KINDS, state.docKind, model::setDocKind)
                GarageField(
                    state.docTitle,
                    model::setDocTitle,
                    "Title",
                    modifier = Modifier.padding(top = 12.dp),
                )
                GarageField(state.docNumber, model::setDocNumber, "Number")
                val expiry = state.docExpiry.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                DateField(
                    label = "Expiry",
                    value = expiry,
                    onValueChange = { model.setDocExpiry(it.toString()) },
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (expiry != null) {
                    Text(
                        text = "CLEAR DATE",
                        style = MaterialTheme.typography.labelLarge,
                        color = Lime,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { model.setDocExpiry("") },
                    )
                }
                GarageField(
                    state.docNote,
                    model::setDocNote,
                    "Note",
                    singleLine = false,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ChunkyButton(
                    text = if (state.docHasFile) "Replace file" else "Attach file",
                    onClick = { picker.launch("*/*") },
                    style = ChunkyStyle.Outline,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
            }
        }

        if (detail != null && !state.editing) {
            val count = documentCountdown(detail.expiryIso, LocalDate.now(), state.remindDays)
            ForgeSheet(
                title = detail.title,
                onDismiss = { detailId = null },
                primaryLabel = "Edit",
                onPrimary = { model.beginEdit(detail) },
            ) {
                if (!detail.filePath.isNullOrBlank()) {
                    PaperPreview(detail.filePath, detail.mime) {
                        GarageFiles.viewIntent(context, detail.filePath, detail.mime)?.let { context.startActivity(it) }
                    }
                }
                Text(prettyKind(detail.kind), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                StatusChip(count.tone(), modifier = Modifier.padding(top = 8.dp))
                Text(
                    count.line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!detail.number.isNullOrBlank()) {
                    Row(
                        Modifier.padding(top = 12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            detail.number,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        ChunkyIconButton(
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("document number", detail.number))
                            },
                            contentDescription = "Copy number",
                        )
                    }
                }
                if (!detail.note.isNullOrBlank()) {
                    Text(
                        detail.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                ChunkyButton(
                    text = "Delete",
                    onClick = { pendingDelete = detail },
                    style = ChunkyStyle.Danger,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }
        }
    }

    pendingDelete?.let { doc ->
        ChunkyDialog(
            title = "Delete paper",
            body = "Remove ${doc.title}? The file goes with it.",
            dismissLabel = "Keep",
            confirmLabel = "Delete",
            confirmStyle = ChunkyDialogConfirmStyle.Danger,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                if (detailId == doc.id) detailId = null
                model.deleteDocument(doc.id)
            },
        )
    }
    if (checkpost && checkpostPages.isNotEmpty()) {
        CheckpostViewer(checkpostPages, onClose = { checkpost = false })
    }
}

@Composable
private fun ExpiryTimeline(docs: List<DocCard>) {
    val today = LocalDate.now()
    val start = YearMonth.from(today)
    val months = (0 until 12).map { start.plusMonths(it.toLong()) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        months.forEach { month ->
            val dots = docs.mapNotNull { doc ->
                val date = doc.expiryIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                if (YearMonth.from(date) == month) doc.band else null
            }
            Column(Modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dots.forEach { band ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(band.dotColor(), ShapePill),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaperPreview(path: String, mime: String?, onOpen: () -> Unit) {
    val image = mime?.startsWith("image/") == true
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    if (image) {
        LaunchedEffect(path) {
            bitmap = withContext(Dispatchers.IO) { decodeThumb(path, maxEdge = 800) }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(bottom = 12.dp)
            .clip(ShapeMd)
            .background(SurfaceRaised)
            .clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Paper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (mime == "application/pdf") Icons.Rounded.PictureAsPdf else Icons.Rounded.Description,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

private fun DocumentCountdown.tone(): StatusChipTone = when (band) {
    CareBand.OVERDUE -> StatusChipTone.Overdue
    CareBand.DUE_SOON -> StatusChipTone.DueSoon
    CareBand.OK -> StatusChipTone.Ok
    CareBand.UNKNOWN, null -> StatusChipTone.NoExpiry
}

private fun CareBand.dotColor() = when (this) {
    CareBand.OVERDUE -> Danger
    CareBand.DUE_SOON -> BlockYellow
    CareBand.OK -> Lime
    CareBand.UNKNOWN -> TextSecondary
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "RC" -> Icons.Rounded.Article
    "INSURANCE" -> Icons.Rounded.HealthAndSafety
    "PUC" -> Icons.Rounded.Verified
    "WARRANTY" -> Icons.Rounded.Verified
    "INVOICE" -> Icons.Rounded.Receipt
    "PERMIT" -> Icons.Rounded.Assignment
    else -> Icons.Rounded.Description
}

@Preview
@Composable
private fun DocumentsListPreview() {
    BumblebeeTheme {
        Column(Modifier.background(Canvas).padding(16.dp)) {
            ExpiryTimeline(
                listOf(
                    DocCard(1, "Insurance", "INSURANCE", "AB1", "2026-11-02", null, null, CareBand.DUE_SOON, "Insurance", null),
                    DocCard(2, "PUC", "PUC", null, "2027-03-01", null, null, CareBand.OK, "PUC", null),
                ),
            )
        }
    }
}
