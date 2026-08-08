package com.example.a4kwa

import com.example.a4kwa.ffmpeg.FfmpegCommandBuilder
import com.example.a4kwa.ffmpeg.parseProgressTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegCommandBuilderTest {

    @Test
    fun buildSegmentCommand_startsWithOverwriteAndInputSeek() {
        val args = FfmpegCommandBuilder.buildSegmentCommand(
            inputPath = "/in/video.mp4",
            outputPath = "/out/clip_1.mp4",
            startMs = 30_000,
            outputWidth = 1080,
            outputHeight = 1920
        )

        assertEquals("-y", args[0])
        assertTrue(args.indexOf("-ss") < args.indexOf("-i"))
        assertEquals("30", args[args.indexOf("-ss") + 1])
    }

    @Test
    fun buildSegmentCommand_encodesH264MediumCrf18() {
        val args = FfmpegCommandBuilder.buildSegmentCommand(
            inputPath = "/in/video.mp4",
            outputPath = "/out/clip_1.mp4",
            startMs = 0,
            outputWidth = 1080,
            outputHeight = 1920,
            crfValue = "18",
            ffmpegPreset = "medium"
        )

        assertEquals("libx264", args[args.indexOf("-c:v") + 1])
        assertEquals("medium", args[args.indexOf("-preset") + 1])
        assertEquals("18", args[args.indexOf("-crf") + 1])
        assertEquals("high", args[args.indexOf("-profile:v") + 1])
        assertEquals("yuv420p", args[args.indexOf("-pix_fmt") + 1])
    }

    @Test
    fun buildSegmentCommand_encodesAacAudioAndClipsAtThirtySeconds() {
        val args = FfmpegCommandBuilder.buildSegmentCommand(
            inputPath = "/in/video.mp4",
            outputPath = "/out/clip_1.mp4",
            startMs = 0,
            outputWidth = 1080,
            outputHeight = 1920
        )

        assertEquals("aac", args[args.indexOf("-c:a") + 1])
        assertEquals("128k", args[args.indexOf("-b:a") + 1])
        assertEquals("30", args[args.indexOf("-t") + 1])
        assertTrue(args.contains("-map"))
        assertTrue(args.contains("0:a:0?"))
        assertTrue(args.contains("+faststart"))
    }

    @Test
    fun buildFilter_padsLandscapeToVertical1080p() {
        val filter = FfmpegCommandBuilder.buildFilter(1080, 1920)

        assertTrue(filter.contains("scale=1080:1920:force_original_aspect_ratio=increase:flags=lanczos"))
        assertTrue(filter.contains("crop=1080:1920"))
        assertTrue(filter.contains("setsar=1"))
    }

    @Test
    fun buildFilter_supports4kResolution() {
        val filter = FfmpegCommandBuilder.buildFilter(2160, 3840)

        assertTrue(filter.contains("scale=2160:3840"))
        assertTrue(filter.contains("crop=2160:3840"))
    }

    @Test
    fun buildFilter_rotatesNinetyDegreeVideoUpright() {
        val filter = FfmpegCommandBuilder.buildFilter(1080, 1920, rotationDegrees = 90)

        assertTrue(filter.startsWith("transpose=1,"))
    }

    @Test
    fun buildFilter_rotatesTwoHundredSeventyDegreeVideoUpright() {
        val filter = FfmpegCommandBuilder.buildFilter(1080, 1920, rotationDegrees = 270)

        assertTrue(filter.startsWith("transpose=2,"))
    }

    @Test
    fun buildFilter_ignoresZeroRotation() {
        val filter = FfmpegCommandBuilder.buildFilter(1080, 1920, rotationDegrees = 0)

        assertTrue(!filter.contains("transpose"))
    }

    @Test
    fun buildFilter_preservesLandscapeWhenForcePortraitDisabled() {
        val filter = FfmpegCommandBuilder.buildFilter(
            outputWidth = 1080,
            outputHeight = 1920,
            sourceWidth = 1920,
            sourceHeight = 1080,
            forcePortrait = false
        )

        assertTrue(filter.contains("scale=1920:1080"))
        assertTrue(!filter.contains("crop"))
    }

    @Test
    fun buildFilter_cropsLandscapeWhenForcePortraitEnabled() {
        val filter = FfmpegCommandBuilder.buildFilter(
            outputWidth = 1080,
            outputHeight = 1920,
            sourceWidth = 1920,
            sourceHeight = 1080,
            forcePortrait = true
        )

        assertTrue(filter.contains("scale=1080:1920"))
        assertTrue(filter.contains("force_original_aspect_ratio=increase"))
        assertTrue(filter.contains("crop=1080:1920"))
    }

    @Test
    fun buildFilter_fitsLandscapeWhenForcePortraitDisabled() {
        val filter = FfmpegCommandBuilder.buildFilter(
            outputWidth = 1080,
            outputHeight = 1920,
            sourceWidth = 1920,
            sourceHeight = 1080,
            forcePortrait = false
        )

        assertTrue(filter.contains("scale=1920:1080"))
        assertTrue(filter.contains("force_original_aspect_ratio=decrease"))
        assertTrue(!filter.contains("crop"))
    }

    @Test
    fun parseProgressTime_extractsTimestamp() {
        val line = "frame=  123 fps= 45 q=28.0 size=    5120kB time=00:01:02.34 bitrate= 8.2Mbits/s speed=1.5x"

        val time = parseProgressTime(line)

        assertEquals(62.34, time!!, 0.001)
    }

    @Test
    fun parseProgressTime_returnsNullForUnrelatedLine() {
        assertNull(parseProgressTime("  Duration: 00:01:02.34, start: 0.000000, bitrate: 8454 kb/s"))
    }

    @Test
    fun buildSegmentCommand_customDuration() {
        val args = FfmpegCommandBuilder.buildSegmentCommand(
            inputPath = "/in/video.mp4",
            outputPath = "/out/clip.mp4",
            startMs = 10_000,
            outputWidth = 1080,
            outputHeight = 1920,
            segmentDurationMs = 15_000
        )
        assertEquals("10", args[args.indexOf("-ss") + 1])
        assertEquals("15", args[args.indexOf("-t") + 1])
    }
}
