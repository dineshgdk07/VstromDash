package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.Scrim
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.TextPrimary
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val MIN_YEAR = 1970
private const val MAX_YEAR = 2100

/** Month stepper with a year-and-month picker (§4.21). */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val label = month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        .plus(" ")
        .plus(month.year)
        .uppercase()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChunkyIconButton(
            icon = Icons.Rounded.ChevronLeft,
            onClick = { onMonthChange(month.minusMonths(1)) },
            contentDescription = "Previous month",
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable { open = true }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ChunkyIconButton(
            icon = Icons.Rounded.ChevronRight,
            onClick = { onMonthChange(month.plusMonths(1)) },
            contentDescription = "Next month",
        )
    }

    if (open) {
        MonthYearDialog(
            month = month,
            onMonthChange = {
                onMonthChange(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
internal fun MonthYearDialog(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            MonthYearPicker(
                month = month,
                onMonthChange = onMonthChange,
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .background(Surface, ShapeLg)
                    .padding(24.dp),
            )
        }
    }
}

@Composable
internal fun MonthYearPicker(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    var year by remember { mutableIntStateOf(month.year) }

    Column(modifier) {
        Text(
            text = "PICK A MONTH",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChunkyIconButton(
                icon = Icons.Rounded.ChevronLeft,
                onClick = { year -= 1 },
                contentDescription = "Previous year",
                enabled = year > MIN_YEAR,
            )
            Text(
                text = year.toString(),
                style = NumberLarge,
                color = TextPrimary,
            )
            ChunkyIconButton(
                icon = Icons.Rounded.ChevronRight,
                onClick = { year += 1 },
                contentDescription = "Next year",
                enabled = year < MAX_YEAR,
            )
        }
        Month.entries.chunked(3).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { item ->
                    PillChip(
                        text = item.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        selected = year == month.year && item == month.month,
                        onClick = { onMonthChange(YearMonth.of(year, item)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
