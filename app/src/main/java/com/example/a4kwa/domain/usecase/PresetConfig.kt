package com.example.a4kwa.domain.usecase

data class PresetConfig(
    val label: String,
    val description: String,
    val maxBitrateKbps: Int,
    val targetFileSizeMB: Int,
    val crfValue: String = "18"
) {
    companion object {
        val ULTRA_HD = PresetConfig("Ultra HD Status", "1080p Max Bitrate", maxBitrateKbps = 8000, targetFileSizeMB = 55, crfValue = "16")
        val BALANCED = PresetConfig("Balanced", "Optimized to skip WA re-encode", maxBitrateKbps = 4000, targetFileSizeMB = 27, crfValue = "20")
        val FAST_EXPORT = PresetConfig("Fast Export", "Data Saver", maxBitrateKbps = 2000, targetFileSizeMB = 14, crfValue = "24")

        val ALL = listOf(ULTRA_HD, BALANCED, FAST_EXPORT)
    }
}
