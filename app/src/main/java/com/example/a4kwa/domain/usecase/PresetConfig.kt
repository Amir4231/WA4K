package com.example.a4kwa.domain.usecase

data class PresetConfig(
    val label: String,
    val description: String,
    val maxBitrateKbps: Int,
    val targetFileSizeMB: Int,
    val crfValue: String = "22",
    val ffmpegPreset: String = "veryfast"
) {
    companion object {
        val ULTRA_HD = PresetConfig("Best Quality", "Slowest, highest quality", maxBitrateKbps = 8000, targetFileSizeMB = 55, crfValue = "18", ffmpegPreset = "medium")
        val BALANCED = PresetConfig("Balanced", "Good quality, faster", maxBitrateKbps = 4000, targetFileSizeMB = 27, crfValue = "22", ffmpegPreset = "veryfast")
        val FAST_EXPORT = PresetConfig("Quick Export", "Fastest, smallest file", maxBitrateKbps = 2000, targetFileSizeMB = 14, crfValue = "26", ffmpegPreset = "ultrafast")

        val ALL = listOf(FAST_EXPORT, BALANCED, ULTRA_HD)
    }
}
