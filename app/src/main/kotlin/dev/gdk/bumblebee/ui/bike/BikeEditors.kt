package dev.gdk.bumblebee.ui.bike

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.gdk.bumblebee.bike.AccentChoice
import dev.gdk.bumblebee.bike.BikeVariant
import dev.gdk.bumblebee.bike.EmergencyContact
import dev.gdk.bumblebee.ride.RegNo
import dev.gdk.bumblebee.ride.SosField
import dev.gdk.bumblebee.ride.SosTemplate
import dev.gdk.bumblebee.ui.components.ChunkyButton
import dev.gdk.bumblebee.ui.components.ChunkyStyle
import dev.gdk.bumblebee.ui.components.PillChip
import dev.gdk.bumblebee.ui.theme.Danger
import dev.gdk.bumblebee.ui.theme.Lime
import dev.gdk.bumblebee.ui.theme.OnBlock
import dev.gdk.bumblebee.ui.theme.ShapeMd
import dev.gdk.bumblebee.ui.theme.ShapePill
import dev.gdk.bumblebee.ui.theme.Stroke
import dev.gdk.bumblebee.ui.theme.SurfaceRaised
import dev.gdk.bumblebee.ui.theme.SurfaceSunken
import dev.gdk.bumblebee.ui.theme.TextPrimary
import dev.gdk.bumblebee.ui.theme.TextSecondary

// ---- Bike colour and app accent ----

/** The bike in the chosen paint, with a swatch for each variant below. */
@Composable
fun VariantPicker(selected: BikeVariant, onSelect: (BikeVariant) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            painter = painterResource(selected.image),
            contentDescription = "V-Strom 250 SX in ${selected.label}",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.55f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BikeVariant.entries.forEach { v ->
                Swatch(v.paint, selected = v == selected, onClick = { onSelect(v) }, contentDescription = v.label)
            }
        }
        Text(
            selected.label,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit, contentDescription: String, size: Int = 40) {
    val ring by animateColorAsState(if (selected) TextPrimary else Color.Transparent, label = "ring")
    Box(
        Modifier
            .size(size.dp + 8.dp)
            .border(2.dp, ring, ShapePill)
            .padding(4.dp)
            .clip(ShapePill)
            .background(color)
            .border(1.dp, Stroke, ShapePill)
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            val onDark = color.red * 0.3f + color.green * 0.59f + color.blue * 0.11f < 0.5f
            Icon(Icons.Rounded.Check, contentDescription = null, tint = if (onDark) Color.White else OnBlock, modifier = Modifier.size(18.dp))
        }
    }
}

/** "Match my bike" or one of the readable accents. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(variant: BikeVariant, choice: AccentChoice?, onChoice: (AccentChoice?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatch(variant.accent, selected = choice == null, onClick = { onChoice(null) }, contentDescription = "Match my bike", size = 32)
            Text("Match my bike", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AccentChoice.entries.forEach { a ->
                Swatch(a.color, selected = choice == a, onClick = { onChoice(a) }, contentDescription = a.label, size = 32)
            }
        }
        Text(
            choice?.label?.let { "App colour: $it" } ?: "App colour follows your ${variant.label} bike",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

// ---- Registration number ----

/** Formats as you leave the field ("ka01ab1234" → "KA 01 AB 1234") and flags what doesn't look right. */
@Composable
fun RegNoField(value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    val bad = value.isNotBlank() && !RegNo.isValid(value)
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.uppercase()) },
        label = { Text("Registration no") },
        placeholder = { Text("KA 01 AB 1234") },
        singleLine = true,
        isError = bad,
        supportingText = {
            Text(if (bad) "Looks unusual. Check it, or keep it if it's right." else "On SOS texts, checkpost view and the RC")
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = modifier.fillMaxWidth(),
    )
}

// ---- Emergency contacts ----

/** Up to three people: picked from the phone's contacts or typed in, each removable. */
@Composable
fun EmergencyContactsEditor(contacts: List<EmergencyContact>, onChange: (List<EmergencyContact>) -> Unit) {
    val context = LocalContext.current
    var typing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    val full = contacts.size >= 3
    // Picking a phone row grants read access to just that row; no contacts permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val picked = EmergencyContact(c.getString(0).orEmpty(), c.getString(1).orEmpty())
                    if (picked.number.isNotBlank()) onChange((contacts + picked).distinctBy { cleanNumber(it.number) }.take(3))
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (contacts.isEmpty()) {
            Text(
                "No one yet. Add up to three people who should hear if you crash.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        contacts.forEachIndexed { i, c ->
            Row(
                Modifier.fillMaxWidth().clip(ShapeMd).background(SurfaceRaised).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).background(Lime.copy(alpha = 0.16f), ShapePill), contentAlignment = Alignment.Center) {
                    Text(c.name.firstOrNull()?.uppercase() ?: "${i + 1}", style = MaterialTheme.typography.titleSmall, color = Lime)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(c.name.ifBlank { "Contact ${i + 1}" }, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(c.number, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                IconButton(onClick = { onChange(contacts - c) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${c.name.ifBlank { c.number }}", tint = TextSecondary)
                }
            }
        }
        if (typing) {
            Column(
                Modifier.fillMaxWidth().clip(ShapeMd).background(SurfaceSunken).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val valid = cleanNumber(number).length >= 10
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } },
                    label = { Text("Mobile number") },
                    placeholder = { Text("+91 98765 43210") },
                    singleLine = true,
                    isError = number.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        ChunkyButton(text = "Cancel", onClick = { typing = false }, compact = true, style = ChunkyStyle.Outline)
                    }
                    Box(Modifier.weight(1f)) {
                        ChunkyButton(
                            text = "Add",
                            enabled = valid,
                            onClick = {
                                onChange((contacts + EmergencyContact(name.trim(), number.trim())).distinctBy { cleanNumber(it.number) }.take(3))
                                name = ""
                                number = ""
                                typing = false
                            },
                            compact = true,
                            style = ChunkyStyle.Lime,
                        )
                    }
                }
            }
        } else if (!full) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    ChunkyButton(
                        text = "From contacts",
                        onClick = {
                            val pick = Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
                            runCatching { picker.launch(pick) }.onFailure { typing = true }
                        },
                        compact = true,
                        style = ChunkyStyle.Outline,
                    )
                }
                Box(Modifier.weight(1f)) {
                    ChunkyButton(text = "Type a number", onClick = { typing = true }, compact = true, style = ChunkyStyle.Outline)
                }
            }
        }
        if (full) Text("Three is the limit, so every alert stays quick to send.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

private fun cleanNumber(n: String) = n.filter { it.isDigit() }.takeLast(10)

// ---- Crash text ----

/**
 * The crash text with tap-to-insert fields, a live check that {location} and {time} are in it,
 * and a preview filled with sample values.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SosTemplateEditor(template: String, onSave: (String) -> Unit) {
    var field by remember(template) { mutableStateOf(TextFieldValue(template, TextRange(template.length))) }
    val problems = SosTemplate.problems(field.text)
    val dirty = field.text != template
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            label = { Text("Crash text") },
            isError = problems.isNotEmpty(),
            minLines = 4,
            supportingText = { Text("${field.text.length} / ${SosTemplate.MAX_CHARS}") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Tap to insert at the cursor", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SosField.entries.forEach { f ->
                val present = field.text.contains(f.token)
                PillChip(
                    text = f.label + if (f.required) " *" else "",
                    selected = present,
                    onClick = {
                        val sel = field.selection
                        val insert = (if (sel.start > 0 && !field.text[sel.start - 1].isWhitespace()) " " else "") + f.token
                        val text = field.text.replaceRange(sel.start, sel.end, insert)
                        field = TextFieldValue(text, TextRange(sel.start + insert.length))
                    },
                )
            }
        }
        problems.forEach { p ->
            Text(p.message, style = MaterialTheme.typography.bodySmall, color = Danger)
        }
        Column(
            Modifier.fillMaxWidth().clip(ShapeMd).background(SurfaceSunken).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(SosTemplate.preview(field.text), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(
                "Empty fields drop out, e.g. no blood group means no \"Blood:\".",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ChunkyButton(
                    text = "Use default",
                    onClick = { field = TextFieldValue(SosTemplate.DEFAULT, TextRange(SosTemplate.DEFAULT.length)) },
                    compact = true,
                    style = ChunkyStyle.Outline,
                )
            }
            Box(Modifier.weight(1f)) {
                ChunkyButton(
                    text = if (dirty) "Save text" else "Saved",
                    enabled = dirty && problems.isEmpty(),
                    onClick = { onSave(field.text) },
                    compact = true,
                    style = ChunkyStyle.Lime,
                )
            }
        }
    }
}
