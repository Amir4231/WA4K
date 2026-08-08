package com.example.a4kwa.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

data class ReframeState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

@Composable
fun ReframingCanvas(
    sourceFile: File,
    modifier: Modifier = Modifier,
    onReframeChanged: (ReframeState) -> Unit = {}
) {
    var reframe by remember { mutableStateOf(ReframeState()) }
    val transformState = remember {
        TransformableState { zoomChange, panChange, _ ->
            val newScale = (reframe.scale * zoomChange).coerceIn(1f, 4f)
            reframe = reframe.copy(
                scale = newScale,
                offsetX = (reframe.offsetX + panChange.x).coerceIn(-100f, 100f),
                offsetY = (reframe.offsetY + panChange.y).coerceIn(-100f, 100f)
            )
            onReframeChanged(reframe)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CropFree, null, Modifier.padding(end = 6.dp), tint = MaterialTheme.colorScheme.primary)
            Text("9:16 Re-Framing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = {
                reframe = ReframeState()
                onReframeChanged(reframe)
            }) {
                Icon(Icons.Filled.Refresh, null, Modifier.padding(end = 4.dp))
                Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Pinch to zoom, drag to pan inside the 9:16 frame", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(158.dp)
                    .height(280.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .clip(RoundedCornerShape(2.dp))
                    .clipToBounds()
                    .transformable(transformState)
            ) {
                AsyncImage(
                    model = sourceFile,
                    contentDescription = "Reframe preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = reframe.scale
                            scaleY = reframe.scale
                            translationX = reframe.offsetX * scaleX
                            translationY = reframe.offsetY * scaleY
                        }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
            )
            {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(158.dp)
                        .height(280.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        .clipToBounds()
                        .transformable(transformState)
                ) {
                    AsyncImage(
                        model = sourceFile,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = reframe.scale
                                scaleY = reframe.scale
                                translationX = reframe.offsetX * scaleX
                                translationY = reframe.offsetY * scaleY
                            }
                    )
                }
            }
        }
    }
}
