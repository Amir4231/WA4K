package com.example.a4kwa.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.a4kwa.R
import com.example.a4kwa.domain.usecase.BitrateEstimate
import com.example.a4kwa.domain.usecase.CalculateTargetBitrateUseCase
import com.example.a4kwa.domain.usecase.PresetConfig
import com.example.a4kwa.domain.usecase.SplitVideoUseCase
import com.example.a4kwa.ffmpeg.PhotoProcessor
import com.example.a4kwa.model.FilterPreset
import com.example.a4kwa.model.MediaType
import com.example.a4kwa.model.OutputResolution
import com.example.a4kwa.model.ProcessedClip
import com.example.a4kwa.model.SplitMode
import com.example.a4kwa.share.ShareManager
import com.example.a4kwa.ui.selector.SplitModeSelector
import com.example.a4kwa.ui.trim.TrimEditingScreen
import com.example.a4kwa.ui.canvas.ReframingCanvas
import com.example.a4kwa.ui.inspector.BeforeAfterInspector
import com.example.a4kwa.ui.preset.PresetSelector
import com.example.a4kwa.ui.timeline.TimelineStrip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import com.example.a4kwa.util.formatDurationMs
import com.example.a4kwa.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun StatusUploaderScreen(viewModel: VideoUploaderViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val photoPickerSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::pickSingleMedia) }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)) { uris -> if (uris.isNotEmpty()) viewModel.addMultipleMedia(uris) }
    val fbSingle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::pickSingleMedia) }
    val fbMulti = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) viewModel.addMultipleMedia(uris) }
    val pickSingle = { if (photoPickerSupported) singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) else fbSingle.launch(arrayOf("image/*", "video/*")) }
    val pickMultiple = { if (photoPickerSupported) multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) else fbMulti.launch(arrayOf("image/*", "video/*")) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val current = state
        when (current) {
            is UploaderUiState.Idle -> {
                val mode by viewModel.splitMode.collectAsState()
                IdleScreen(pickSingle, pickMultiple, mode, viewModel::setSplitMode)
            }
            is UploaderUiState.Loading -> LoadingScreen(current.message)
            is UploaderUiState.Picked -> {
                val mode by viewModel.splitMode.collectAsState()
                PickedScreen(current, viewModel::setResolution, viewModel::processSingle, pickSingle, viewModel::toggleForcePortrait, viewModel::toggleBlurBackground, viewModel::toggleSharpen, viewModel::toggleDenoise, viewModel::toggleAutoLevels, viewModel::toggleDeblock, viewModel::setFilter, viewModel::setCustomWidthText, viewModel::setCustomHeightText, viewModel::setPreset, viewModel, mode, viewModel::setSplitMode, onStartTrim = { (viewModel.uiState.value as? UploaderUiState.Picked)?.let { viewModel.startTrimEditing(it) } })
            }
            is UploaderUiState.Queue -> QueueScreen(current, pickMultiple, viewModel::processBatch, viewModel::removeFromQueue, viewModel::setQueueItemResolution, viewModel::toggleQueueItemPortrait, viewModel::toggleQueueItemBlur, viewModel::setQueueItemFilter, viewModel::moveQueueItem, viewModel::setQueueItemPreset)
            is UploaderUiState.Processing -> ProcessingScreen(current, viewModel::cancelProcessing)
            is UploaderUiState.Results -> ResultsScreen(current, context, viewModel::reset)
            is UploaderUiState.BatchResults -> BatchResultsScreen(current, context, viewModel::reset)
            is UploaderUiState.Error -> ErrorScreen(current.message, viewModel::reset)
            is UploaderUiState.TrimEditing -> {
                TrimEditingScreen(
                    video = current.video,
                    trimStartMs = current.trimStartMs,
                    trimEndMs = current.trimEndMs,
                    onTrimRangeChanged = viewModel::onTrimRangeChanged,
                    onProcessTrimmed = viewModel::processTrimmed,
                    onBack = viewModel::backToPicked
                )
            }
            is UploaderUiState.SegmentResults -> SegmentResultsScreen(
                clips = current.clips,
                onToggleClip = viewModel::toggleClipSelected,
                onReset = viewModel::reset,
                context = context
            )
        }
    }
}

@Composable private fun IdleScreen(
    pickSingle: () -> Unit,
    pickMultiple: () -> Unit,
    splitMode: SplitMode,
    onSplitModeChanged: (SplitMode) -> Unit
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp)) {
            Box(Modifier.size(110.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Icon(Icons.Filled.VideoLibrary, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            SplitModeSelector(
                currentMode = splitMode,
                onModeSelected = onSplitModeChanged,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (splitMode) {
                    SplitMode.Auto -> stringResource(R.string.split_hint)
                    SplitMode.ManualTrim -> "Choose a 30-second clip from your video"
                    SplitMode.ManualSegments -> "Auto-split, then pick which clips to keep"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = pickSingle, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Filled.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(when (splitMode) { SplitMode.Auto -> "Single Item"; SplitMode.ManualTrim -> "Pick Video to Trim"; SplitMode.ManualSegments -> "Pick Video" }) }
            Spacer(Modifier.height(12.dp))
            if (splitMode == SplitMode.Auto) {
                OutlinedButton(onClick = pickMultiple, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) { Text("Queue Multiple") }
            }
            Spacer(Modifier.height(16.dp))
            Text("Built by amirdevs.org", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable private fun LoadingScreen(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(20.dp)); Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PickedScreen(
    picked: UploaderUiState.Picked, onResChange: (OutputResolution) -> Unit, onProcess: () -> Unit, pickSingle: () -> Unit,
    togglePortrait: () -> Unit, toggleBlur: () -> Unit, toggleSharpen: () -> Unit, toggleDenoise: () -> Unit,
    toggleAutoLevels: () -> Unit, toggleDeblock: () -> Unit, setFilter: (FilterPreset) -> Unit,
    setCustomW: (String) -> Unit, setCustomH: (String) -> Unit, setPreset: (PresetConfig) -> Unit,
    viewModel: VideoUploaderViewModel,
    splitMode: SplitMode,
    onSplitModeChanged: (SplitMode) -> Unit,
    onStartTrim: () -> Unit
) {
    var showSheet by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    val isLandscape = picked.video.width >= picked.video.height
    val keepOriginal = !picked.forcePortrait && isLandscape
    val outputW = if (picked.resolution == OutputResolution.CUSTOM) picked.customWidthText.toIntOrNull()?.coerceIn(1, 7680) ?: 1080 else picked.resolution.width
    val outputH = if (picked.resolution == OutputResolution.CUSTOM) picked.customHeightText.toIntOrNull()?.coerceIn(1, 7680) ?: 1920 else picked.resolution.height
    val dispW = if (keepOriginal) outputH else outputW; val dispH = if (keepOriginal) outputW else outputH
    val splitter = remember { SplitVideoUseCase() }
    val segments = remember(picked.video.durationMs) { splitter.calculateSegments(picked.video.durationMs) }
    val estimate = remember(picked.preset, picked.video.durationMs) { viewModel.getSizeEstimate(picked.video, picked.preset) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (picked.video.mediaType == MediaType.VIDEO) {
            VideoBackground(picked.video.file)
        } else {
            ProcessedPreview(picked, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.align(Alignment.TopStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (picked.video.mediaType == MediaType.VIDEO) InfoChip(formatDurationMs(picked.video.durationMs))
            InfoChip(stringResource(R.string.resolution_label, picked.video.width, picked.video.height))
            InfoChip(formatFileSize(picked.video.sizeBytes))
        }
        AnimatedVisibility(visible = !showSheet, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp, start = 16.dp, end = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$dispW x $dispH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(12.dp))
                val isTrimMode = splitMode == SplitMode.ManualTrim
                Button(onClick = if (isTrimMode) {{ showSheet = false; onStartTrim() }} else onProcess, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (isTrimMode) "Next: Trim Clip" else stringResource(R.string.process_for_whatsapp)) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = pickSingle, Modifier.weight(1f)) { Text(stringResource(R.string.choose_different_video), color = Color.White.copy(alpha = 0.9f)) }; TextButton(onClick = { showSheet = true }, Modifier.weight(1f)) { Text("Settings", color = Color.White.copy(alpha = 0.9f)) } }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 36.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(picked.video.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                SplitModeSelector(
                    currentMode = splitMode,
                    onModeSelected = onSplitModeChanged,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                Text(stringResource(R.string.output_resolution), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp)); Text("Output: $dispW x $dispH", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { OutputResolution.entries.forEachIndexed { i, o -> SegmentedButton(o == picked.resolution, { onResChange(o) }, shape = SegmentedButtonDefaults.itemShape(i, OutputResolution.entries.size)) { Text(o.label) } } }
                if (picked.resolution == OutputResolution.CUSTOM) { Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(picked.customWidthText, { setCustomW(it) }, Modifier.weight(1f), label = { Text("Width") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); Text("x", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)); OutlinedTextField(picked.customHeightText, { setCustomH(it) }, Modifier.weight(1f), label = { Text("Height") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } }
                if (picked.video.mediaType == MediaType.VIDEO && picked.video.segmentCount > 1) { Text(pluralStringResource(R.plurals.will_be_split, picked.video.segmentCount, picked.video.segmentCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }

                Spacer(Modifier.height(12.dp))
                PresetSelector(selectedPreset = picked.preset, estimate = estimate, onPresetSelected = setPreset)

                Spacer(Modifier.height(12.dp))
                ChoiceRow("Fit to Portrait (9:16)", "Zooms to fill vertical frame", picked.forcePortrait, togglePortrait)
                ChoiceRow("Blurred Background", "Blurred fill for empty areas", picked.blurBackground, toggleBlur)
                ChoiceRow("Sharpen", "Edge detail enhancement", picked.sharpen, toggleSharpen)
                ChoiceRow("Denoise", "Noise reduction", picked.denoise, toggleDenoise)
                ChoiceRow("Auto Levels", "Full dynamic range stretch", picked.autoLevels, toggleAutoLevels)
                ChoiceRow("Deblock", "Smooth compression artifacts", picked.deblock, toggleDeblock)
                Spacer(Modifier.height(6.dp)); FilterDropdown(picked.filter, setFilter)

                if (picked.video.mediaType == MediaType.VIDEO && segments.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    if (splitMode == SplitMode.ManualSegments) {
                        TimelineStrip(
                            segments = segments,
                            sourceFile = picked.video.file,
                            deselectedIndices = picked.deselectedSegmentIndices,
                            onToggleDeselect = viewModel::togglePickedSegment
                        )
                    } else {
                        TimelineStrip(segments = segments, sourceFile = picked.video.file)
                    }
                }

                if (picked.video.mediaType == MediaType.IMAGE) {
                    Spacer(Modifier.height(12.dp))
                    ReframingCanvas(sourceFile = picked.video.file)
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = { showSheet = false; if (splitMode == SplitMode.ManualTrim) onStartTrim() else onProcess() }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Filled.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (splitMode == SplitMode.ManualTrim) "Next: Trim Clip" else stringResource(R.string.process_for_whatsapp)) }
            }
        }
    }
}

@Composable private fun ChoiceRow(label: String, desc: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) { Switch(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.padding(top = 2.dp)); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.bodyMedium); Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FilterDropdown(current: FilterPreset, onSelect: (FilterPreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) { OutlinedTextField(value = current.label, onValueChange = {}, readOnly = true, label = { Text("Filter") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true); ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { FilterPreset.entries.forEach { f -> DropdownMenuItem(text = { Text(f.label) }, onClick = { onSelect(f); expanded = false }) } } }
}

@Composable private fun QueueScreen(state: UploaderUiState.Queue, pickMultiple: () -> Unit, onProcessAll: () -> Unit, onRemove: (String) -> Unit, onSetRes: (String, OutputResolution) -> Unit, onTogglePortrait: (String) -> Unit, onToggleBlur: (String) -> Unit, onSetFilter: (String, FilterPreset) -> Unit, onMove: (Int, Int) -> Unit, onSetPreset: (String, PresetConfig) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("${state.items.size} items", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Button(onClick = onProcessAll, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Process All") } }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) { itemsIndexed(state.items) { i, item -> QueueItemCard(i, item, state.items.size, onRemove, onSetRes, onTogglePortrait, onToggleBlur, onSetFilter, onMove, onSetPreset) }; item { OutlinedButton(onClick = pickMultiple, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add More") } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun QueueItemCard(index: Int, item: QueuedItem, total: Int, onRemove: (String) -> Unit, onSetRes: (String, OutputResolution) -> Unit, onTogglePortrait: (String) -> Unit, onToggleBlur: (String) -> Unit, onSetFilter: (String, FilterPreset) -> Unit, onMove: (Int, Int) -> Unit, onSetPreset: (String, PresetConfig) -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(10.dp)) { AsyncImage(model = item.video.file, null, contentScale = ContentScale.Crop, modifier = Modifier.width(72.dp).height(128.dp).clip(RoundedCornerShape(12.dp))); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(item.video.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); if (item.video.mediaType == MediaType.VIDEO) Text(stringResource(R.string.duration_label, formatDurationMs(item.video.durationMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(stringResource(R.string.size_label, formatFileSize(item.video.sizeBytes)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { if (index > 0) IconButton(onClick = { onMove(index, index - 1) }, Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowUpward, "Up", Modifier.size(18.dp)) }; if (index < total - 1) IconButton(onClick = { onMove(index, index + 1) }, Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowDownward, "Down", Modifier.size(18.dp)) }; IconButton(onClick = { onRemove(item.id) }, Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Remove", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) } }; TextButton(onClick = { showSettings = !showSettings }, contentPadding = PaddingValues(4.dp)) { Text(if (showSettings) "Hide" else "Settings", style = MaterialTheme.typography.labelMedium) } } }
        AnimatedVisibility(visible = showSettings) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { OutputResolution.entries.forEachIndexed { i, o -> SegmentedButton(o == item.resolution, { onSetRes(item.id, o) }, shape = SegmentedButtonDefaults.itemShape(i, OutputResolution.entries.size)) { Text(o.label, style = MaterialTheme.typography.labelSmall) } } }
                ChoiceRow("Fit Portrait", "Zoom to fill", item.forcePortrait) { onTogglePortrait(item.id) }
                ChoiceRow("Blurred BG", "Blurred fill", item.blurBackground) { onToggleBlur(item.id) }
                var fexp by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = fexp, onExpandedChange = { fexp = it }) { OutlinedTextField(value = item.filter.label, onValueChange = {}, readOnly = true, label = { Text("Filter") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fexp) }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true); ExposedDropdownMenu(expanded = fexp, onDismissRequest = { fexp = false }) { FilterPreset.entries.forEach { f -> DropdownMenuItem(text = { Text(f.label) }, onClick = { onSetFilter(item.id, f); fexp = false }) } } }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PresetConfig.ALL.forEach { p ->
                        TextButton(onClick = { onSetPreset(item.id, p) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.textButtonColors(contentColor = if (item.preset == p) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) { Text(p.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable private fun ProcessingScreen(state: UploaderUiState.Processing, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { state.progress }, modifier = Modifier.size(140.dp), strokeWidth = 10.dp, strokeCap = StrokeCap.Round, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        if (state.queueTotal > 1) Text("Item ${state.queueIndex + 1} / ${state.queueTotal}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.totalClips > 0) Text(stringResource(R.string.processing_clip, state.clipIndex + 1, state.totalClips), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.presetLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        if (state.speedMultiplier > 0f) {
            Spacer(Modifier.height(8.dp))
            Text("${"%.1f".format(state.speedMultiplier)}x realtime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.etaSeconds > 0L) {
            Text("ETA: ~${state.etaSeconds}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.Cancel, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.cancel)) }
    }
}

@Composable private fun ResultsScreen(results: UploaderUiState.Results, context: android.content.Context, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(pluralStringResource(R.plurals.clips_ready, results.clips.size, results.clips.size), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.results_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { context.startActivity(ShareManager.buildShareAllIntent(context, results.clips.map { it.file }, context.getString(R.string.share_via))) }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share_all)) }
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) { items(results.clips, key = { it.file.absolutePath }) { clip -> ClipCard(clip) { context.startActivity(ShareManager.buildShareClipIntent(context, clip.file, context.getString(R.string.share_via))) } }; item { TextButton(onClick = onReset, Modifier.fillMaxWidth()) { Text(stringResource(R.string.process_another)) } } }
        }
        FloatingActionButton(
            onClick = { context.startActivity(ShareManager.buildShareAllIntent(context, results.clips.map { it.file }, context.getString(R.string.share_via))) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Filled.Share, stringResource(R.string.share_all)) }
    }
}

@Composable private fun BatchResultsScreen(results: UploaderUiState.BatchResults, context: android.content.Context, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("${results.items.count { it.processedClips.isNotEmpty() }} items done", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(results.items) { item ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.video.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (item.error != null) { Spacer(Modifier.height(4.dp)); Text(item.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                            item.processedClips.forEach { clip -> ClipCard(clip) { context.startActivity(ShareManager.buildShareClipIntent(context, clip.file, context.getString(R.string.share_via))) } }
                            if (item.processedClips.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                BeforeAfterInspector(sourceFile = item.video.file, processedFile = item.processedClips.firstOrNull()?.file)
                            }
                        }
                    }
                }
                item { TextButton(onClick = onReset, Modifier.fillMaxWidth()) { Text(stringResource(R.string.process_another)) } }
            }
        }
        FloatingActionButton(
            onClick = {
                val allClips = results.items.flatMap { it.processedClips }.map { it.file }
                if (allClips.isNotEmpty()) context.startActivity(ShareManager.buildShareAllIntent(context, allClips, context.getString(R.string.share_via)))
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Filled.Share, stringResource(R.string.share_all)) }
    }
}

@Composable internal fun ClipCard(clip: ProcessedClip, onShare: (() -> Unit)? = null) {
    Row(Modifier.padding(vertical = 4.dp)) { AsyncImage(model = clip.file, null, contentScale = ContentScale.Crop, modifier = Modifier.width(56.dp).height(100.dp).clip(RoundedCornerShape(10.dp))); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) { Text(stringResource(R.string.clip_label, clip.index + 1, clip.totalClips), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); if (clip.mediaType == MediaType.VIDEO) Text(stringResource(R.string.duration_label, formatDurationMs(clip.durationMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(stringResource(R.string.size_label, formatFileSize(clip.file.length())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (onShare != null) { Spacer(Modifier.height(4.dp)); Button(onClick = onShare, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), modifier = Modifier.height(34.dp)) { Icon(Icons.Filled.Share, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.share_to_whatsapp), style = MaterialTheme.typography.labelSmall) } } }
    }
}

@Composable private fun InfoChip(text: String) { Box(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)).padding(horizontal = 12.dp, vertical = 7.dp)) { Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface) } }

@Composable private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(stringResource(R.string.error), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(12.dp)); Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center); Spacer(Modifier.height(28.dp)); Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.retry)) } }
}

@Composable private fun ProcessedPreview(picked: UploaderUiState.Picked, modifier: Modifier = Modifier) {
    val isLandscape = picked.video.width >= picked.video.height; val keepOriginal = !picked.forcePortrait && isLandscape
    val outputW = if (picked.resolution == OutputResolution.CUSTOM) picked.customWidthText.toIntOrNull()?.coerceIn(1, 7680) ?: 1080 else picked.resolution.width
    val outputH = if (picked.resolution == OutputResolution.CUSTOM) picked.customHeightText.toIntOrNull()?.coerceIn(1, 7680) ?: 1920 else picked.resolution.height
    val tW = if (keepOriginal) outputH else outputW; val tH = if (keepOriginal) outputW else outputH
    val key = "prev_${picked.video.file.absolutePath}_${tW}x${tH}_${picked.forcePortrait}_${picked.blurBackground}_${picked.filter.name}_${picked.sharpen}_${picked.denoise}_${picked.autoLevels}_${picked.deblock}_${picked.video.rotationDegrees}"
    var preview by remember { mutableStateOf<Bitmap?>(null) }; var loading by remember { mutableStateOf(false) }
    LaunchedEffect(key) { loading = true; delay(180); preview = withContext(Dispatchers.IO) { try { val src: Bitmap? = if (picked.video.mediaType == MediaType.VIDEO) { val r = MediaMetadataRetriever(); try { r.setDataSource(picked.video.file.absolutePath); r.frameAtTime } catch (_: Exception) { null } finally { r.release() } } else { val o = BitmapFactory.Options().apply { inSampleSize = computeSampleSize(picked.video.width, picked.video.height, maxOf(tW, tH)) }; BitmapFactory.decodeFile(picked.video.file.absolutePath, o) }; src?.let { processBitmapPreview(it, tW, tH, picked.forcePortrait, picked.blurBackground, picked.filter, picked.sharpen, picked.denoise, picked.autoLevels, picked.deblock, picked.video.rotationDegrees) } } catch (_: Exception) { null } }; loading = false }
    Box(modifier, contentAlignment = Alignment.Center) { preview?.let { Image(bitmap = it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) } ?: AsyncImage(model = picked.video.file, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()); if (loading) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp), strokeWidth = 3.dp) } }
}

private fun computeSampleSize(w: Int, h: Int, maxDim: Int) = generateSequence(1) { it * 2 }.first { w / it <= maxDim && h / it <= maxDim }

private fun processBitmapPreview(source: Bitmap, tW: Int, tH: Int, fp: Boolean, blur: Boolean, flt: FilterPreset, sh: Boolean, dn: Boolean, al: Boolean, db: Boolean, rot: Int): Bitmap {
    var img = source; if (rot != 0) { val m = android.graphics.Matrix().apply { postRotate(rot.toFloat()) }; val r = Bitmap.createBitmap(img, 0, 0, img.width, img.height, m, true); if (r !== img) img.recycle(); img = r }
    val sc = if (fp) maxOf(tW.toFloat() / img.width, tH.toFloat() / img.height) else minOf(tW.toFloat() / img.width, tH.toFloat() / img.height)
    val sw = (img.width * sc + 0.5f).toInt(); val sh2 = (img.height * sc + 0.5f).toInt()
    val cw = if (fp) tW else sw; val ch = if (fp) tH else sh2
    val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888); val c = android.graphics.Canvas(out)
    if (fp && blur) { val bw = maxOf(img.width / 30, 4); val bh = maxOf(img.height / 30, 4); val t = Bitmap.createScaledBitmap(img, bw, bh, true); val bg = Bitmap.createScaledBitmap(t, tW, tH, false); t.recycle(); c.drawBitmap(bg, 0f, 0f, null); bg.recycle() } else if (fp) c.drawColor(android.graphics.Color.BLACK)
    val sd = Bitmap.createScaledBitmap(img, sw, sh2, true); img.recycle()
    c.drawBitmap(sd, if (fp) (tW - sw) / 2f else 0f, if (fp) (tH - sh2) / 2f else 0f, null); sd.recycle()
    if (flt != FilterPreset.NONE) PhotoProcessor.applyFilterToBitmap(out, flt)
    if (sh || dn || al || db) PhotoProcessor.applyEnhancementsPreview(out, sh, dn, al, db)
    return out
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoBackground(file: File) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { PlayerView(context).apply { this.player = player; useController = false } },
        modifier = Modifier.fillMaxSize()
    )
}
