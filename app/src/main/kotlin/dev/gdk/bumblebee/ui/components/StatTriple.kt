package dev.gdk.bumblebee.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.NumberLarge
import dev.gdk.bumblebee.ui.theme.NumberMedium
import dev.gdk.bumblebee.ui.theme.TextDisabled
import dev.gdk.bumblebee.ui.theme.TextPrimary
import androidx.compose.material3.MaterialTheme

enum class StatTone {
    Good,
    Bad,
    Neutral,

    /** A plain reading such as an odometer, not a comparison. */
    Plain,
}

data class StatTripleColumn(
    val value: String,
    val caption: String,
    val tone: StatTone,
)

private fun StatTone.toColor() =
    when (this) {
        StatTone.Good -> Lime
        StatTone.Bad -> Danger
        StatTone.Neutral -> TextDisabled
        StatTone.Plain -> TextPrimary
    }

/** Three-column stats with optional headline (§4.9). Ref: `insights.jpeg`. */
@Composable
fun StatTriple(
    columns: List<StatTripleColumn>,
    modifier: Modifier = Modifier,
    headlineLabel: String? = null,
    headlineValue: String? = null,
) {
    require(columns.size == 3) { "StatTriple requires exactly 3 columns" }

    Column(modifier = modifier.fillMaxWidth()) {
        if (headlineLabel != null || headlineValue != null) {
            if (headlineLabel != null) {
                SectionLabel(text = headlineLabel)
            }
            if (headlineValue != null) {
                Text(
                    text = headlineValue,
                    style = NumberLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            columns.forEach { column ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = column.value,
                        style = NumberMedium,
                        color = column.tone.toColor(),
                    )
                    Text(
                        text = column.caption.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
