package com.example.a4kwa.ffmpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import com.example.a4kwa.model.FilterPreset
import com.example.a4kwa.model.MediaType
import com.example.a4kwa.model.ProcessedClip
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class PhotoProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)

object PhotoProcessor {

    fun readDimensions(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            throw PhotoProcessingException("Cannot decode image dimensions")
        }
        return opts.outWidth to opts.outHeight
    }

    fun readExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(FileInputStream(file))
            when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: IOException) {
            0
        }
    }

    fun process(
        input: File,
        outputFile: File,
        outputWidth: Int,
        outputHeight: Int,
        forcePortrait: Boolean = true,
        blurBackground: Boolean = false,
        filter: FilterPreset = FilterPreset.NONE,
        sharpen: Boolean = false,
        denoise: Boolean = false,
        autoLevels: Boolean = false,
        deblock: Boolean = false
    ): ProcessedClip {
        var source = BitmapFactory.decodeFile(input.absolutePath)
            ?: throw PhotoProcessingException("Cannot decode image")

        val rotation = readExifRotation(input)
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) source.recycle()
            source = rotated
        }

        val isLandscape = source.width >= source.height
        val keepOriginal = !forcePortrait && isLandscape
        val targetW = if (keepOriginal) outputHeight else outputWidth
        val targetH = if (keepOriginal) outputWidth else outputHeight

        val scale = if (forcePortrait) maxOf(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
                    else minOf(targetW.toFloat() / source.width, targetH.toFloat() / source.height)
        val scaledW = (source.width * scale + 0.5f).toInt()
        val scaledH = (source.height * scale + 0.5f).toInt()
        val scaled = if (scaledW != source.width || scaledH != source.height) {
            val s = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
            source.recycle()
            s
        } else {
            source
        }

        val canvasW = if (forcePortrait) targetW else scaledW
        val canvasH = if (forcePortrait) targetH else scaledH
        val drawX = if (forcePortrait) (targetW - scaledW) / 2f else 0f
        val drawY = if (forcePortrait) (targetH - scaledH) / 2f else 0f

        val output = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        if (forcePortrait && blurBackground) {
            val smallW = maxOf(source.width / 30, 4)
            val smallH = maxOf(source.height / 30, 4)
            val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
            val blurBg = Bitmap.createScaledBitmap(small, targetW, targetH, false)
            small.recycle()
            canvas.drawBitmap(blurBg, 0f, 0f, null)
            blurBg.recycle()
        } else if (forcePortrait) {
            canvas.drawColor(Color.BLACK)
        }

        canvas.drawBitmap(scaled, drawX, drawY, null)
        scaled.recycle()

        if (filter != FilterPreset.NONE) {
            applyFilter(output, filter)
        }
        if (sharpen || denoise || autoLevels || deblock) {
            applyEnhancements(output, sharpen, denoise, autoLevels, deblock)
        }

        val parent = outputFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        FileOutputStream(outputFile).use { stream ->
            if (!output.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                output.recycle()
                throw PhotoProcessingException("Failed to compress image")
            }
        }
        output.recycle()

        return ProcessedClip(
            file = outputFile,
            index = 0,
            startMs = 0,
            durationMs = 0,
            totalClips = 1,
            mediaType = MediaType.IMAGE
        )
    }

    private fun applyFilter(bitmap: Bitmap, filter: FilterPreset) {
        applyFilterToBitmap(bitmap, filter)
    }

    fun applyFilterToBitmap(bitmap: Bitmap, filter: FilterPreset) {
        applyFilterImpl(bitmap, filter)
    }

    fun applyEnhancementsPreview(bitmap: Bitmap, sharpen: Boolean, denoise: Boolean, autoLevels: Boolean, deblock: Boolean) {
        applyEnhancements(bitmap, sharpen, denoise, autoLevels, deblock)
    }

    private fun applyFilterImpl(bitmap: Bitmap, filter: FilterPreset) {
        val colorMatrix = when (filter) {
            FilterPreset.CINEMATIC -> ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, -5f,
                0f, 1.1f, 0f, 0f, -5f,
                0f, 0f, 0.85f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.VIBRANT -> ColorMatrix(floatArrayOf(
                1.05f, 0f, 0f, 0f, 5f,
                0f, 1.3f, 0f, 0f, 0f,
                0f, 0f, 1.05f, 0f, 5f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.WARM -> ColorMatrix(floatArrayOf(
                1.08f, 0f, 0f, 0f, 10f,
                0f, 0.95f, 0f, 0f, -5f,
                0f, 0f, 0.85f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.COOL -> ColorMatrix(floatArrayOf(
                0.9f, 0f, 0f, 0f, -10f,
                0f, 0.98f, 0f, 0f, -2f,
                0f, 0f, 1.12f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.VINTAGE -> ColorMatrix(floatArrayOf(
                0.95f, 0.03f, 0.02f, 0f, 5f,
                0.04f, 0.95f, 0.01f, 0f, 5f,
                0.02f, 0.01f, 0.7f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterPreset.NOIR -> ColorMatrix().apply {
                setSaturation(0f)
                setScale(1.15f, 1.15f, 1.15f, 1f)
            }
            else -> return
        }

        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        val temp = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        Canvas(bitmap).apply {
            drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            drawBitmap(temp, 0f, 0f, paint)
        }
        temp.recycle()
    }

    private fun applyEnhancements(bitmap: Bitmap, sharpen: Boolean, denoise: Boolean, autoLevels: Boolean, deblock: Boolean) {
        if (sharpen) sharpenBitmap(bitmap)
        if (denoise) {
            val median = medianFilter(bitmap)
            bitmap.eraseColor(Color.TRANSPARENT)
            android.graphics.Canvas(bitmap).drawBitmap(median, 0f, 0f, null)
            median.recycle()
        }
        if (autoLevels) autoLevelBitmap(bitmap)
        if (deblock) deblockBitmap(bitmap)
    }

    private fun sharpenBitmap(bitmap: Bitmap) {
        val w = bitmap.width; val h = bitmap.height
        val small = Bitmap.createScaledBitmap(bitmap, w / 4, h / 4, true)
        val blur = Bitmap.createScaledBitmap(small, w, h, true)
        small.recycle()
        val srcPx = IntArray(w * h); val blurPx = IntArray(w * h)
        bitmap.getPixels(srcPx, 0, w, 0, 0, w, h)
        blur.getPixels(blurPx, 0, w, 0, 0, w, h)
        for (i in srcPx.indices) {
            val sr = (srcPx[i] shr 16) and 0xFF; val sg = (srcPx[i] shr 8) and 0xFF; val sb = srcPx[i] and 0xFF
            val br = (blurPx[i] shr 16) and 0xFF; val bg = (blurPx[i] shr 8) and 0xFF; val bb = blurPx[i] and 0xFF
            val rr = (sr * 1.5f - br * 0.5f).toInt().coerceIn(0, 255)
            val rg = (sg * 1.5f - bg * 0.5f).toInt().coerceIn(0, 255)
            val rb = (sb * 1.5f - bb * 0.5f).toInt().coerceIn(0, 255)
            srcPx[i] = (0xFF shl 24) or (rr shl 16) or (rg shl 8) or rb
        }
        bitmap.setPixels(srcPx, 0, w, 0, 0, w, h)
        blur.recycle()
    }

    private fun deblockBitmap(bitmap: Bitmap) {
        val w = bitmap.width; val h = bitmap.height
        val small = Bitmap.createScaledBitmap(bitmap, w / 2, h / 2, true)
        bitmap.eraseColor(Color.TRANSPARENT)
        val restored = Bitmap.createScaledBitmap(small, w, h, false)
        android.graphics.Canvas(bitmap).drawBitmap(restored, 0f, 0f, null)
        small.recycle(); restored.recycle()
    }

    private fun medianFilter(source: Bitmap): Bitmap {
        val w = source.width; val h = source.height
        val result = Bitmap.createBitmap(w, h, source.config ?: Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(w * h); source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        val dstPixels = IntArray(w * h)
        val r = IntArray(9); val g = IntArray(9); val b = IntArray(9)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = (x + dx).coerceIn(0, w - 1); val ny = (y + dy).coerceIn(0, h - 1)
                    val pixel = srcPixels[ny * w + nx]
                    r[n] = (pixel shr 16) and 0xFF; g[n] = (pixel shr 8) and 0xFF; b[n] = pixel and 0xFF; n++
                }
                r.sort(); g.sort(); b.sort()
                dstPixels[y * w + x] = (0xFF shl 24) or (r[4] shl 16) or (g[4] shl 8) or b[4]
            }
        }
        result.setPixels(dstPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun autoLevelBitmap(bitmap: Bitmap) {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h); bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var minR = 255; var maxR = 0; var minG = 255; var maxG = 0; var minB = 255; var maxB = 0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            if (r < minR) minR = r; if (r > maxR) maxR = r
            if (g < minG) minG = g; if (g > maxG) maxG = g
            if (b < minB) minB = b; if (b > maxB) maxB = b
        }
        if (maxR <= minR) maxR = minR + 1; if (maxG <= minG) maxG = minG + 1; if (maxB <= minB) maxB = minB + 1
        for (i in pixels.indices) {
            val r = ((pixels[i] shr 16) and 0xFF); val g = ((pixels[i] shr 8) and 0xFF); val b = pixels[i] and 0xFF
            val nr = ((r - minR) * 255 / (maxR - minR)).coerceIn(0, 255)
            val ng = ((g - minG) * 255 / (maxG - minG)).coerceIn(0, 255)
            val nb = ((b - minB) * 255 / (maxB - minB)).coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
