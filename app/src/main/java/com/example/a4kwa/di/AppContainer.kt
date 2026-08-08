package com.example.a4kwa.di

import android.content.Context
import com.example.a4kwa.data.hardware.HardwareProfileChecker
import com.example.a4kwa.data.media3.Media3TransformerWrapper
import com.example.a4kwa.data.repository.VideoTranscoderRepository
import com.example.a4kwa.domain.usecase.CalculateTargetBitrateUseCase
import com.example.a4kwa.domain.usecase.ProcessVideoUseCase
import com.example.a4kwa.domain.usecase.SplitVideoUseCase
import com.example.a4kwa.ffmpeg.VideoProcessor
import com.example.a4kwa.share.ShareManager

class AppContainer(context: Context) {

    val videoProcessor = VideoProcessor()
    val hardwareProfileChecker = HardwareProfileChecker()
    val media3Transformer = Media3TransformerWrapper(context.applicationContext)
    val shareManager: ShareManager get() = ShareManager

    val videoTranscoderRepository = VideoTranscoderRepository(
        media3Transformer = media3Transformer,
        videoProcessor = videoProcessor,
        hardwareChecker = hardwareProfileChecker
    )

    val calculateTargetBitrateUseCase = CalculateTargetBitrateUseCase()
    val splitVideoUseCase = SplitVideoUseCase()

    val processVideoUseCase = ProcessVideoUseCase(
        repository = videoTranscoderRepository,
        calculateBitrate = calculateTargetBitrateUseCase,
        splitVideo = splitVideoUseCase
    )
}
