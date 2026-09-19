package dev.gdk.bumblebee.ui.cockpit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.ui.theme.ErrorRed
import dev.gdk.bumblebee.ui.theme.ForgeGraphite
import dev.gdk.bumblebee.ui.theme.ForgeWhite
import dev.gdk.bumblebee.ui.theme.SuccessGreen
import dev.gdk.bumblebee.ui.theme.WarningAmber

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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ForgeGraphite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("WARNINGS", style = MaterialTheme.typography.labelMedium, color = ForgeWhite.copy(alpha = 0.6f))
        if (warnings.isEmpty()) {
            Text("All clear", style = MaterialTheme.typography.headlineMedium, color = SuccessGreen)
            Text("App & connection", style = MaterialTheme.typography.bodySmall, color = ForgeWhite.copy(alpha = 0.5f))
        } else {
            warnings.forEach { w ->
                val color = when (w.severity) {
                    WarningSeverity.CRITICAL -> ErrorRed
                    WarningSeverity.WARN -> WarningAmber
                    WarningSeverity.INFO -> ForgeWhite
                }
                Text(w.title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(w.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
