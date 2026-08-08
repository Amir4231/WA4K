package com.example.a4kwa.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.a4kwa.VideoApplication
import com.example.a4kwa.data.repository.VideoTranscoderRepository
import com.example.a4kwa.domain.usecase.ProcessRequest
import com.example.a4kwa.model.FilterPreset
import com.example.a4kwa.service.TranscodeForegroundService

class TranscodeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository: VideoTranscoderRepository
        get() = (applicationContext as VideoApplication).container.videoTranscoderRepository

    companion object {
        const val KEY_INPUT_PATH = "input_path"
        const val KEY_OUTPUT_DIR = "output_dir"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_ROTATION = "rotation"
        const val KEY_SRC_WIDTH = "src_width"
        const val KEY_SRC_HEIGHT = "src_height"
        const val KEY_FORCE_PORTRAIT = "force_portrait"
        const val KEY_BLUR_BG = "blur_bg"
        const val KEY_FILTER = "filter"
        const val KEY_PRESET_LABEL = "preset_label"
        const val KEY_MEDIA_TYPE = "media_type"
        const val KEY_OUTPUT_PATHS = "output_paths"
        private const val TAG = "TranscodeWorker"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = TranscodeForegroundService.createNotification(applicationContext, 0, 0, "Queued...")
        return ForegroundInfo(TranscodeForegroundService.NOTIFICATION_ID + 1, notification)
    }

    override suspend fun doWork(): Result {
        val data = inputData
        val inputPath = data.getString(KEY_INPUT_PATH) ?: return Result.failure()
        val outputDirPath = data.getString(KEY_OUTPUT_DIR) ?: return Result.failure()

        val request = ProcessRequest(
            inputFile = java.io.File(inputPath),
            outputDir = java.io.File(outputDirPath),
            durationMs = data.getLong(KEY_DURATION_MS, 0L),
            outputWidth = data.getInt(KEY_WIDTH, 1080),
            outputHeight = data.getInt(KEY_HEIGHT, 1920),
            rotationDegrees = data.getInt(KEY_ROTATION, 0),
            sourceWidth = data.getInt(KEY_SRC_WIDTH, 0),
            sourceHeight = data.getInt(KEY_SRC_HEIGHT, 0),
            forcePortrait = data.getBoolean(KEY_FORCE_PORTRAIT, true),
            blurBackground = data.getBoolean(KEY_BLUR_BG, false),
            filter = FilterPreset.NONE,
            presetLabel = data.getString(KEY_PRESET_LABEL) ?: "Balanced",
            mediaType = data.getString(KEY_MEDIA_TYPE) ?: "video"
        )

        setForeground(getForegroundInfo())

        return try {
            val result = repository.transcode(request)
            result.fold(
                onSuccess = { files ->
                    val outputData = Data.Builder()
                        .putStringArray(KEY_OUTPUT_PATHS, files.map { it.absolutePath }.toTypedArray())
                        .build()
                    Result.success(outputData)
                },
                onFailure = { Result.failure() }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Transcode failed", e)
            Result.failure()
        }
    }
}
