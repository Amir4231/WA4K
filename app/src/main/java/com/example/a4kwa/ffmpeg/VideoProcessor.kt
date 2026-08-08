package com.example.a4kwa.ffmpeg

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import com.arthenica.ffmpegkit.StreamInformation
import com.example.a4kwa.model.FilterPreset
import com.example.a4kwa.model.OutputResolution
import com.example.a4kwa.model.ProcessedClip
import com.example.a4kwa.model.VideoInfo
import java.io.File

class VideoProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Orchestrates FFmpegKit executions: probes source metadata with FFprobe and re-encodes
 * the input into sequential 30-second vertical 9:16 clips, reporting live progress.
 */
class VideoProcessor {

    interface Listener {
        fun onProgress(progress: Float, clipIndex: Int, totalClips: Int)
        fun onSegmentComplete(clip: ProcessedClip)
        fun onComplete(clips: List<ProcessedClip>)
        fun onCancelled()
        fun onError(message: String)
    }

    @Volatile
    private var cancelled = false
    private var activeSession: FFmpegSession? = null
    private var lastProgress = 0f

    fun probe(file: File, displayName: String): VideoInfo {
        val session = FFprobeKit.getMediaInformation(file.absolutePath)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            throw VideoProcessingException("Could not read video metadata")
        }
        val info = session.mediaInformation ?: throw VideoProcessingException("Could not read video metadata")
        val streams = info.streams ?: emptyList()
        val videoStream = streams.firstOrNull { it.type == "video" }
            ?: throw VideoProcessingException("The selected file has no video stream")

        var width = (videoStream.width ?: 0L).toInt()
        var height = (videoStream.height ?: 0L).toInt()
        val rotation = readRotationDegrees(videoStream)
        if (rotation == 90 || rotation == 270) {
            val tmp = width
            width = height
            height = tmp
        }

        val durationSec = info.duration?.toDoubleOrNull() ?: 0.0
        return VideoInfo(
            file = file,
            displayName = displayName,
            durationMs = (durationSec * 1000.0).toLong(),
            width = width,
            height = height,
            rotationDegrees = rotation,
            sizeBytes = file.length()
        )
    }

    fun start(
        inputFile: File,
        outputDir: File,
        totalDurationMs: Long,
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
        listener: Listener,
        deselectedIndices: Set<Int> = emptySet(),
        crfValue: String = "22",
        ffmpegPreset: String = "veryfast"
    ) {
        cancelled = false
        lastProgress = 0f
        outputDir.mkdirs()
        val totalClips = (totalDurationMs + SEGMENT_DURATION_MS - 1) / SEGMENT_DURATION_MS
        val clips = ArrayList<ProcessedClip>()
        runSegment(inputFile, outputDir, 0, totalClips.toInt(), totalDurationMs, outputWidth, outputHeight, rotationDegrees, sourceWidth, sourceHeight, forcePortrait, blurBackground, filter, sharpen, denoise, autoLevels, deblock, listener, clips, deselectedIndices, crfValue, ffmpegPreset)
    }

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
        listener: Listener,
        crfValue: String = "22",
        ffmpegPreset: String = "veryfast"
    ) {
        cancelled = false
        lastProgress = 0f
        outputDir.mkdirs()
        val durationMs = (endMs - startMs).coerceAtLeast(1L)
        val outFile = File(outputDir, "clip_1.mp4")
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
            segmentDurationMs = durationMs,
            crfValue = crfValue,
            ffmpegPreset = ffmpegPreset
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
                    if (outFile.length() < MIN_CLIP_BYTES) {
                        listener.onError("Output file is empty")
                    } else {
                        val clip = ProcessedClip(
                            file = outFile,
                            index = 0,
                            startMs = startMs,
                            durationMs = durationMs,
                            totalClips = 1,
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

    fun cancel() {
        cancelled = true
        activeSession?.cancel()
        FFmpegKit.cancel()
    }

    private fun runSegment(
        inputFile: File,
        outputDir: File,
        index: Int,
        totalClips: Int,
        totalDurationMs: Long,
        outputWidth: Int,
        outputHeight: Int,
        rotationDegrees: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        forcePortrait: Boolean,
        blurBackground: Boolean,
        filter: FilterPreset,
        sharpen: Boolean,
        denoise: Boolean,
        autoLevels: Boolean,
        deblock: Boolean,
        listener: Listener,
        clips: MutableList<ProcessedClip>,
        deselectedIndices: Set<Int> = emptySet(),
        crfValue: String = "22",
        ffmpegPreset: String = "veryfast"
    ) {
        if (cancelled) {
            listener.onCancelled()
            return
        }

        if (index in deselectedIndices) {
            if (index + 1 < totalClips) {
                            runSegment(inputFile, outputDir, index + 1, totalClips, totalDurationMs, outputWidth, outputHeight, rotationDegrees, sourceWidth, sourceHeight, forcePortrait, blurBackground, filter, sharpen, denoise, autoLevels, deblock, listener, clips, deselectedIndices, crfValue, ffmpegPreset)
            } else {
                val finalClips = clips.mapIndexed { i, c -> c.copy(index = i, totalClips = clips.size) }
                listener.onComplete(finalClips)
            }
            return
        }

        val startMs = index.toLong() * SEGMENT_DURATION_MS
        val outFile = File(outputDir, "clip_${index + 1}.mp4")
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
            crfValue = crfValue,
            ffmpegPreset = ffmpegPreset
        )

        val session = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            FFmpegSessionCompleteCallback { completed ->
                if (cancelled) {
                    listener.onCancelled()
                } else if (!ReturnCode.isSuccess(completed.returnCode)) {
                    val detail = completed.failStackTrace?.take(400) ?: ""
                    listener.onError("Failed to process clip ${index + 1}. $detail")
                } else {
                    if (outFile.length() < MIN_CLIP_BYTES) {
                        val finalClips = clips.mapIndexed { i, c -> c.copy(index = i, totalClips = clips.size) }
                        listener.onComplete(finalClips)
                    } else {
                        val clip = ProcessedClip(
                            file = outFile,
                            index = index,
                            startMs = startMs,
                            durationMs = segmentDurationMs(startMs, totalDurationMs),
                            totalClips = totalClips,
                            mediaType = com.example.a4kwa.model.MediaType.VIDEO
                        )
                        clips.add(clip)
                        listener.onSegmentComplete(clip)
                        if (index + 1 < totalClips) {
                runSegment(inputFile, outputDir, index + 1, totalClips, totalDurationMs, outputWidth, outputHeight, rotationDegrees, sourceWidth, sourceHeight, forcePortrait, blurBackground, filter, sharpen, denoise, autoLevels, deblock, listener, clips, deselectedIndices, crfValue, ffmpegPreset)
                        } else {
                            listener.onComplete(clips)
                        }
                    }
                }
            },
            LogCallback { log ->
                parseProgressTime(log.message)?.let { timeSec ->
                    reportProgress(timeSec * 1000.0, totalDurationMs, index, totalClips, listener)
                }
            },
            StatisticsCallback { stats ->
                val timeUs = stats.time
                if (timeUs > 0L) {
                    reportProgress(timeUs / 1000.0, totalDurationMs, index, totalClips, listener)
                }
            }
        )
        activeSession = session
    }

    private fun reportProgress(timeMs: Double, totalDurationMs: Long, clipIndex: Int, totalClips: Int, listener: Listener) {
        if (totalDurationMs <= 0L) return
        val absolute = timeMs.coerceIn(0.0, totalDurationMs.toDouble())
        val progress = (absolute / totalDurationMs.toDouble()).toFloat().coerceIn(0f, 1f)
        if (progress >= lastProgress) {
            lastProgress = progress
            listener.onProgress(progress, clipIndex, totalClips)
        }
    }

    private fun segmentDurationMs(startMs: Long, totalDurationMs: Long): Long {
        val end = startMs + SEGMENT_DURATION_MS
        return if (end > totalDurationMs) (totalDurationMs - startMs).coerceAtLeast(0L) else SEGMENT_DURATION_MS
    }

    private fun readRotationDegrees(stream: StreamInformation): Int {
        val props = stream.allProperties ?: return 0
        props.optJSONArray("side_data_list")?.let { array ->
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i)
                val rotation = entry?.optString("rotation")?.toIntOrNull()
                if (rotation != null) return rotation
            }
        }
        props.optJSONObject("tags")?.optString("rotate")?.toIntOrNull()?.let { return it }
        return 0
    }

    companion object {
        private const val SEGMENT_DURATION_MS = 30_000L
        private const val MIN_CLIP_BYTES = 1_024L
    }
}

private val TIME_REGEX = Regex("""time=(\d{2}):(\d{2}):(\d{2}(?:\.\d+)?)""")
private val TIME_PLAIN_REGEX = Regex("""time=(\d+(?:\.\d+)?)""")

/**
 * Extracts the current position in seconds from an FFmpeg stats log line such as
 * "frame= 123 fps= 45 ... time=00:01:02.34 ...". Returns null when the line has no time.
 */
fun parseProgressTime(line: String): Double? {
    TIME_REGEX.find(line)?.let { match ->
        val hours = match.groupValues[1].toDouble()
        val minutes = match.groupValues[2].toDouble()
        val seconds = match.groupValues[3].toDouble()
        return hours * 3600.0 + minutes * 60.0 + seconds
    }
    TIME_PLAIN_REGEX.find(line)?.let { match ->
        return match.groupValues[1].toDouble()
    }
    return null
}
