package com.example.a4kwa.domain.usecase

data class SplitSegment(
    val startMs: Long,
    val durationMs: Long,
    val index: Int
)

class SplitVideoUseCase {

    fun calculateSegments(totalDurationMs: Long, segmentDurationMs: Long = 30_000L): List<SplitSegment> {
        if (totalDurationMs <= 0L) return emptyList()
        val totalSegments = ((totalDurationMs + segmentDurationMs - 1) / segmentDurationMs).toInt()
        return (0 until totalSegments).map { index ->
            val startMs = index.toLong() * segmentDurationMs
            val endMs = (startMs + segmentDurationMs).coerceAtMost(totalDurationMs)
            SplitSegment(startMs, endMs - startMs, index)
        }
    }

    fun findClosestSyncFrame(durationMs: Long, targetMs: Long, frameRateHint: Float = 30f): Long {
        val frameDurationMs = if (frameRateHint > 0f) (1000f / frameRateHint).toLong() else 33L
        val remainder = targetMs % frameDurationMs
        return if (remainder < frameDurationMs / 2) targetMs - remainder else targetMs + (frameDurationMs - remainder)
    }
}
