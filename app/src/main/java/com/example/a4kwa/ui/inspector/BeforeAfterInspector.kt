package com.example.a4kwa.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

@Composable
fun BeforeAfterInspector(
    sourceFile: File,
    processedFile: File?,
    modifier: Modifier = Modifier
) {
    var splitRatio by remember { mutableFloatStateOf(0.5f) }
    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Compare, null, Modifier.padding(end = 6.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Quality Inspector", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("Drag to compare", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .clipToBounds()
        ) {
            Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
                if (processedFile != null) {
                    AsyncImage(model = processedFile, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset((splitRatio * 1000).roundToInt(), 0) }
                    .clipToBounds()
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AsyncImage(model = sourceFile, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset((splitRatio * 1000).roundToInt() - 2, 0) }
                    .width(4.dp)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            val parentWidth = size.width.toFloat()
                            splitRatio = (splitRatio + dragAmount / parentWidth).coerceIn(0.05f, 0.95f)
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(6.dp)
                ) {
                    Icon(Icons.Filled.Compare, null, Modifier, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text("Original", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
            Text("Processed", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}
