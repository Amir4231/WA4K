package com.example.a4kwa.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.a4kwa.domain.usecase.SplitSegment
import com.example.a4kwa.util.formatDurationMs
import java.io.File

@Composable
fun TimelineStrip(
    segments: List<SplitSegment>,
    sourceFile: File,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    onSegmentSelected: (Int) -> Unit = {},
    deselectedIndices: Set<Int> = emptySet(),
    onToggleDeselect: ((Int) -> Unit)? = null
) {
    val isMultiSelect = onToggleDeselect != null
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(if (isMultiSelect) "Tap to deselect" else "${segments.size} clips", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            segments.forEach { segment ->
                val sel = if (isMultiSelect) segment.index !in deselectedIndices else segment.index == selectedIndex
                SegmentCard(
                    segment = segment,
                    sourceFile = sourceFile,
                    isSelected = sel,
                    isDeselected = isMultiSelect && segment.index in deselectedIndices,
                    modifier = Modifier.width(130.dp),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isMultiSelect) onToggleDeselect!!(segment.index) else onSegmentSelected(segment.index)
                    }
                )
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun SegmentCard(
    segment: SplitSegment,
    sourceFile: File,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDeselected: Boolean = false
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isDeselected -> Color.Red.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val overlayAlpha = if (isDeselected) 0.5f else 0f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(if (isDeselected) 1.dp else 2.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = sourceFile,
                contentDescription = "Clip ${segment.index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(72.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Column {
                Text(
                    "${formatDurationMs(segment.startMs)} - ${formatDurationMs(segment.startMs + segment.durationMs)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Clip ${segment.index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
