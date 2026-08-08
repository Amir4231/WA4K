package com.example.a4kwa.ui.trim

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.a4kwa.model.VideoInfo
import com.example.a4kwa.util.formatDurationMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MAX_WINDOW_MS = 30_000L
private const val FRAME_COUNT = 10

@Composable
fun TrimEditingScreen(
    video: VideoInfo,
    trimStartMs: Long,
    trimEndMs: Long,
    onTrimRangeChanged: (Long, Long) -> Unit,
    onProcessTrimmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var timelineWidth by remember { mutableIntStateOf(0) }

    val currentTrimStartMs by rememberUpdatedState(trimStartMs)
    val currentTrimEndMs by rememberUpdatedState(trimEndMs)
    val currentDurationMs by rememberUpdatedState(video.durationMs)

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(video.file.toURI().toString()))
            prepare()
        }
    }

    LaunchedEffect(trimStartMs, trimEndMs) {
        player.seekTo(trimStartMs)
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val frameBitmaps = remember(video.file.absolutePath) {
        mutableStateOf<List<Bitmap?>>(List(FRAME_COUNT) { null })
    }
    var frameLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(video.file.absolutePath) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.file.absolutePath)
                val bitmaps = (0 until FRAME_COUNT).map { i ->
                    val timeUs = (video.durationMs * i / (FRAME_COUNT - 1).coerceAtLeast(1)) * 1000L
                    try {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (_: Exception) { null }
                }
                frameBitmaps.value = bitmaps
                retriever.release()
            } catch (_: Exception) {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
        frameLoaded = true
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(context).apply { this.player = player; useController = false } },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f)
                        )
                    )
                )
        )

        FilledIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            )
        ) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(26.dp))
        }

        FilledIconButton(
            onClick = onProcessTrimmed,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF25D366)
            )
        ) {
            Icon(Icons.Filled.Check, "Done", tint = Color.White, modifier = Modifier.size(26.dp))
        }

        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(110.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 0.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(trimStartMs),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDurationMs(trimEndMs),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp)
                        .onSizeChanged { timelineWidth = it.width }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                    ) {
                        if (frameLoaded) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                frameBitmaps.value.forEachIndexed { i, bmp ->
                                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                                        bmp?.let {
                                            Image(
                                                bitmap = it.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (timelineWidth > 0) {
                        val pxPerMs = timelineWidth.toFloat() / video.durationMs
                        val startX = (trimStartMs * pxPerMs).coerceIn(0f, timelineWidth.toFloat())
                        val endX = (trimEndMs * pxPerMs).coerceIn(0f, timelineWidth.toFloat())

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (startX > 0f) drawRect(Color.Black.copy(alpha = 0.55f), Offset.Zero, Size(startX, size.height))
                            if (endX < size.width) drawRect(Color.Black.copy(alpha = 0.55f), Offset(endX, 0f), Size(size.width - endX, size.height))
                            drawRect(Color.White.copy(alpha = 0.18f), Offset(startX, 0f), Size(endX - startX, size.height))
                            val bc = Color(0xFF25D366)
                            drawLine(bc, Offset(startX, 0f), Offset(startX, size.height), 2.dp.toPx())
                            drawLine(bc, Offset(endX, 0f), Offset(endX, size.height), 2.dp.toPx())
                        }

                        val selectionWidthPx = endX - startX
                        if (selectionWidthPx > 4f) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(x = with(density) { (startX / density.density).dp.roundToPx() }, y = 0) }
                                    .width(with(density) { (selectionWidthPx / density.density).dp })
                                    .height(48.dp)
                                    .pointerInput(timelineWidth) {
                                        detectHorizontalDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (pxPerMs > 0f) {
                                                val deltaMs = (dragAmount / pxPerMs).toLong()
                                                val window = currentTrimEndMs - currentTrimStartMs
                                                val ns = (currentTrimStartMs + deltaMs).coerceIn(0, currentDurationMs - window)
                                                val ne = ns + window
                                                onTrimRangeChanged(ns, ne)
                                            }
                                        }
                                    }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(x = with(density) { ((startX / density.density) - 18f).dp.roundToPx() }, y = 0) }
                                .width(36.dp)
                                .height(48.dp)
                                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                .pointerInput(timelineWidth) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        if (pxPerMs > 0f) {
                                            val deltaMs = (dragAmount / pxPerMs).toLong()
                                            val window = currentTrimEndMs - currentTrimStartMs
                                            val ns = (currentTrimStartMs + deltaMs).coerceIn(0, (currentDurationMs - 1000).coerceAtLeast(0))
                                            val ne = (ns + window).coerceAtMost(ns + MAX_WINDOW_MS).coerceAtMost(currentDurationMs)
                                            onTrimRangeChanged(ns, ne)
                                        }
                                    }
                                }
                        )

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(x = with(density) { ((endX / density.density) - 18f).dp.roundToPx() }, y = 0) }
                                .width(36.dp)
                                .height(48.dp)
                                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                .pointerInput(timelineWidth) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        if (pxPerMs > 0f) {
                                            val ne = (currentTrimEndMs + (dragAmount / pxPerMs).toLong()).coerceIn(
                                                currentTrimStartMs + 1000,
                                                (currentTrimStartMs + MAX_WINDOW_MS).coerceAtMost(currentDurationMs)
                                            )
                                            onTrimRangeChanged(currentTrimStartMs, ne)
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}
