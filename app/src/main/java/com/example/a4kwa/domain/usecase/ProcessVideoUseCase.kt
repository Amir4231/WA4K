package com.example.a4kwa.domain.usecase

import com.example.a4kwa.data.repository.VideoTranscoderRepository
import com.example.a4kwa.model.FilterPreset
import java.io.File

class ProcessVideoUseCase(
    private val repository: VideoTranscoderRepository,
    private val calculateBitrate: CalculateTargetBitrateUseCase,
    private val splitVideo: SplitVideoUseCase
) {
    suspend operator fun invoke(request: ProcessRequest): Result<List<File>> = repository.transcode(request)

    fun estimateSize(request: ProcessRequest): BitrateEstimate {
        val durationSec = request.durationMs / 1000f
        val preset = when (request.presetLabel) {
            "Ultra HD Status" -> PresetConfig.ULTRA_HD
            "Fast Export" -> PresetConfig.FAST_EXPORT
            else -> PresetConfig.BALANCED
        }
        return calculateBitrate.forPreset(preset, durationSec)
    }

    fun computeSegments(durationMs: Long): List<SplitSegment> = splitVideo.calculateSegments(durationMs)
}

data class ProcessRequest(
    val inputFile: File,
    val outputDir: File,
    val durationMs: Long,
    val outputWidth: Int = 1080,
    val outputHeight: Int = 1920,
    val rotationDegrees: Int = 0,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val forcePortrait: Boolean = true,
    val blurBackground: Boolean = false,
    val filter: FilterPreset = FilterPreset.NONE,
    val sharpen: Boolean = false,
    val denoise: Boolean = false,
    val autoLevels: Boolean = false,
    val deblock: Boolean = false,
    val presetLabel: String = "Balanced",
    val mediaType: String = "video"
)
