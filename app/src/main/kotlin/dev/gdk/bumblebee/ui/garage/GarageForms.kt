package dev.gdk.bumblebee.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.gdk.bumblebee.ride.CareBand
import dev.gdk.bumblebee.ui.components.ChunkyIconButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning

internal val DOC_KINDS = listOf("RC", "INSURANCE", "PUC", "WARRANTY", "INVOICE", "PERMIT", "OTHER")
internal val PLAN_KINDS = listOf("SERVICE", "OIL", "CHAIN", "BRAKES", "TYRES", "BATTERY", "OTHER")
internal val SERVICE_KINDS = listOf("OIL", "CHAIN", "BRAKES", "TYRES", "BATTERY", "SERVICE", "OTHER")

@Composable
internal fun RefreshOnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, onResume) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun KindChips(
    kinds: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    raw: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.forEach { kind ->
            PillChip(
                text = if (raw) kind else prettyKind(kind),
                selected = kind.equals(selected, ignoreCase = true),
                onClick = { onSelect(kind) },
            )
        }
    }
}

@Composable
internal fun GarageField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = ShapeMd,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = Lime,
            unfocusedBorderColor = Stroke,
            focusedLabelColor = Lime,
            unfocusedLabelColor = TextSecondary,
            cursorColor = Lime,
            focusedPlaceholderColor = TextDisabled,
            unfocusedPlaceholderColor = TextDisabled,
        ),
    )
}

@Composable
internal fun CareCard(
    kicker: String,
    story: String,
    band: CareBand,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceRaised, ShapeMd)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(kicker, style = MaterialTheme.typography.labelSmall, color = bandColor(band))
            Text(story, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        if (onOpen != null) {
            ChunkyIconButton(icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = onOpen, contentDescription = "Open")
        }
        ChunkyIconButton(icon = Icons.Rounded.Edit, onClick = onEdit, contentDescription = "Edit", style = ChunkyStyle.Lime)
        ChunkyIconButton(icon = Icons.Rounded.Delete, onClick = onDelete, contentDescription = "Delete")
    }
}

internal fun bandColor(band: CareBand) = when (band) {
    CareBand.OVERDUE -> Danger
    CareBand.DUE_SOON -> Warning
    CareBand.OK -> Lime
    CareBand.UNKNOWN -> TextSecondary
}

internal fun selectedKind(item: String, plans: List<PlanCard>, planId: Long?): String {
    plans.firstOrNull { it.id == planId }?.kind?.let { return it }
    val text = item.lowercase()
    return when {
        text.contains("oil") -> "OIL"
        text.contains("chain") -> "CHAIN"
        text.contains("brake") -> "BRAKES"
        text.contains("tyre") || text.contains("tire") -> "TYRES"
        text.contains("battery") -> "BATTERY"
        text.contains("service") -> "SERVICE"
        else -> "OTHER"
    }
}
