package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import dev.gdk.bumblebee.ui.theme.Warning as WarningColor

enum class WarningSeverity { CRITICAL, WARN, INFO }

data class CockpitWarning(
    val severity: WarningSeverity,
    val title: String,
    val detail: String,
)

@Composable
fun WarningsCard(
    warnings: List<CockpitWarning>,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier,
        header = { SectionLabel(text = "Warnings", count = warnings.size) },
    ) {
        if (warnings.isEmpty()) {
            Text(
                text = "All clear. Nothing on the bike needs you.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Column(
                Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                warnings.forEach { warning ->
                    WarningRow(warning)
                }
            }
        }
    }
}

@Composable
private fun WarningRow(warning: CockpitWarning) {
    val (icon, tint) = when (warning.severity) {
        WarningSeverity.CRITICAL -> Icons.Rounded.Error to Danger
        WarningSeverity.WARN -> Icons.Rounded.Warning to WarningColor
        WarningSeverity.INFO -> Icons.Rounded.Info to TextSecondary
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(24.dp),
        )
        Column {
            Text(warning.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                warning.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
