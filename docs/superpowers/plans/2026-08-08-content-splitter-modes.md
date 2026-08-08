# Content Splitter Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three content-splitting modes (Auto, ManualTrim, ManualSegments) to the 4K Status Uploader app.

**Architecture:** `SplitMode` enum drives branching through ViewModel states. `ManualTrim` enters a full-screen trim editor with a draggable range slider. `ManualSegments` uses existing processing but shows results with checkboxes. `SplitModeSelector` appears on IdleScreen and PickedScreen.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, FFmpegKit, Coil

## Global Constraints

- minSdk 24, targetSdk 36, compileSdk `release(36) { minorApiLevel = 1 }`
- No `java.time` (no core-library desugaring)
- Package: `com.example.a4kwa`
- No `org.jetbrains.kotlin.android` plugin
- Theme composable: `_4KWATheme`
- Build: `.\gradlew.bat assembleDebug`, Test: `.\gradlew.bat test`

---
### Task 1: Foundation — SplitMode enum + ProcessedClip.selected

**Files:**
- Create: `app/src/main/java/com/example/a4kwa/model/SplitMode.kt`
- Modify: `app/src/main/java/com/example/a4kwa/model/VideoModels.kt:38-45`

**Interfaces:**
- Produces: `enum class SplitMode { Auto, ManualTrim, ManualSegments }`
- Produces: `ProcessedClip` gains `val selected: Boolean = true`

- [ ] **Step 1: Create SplitMode.kt**

```kotlin
package com.example.a4kwa.model

enum class SplitMode { Auto, ManualTrim, ManualSegments }
```

- [ ] **Step 2: Add `selected` field to ProcessedClip in VideoModels.kt**

Change the existing `ProcessedClip` from:
```kotlin
data class ProcessedClip(
    val file: File,
    val index: Int,
    val startMs: Long,
    val durationMs: Long,
    val totalClips: Int,
    val mediaType: MediaType = MediaType.VIDEO
)
```
To:
```kotlin
data class ProcessedClip(
    val file: File,
    val index: Int,
    val startMs: Long,
    val durationMs: Long,
    val totalClips: Int,
    val mediaType: MediaType = MediaType.VIDEO,
    val selected: Boolean = true
)
```

- [ ] **Step 3: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 2: FfmpegCommandBuilder — custom duration parameter

**Files:**
- Modify: `app/src/main/java/com/example/a4kwa/ffmpeg/FfmpegCommandBuilder.kt` (line 13 signature + line 59 body)
- Modify: `app/src/test/java/com/example/a4kwa/FfmpegCommandBuilderTest.kt`

**Interfaces:**
- Modifies: `buildSegmentCommand` gains parameter `segmentDurationMs: Long = 30_000L`

- [ ] **Step 1: Add `segmentDurationMs` parameter**

In `FfmpegCommandBuilder.kt`, change the function signature (add at end of params):
```kotlin
fun buildSegmentCommand(
    ...
    deblock: Boolean = false,
    segmentDurationMs: Long = 30_000L
): List<String> {
```

Change line 59 from:
```kotlin
add("-t"); add(SEGMENT_DURATION_SECONDS.toString())
```
To:
```kotlin
add("-t"); add((segmentDurationMs / 1000L).toString())
```

- [ ] **Step 2: Add test for custom duration**

In `FfmpegCommandBuilderTest.kt`, add:
```kotlin
@Test
fun buildSegmentCommand_customDuration() {
    val args = FfmpegCommandBuilder.buildSegmentCommand(
        inputPath = "/in/video.mp4",
        outputPath = "/out/clip.mp4",
        startMs = 10_000,
        outputWidth = 1080,
        outputHeight = 1920,
        segmentDurationMs = 15_000
    )
    assertEquals("10", args[args.indexOf("-ss") + 1])
    assertEquals("15", args[args.indexOf("-t") + 1])
}
```

- [ ] **Step 3: Run tests**

Run: `.\gradlew.bat test --tests "com.example.a4kwa.FfmpegCommandBuilderTest"`
Expected: All 16 tests PASS

### Task 3: VideoProcessor.startSingleSegment

**Files:**
- Modify: `app/src/main/java/com/example/a4kwa/ffmpeg/VideoProcessor.kt` (add method after `start()` body ends at line 93)

**Interfaces:**
- Produces: `fun startSingleSegment(inputFile, outputDir, startMs, endMs, outputWidth, outputHeight, rotationDegrees, sourceWidth, sourceHeight, forcePortrait, blurBackground, filter, sharpen, denoise, autoLevels, deblock, listener: Listener)` — processes one segment from `startMs` to `endMs`

- [ ] **Step 1: Add startSingleSegment method**

Add after the closing brace of `start()` (after line 93):

```kotlin
fun startSingleSegment(
    inputFile: File,
    outputDir: File,
    startMs: Long,
    endMs: Long,
    outputWidth: Int,
    outputHeight: Int,
    rotationDegrees: Int,
    sourceWidth: Int = 0,
    sourceHeight: Int = 0,
    forcePortrait: Boolean = true,
    blurBackground: Boolean = false,
    filter: FilterPreset = FilterPreset.NONE,
    sharpen: Boolean = false,
    denoise: Boolean = false,
    autoLevels: Boolean = false,
    deblock: Boolean = false,
    listener: Listener
) {
    cancelled = false
    lastProgress = 0f
    outputDir.mkdirs()
    val outFile = File(outputDir, "clip_1.mp4")
    val durationMs = (endMs - startMs).coerceAtLeast(1L)
    val args = FfmpegCommandBuilder.buildSegmentCommand(
        inputPath = inputFile.absolutePath,
        outputPath = outFile.absolutePath,
        startMs = startMs,
        outputWidth = outputWidth,
        outputHeight = outputHeight,
        rotationDegrees = rotationDegrees,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        forcePortrait = forcePortrait,
        blurBackground = blurBackground,
        filter = filter,
        sharpen = sharpen,
        denoise = denoise,
        autoLevels = autoLevels,
        deblock = deblock,
        segmentDurationMs = durationMs
    )
    val session = FFmpegKit.executeWithArgumentsAsync(
        args.toTypedArray(),
        FFmpegSessionCompleteCallback { completed ->
            if (cancelled) {
                listener.onCancelled()
            } else if (!ReturnCode.isSuccess(completed.returnCode)) {
                val detail = completed.failStackTrace?.take(400) ?: ""
                listener.onError("Failed to process clip. $detail")
            } else {
                if (outFile.length() < 1024L) {
                    listener.onError("Output file is empty")
                } else {
                    val clip = ProcessedClip(
                        file = outFile, index = 0, startMs = startMs,
                        durationMs = durationMs, totalClips = 1,
                        mediaType = com.example.a4kwa.model.MediaType.VIDEO
                    )
                    listener.onComplete(listOf(clip))
                }
            }
        },
        LogCallback { log ->
            parseProgressTime(log.message)?.let { timeSec ->
                reportProgress(timeSec * 1000.0, durationMs, 0, 1, listener)
            }
        },
        StatisticsCallback { stats ->
            val timeUs = stats.time
            if (timeUs > 0L) {
                reportProgress(timeUs / 1000.0, durationMs, 0, 1, listener)
            }
        }
    )
    activeSession = session
}
```

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 4: ShareManager.buildShareSelectedIntent

**Files:**
- Modify: `app/src/main/java/com/example/a4kwa/share/ShareManager.kt`

**Interfaces:**
- Produces: `fun buildShareAllIntent(context, clips: List<ProcessedClip>, fallbackTitle)` that filters to `selected` only

- [ ] **Step 1: Add ProcessedClip overload**

Add import at top:
```kotlin
import com.example.a4kwa.model.ProcessedClip
```

Add method after existing `buildShareAllIntent(files: List<File>, ...)` at line 46:
```kotlin
fun buildShareAllIntent(context: Context, clips: List<ProcessedClip>, fallbackTitle: String): Intent {
    val selectedFiles = clips.filter { it.selected }.map { it.file }
    return buildShareAllIntent(context, selectedFiles, fallbackTitle)
}
```

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 5: SplitModeSelector composable

**Files:**
- Create: `app/src/main/java/com/example/a4kwa/ui/selector/SplitModeSelector.kt`

**Interfaces:**
- Produces: `@Composable fun SplitModeSelector(currentMode: SplitMode, onModeSelected: (SplitMode) -> Unit, modifier: Modifier)`

- [ ] **Step 1: Create SplitModeSelector.kt**

```kotlin
package com.example.a4kwa.ui.selector

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.SplitScreen
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
                            SplitMode.Auto -> Icons.Outlined.SplitScreen
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
```

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL (composable is isolated, no wiring yet)

### Task 6: TrimRangeSlider composable

**Files:**
- Create: `app/src/main/java/com/example/a4kwa/ui/trim/TrimRangeSlider.kt`

**Interfaces:**
- Produces: `@Composable fun TrimRangeSlider(sourceFile: File, durationMs: Long, startMs: Long, endMs: Long, onRangeChanged: (Long, Long) -> Unit, onPreviewSeek: (Long) -> Unit, modifier: Modifier)`

- [ ] **Step 1: Create TrimRangeSlider.kt**

```kotlin
package com.example.a4kwa.ui.trim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.a4kwa.util.formatDurationMs
import java.io.File

private const val MAX_WINDOW_MS = 30_000L

@Composable
fun TrimRangeSlider(
    sourceFile: File,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChanged: (Long, Long) -> Unit,
    onPreviewSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0L) return

    val density = LocalDensity.current
    var sliderWidth by remember { mutableStateOf(IntSize.Zero) }
    val pxPerMs = if (sliderWidth.width > 0) sliderWidth.width.toFloat() / durationMs else 0f

    val startXPx = if (pxPerMs > 0f) (startMs * pxPerMs).coerceIn(0f, sliderWidth.width.toFloat() - 1f) else 0f
    val endXPx = if (pxPerMs > 0f) (endMs * pxPerMs).coerceIn(1f, sliderWidth.width.toFloat()) else 0f

    val selectionColor = Color(0xFF25D366)
    val handleColor = Color.White

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.DarkGray)
                .onSizeChanged { sliderWidth = it }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val tapMs = (offset.x / pxPerMs).toLong().coerceIn(0, durationMs)
                        onPreviewSeek(tapMs)
                    }
                }
        ) {
            AsyncImage(
                model = sourceFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )

            Canvas(Modifier.matchParentSize()) {
                drawRect(
                    color = selectionColor.copy(alpha = 0.35f),
                    topLeft = Offset(startXPx, 0f),
                    size = Size((endXPx - startXPx).coerceAtLeast(0f), size.height)
                )
            }

            if (pxPerMs > 0f) {
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { ((startXPx / density.density) - 14f).dp })
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(handleColor)
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaMs = (dragAmount / pxPerMs).toLong()
                                val window = endMs - startMs
                                val newStart = (startMs + deltaMs).coerceIn(0, durationMs - 1000)
                                val newEnd = (newStart + window).coerceAtMost(newStart + MAX_WINDOW_MS).coerceAtMost(durationMs)
                                onRangeChanged(newStart, newEnd)
                            }
                        }
                )

                Box(
                    modifier = Modifier
                        .offset(x = with(density) { ((endXPx / density.density) - 14f).dp })
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(handleColor)
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                val deltaMs = (dragAmount / pxPerMs).toLong()
                                val newEnd = (endMs + deltaMs).coerceIn(startMs + 1000, (startMs + MAX_WINDOW_MS).coerceAtMost(durationMs))
                                onRangeChanged(startMs, newEnd)
                            }
                        }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = "${formatDurationMs(startMs)} \u2014 ${formatDurationMs(endMs)} (${formatDurationMs(endMs - startMs)})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
```

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 7: TrimEditingScreen composable

**Files:**
- Create: `app/src/main/java/com/example/a4kwa/ui/trim/TrimEditingScreen.kt`

**Interfaces:**
- Consumes: `VideoUploaderViewModel` for trim state + preview state
- Produces: Full-screen composable with video preview (top 70%) + TrimRangeSlider (bottom 30%)

- [ ] **Step 1: Create TrimEditingScreen.kt**

This screen replaces `PickedScreen` for ManualTrim mode. It shows the video preview, a "Select 30s clip" title, and the range slider. The ViewModel provides `video: VideoInfo`, `trimStartMs`, `trimEndMs`, and a callback that moves to Processing.

The screen is a `Box(fillMaxSize)` with:
- Top: full-size video preview (AsyncImage or ProcessedPreview-like)
- Top-left corner: info chips (duration, resolution, size) — same as existing `PickedScreen`
- Bottom: `TrimRangeSlider` + "Process Trimmed Clip" button
- Top bar: Back arrow (resets to Idle), title, confirm icon

Since the video preview already exists in `PickedScreen` as `ProcessedPreview`, extract or reuse the preview + info chips from `PickedScreen`. For this task, keep the preview inline within `TrimEditingScreen`:

```kotlin
package com.example.a4kwa.ui.trim

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.a4kwa.R
import com.example.a4kwa.model.VideoInfo
import com.example.a4kwa.ui.InfoChip
import com.example.a4kwa.util.formatDurationMs
import com.example.a4kwa.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimEditingScreen(
    video: VideoInfo,
    trimStartMs: Long,
    trimEndMs: Long,
    onTrimRangeChanged: (Long, Long) -> Unit,
    onPreviewSeekMs: (Long) -> Unit,
    onProcessTrimmed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select 30s clip", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onProcessTrimmed) {
                        Icon(Icons.Filled.Check, contentDescription = "Confirm trim")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(0.65f).fillMaxWidth()) {
                VideoPreview(video)
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                ) {
                    InfoChip(formatDurationMs(video.durationMs))
                    InfoChip(stringResource(R.string.resolution_label, video.width, video.height))
                    InfoChip(formatFileSize(video.sizeBytes))
                }
            }

            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    TrimRangeSlider(
                        sourceFile = video.file,
                        durationMs = video.durationMs,
                        startMs = trimStartMs,
                        endMs = trimEndMs,
                        onRangeChanged = onTrimRangeChanged,
                        onPreviewSeek = onPreviewSeekMs,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onProcessTrimmed,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Check, null, Modifier.size(20.dp))
                        Text(stringResource(R.string.process_for_whatsapp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPreview(video: VideoInfo) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(video.file.absolutePath) {
        loading = true
        preview = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(video.file.absolutePath)
                    retriever.frameAtTime
                } finally {
                    retriever.release()
                }
            } catch (_: Exception) { null }
        }
        loading = false
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (preview != null) {
            Image(
                bitmap = preview!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = video.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (loading) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 8: SegmentResultsScreen (checkbox variant of ResultsScreen)

**Files:**
- Create: `app/src/main/java/com/example/a4kwa/ui/SegmentResultsScreen.kt`

**Interfaces:**
- Consumes: `List<ProcessedClip>` with `selected` flags
- Produces: Results list with checkboxes + "Share Selected (N)" button

- [ ] **Step 1: Create SegmentResultsScreen.kt**

This is a variant of the existing `ResultsScreen`. Key differences:
- Each `ClipCard` has a `Checkbox` at the leading edge
- Top bar shows "Select clips to share" + "Share Selected (N)" button
- `onToggleClip(filePath)` callback toggles `selected`
- FAB shares only selected clips

```kotlin
package com.example.a4kwa.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.a4kwa.R
import com.example.a4kwa.model.ProcessedClip
import com.example.a4kwa.share.ShareManager

@Composable
fun SegmentResultsScreen(
    clips: List<ProcessedClip>,
    onToggleClip: (String) -> Unit,
    onReset: () -> Unit,
    context: Context
) {
    val selectedCount = clips.count { it.selected }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(pluralStringResource(R.plurals.clips_ready, clips.size, clips.size), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Select clips to keep, then share", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    context.startActivity(ShareManager.buildShareAllIntent(context, clips, context.getString(R.string.share_via)))
                },
                enabled = selectedCount > 0,
                Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Selected ($selectedCount)")
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(clips, key = { it.file.absolutePath }) { clip ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = clip.selected,
                            onCheckedChange = { onToggleClip(clip.file.absolutePath) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.weight(1f)) {
                            ClipCard(clip)
                        }
                        Button(
                            onClick = {
                                context.startActivity(ShareManager.buildShareClipIntent(context, clip.file, context.getString(R.string.share_via)))
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Filled.Share, null, Modifier.size(14.dp))
                            Text("Share", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                item {
                    TextButton(onClick = onReset, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.process_another))
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                context.startActivity(ShareManager.buildShareAllIntent(context, clips, context.getString(R.string.share_via)))
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Filled.Share, stringResource(R.string.share_all)) }
    }
}
```

- [ ] **Step 2: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 9: VideoUploaderViewModel — mode + trim state + new transitions

**Files:**
- Modify: `app/src/main/java/com/example/a4kwa/ui/VideoUploaderViewModel.kt`

**Interfaces:**
- Consumes: `videoProcessor.startSingleSegment` (from Task 3)
- Modifies: `UploaderUiState` sealed interface, adds new states
- Produces: `currentSplitMode`, `trimStartMs`, `trimEndMs`, `onSplitModeChanged`, `onTrimRangeChanged`, `pickSingleMedia` with mode awareness, `processTrimmed`, `toggleClipSelected`

- [ ] **Step 1: Add new UI states to UploaderUiState**

Add after `data class Error` at line 81:

```kotlin
data class TrimEditing(
    val video: VideoInfo,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L
) : UploaderUiState

data class SegmentResults(val clips: List<ProcessedClip>) : UploaderUiState
```

- [ ] **Step 2: Add splitMode, trim state, and new callbacks to VideoUploaderViewModel**

Inside `VideoUploaderViewModel`, add:

```kotlin
private val _splitMode = MutableStateFlow(SplitMode.Auto)
val splitMode: StateFlow<SplitMode> = _splitMode.asStateFlow()

fun setSplitMode(mode: SplitMode) {
    _splitMode.value = mode
}

fun startTrimEditing(video: VideoInfo) {
    val maxEnd = video.durationMs.coerceAtMost(30_000L)
    _uiState.value = UploaderUiState.TrimEditing(video = video, trimStartMs = 0L, trimEndMs = maxEnd)
}

fun onTrimRangeChanged(startMs: Long, endMs: Long) {
    val state = _uiState.value as? UploaderUiState.TrimEditing ?: return
    _uiState.value = state.copy(trimStartMs = startMs, trimEndMs = endMs)
}

fun onPreviewSeekMs(seekMs: Long) {
    // Preview seek handled by TrimRangeSlider composable — placeholder for future video scrub
}

fun processTrimmed() {
    val state = _uiState.value as? UploaderUiState.TrimEditing ?: return
    val video = state.video
    val startMs = state.trimStartMs
    val endMs = state.trimEndMs
    val outDir = File(app.cacheDir, "jobs/${System.currentTimeMillis()}/out")
    processingStartTime = System.currentTimeMillis()

    if (video.mediaType == MediaType.IMAGE) {
        // Images don't need trimming — treat as single-item process
        _uiState.value = UploaderUiState.Processing(0f, 0, 1, presetLabel = "Balanced")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val clip = PhotoProcessor.process(video.file, File(outDir, "processed_1.jpg"), 1080, 1920, true, false, FilterPreset.NONE, false, false, false, false)
                _uiState.value = UploaderUiState.Results(listOf(clip))
            } catch (e: Exception) {
                _uiState.value = UploaderUiState.Error(app.getString(R.string.error_processing))
            }
        }
        return
    }

    _uiState.value = UploaderUiState.Processing(0f, 0, 1, presetLabel = "Ultra HD Status")
    videoProcessor.startSingleSegment(
        inputFile = video.file,
        outputDir = outDir,
        startMs = startMs,
        endMs = endMs,
        outputWidth = 1080,
        outputHeight = 1920,
        rotationDegrees = video.rotationDegrees,
        sourceWidth = video.width,
        sourceHeight = video.height,
        forcePortrait = true,
        blurBackground = false,
        filter = FilterPreset.NONE,
        listener = object : VideoProcessor.Listener {
            override fun onProgress(progress: Float, clipIndex: Int, totalClips: Int) {
                val elapsed = System.currentTimeMillis() - processingStartTime
                val speed = if (elapsed > 0) (progress * (endMs - startMs) / elapsed).toFloat() else 0f
                val eta = if (progress > 0f && speed > 0f) ((1f - progress) * (endMs - startMs) / (speed * 1000f)).toLong() else 0L
                _uiState.value = UploaderUiState.Processing(progress, clipIndex, totalClips, speedMultiplier = speed, etaSeconds = eta, presetLabel = "Ultra HD Status")
            }
            override fun onSegmentComplete(clip: ProcessedClip) {}
            override fun onComplete(clips: List<ProcessedClip>) {
                _uiState.value = if (clips.isEmpty()) UploaderUiState.Error(app.getString(R.string.error_processing)) else UploaderUiState.Results(clips)
            }
            override fun onCancelled() {
                _uiState.value = UploaderUiState.TrimEditing(video, startMs, endMs)
            }
            override fun onError(message: String) { _uiState.value = UploaderUiState.Error(message) }
        }
    )
}

fun toggleClipSelected(filePath: String) {
    val state = _uiState.value as? UploaderUiState.SegmentResults ?: return
    val updated = state.clips.map { if (it.file.absolutePath == filePath) it.copy(selected = !it.selected) else it }
    _uiState.value = UploaderUiState.SegmentResults(updated)
}
```

- [ ] **Step 3: Import SplitMode in ViewModel**

Add import:
```kotlin
import com.example.a4kwa.model.SplitMode
```

- [ ] **Step 4: Modify pickSingleMedia to route based on mode**

Change `pickSingleMedia` (line 161-182) so it branches based on `_splitMode.value`. After the video probe, check the mode:

Replace lines 176-177:
```kotlin
                _uiState.value = UploaderUiState.Picked(video)
```
With:
```kotlin
                when (_splitMode.value) {
                    SplitMode.Auto, SplitMode.ManualSegments -> _uiState.value = UploaderUiState.Picked(video)
                    SplitMode.ManualTrim -> startTrimEditing(video)
                }
```

- [ ] **Step 5: Modify processSingle to route ManualSegments to SegmentResults**

In `processSingle()` (line 229-232), wrap the `onComplete` listener to check mode. In the existing `runProcess` method (line 357), change the `onComplete` block from:
```kotlin
override fun onComplete(clips: List<ProcessedClip>) {
    _uiState.value = if (clips.isEmpty()) UploaderUiState.Error(app.getString(R.string.error_processing)) else UploaderUiState.Results(clips)
}
```
To:
```kotlin
override fun onComplete(clips: List<ProcessedClip>) {
    if (clips.isEmpty()) {
        _uiState.value = UploaderUiState.Error(app.getString(R.string.error_processing))
    } else if (_splitMode.value == SplitMode.ManualSegments) {
        _uiState.value = UploaderUiState.SegmentResults(clips)
    } else {
        _uiState.value = UploaderUiState.Results(clips)
    }
}
```

- [ ] **Step 6: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 10: StatusUploaderScreen — wire everything together

**Files:**
- Modify: `app/src/main/java/com/example/a4kwa/ui/StatusUploaderScreen.kt`

**Interfaces:**
- Consumes: All new ViewModel states and callbacks from Task 9
- Routes: `TrimEditing` state → `TrimEditingScreen`, `SegmentResults` state → `SegmentResultsScreen`
- Adds: `SplitModeSelector` to IdleScreen and PickedScreen bottom sheet

- [ ] **Step 1: Add import for SplitMode**

```kotlin
import com.example.a4kwa.model.SplitMode
```

- [ ] **Step 2: Add TrimEditing and SegmentResults cases to the when block**

After line 136:
```kotlin
is UploaderUiState.BatchResults -> BatchResultsScreen(current, context, viewModel::reset)
```
Add:
```kotlin
is UploaderUiState.TrimEditing -> TrimEditingScreenWrapper(current, viewModel, context)
is UploaderUiState.SegmentResults -> SegmentResultsScreen(
    clips = current.clips,
    onToggleClip = viewModel::toggleClipSelected,
    onReset = viewModel::reset,
    context = context
)
```

- [ ] **Step 3: Add TrimEditingScreenWrapper composable**

Before the last `}` of StatusUploaderScreen or as a private function:

```kotlin
@Composable
private fun TrimEditingScreenWrapper(
    state: UploaderUiState.TrimEditing,
    viewModel: VideoUploaderViewModel,
    context: Context
) {
    TrimEditingScreen(
        video = state.video,
        trimStartMs = state.trimStartMs,
        trimEndMs = state.trimEndMs,
        onTrimRangeChanged = viewModel::onTrimRangeChanged,
        onPreviewSeekMs = viewModel::onPreviewSeekMs,
        onProcessTrimmed = viewModel::processTrimmed,
        onBack = viewModel::reset
    )
}
```

- [ ] **Step 4: Add SplitModeSelector to IdleScreen**

Modify `IdleScreen` signature to accept `splitMode` and `onSplitModeChanged`. Current signature (line 142):
```kotlin
@Composable private fun IdleScreen(pickSingle: () -> Unit, pickMultiple: () -> Unit) {
```

Change to:
```kotlin
@Composable private fun IdleScreen(
    pickSingle: () -> Unit,
    pickMultiple: () -> Unit,
    splitMode: SplitMode,
    onSplitModeChanged: (SplitMode) -> Unit
) {
```

Inside the IdleScreen Column, after the tagline `Text(stringResource(R.string.app_tagline), ...)` (line 149) and before `Spacer(Modifier.height(44.dp))` (line 150), insert:

```kotlin
Spacer(Modifier.height(16.dp))
SplitModeSelector(
    currentMode = splitMode,
    onModeSelected = onSplitModeChanged,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
)
Spacer(Modifier.height(8.dp))
```

Also update the `split_hint` text to reflect the current mode. Replace the static text at line 155:
```kotlin
Text(stringResource(R.string.split_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
```
With:
```kotlin
Text(
    text = when (splitMode) {
        SplitMode.Auto -> stringResource(R.string.split_hint)
        SplitMode.ManualTrim -> "Choose a 30-second clip from your video"
        SplitMode.ManualSegments -> "Auto-split, then pick which clips to keep"
    },
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center
)
```

- [ ] **Step 5: Update IdleScreen call site**

In `StatusUploaderScreen`, update the `IdleScreen` call (line 130) to pass the mode state:

```kotlin
is UploaderUiState.Idle -> {
    val mode by viewModel.splitMode.collectAsState()
    IdleScreen(pickSingle, pickMultiple, mode, viewModel::setSplitMode)
}
```

- [ ] **Step 6: Add SplitModeSelector to PickedScreen bottom sheet**

Modify the `PickedScreen` signature to accept `splitMode` and `onSplitModeChanged`. At line 165, add parameters:
```kotlin
@Composable private fun PickedScreen(
    picked: UploaderUiState.Picked, onResChange: (OutputResolution) -> Unit, onProcess: () -> Unit, pickSingle: () -> Unit,
    togglePortrait: () -> Unit, toggleBlur: () -> Unit, toggleSharpen: () -> Unit, toggleDenoise: () -> Unit,
    toggleAutoLevels: () -> Unit, toggleDeblock: () -> Unit, setFilter: (FilterPreset) -> Unit,
    setCustomW: (String) -> Unit, setCustomH: (String) -> Unit, setPreset: (PresetConfig) -> Unit,
    viewModel: VideoUploaderViewModel,
    splitMode: SplitMode,
    onSplitModeChanged: (SplitMode) -> Unit
) {
```

Inside the `ModalBottomSheet` content Column, add after `Text(picked.video.displayName, ...)` (after line 208):

```kotlin
Spacer(Modifier.height(8.dp))
SplitModeSelector(
    currentMode = splitMode,
    onModeSelected = onSplitModeChanged,
    modifier = Modifier.fillMaxWidth()
)
Spacer(Modifier.height(14.dp))
```

- [ ] **Step 7: Update PickedScreen call site**

In `StatusUploaderScreen` line 132, add the new params:
```kotlin
is UploaderUiState.Picked -> {
    val mode by viewModel.splitMode.collectAsState()
    PickedScreen(current, viewModel::setResolution, viewModel::processSingle, pickSingle, viewModel::toggleForcePortrait, viewModel::toggleBlurBackground, viewModel::toggleSharpen, viewModel::toggleDenoise, viewModel::toggleAutoLevels, viewModel::toggleDeblock, viewModel::setFilter, viewModel::setCustomWidthText, viewModel::setCustomHeightText, viewModel::setPreset, viewModel, mode, viewModel::setSplitMode)
}
```

- [ ] **Step 8: Add import for the new screens**

At the top of StatusUploaderScreen.kt:
```kotlin
import com.example.a4kwa.ui.trim.TrimEditingScreen
```

- [ ] **Step 9: Build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

### Task 11: Final integration check — build + test

**Files:**
- None new (verification only)

- [ ] **Step 1: Run full build**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run unit tests**

Run: `.\gradlew.bat test`
Expected: All tests PASS

- [ ] **Step 3: Run lint**

Run: `.\gradlew.bat lint`
Expected: No new lint errors introduced by the changes.
