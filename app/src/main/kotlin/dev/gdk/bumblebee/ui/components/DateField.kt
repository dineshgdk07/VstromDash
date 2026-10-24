package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeLg
import dev.gdk.bumblebee.ui.theme.ShapeSm
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.Surface
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Read-only date field that opens a Forge-themed date picker (§4.18). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pick a date",
) {
    var open by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }

    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .background(SurfaceSunken, ShapeSm)
                .border(1.dp, Stroke, ShapeSm)
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value?.format(formatter) ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) TextDisabled else TextPrimary,
            )
        }
    }

    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.let(::localDateToPickerMillis),
        )
        val colors = DatePickerDefaults.colors(
            containerColor = Surface,
            titleContentColor = TextSecondary,
            headlineContentColor = TextPrimary,
            weekdayContentColor = TextSecondary,
            subheadContentColor = TextSecondary,
            navigationContentColor = TextPrimary,
            yearContentColor = TextPrimary,
            currentYearContentColor = Lime,
            selectedYearContentColor = OnBlock,
            selectedYearContainerColor = Lime,
            dayContentColor = TextPrimary,
            disabledDayContentColor = TextDisabled,
            selectedDayContentColor = OnBlock,
            selectedDayContainerColor = Lime,
            todayContentColor = Lime,
            todayDateBorderColor = Lime,
            dividerColor = Stroke,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                DateDialogAction(
                    label = "Set",
                    color = if (state.selectedDateMillis == null) TextDisabled else Lime,
                    onClick = {
                        val millis = state.selectedDateMillis ?: return@DateDialogAction
                        onValueChange(pickerMillisToLocalDate(millis))
                        open = false
                    },
                )
            },
            dismissButton = {
                DateDialogAction(
                    label = "Cancel",
                    color = TextSecondary,
                    onClick = { open = false },
                )
            },
            shape = ShapeLg,
            tonalElevation = 0.dp,
            colors = colors,
        ) {
            DatePicker(state = state, colors = colors)
        }
    }
}

@Composable
private fun DateDialogAction(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

private fun localDateToPickerMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
