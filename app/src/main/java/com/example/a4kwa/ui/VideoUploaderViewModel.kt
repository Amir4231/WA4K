package com.example.a4kwa.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a4kwa.service.TranscodeForegroundService
import com.example.a4kwa.domain.usecase.BitrateEstimate
import com.example.a4kwa.domain.usecase.CalculateTargetBitrateUseCase
import com.example.a4kwa.domain.usecase.PresetConfig
import com.example.a4kwa.domain.usecase.ProcessRequest
import com.example.a4kwa.domain.usecase.ProcessVideoUseCase
import com.example.a4kwa.ffmpeg.PhotoProcessor
import com.example.a4kwa.ffmpeg.VideoProcessor
import com.example.a4kwa.model.FilterPreset
import com.example.a4kwa.model.SplitMode
import com.example.a4kwa.model.MediaType
import com.example.a4kwa.model.OutputResolution
import com.example.a4kwa.model.ProcessedClip
import com.example.a4kwa.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class QueuedItem(
    val id: String = UUID.randomUUID().toString(),
    val video: VideoInfo,
    val resolution: OutputResolution = OutputResolution.FULL_HD,
    val forcePortrait: Boolean = true,
    val blurBackground: Boolean = false,
    val filter: FilterPreset = FilterPreset.NONE,
    val sharpen: Boolean = false,
    val denoise: Boolean = false,
    val autoLevels: Boolean = false,
    val deblock: Boolean = false,
    val customWidthText: String = "1080",
    val customHeightText: String = "1920",
    var processedClips: List<ProcessedClip> = emptyList(),
    var error: String? = null,
    val preset: PresetConfig = PresetConfig.BALANCED
)

sealed interface UploaderUiState {
    data object Idle : UploaderUiState
    data class Loading(val message: String) : UploaderUiState
    data class Picked(
        val video: VideoInfo,
        val resolution: OutputResolution = OutputResolution.FULL_HD,
        val forcePortrait: Boolean = true,
        val blurBackground: Boolean = false,
        val filter: FilterPreset = FilterPreset.NONE,
        val sharpen: Boolean = false,
        val denoise: Boolean = false,
        val autoLevels: Boolean = false,
        val deblock: Boolean = false,
        val customWidthText: String = "1080",
        val customHeightText: String = "1920",
        val preset: PresetConfig = PresetConfig.BALANCED,
        val deselectedSegmentIndices: Set<Int> = emptySet()
    ) : UploaderUiState

    data class Queue(val items: List<QueuedItem>) : UploaderUiState
    data class Processing(
        val progress: Float,
        val clipIndex: Int,
        val totalClips: Int,
        val queueIndex: Int = 0,
        val queueTotal: Int = 1,
        val speedMultiplier: Float = 0f,
        val etaSeconds: Long = 0L,
        val presetLabel: String = "Balanced"
    ) : UploaderUiState

    data class Results(val clips: List<ProcessedClip>) : UploaderUiState
    data class BatchResults(val items: List<QueuedItem>) : UploaderUiState
    data class Error(val message: String) : UploaderUiState

    data class TrimEditing(
        val video: VideoInfo,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val resolution: OutputResolution = OutputResolution.FULL_HD,
        val forcePortrait: Boolean = true,
        val blurBackground: Boolean = false,
        val filter: FilterPreset = FilterPreset.NONE,
        val sharpen: Boolean = false,
        val denoise: Boolean = false,
        val autoLevels: Boolean = false,
        val deblock: Boolean = false,
        val customWidthText: String = "1080",
        val customHeightText: String = "1920",
        val preset: PresetConfig = PresetConfig.BALANCED
    ) : UploaderUiState

    data class SegmentResults(val clips: List<ProcessedClip>) : UploaderUiState
}

class VideoUploaderViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application get() = getApplication()

    private val processVideoUseCase: ProcessVideoUseCase
        get() = (app as com.example.a4kwa.VideoApplication).container.processVideoUseCase
    private val calculateBitrate: CalculateTargetBitrateUseCase
        get() = (app as com.example.a4kwa.VideoApplication).container.calculateTargetBitrateUseCase
    internal val videoProcessor: VideoProcessor
        get() = (app as com.example.a4kwa.VideoApplication).container.videoProcessor

    private val _uiState = MutableStateFlow<UploaderUiState>(UploaderUiState.Idle)
    val uiState: StateFlow<UploaderUiState> = _uiState.asStateFlow()

    private var processingStartTime: Long = 0L
    private val notificationManager by lazy { app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private val _splitMode = MutableStateFlow(SplitMode.Auto)
    val splitMode: StateFlow<SplitMode> = _splitMode.asStateFlow()

    fun setSplitMode(mode: SplitMode) {
        _splitMode.value = mode
    }

    fun startTrimEditing(picked: UploaderUiState.Picked) {
        val video = picked.video
        val maxEnd = video.durationMs.coerceAtMost(30_000L)
        _uiState.value = UploaderUiState.TrimEditing(
            video = video, trimStartMs = 0L, trimEndMs = maxEnd,
            resolution = picked.resolution, forcePortrait = picked.forcePortrait,
            blurBackground = picked.blurBackground, filter = picked.filter,
            sharpen = picked.sharpen, denoise = picked.denoise,
            autoLevels = picked.autoLevels, deblock = picked.deblock,
            customWidthText = picked.customWidthText, customHeightText = picked.customHeightText,
            preset = picked.preset
        )
    }

    fun onTrimRangeChanged(startMs: Long, endMs: Long) {
        val state = _uiState.value as? UploaderUiState.TrimEditing ?: return
        _uiState.value = state.copy(trimStartMs = startMs, trimEndMs = endMs)
    }

    fun onPreviewSeekMs(seekMs: Long) {
    }

    fun processTrimmed() {
        val state = _uiState.value as? UploaderUiState.TrimEditing ?: return
        val video = state.video
        val startMs = state.trimStartMs
        val endMs = state.trimEndMs
        val outDir = File(app.cacheDir, "jobs/${System.currentTimeMillis()}/out")
        processingStartTime = System.currentTimeMillis()

        val outputW: Int
        val outputH: Int
        if (state.resolution == OutputResolution.CUSTOM) {
            outputW = state.customWidthText.toIntOrNull()?.coerceIn(1, 7680) ?: 1080
            outputH = state.customHeightText.toIntOrNull()?.coerceIn(1, 7680) ?: 1920
        } else {
            outputW = state.resolution.width
            outputH = state.resolution.height
        }

        if (video.mediaType == MediaType.IMAGE) {
            _uiState.value = UploaderUiState.Processing(0f, 0, 1, presetLabel = state.preset.label)
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val clip = PhotoProcessor.process(
                        video.file, File(outDir, "processed_1.jpg"),
                        outputW, outputH, state.forcePortrait, state.blurBackground,
                        state.filter, state.sharpen, state.denoise,
                        state.autoLevels, state.deblock
                    )
                    _uiState.value = UploaderUiState.Results(listOf(clip))
                } catch (e: Exception) {
                    _uiState.value = UploaderUiState.Error(app.getString(com.example.a4kwa.R.string.error_processing))
                }
            }
            return
        }

        val presetLabel = state.preset.label
        showProgressNotification(0, 1, 0)
        _uiState.value = UploaderUiState.Processing(0f, 0, 1, presetLabel = presetLabel)
        videoProcessor.startSingleSegment(
            inputFile = video.file,
            outputDir = outDir,
            startMs = startMs,
            endMs = endMs,
            outputWidth = outputW,
            outputHeight = outputH,
            rotationDegrees = video.rotationDegrees,
            sourceWidth = video.width,
            sourceHeight = video.height,
            forcePortrait = state.forcePortrait,
            blurBackground = state.blurBackground,
            filter = state.filter,
            sharpen = state.sharpen,
            denoise = state.denoise,
            autoLevels = state.autoLevels,
            deblock = state.deblock,
            listener = object : VideoProcessor.Listener {
                override fun onProgress(progress: Float, clipIndex: Int, totalClips: Int) {
                    val elapsed = System.currentTimeMillis() - processingStartTime
                    val speed = if (elapsed > 0) (progress * (endMs - startMs) / elapsed).toFloat() else 0f
                    val eta = if (progress > 0f && speed > 0f) ((1f - progress) * (endMs - startMs) / (speed * 1000f)).toLong() else 0L
                    _uiState.value = UploaderUiState.Processing(progress, clipIndex, totalClips, speedMultiplier = speed, etaSeconds = eta, presetLabel = presetLabel)
                    showProgressNotification(0, 1, (progress * 100).toInt())
                }
                override fun onSegmentComplete(clip: ProcessedClip) {}
                override fun onComplete(clips: List<ProcessedClip>) {
                    dismissProgressNotification()
                    _uiState.value = if (clips.isEmpty()) UploaderUiState.Error(app.getString(com.example.a4kwa.R.string.error_processing)) else UploaderUiState.Results(clips)
                }
                override fun onCancelled() {
                    dismissProgressNotification()
                    _uiState.value = UploaderUiState.TrimEditing(video, startMs, endMs, state.resolution, state.forcePortrait, state.blurBackground, state.filter, state.sharpen, state.denoise, state.autoLevels, state.deblock, state.customWidthText, state.customHeightText, state.preset)
                }
                override fun onError(message: String) {
                    dismissProgressNotification()
                    _uiState.value = UploaderUiState.Error(message)
                }
            },
            crfValue = state.preset.crfValue,
            ffmpegPreset = state.preset.ffmpegPreset
        )
    }

    fun toggleClipSelected(filePath: String) {
        val state = _uiState.value as? UploaderUiState.SegmentResults ?: return
        val updated = state.clips.map {
            if (it.file.absolutePath == filePath) it.copy(selected = !it.selected) else it
        }
        _uiState.value = UploaderUiState.SegmentResults(updated)
    }

    fun addMedia(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UploaderUiState.Loading(app.getString(com.example.a4kwa.R.string.reading_video))
            try {
                val input = copyToCache(uri)
                val mimeType = app.contentResolver.getType(uri) ?: ""
                val isImage = mimeType.startsWith("image/")
                val video = if (isImage) {
                    val (w, h) = PhotoProcessor.readDimensions(input)
                    val rot = PhotoProcessor.readExifRotation(input)
                    var displayW = w; var displayH = h
                    if (rot == 90 || rot == 270) { displayW = h; displayH = w }
                    VideoInfo(file = input, displayName = displayName(uri), durationMs = 0, width = displayW, height = displayH, rotationDegrees = rot, sizeBytes = input.length(), mediaType = MediaType.IMAGE)
                } else {
                    videoProcessor.probe(input, displayName(uri))
                }
                val current = _uiState.value
                val queue = if (current is UploaderUiState.Queue) current.items else emptyList()
                val newItem = QueuedItem(video = video)
                _uiState.value = UploaderUiState.Queue(queue + newItem)
            } catch (e: Exception) {
                Log.e(TAG, "addMedia failed", e)
                _uiState.value = UploaderUiState.Error(app.getString(com.example.a4kwa.R.string.error_could_not_read))
            }
        }
    }

    fun addMultipleMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UploaderUiState.Loading(app.getString(com.example.a4kwa.R.string.reading_video))
            try {
                val queue = mutableListOf<QueuedItem>()
                val current = _uiState.value
                if (current is UploaderUiState.Queue) queue.addAll(current.items)
                for (uri in uris) {
                    try {
                        val input = copyToCache(uri)
                        val mimeType = app.contentResolver.getType(uri) ?: ""
                        val isImage = mimeType.startsWith("image/")
                        val video = if (isImage) {
                            val (w, h) = PhotoProcessor.readDimensions(input)
                            val rot = PhotoProcessor.readExifRotation(input)
                            var displayW = w; var displayH = h
                            if (rot == 90 || rot == 270) { displayW = h; displayH = w }
                            VideoInfo(file = input, displayName = displayName(uri), durationMs = 0, width = displayW, height = displayH, rotationDegrees = rot, sizeBytes = input.length(), mediaType = MediaType.IMAGE)
                        } else {
                            videoProcessor.probe(input, displayName(uri))
                        }
                        queue.add(QueuedItem(video = video))
                    } catch (e: Exception) {
                        Log.e(TAG, "addMedia failed for $uri", e)
                    }
                }
                _uiState.value = if (queue.isEmpty()) UploaderUiState.Idle else UploaderUiState.Queue(queue)
            } catch (e: Exception) {
                _uiState.value = UploaderUiState.Error(app.getString(com.example.a4kwa.R.string.error_could_not_read))
            }
        }
    }

    fun pickSingleMedia(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UploaderUiState.Loading(app.getString(com.example.a4kwa.R.string.reading_video))
            try {
                val input = copyToCache(uri)
                val mimeType = app.contentResolver.getType(uri) ?: ""
                val isImage = mimeType.startsWith("image/")
                val video = if (isImage) {
                    val (w, h) = PhotoProcessor.readDimensions(input)
                    val rot = PhotoProcessor.readExifRotation(input)
                    var displayW = w; var displayH = h
                    if (rot == 90 || rot == 270) { displayW = h; displayH = w }
                    VideoInfo(file = input, displayName = displayName(uri), durationMs = 0, width = displayW, height = displayH, rotationDegrees = rot, sizeBytes = input.length(), mediaType = MediaType.IMAGE)
                } else {
                    videoProcessor.probe(input, displayName(uri))
                }
                _uiState.value = UploaderUiState.Picked(video)
            } catch (e: Exception) {
                _uiState.value = UploaderUiState.Error(app.getString(com.example.a4kwa.R.string.error_could_not_read))
            }
        }
    }

    fun removeFromQueue(id: String) {
        val state = _uiState.value as? UploaderUiState.Queue ?: return
        val items = state.items.filter { it.id != id }
        _uiState.value = if (items.isEmpty()) UploaderUiState.Idle else UploaderUiState.Queue(items)
    }

    fun setQueueItemResolution(id: String, resolution: OutputResolution) = updateQueueItem(id) { it.copy(resolution = resolution) }
    fun toggleQueueItemPortrait(id: String) = updateQueueItem(id) { it.copy(forcePortrait = !it.forcePortrait) }
    fun toggleQueueItemBlur(id: String) = updateQueueItem(id) { it.copy(blurBackground = !it.blurBackground) }
    fun toggleQueueItemSharpen(id: String) = updateQueueItem(id) { it.copy(sharpen = !it.sharpen) }
    fun toggleQueueItemDenoise(id: String) = updateQueueItem(id) { it.copy(denoise = !it.denoise) }
    fun toggleQueueItemAutoLevels(id: String) = updateQueueItem(id) { it.copy(autoLevels = !it.autoLevels) }
    fun toggleQueueItemDeblock(id: String) = updateQueueItem(id) { it.copy(deblock = !it.deblock) }
    fun setQueueItemFilter(id: String, filter: FilterPreset) = updateQueueItem(id) { it.copy(filter = filter) }
    fun setQueueItemCustomWidth(id: String, text: String) = updateQueueItem(id) { it.copy(customWidthText = text) }
    fun setQueueItemCustomHeight(id: String, text: String) = updateQueueItem(id) { it.copy(customHeightText = text) }
    fun setQueueItemPreset(id: String, preset: PresetConfig) = updateQueueItem(id) { it.copy(preset = preset) }

    private fun updateQueueItem(id: String, transform: (QueuedItem) -> QueuedItem) {
        val state = _uiState.value as? UploaderUiState.Queue ?: return
        _uiState.value = UploaderUiState.Queue(state.items.map { if (it.id == id) transform(it) else it })
    }

    fun setResolution(resolution: OutputResolution) { updatePicked { it.copy(resolution = resolution) } }
    fun toggleForcePortrait() { updatePicked { it.copy(forcePortrait = !it.forcePortrait) } }
    fun toggleBlurBackground() { updatePicked { it.copy(blurBackground = !it.blurBackground) } }
    fun toggleSharpen() { updatePicked { it.copy(sharpen = !it.sharpen) } }
    fun toggleDenoise() { updatePicked { it.copy(denoise = !it.denoise) } }
    fun toggleAutoLevels() { updatePicked { it.copy(autoLevels = !it.autoLevels) } }
    fun toggleDeblock() { updatePicked { it.copy(deblock = !it.deblock) } }
    fun setFilter(filter: FilterPreset) { updatePicked { it.copy(filter = filter) } }
    fun setCustomWidthText(text: String) { updatePicked { it.copy(customWidthText = text) } }
    fun setCustomHeightText(text: String) { updatePicked { it.copy(customHeightText = text) } }
    fun setPreset(preset: PresetConfig) { updatePicked { it.copy(preset = preset) } }
    fun togglePickedSegment(index: Int) { updatePicked { val sel = it.deselectedSegmentIndices.toMutableSet(); if (index in sel) sel.remove(index) else sel.add(index); it.copy(deselectedSegmentIndices = sel) } }

    private inline fun updatePicked(crossinline transform: (UploaderUiState.Picked) -> UploaderUiState.Picked) {
        val state = _uiState.value as? UploaderUiState.Picked ?: return
        _uiState.value = transform(state)
    }

    fun getSizeEstimate(video: VideoInfo, preset: PresetConfig): BitrateEstimate {
        val durationSec = video.durationMs / 1000f
        return calculateBitrate.forPreset(preset, durationSec)
    }

    fun processSingle() {
        val state = _uiState.value as? UploaderUiState.Picked ?: return
        runProcess(state.video, state.resolution, state.forcePortrait, state.blurBackground, state.filter, state.sharpen, state.denoise, state.autoLevels, state.deblock, state.customWidthText, state.customHeightText, state.preset, singleItem = true, deselectedIndices = state.deselectedSegmentIndices)
    }

    fun processBatch() {
        val state = _uiState.value as? UploaderUiState.Queue ?: return
        val items = state.items.toList()
        viewModelScope.launch(Dispatchers.IO) {
            val results = items.toMutableList()
            for ((i, item) in items.withIndex()) {
                processingStartTime = System.currentTimeMillis()
                _uiState.value = UploaderUiState.Processing(0f, 0, 0, i, items.size, presetLabel = item.preset.label)
                runProcessSync(item, i, items.size)
                val current = _uiState.value
                if (current is UploaderUiState.BatchResults) {
                    results[i] = current.items[i]
                }
            }
            val allGood = results.all { it.error == null && it.processedClips.isNotEmpty() }
            if (allGood || results.any { it.processedClips.isNotEmpty() }) {
                _uiState.value = UploaderUiState.BatchResults(results)
            } else {
                _uiState.value = UploaderUiState.Error(app.getString(com.example.a4kwa.R.string.error_processing))
            }
            completeResults = results
        }
    }

    private var completeResults: List<QueuedItem>? = null

    private suspend fun runProcessSync(item: QueuedItem, queueIndex: Int, queueTotal: Int) {
        val result = item.copy()
        try {
            val clips = processItemSuspend(item)
            result.processedClips = clips
            result.error = null
        } catch (e: Exception) {
            result.error = e.message ?: "Unknown error"
        }
        val state = _uiState.value
        val currentItems = if (state is UploaderUiState.BatchResults) state.items.toMutableList() else MutableList(queueTotal) { QueuedItem(video = item.video) }
        currentItems[queueIndex] = result
        _uiState.value = UploaderUiState.BatchResults(currentItems)
    }

    private suspend fun processItemSuspend(item: QueuedItem): List<ProcessedClip> {
        val outDir = File(app.cacheDir, "jobs/${System.currentTimeMillis()}/out")
        outDir.mkdirs()

        val outputW: Int
        val outputH: Int
        if (item.resolution == OutputResolution.CUSTOM) {
            outputW = item.customWidthText.toIntOrNull()?.coerceIn(1, 7680) ?: 1080
            outputH = item.customHeightText.toIntOrNull()?.coerceIn(1, 7680) ?: 1920
        } else {
            outputW = item.resolution.width
            outputH = item.resolution.height
        }

        if (item.video.mediaType == MediaType.IMAGE) {
            val outputFile = File(outDir, "processed_1.jpg")
            return listOf(PhotoProcessor.process(item.video.file, outputFile, outputW, outputH, item.forcePortrait, item.blurBackground, item.filter, item.sharpen, item.denoise, item.autoLevels, item.deblock))
        }

        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            videoProcessor.start(
                inputFile = item.video.file, outputDir = outDir, totalDurationMs = item.video.durationMs,
                outputWidth = outputW, outputHeight = outputH, rotationDegrees = item.video.rotationDegrees,
                sourceWidth = item.video.width, sourceHeight = item.video.height,
                forcePortrait = item.forcePortrait, blurBackground = item.blurBackground,
                filter = item.filter, sharpen = item.sharpen, denoise = item.denoise,
                autoLevels = item.autoLevels, deblock = item.deblock,
                listener = object : VideoProcessor.Listener {
                    override fun onProgress(progress: Float, clipIndex: Int, totalClips: Int) {}
                    override fun onSegmentComplete(clip: ProcessedClip) {}
                    override fun onComplete(clips: List<ProcessedClip>) { cont.resume(clips) {} }
                    override fun onCancelled() { cont.resume(emptyList()) {} }
                    override fun onError(message: String) { cont.resumeWith(Result.failure(Exception(message))) }
                }
            )
        }
    }

    private fun runProcess(video: VideoInfo, resolution: OutputResolution, forcePortrait: Boolean, blurBackground: Boolean, filter: FilterPreset, sharpen: Boolean, denoise: Boolean, autoLevels: Boolean, deblock: Boolean, customW: String, customH: String, preset: PresetConfig, singleItem: Boolean, deselectedIndices: Set<Int> = emptySet()) {
        val outDir = File(app.cacheDir, "jobs/${System.currentTimeMillis()}/out")

        val outputW: Int
        val outputH: Int
        if (resolution == OutputResolution.CUSTOM) {
            outputW = customW.toIntOrNull()?.coerceIn(1, 7680) ?: 1080
            outputH = customH.toIntOrNull()?.coerceIn(1, 7680) ?: 1920
        } else {
            outputW = resolution.width
            outputH = resolution.height
        }

        processingStartTime = System.currentTimeMillis()

        if (video.mediaType == MediaType.IMAGE) {
            _uiState.value = UploaderUiState.Processing(0f, 0, 1, presetLabel = preset.label)
            val outputFile = File(outDir, "processed_1.jpg")
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val clip = PhotoProcessor.process(video.file, outputFile, outputW, outputH, forcePortrait, blurBackground, filter, sharpen, denoise, autoLevels, deblock)
                    _uiState.value = UploaderUiState.Results(listOf(clip))
                } catch (e: Exception) {
                    _uiState.value = UploaderUiState.Error(app.getString(com.example.a4kwa.R.string.error_processing))
                }
            }
            return
        }

        _uiState.value = UploaderUiState.Processing(0f, 0, video.segmentCount, presetLabel = preset.label)
        showProgressNotification(0, video.segmentCount, 0)
        videoProcessor.start(
            inputFile = video.file, outputDir = outDir, totalDurationMs = video.durationMs,
            outputWidth = outputW, outputHeight = outputH, rotationDegrees = video.rotationDegrees,
            sourceWidth = video.width, sourceHeight = video.height,
            forcePortrait = forcePortrait, blurBackground = blurBackground, filter = filter,
            sharpen = sharpen, denoise = denoise, autoLevels = autoLevels, deblock = deblock,
            listener = object : VideoProcessor.Listener {
                override fun onProgress(progress: Float, clipIndex: Int, totalClips: Int) {
                    val elapsed = System.currentTimeMillis() - processingStartTime
                    if (progress > 0f && elapsed > 0) {
                        val totalEstimated = (elapsed / progress).toLong()
                        val eta = (totalEstimated - elapsed) / 1000L
                        val speed = if (elapsed > 0) (video.durationMs * progress / elapsed).toFloat() / 1000f else 0f
                        _uiState.value = UploaderUiState.Processing(progress, clipIndex, totalClips, speedMultiplier = speed, etaSeconds = eta, presetLabel = preset.label)
                        showProgressNotification(clipIndex, totalClips, (progress * 100).toInt())
                    } else {
                        _uiState.value = UploaderUiState.Processing(progress, clipIndex, totalClips, presetLabel = preset.label)
                    }
                }
                override fun onSegmentComplete(clip: ProcessedClip) {}
                override fun onComplete(clips: List<ProcessedClip>) {
                    dismissProgressNotification()
                    if (clips.isEmpty()) {
                        _uiState.value = UploaderUiState.Error(app.getString(com.example.a4kwa.R.string.error_processing))
                    } else if (_splitMode.value == SplitMode.ManualSegments) {
                        _uiState.value = UploaderUiState.SegmentResults(clips)
                    } else {
                        _uiState.value = UploaderUiState.Results(clips)
                    }
                }
                override fun onCancelled() {
                    dismissProgressNotification()
                    _uiState.value = UploaderUiState.Picked(video, resolution, forcePortrait, blurBackground, filter, sharpen, denoise, autoLevels, deblock, customW, customH, preset, deselectedIndices)
                }
                override fun onError(message: String) {
                    dismissProgressNotification()
                    _uiState.value = UploaderUiState.Error(message)
                }
            },
            deselectedIndices = deselectedIndices,
            crfValue = preset.crfValue,
            ffmpegPreset = preset.ffmpegPreset
        )
    }

    fun cancelProcessing() { videoProcessor.cancel(); dismissProgressNotification() }

    private fun showProgressNotification(clipIndex: Int, totalClips: Int, percent: Int) {
        val statusText = if (totalClips > 0) "Processing clip ${clipIndex + 1} of $totalClips ($percent%)" else "Processing ($percent%)"
        val notification = TranscodeForegroundService.createNotification(app, percent, 100, statusText)
        notificationManager.notify(TranscodeForegroundService.NOTIFICATION_ID, notification)
    }

    private fun dismissProgressNotification() {
        notificationManager.cancel(TranscodeForegroundService.NOTIFICATION_ID)
    }

    fun reset() {
        videoProcessor.cancel()
        clearJobDirectories()
        completeResults = null
        _uiState.value = UploaderUiState.Idle
    }

    fun backToPicked() {
        videoProcessor.cancel()
        val state = _uiState.value as? UploaderUiState.TrimEditing ?: return
        _uiState.value = UploaderUiState.Picked(
            video = state.video,
            resolution = state.resolution,
            forcePortrait = state.forcePortrait,
            blurBackground = state.blurBackground,
            filter = state.filter,
            sharpen = state.sharpen,
            denoise = state.denoise,
            autoLevels = state.autoLevels,
            deblock = state.deblock,
            customWidthText = state.customWidthText,
            customHeightText = state.customHeightText,
            preset = state.preset
        )
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value as? UploaderUiState.Queue ?: return
        val items = state.items.toMutableList()
        val item = items.removeAt(fromIndex)
        items.add(toIndex.coerceIn(0, items.size), item)
        _uiState.value = UploaderUiState.Queue(items)
    }

    private fun copyToCache(uri: Uri): File {
        val resolver = app.contentResolver
        val jobDir = File(app.cacheDir, "jobs/${System.currentTimeMillis()}")
        jobDir.mkdirs()
        val ext = extensionFromDisplayName(displayName(uri))
        val file = File(jobDir, "input.$ext")
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        input.use { s -> FileOutputStream(file).use { o -> s.copyTo(o) } }
        if (!file.exists() || file.length() == 0L) throw IOException("Empty file")
        return file
    }

    private fun displayName(uri: Uri): String {
        var name: String? = null
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && cursor.moveToFirst()) name = cursor.getString(i)
        }
        return name ?: "media_${System.currentTimeMillis()}"
    }

    private fun extensionFromDisplayName(name: String): String {
        val dot = name.lastIndexOf('.')
        val e = if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1) else "jpg"
        return if (e.matches(Regex("[a-zA-Z0-9]{1,10}"))) e else "jpg"
    }

    private fun clearJobDirectories() {
        File(app.cacheDir, "jobs").listFiles()?.forEach { it.deleteRecursively() }
    }

    override fun onCleared() { videoProcessor.cancel(); super.onCleared() }

    companion object { const val TAG = "VideoUploader" }
}
