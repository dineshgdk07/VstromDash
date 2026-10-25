package dev.gdk.bumblebee.ui.bike

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.gdk.bumblebee.DashApp
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.DateField
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.components.SectionCard
import dev.gdk.bumblebee.ui.components.SectionLabel
import dev.gdk.bumblebee.ui.theme.Canvas
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary
import androidx.compose.ui.platform.LocalContext

/**
 * Four short steps: rider and bike, ownership, papers, safety and pairing. Any step can be
 * skipped; [firstRun] false is the same flow opened later as "Bike profile".
 */
@Composable
fun OnboardingScreen(
    firstRun: Boolean,
    onDone: (pairNext: Boolean) -> Unit,
    model: OnboardingViewModel = viewModel(),
) {
    val state by model.ui.collectAsStateWithLifecycle()
    val steps = OnboardingStep.entries
    val index = steps.indexOf(state.step)
    var pairNext by remember { mutableStateOf(false) }
    LaunchedEffect(state.done) { if (state.done) onDone(pairNext) }
    BackHandler(enabled = index > 0) { model.go(steps[index - 1]) }
    val progress by animateFloatAsState((index + 1f) / steps.size, label = "progress")

    Column(Modifier.fillMaxSize().background(Canvas).then(if (firstRun) Modifier.safeDrawingPadding() else Modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("STEP ${index + 1} OF ${steps.size}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(state.step.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            Text(
                if (firstRun) "Skip" else "Close",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.clip(ShapePill).clickable { if (firstRun) model.skip() else onDone(false) }.padding(8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp).clip(ShapePill).background(SurfaceRaised)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Lime, ShapePill))
        }
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { step ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    OnboardingStep.BIKE -> BikeStep(state, model)
                    OnboardingStep.OWNERSHIP -> OwnershipStep(state, model)
                    OnboardingStep.PAPERS -> PapersStep(state, model)
                    OnboardingStep.SAFETY -> SafetyStep(state, model, onPair = {
                        pairNext = true
                        model.finish()
                    })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (index > 0) {
                Box(Modifier.weight(1f)) {
                    ChunkyButton(text = "Back", onClick = { model.go(steps[index - 1]) }, style = ChunkyStyle.Outline)
                }
            }
            Box(Modifier.weight(if (index > 0) 2f else 1f)) {
                val last = index == steps.lastIndex
                ChunkyButton(
                    text = when {
                        state.saving -> "Saving…"
                        last -> "Finish"
                        else -> "Next"
                    },
                    enabled = !state.saving,
                    onClick = { if (last) model.finish() else model.go(steps[index + 1]) },
                    style = ChunkyStyle.Lime,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
}

@Composable
private fun BikeStep(state: OnboardingUi, model: OnboardingViewModel) {
    OutlinedTextField(
        value = state.riderName,
        onValueChange = model::setName,
        label = { Text("Your name") },
        supportingText = { Text("Shown on the bike's cluster and in crash texts") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    SectionCard(header = { SectionLabel(text = "Which colour is yours?") }) {
        Box(Modifier.padding(top = 8.dp)) { VariantPicker(state.variant, model::setVariant) }
    }
    RegNoField(state.regNo, model::setRegNo)
    SectionCard(header = { SectionLabel(text = "App colour") }) {
        Box(Modifier.padding(top = 8.dp)) { AccentPicker(state.variant, state.accent, model::setAccent) }
    }
}

@Composable
private fun OwnershipStep(state: OnboardingUi, model: OnboardingViewModel) {
    Hint("This tells Parts where your original tyres, chain and pads started, so wear is right from day one.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceCard("Bought new", "0 km on the clock", state.boughtNew == true, Modifier.weight(1f)) { model.setBoughtNew(true) }
        ChoiceCard("Bought used", "Enter the odo then", state.boughtNew == false, Modifier.weight(1f)) { model.setBoughtNew(false) }
    }
    if (state.boughtNew != null) {
        DateField(
            label = if (state.boughtNew) "Delivery date" else "Bought on",
            value = state.purchaseDate,
            onValueChange = model::setPurchaseDate,
        )
    }
    if (state.boughtNew == false) {
        OutlinedTextField(
            value = state.purchaseOdo,
            onValueChange = model::setPurchaseOdo,
            label = { Text("Odometer when bought (km)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.currentOdo?.let { Hint("The bike last reported ${"%,d".format(it)} km.") }
    Hint("Replaced tyres or a chain since? Set those later in Service → Parts.")
}

@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(ShapeMd)
            .background(if (selected) Lime.copy(alpha = 0.14f) else SurfaceRaised)
            .border(if (selected) 2.dp else 1.dp, if (selected) Lime else Stroke, ShapeMd)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun PapersStep(state: OnboardingUi, model: OnboardingViewModel) {
    Hint("Expiry dates drive renewal reminders. A photo or PDF of each shows up in the checkpost view when you're stopped.")
    state.papers.forEach { p -> PaperCard(p, state.regNo, model) }
}

@Composable
private fun PaperCard(p: PaperDraft, regNo: String, model: OnboardingViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { model.setPaperFile(p.kind, it) }
    SectionCard(header = { SectionLabel(text = p.title) }) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(
                label = if (p.kind == "RC") "Valid till" else "Expires on",
                value = p.expiry,
                onValueChange = { model.setPaperExpiry(p.kind, it) },
            )
            OutlinedTextField(
                value = p.number,
                onValueChange = { model.setPaperNumber(p.kind, it) },
                label = {
                    Text(
                        when (p.kind) {
                            "RC" -> "Registration no (blank uses ${regNo.ifBlank { "the one above" }})"
                            "INSURANCE" -> "Policy no"
                            else -> "Certificate no"
                        },
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().clip(ShapeMd).background(SurfaceRaised).clickable { picker.launch("*/*") }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val has = p.fileName != null
                Icon(
                    if (has) Icons.Rounded.CheckCircle else Icons.Rounded.AttachFile,
                    contentDescription = null,
                    tint = if (has) Lime else TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    p.fileName ?: "Attach a photo or PDF",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                Text(if (has) "Replace" else "Add", style = MaterialTheme.typography.labelLarge, color = Lime)
            }
        }
    }
}

private val BLOOD = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SafetyStep(state: OnboardingUi, model: OnboardingViewModel, onPair: () -> Unit) {
    val app = LocalContext.current.applicationContext as DashApp
    SectionCard(header = { SectionLabel(text = "Emergency contacts") }) {
        Box(Modifier.padding(top = 8.dp)) { EmergencyContactsEditor(state.contacts, model::setContacts) }
    }
    SectionCard(header = { SectionLabel(text = "Blood group") }) {
        FlowRow(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BLOOD.forEach { b ->
                PillChip(text = b, selected = state.bloodGroup == b, onClick = { model.setBlood(if (state.bloodGroup == b) "" else b) })
            }
        }
    }
    Hint("If a crash is detected you get 30 s to tap I'm OK, then these people get a text with your location. Edit the text and sensitivity in Settings → Crash alerts.")
    SectionCard(header = { SectionLabel(text = "Pair the bike") }) {
        if (app.prefs.bikeMac != null) {
            Text("Paired. The bike connects when you switch it on.", style = MaterialTheme.typography.bodyMedium, color = Lime, modifier = Modifier.padding(top = 8.dp))
        } else {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Hint("Turn the ignition on, then scan. Bluetooth links the cluster for speed, fuel and navigation.")
                ChunkyButton(text = "Save and pair now", onClick = onPair, style = ChunkyStyle.Outline)
            }
        }
    }
}
