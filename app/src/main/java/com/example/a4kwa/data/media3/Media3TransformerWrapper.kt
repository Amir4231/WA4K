package com.example.a4kwa.data.media3

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TranscodeConfig(
    val inputFile: File,
    val outputFile: File,
    val outputWidth: Int,
    val outputHeight: Int,
    val bitrateKbps: Int = 4000,
    val enableHdrToneMapping: Boolean = false
)

class Media3TransformerWrapper(
    private val context: Context
) {
    companion object {
        private const val TAG = "Media3Transformer"
    }

    suspend fun transcode(config: TranscodeConfig): File = suspendCancellableCoroutine { continuation ->
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

        val mediaItem = MediaItem.fromUri(config.inputFile.toURI().toString())
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(false)
            .build()

        val composition = Composition.Builder(
            EditedMediaItemSequence(editedMediaItem)
        ).build()

        transformer.start(composition, config.outputFile.absolutePath)
        transformer.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Log.d(TAG, "Transcode completed: ${config.outputFile.absolutePath}")
                if (continuation.isActive) continuation.resume(config.outputFile)
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                Log.e(TAG, "Transcode failed", exportException)
                if (continuation.isActive) continuation.resumeWithException(exportException)
            }
        })
    }
}
