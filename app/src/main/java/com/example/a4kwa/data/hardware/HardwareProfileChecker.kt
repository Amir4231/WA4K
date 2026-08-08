package com.example.a4kwa.data.hardware

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

data class EncoderCapability(
    val codecName: String,
    val mimeType: String,
    val isHardware: Boolean,
    val supportsHighProfile: Boolean,
    val maxSupportedWidth: Int,
    val maxSupportedHeight: Int
)

class HardwareProfileChecker {

    fun getAvailableVideoEncoders(): List<EncoderCapability> {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        return codecList.codecInfos
            .filter { it.isEncoder }
            .flatMap { info ->
                info.supportedTypes
                    .filter { it.startsWith("video/") }
                    .map { mime -> buildCapability(info, mime) }
            }
    }

    fun supports4KEncoding(): Boolean {
        val encoders = getAvailableVideoEncoders()
        return encoders.any { encoder ->
            encoder.supportsHighProfile &&
            encoder.maxSupportedWidth >= 3840 &&
            encoder.maxSupportedHeight >= 2160
        }
    }

    fun shouldUseMedia3(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val encoders = getAvailableVideoEncoders()
        val hasHwEncoder = encoders.any { it.isHardware && it.supportsHighProfile && it.mimeType == "video/avc" }
        return hasHwEncoder
    }

    private fun buildCapability(info: MediaCodecInfo, mime: String): EncoderCapability {
        val caps = info.getCapabilitiesForType(mime)
        val profileLevels = caps.profileLevels
        val hasHighProfile = profileLevels?.any { profile ->
            when (mime) {
                "video/avc" -> profile.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                "video/hevc" -> profile.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
                else -> false
            }
        } ?: false

        val vidCaps = caps.videoCapabilities
        val maxW = vidCaps?.supportedWidths?.upper ?: 4096
        val maxH = vidCaps?.supportedHeights?.upper ?: 4096

        return EncoderCapability(
            codecName = info.name,
            mimeType = mime,
            isHardware = !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android."),
            supportsHighProfile = hasHighProfile,
            maxSupportedWidth = maxW,
            maxSupportedHeight = maxH
        )
    }
}
