package com.example.a4kwa.domain.usecase

data class BitrateEstimate(val targetKbps: Int, val estimatedFileSizeMB: Float, val exceedsWhatsAppLimit: Boolean) {
    companion object {
        const val WHATSAPP_LIMIT_MB = 64f
    }
}

class CalculateTargetBitrateUseCase {

    fun forPreset(preset: PresetConfig, durationSeconds: Float): BitrateEstimate {
        val targetKbps = preset.maxBitrateKbps
        val estimatedMB = (targetKbps * durationSeconds) / 8192f
        return BitrateEstimate(targetKbps, estimatedMB, estimatedMB > BitrateEstimate.WHATSAPP_LIMIT_MB)
    }

    fun forTargetSize(targetSizeMB: Float, durationSeconds: Float): BitrateEstimate {
        val targetKbps = ((targetSizeMB * 8192f) / durationSeconds).toInt().coerceIn(500, 10000)
        val estimatedMB = (targetKbps * durationSeconds) / 8192f
        return BitrateEstimate(targetKbps, estimatedMB, estimatedMB > BitrateEstimate.WHATSAPP_LIMIT_MB)
    }
}
