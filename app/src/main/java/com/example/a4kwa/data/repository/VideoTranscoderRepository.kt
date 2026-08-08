package com.example.a4kwa.data.repository

import android.util.Log
import com.example.a4kwa.data.hardware.HardwareProfileChecker
import com.example.a4kwa.data.media3.Media3TransformerWrapper
import com.example.a4kwa.data.media3.TranscodeConfig
import com.example.a4kwa.domain.usecase.ProcessRequest
import com.example.a4kwa.ffmpeg.VideoProcessor
import com.example.a4kwa.model.ProcessedClip
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

class VideoTranscoderRepository(
    private val media3Transformer: Media3TransformerWrapper,
    private val videoProcessor: VideoProcessor,
    private val hardwareChecker: HardwareProfileChecker
) {
    companion object {
        private const val TAG = "VideoTranscoderRepo"
    }

    suspend fun transcode(request: ProcessRequest): Result<List<File>> {
        val useMedia3 = hardwareChecker.shouldUseMedia3()
        Log.d(TAG, "Encoding engine: ${if (useMedia3) "Media3" else "FFmpeg"}")

        return try {
            if (useMedia3) {
                transcodeWithMedia3(request)
            } else {
                transcodeWithFfmpeg(request)
            }
        } catch (e: Exception) {
            if (useMedia3) {
                Log.w(TAG, "Media3 failed, falling back to FFmpeg", e)
                try {
                    transcodeWithFfmpeg(request)
                } catch (ee: Exception) {
                    Result.failure(ee)
                }
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun transcodeWithMedia3(request: ProcessRequest): Result<List<File>> {
        request.outputDir.mkdirs()
        val segmentDurationMs = 30_000L
        val totalSegments = ((request.durationMs + segmentDurationMs - 1) / segmentDurationMs).toInt().coerceAtLeast(1)
        val outputFiles = mutableListOf<File>()

        for (i in 0 until totalSegments) {
            val outFile = File(request.outputDir, "clip_${i + 1}.mp4")
            val config = TranscodeConfig(
                inputFile = request.inputFile,
                outputFile = outFile,
                outputWidth = request.outputWidth,
                outputHeight = request.outputHeight,
                bitrateKbps = when (request.presetLabel) {
                    "Ultra HD Status" -> 8000
                    "Fast Export" -> 2000
                    else -> 4000
                }
            )
            media3Transformer.transcode(config)
            if (outFile.exists() && outFile.length() > 1024) outputFiles.add(outFile)
        }
        return if (outputFiles.isEmpty()) Result.failure(Exception("No clips produced")) else Result.success(outputFiles)
    }

    private suspend fun transcodeWithFfmpeg(request: ProcessRequest): Result<List<File>> = suspendCancellableCoroutine { cont ->
        videoProcessor.start(
            inputFile = request.inputFile,
            outputDir = request.outputDir,
            totalDurationMs = request.durationMs,
            outputWidth = request.outputWidth,
            outputHeight = request.outputHeight,
            rotationDegrees = request.rotationDegrees,
            sourceWidth = request.sourceWidth,
            sourceHeight = request.sourceHeight,
            forcePortrait = request.forcePortrait,
            blurBackground = request.blurBackground,
            filter = request.filter,
            sharpen = request.sharpen,
            denoise = request.denoise,
            autoLevels = request.autoLevels,
            deblock = request.deblock,
            listener = object : VideoProcessor.Listener {
                override fun onProgress(progress: Float, clipIndex: Int, totalClips: Int) {}
                override fun onSegmentComplete(clip: ProcessedClip) {}
                override fun onComplete(clips: List<ProcessedClip>) {
                    if (cont.isActive) cont.resume(Result.success(clips.map { it.file }))
                }
                override fun onCancelled() {
                    if (cont.isActive) cont.resume(Result.success(emptyList()))
                }
                override fun onError(message: String) {
                    if (cont.isActive) cont.resume(Result.failure(Exception(message)))
                }
            }
        )
    }
}
