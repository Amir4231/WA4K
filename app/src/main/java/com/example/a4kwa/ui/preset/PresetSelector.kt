package com.example.a4kwa.ui.preset

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.a4kwa.domain.usecase.BitrateEstimate
import com.example.a4kwa.domain.usecase.PresetConfig

@Composable
fun PresetSelector(
    selectedPreset: PresetConfig,
    estimate: BitrateEstimate,
    onPresetSelected: (PresetConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Encoding Preset", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetConfig.ALL.forEach { preset ->
                ElevatedFilterChip(
                    selected = preset == selectedPreset,
                    onClick = { onPresetSelected(preset) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (preset) {
                                    PresetConfig.ULTRA_HD -> Icons.Filled.Hd
                                    PresetConfig.BALANCED -> Icons.Filled.NetworkCheck
                                    PresetConfig.FAST_EXPORT -> Icons.Filled.Speed
                                    else -> Icons.Filled.NetworkCheck
                                },
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(preset.label, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Estimated size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${"%.1f".format(estimate.estimatedFileSizeMB)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (estimate.exceedsWhatsAppLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            val progress = (estimate.estimatedFileSizeMB / BitrateEstimate.WHATSAPP_LIMIT_MB).coerceIn(0f, 1.5f)
            val barColor by animateColorAsState(
                if (estimate.exceedsWhatsAppLimit) MaterialTheme.colorScheme.error
                else if (progress > 0.7f) Color(0xFFFFA726)
                else MaterialTheme.colorScheme.primary
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0 MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${BitrateEstimate.WHATSAPP_LIMIT_MB.toInt()} MB limit", style = MaterialTheme.typography.labelSmall, color = if (estimate.exceedsWhatsAppLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (estimate.exceedsWhatsAppLimit) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "File exceeds WhatsApp limit. Choose a lower preset or shorter duration.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
