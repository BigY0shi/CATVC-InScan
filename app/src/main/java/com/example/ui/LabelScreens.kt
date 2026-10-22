package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.countryatv.core.LabelSpec
import com.example.data.InventoryViewModel
import com.example.label.LabelRenderer
import com.example.label.LabelShare
import com.example.ui.theme.CountryAmber
import com.example.ui.theme.MutedBone

// The label for one item: what will print, at the size set for this phone's printer.
@Composable
fun LabelSheet(viewModel: InventoryViewModel, onShowMessage: (String) -> Unit) {
    val item by viewModel.labelItem.collectAsState()
    val spec by viewModel.labelSpec.collectAsState()
    val unit = item ?: return
    val context = LocalContext.current
    var reprintReason by remember { mutableStateOf("") }
    var showSize by remember { mutableStateOf(false) }
    val rendered = remember(unit, spec) { LabelRenderer.render(viewModel.labelContentFor(unit), spec) }

    if (showSize) LabelSizeDialog(spec, onSave = { viewModel.saveLabelSpec(it); showSize = false }, onDismiss = { showSize = false })

    AlertDialog(
        onDismissRequest = { viewModel.closeLabelSheet() },
        title = { Text("Label for ${viewModel.itemName(unit.id)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rendered.onSuccess { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Label preview",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().background(Color.White).padding(4.dp).testTag("label_preview")
                    )
                }
                rendered.onFailure { Text(it.message.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("label_too_small")) }
                Text("%.0f × %.0f mm, %d dpi".format(spec.widthMm, spec.heightMm, spec.dpi), color = MutedBone, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showSize = true }, modifier = Modifier.testTag("label_change_size")) {
                    Text("Set label size for this phone's printer")
                }
                Text(
                    "No label printer is built into InScan yet. Share the label to the printer maker's app and print it there. " +
                        "Then stick it on the box and scan it: that scan moves a new item to In Stock.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = reprintReason, onValueChange = { reprintReason = it },
                    label = { Text("Replacing a label? Why (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("label_reprint_reason")
                )
            }
        },
        confirmButton = {
            Button(
                enabled = rendered.isSuccess,
                onClick = {
                    val bitmap = rendered.getOrNull() ?: return@Button
                    runCatching { LabelShare.intentFor(context, bitmap, unit.id) }
                        .onSuccess {
                            context.startActivity(it)
                            viewModel.recordLabelShared(unit, reprintReason)
                            viewModel.closeLabelSheet()
                        }
                        .onFailure { onShowMessage("Couldn't save the label image to share it. Free up some space on the phone and try again.") }
                },
                modifier = Modifier.testTag("label_share_button")
            ) { Text("Share to printer app") }
        },
        dismissButton = { TextButton(onClick = { viewModel.closeLabelSheet() }) { Text("Close") } }
    )
}

private data class Preset(val name: String, val spec: LabelSpec)

private val PRESETS = listOf(
    Preset("50 × 30 mm, 203 dpi", LabelSpec(50.0, 30.0, 203)),
    Preset("40 × 30 mm, 203 dpi", LabelSpec(40.0, 30.0, 203)),
    Preset("36 mm tape, 70 mm long, 180 dpi", LabelSpec(70.0, 36.0, 180)),
    Preset("62 × 29 mm, 300 dpi", LabelSpec(62.0, 29.0, 300))
)

@Composable
fun LabelSizeDialog(current: LabelSpec, onSave: (LabelSpec) -> Unit, onDismiss: () -> Unit) {
    var width by remember { mutableStateOf(current.widthMm.toString()) }
    var height by remember { mutableStateOf(current.heightMm.toString()) }
    var dpi by remember { mutableStateOf(current.dpi.toString()) }
    val parsed = run {
        val w = width.toDoubleOrNull(); val h = height.toDoubleOrNull(); val d = dpi.toIntOrNull()
        if (w != null && h != null && d != null && w in 10.0..200.0 && h in 6.0..200.0 && d in 100..600) LabelSpec(w, h, d) else null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Label size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Match the labels loaded in this phone's printer. The resolution is in the printer's specs.", style = MaterialTheme.typography.bodySmall)
                PRESETS.forEach { p ->
                    FilterChip(
                        selected = parsed == p.spec,
                        onClick = { width = p.spec.widthMm.toString(); height = p.spec.heightMm.toString(); dpi = p.spec.dpi.toString() },
                        label = { Text(p.name) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(width, { width = it }, label = { Text("Width mm") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(height, { height = it }, label = { Text("Height mm") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(dpi, { dpi = it }, label = { Text("dpi") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                if (parsed == null) Text("Width 10–200 mm, height 6–200 mm, 100–600 dpi.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Shown when a scanned label's item isn't on this phone or in the shop system yet.
@Composable
fun LabelOnlyCard(content: com.countryatv.core.LabelContent) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("label_only_card"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("From the label — may be out of date", color = CountryAmber, fontWeight = FontWeight.Bold)
        com.countryatv.core.Label.lines(content).forEach { Text(it) }
        Text(
            "This item was logged on another phone that hasn't connected yet. Don't log it again.",
            color = MutedBone, style = MaterialTheme.typography.bodySmall
        )
    }
}
