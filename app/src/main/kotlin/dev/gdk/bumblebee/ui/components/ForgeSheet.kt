package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.Scrim
import dev.gdk.bumblebee.ui.theme.ShapeSheet
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

/** Form sheet (§4.17). Compose it while it should be open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgeSheet(
    title: String,
    onDismiss: () -> Unit,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = true,
        shape = ShapeSheet,
        containerColor = Surface,
        contentColor = TextPrimary,
        tonalElevation = 0.dp,
        scrimColor = Scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) },
    ) {
        ForgeSheetBody(
            title = title,
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            primaryEnabled = primaryEnabled,
            content = content,
        )
    }
}

@Composable
internal fun ForgeSheetBody(
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cap = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    Column(modifier.fillMaxWidth().heightIn(max = cap)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            content = content,
        )
        ChunkyButton(
            text = primaryLabel,
            onClick = onPrimary,
            style = ChunkyStyle.Lime,
            enabled = primaryEnabled,
            modifier = Modifier.padding(16.dp),
        )
    }
}
