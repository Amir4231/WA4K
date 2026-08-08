package com.example.a4kwa.ffmpeg

import com.example.a4kwa.model.FilterPreset

object FfmpegCommandBuilder {

    const val SEGMENT_DURATION_SECONDS = 30L
    private const val CRF = "18"
    private const val PRESET = "medium"
    private const val PROFILE = "high"
    private const val AUDIO_BITRATE = "192k"

    fun buildSegmentCommand(
        inputPath: String,
        outputPath: String,
        startMs: Long,
        outputWidth: Int,
        outputHeight: Int,
        rotationDegrees: Int = 0,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
        forcePortrait: Boolean = true,
        blurBackground: Boolean = false,
        filter: FilterPreset = FilterPreset.NONE,
        sharpen: Boolean = false,
        denoise: Boolean = false,
        autoLevels: Boolean = false,
        deblock: Boolean = false,
        segmentDurationMs: Long = 30_000L
    ): List<String> {
        val startSeconds = (startMs / 1000L).coerceAtLeast(0L)

        return buildList {
            add("-y")
            add("-ss"); add(startSeconds.toString())
            add("-noautorotate")
            add("-i"); add(inputPath)

            if (blurBackground) {
                val (vf, mapLabel) = buildBlurFilterComplex(outputWidth, outputHeight, rotationDegrees, sourceWidth, sourceHeight, forcePortrait, sharpen, denoise, autoLevels, deblock)
                add("-filter_complex"); add(vf)
                add("-map"); add(mapLabel)
            } else {
                add("-vf"); add(buildFilter(outputWidth, outputHeight, rotationDegrees, sourceWidth, sourceHeight, forcePortrait, filter, sharpen, denoise, autoLevels, deblock))
            }

            add("-c:v"); add("libx264")
            add("-preset"); add(PRESET)
            add("-crf"); add(CRF)
            add("-profile:v"); add(PROFILE)
            add("-pix_fmt"); add("yuv420p")
            add("-c:a"); add("aac")
            add("-b:a"); add(AUDIO_BITRATE)
            add("-ac"); add("2")
            add("-movflags"); add("+faststart")
            if (!blurBackground) {
                add("-map"); add("0:v:0")
            }
            add("-map"); add("0:a:0?")
            add("-t"); add((segmentDurationMs / 1000L).toString())
            add("-metadata:s:v"); add("rotate=0")
            add(outputPath)
        }
    }

    private fun buildBlurFilterComplex(
        outputWidth: Int, outputHeight: Int,
        rotationDegrees: Int, sourceWidth: Int, sourceHeight: Int,
        forcePortrait: Boolean,
        sharpen: Boolean, denoise: Boolean, autoLevels: Boolean, deblock: Boolean
    ): Pair<String, String> {
        val isLandscape = sourceWidth > 0 && sourceHeight > 0 && sourceWidth >= sourceHeight
        val keepOriginal = !forcePortrait && isLandscape
        val targetW = if (keepOriginal) outputHeight else outputWidth
        val targetH = if (keepOriginal) outputWidth else outputHeight

        val rotChain = rotationFilter(rotationDegrees)
        val scaleMode = if (forcePortrait) "increase" else "decrease"
        val scaledFilter = "scale=$targetW:$targetH:force_original_aspect_ratio=$scaleMode:flags=lanczos"
        val blurChain = "crop=$targetW:$targetH,boxblur=20:10"
        val enhance = buildEnhancementFilter(sharpen, denoise, autoLevels, deblock)

        val prefix = if (rotChain != null) "$rotChain," else ""
        val overlay = "[0:v]${prefix}split[src][bg];[bg]$scaledFilter,$blurChain[bg];[bg][src]overlay=(W-w)/2:(H-h)/2"
        val full = if (enhance.isNotEmpty()) "$overlay,$enhance[out]" else "${overlay}[out]"
        return full to "[out]"
    }

    fun buildFilter(
        outputWidth: Int,
        outputHeight: Int,
        rotationDegrees: Int = 0,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
        forcePortrait: Boolean = true,
        filterPreset: FilterPreset = FilterPreset.NONE,
        sharpen: Boolean = false,
        denoise: Boolean = false,
        autoLevels: Boolean = false,
        deblock: Boolean = false
    ): String {
        val isLandscape = sourceWidth > 0 && sourceHeight > 0 && sourceWidth >= sourceHeight
        val keepOriginal = !forcePortrait && isLandscape
        val targetW = if (keepOriginal) outputHeight else outputWidth
        val targetH = if (keepOriginal) outputWidth else outputHeight

        val parts = mutableListOf<String>()
        val rot = rotationFilter(rotationDegrees)
        if (rot != null) parts.add(rot)

        val scaleMode = if (forcePortrait) "increase" else "decrease"
        parts.add("scale=$targetW:$targetH:force_original_aspect_ratio=$scaleMode:flags=lanczos")
        if (forcePortrait) parts.add("crop=$targetW:$targetH")
        parts.add("setsar=1")

        val filterChain = parts.joinToString(",")

        val extras = listOfNotNull(
            buildFilterParams(filterPreset).takeIf { it.isNotEmpty() },
            buildEnhancementFilter(sharpen, denoise, autoLevels, deblock).takeIf { it.isNotEmpty() }
        ).joinToString(",")
        return if (extras.isNotEmpty()) "$filterChain,$extras" else filterChain
    }

    private fun rotationFilter(rotationDegrees: Int): String? = when (rotationDegrees % 360) {
        90 -> "transpose=1"
        180 -> "transpose=1,transpose=1"
        270 -> "transpose=2"
        else -> null
    }

    fun buildFilterParams(filter: FilterPreset): String = when (filter) {
        FilterPreset.CINEMATIC -> "eq=brightness=-0.04:contrast=1.1:saturation=0.85"
        FilterPreset.VIBRANT -> "eq=brightness=0.04:saturation=1.3:contrast=1.02"
        FilterPreset.WARM -> "colorbalance=rs=0.08:gs=-0.04:bs=-0.15"
        FilterPreset.COOL -> "colorbalance=rs=-0.10:gs=-0.02:bs=0.12"
        FilterPreset.VINTAGE -> "eq=brightness=0.02:contrast=0.95:saturation=0.7,curves=vintage"
        FilterPreset.NOIR -> "hue=s=0,eq=contrast=1.15:brightness=-0.02"
        else -> ""
    }

    fun buildEnhancementFilter(sharpen: Boolean, denoise: Boolean, autoLevels: Boolean, deblock: Boolean): String =
        buildList {
            if (sharpen) add("unsharp=5:5:0.8:3:3:0.4")
            if (denoise) add("hqdn3d=4:3:6:4")
            if (autoLevels) add("pp=al")
            if (deblock) add("deblock=1:1:1:1")
        }.joinToString(",")
}
