package com.example.a4kwa.ui.selector

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.a4kwa.model.SplitMode

@Composable
fun SplitModeSelector(
    currentMode: SplitMode,
    onModeSelected: (SplitMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = SplitMode.entries.toList()
    SingleChoiceSegmentedButtonRow(modifier) {
        modes.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = currentMode == mode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                icon = {
                    Icon(
                        imageVector = when (mode) {
                            SplitMode.Auto -> Icons.Outlined.Splitscreen
                            SplitMode.ManualTrim -> Icons.Outlined.ContentCut
                            SplitMode.ManualSegments -> Icons.Outlined.Checklist
                        },
                        contentDescription = null
                    )
                }
            ) {
                Text(
                    text = when (mode) {
                        SplitMode.Auto -> "Auto Split"
                        SplitMode.ManualTrim -> "Trim Clip"
                        SplitMode.ManualSegments -> "Pick Segments"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
