package com.example.a4kwa.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.a4kwa.model.ProcessedClip
import java.io.File

object ShareManager {

    private val WHATSAPP_PACKAGES = arrayOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    private fun authorityFor(context: Context): String = "${context.packageName}.fileprovider"

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authorityFor(context), file)

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "video/mp4"
    }

    fun buildShareClipIntent(context: Context, file: File, fallbackTitle: String): Intent {
        val uri = uriFor(context, file)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return targetWhatsAppOrChooser(context, base, fallbackTitle)
    }

    fun buildShareAllIntent(context: Context, files: List<File>, fallbackTitle: String): Intent {
        val uris = files.map { uriFor(context, it) }
        val primaryType = if (files.any { it.extension.lowercase() in setOf("jpg", "jpeg", "png") }) "image/*" else "video/*"
        val base = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = primaryType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return targetWhatsAppOrChooser(context, base, fallbackTitle)
    }

    @JvmName("buildShareAllIntentFromProcessedClips")
    fun buildShareAllIntent(context: Context, clips: List<ProcessedClip>, fallbackTitle: String): Intent {
        val selectedFiles = clips.filter { it.selected }.map { it.file }
        return buildShareAllIntent(context, selectedFiles, fallbackTitle)
    }

    private fun targetWhatsAppOrChooser(context: Context, base: Intent, fallbackTitle: String): Intent {
        for (pkg in WHATSAPP_PACKAGES) {
            val targeted = base.clone() as Intent
            targeted.setPackage(pkg)
            if (context.packageManager.resolveActivity(targeted, 0) != null) {
                return targeted
            }
        }
        return Intent.createChooser(base, fallbackTitle)
    }
}
