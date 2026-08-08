package com.example.a4kwa.model

import java.io.File

enum class OutputResolution(val label: String, val width: Int, val height: Int) {
    FULL_HD("1080p", 1080, 1920),
    FOUR_K("4K", 2160, 3840),
    CUSTOM("Custom", 0, 0)
}

enum class MediaType {
    VIDEO,
    IMAGE
}

data class VideoInfo(
    val file: File,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val sizeBytes: Long,
    val mediaType: MediaType = MediaType.VIDEO
) {
    val segmentCount: Int
        get() {
            if (mediaType == MediaType.IMAGE) return 1
            val segments = (durationMs + SEGMENT_DURATION_MS - 1) / SEGMENT_DURATION_MS
            return segments.toInt().coerceAtLeast(1)
        }

    companion object {
        const val SEGMENT_DURATION_MS = 30_000L
    }
}

data class ProcessedClip(
    val file: File,
    val index: Int,
    val startMs: Long,
    val durationMs: Long,
    val totalClips: Int,
    val mediaType: MediaType = MediaType.VIDEO,
    val selected: Boolean = true
)

data class VideoResolution(val width: Int, val height: Int)
