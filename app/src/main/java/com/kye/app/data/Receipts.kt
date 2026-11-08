package com.kye.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Receipt photos: stored privately in files/receipts (not in the gallery), shrunk to at most
 * 1600 px on the long side as ~80% JPEG, usually 150–400 KB.
 */
object Receipts {
    private const val MAX_SIDE = 1600
    private const val AUTHORITY_SUFFIX = ".files"

    fun dir(context: Context): File = File(context.filesDir, "receipts").apply { mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), fileName)

    /** Copies a picked or captured photo into receipts, shrunk. Returns the new file name. */
    fun import(context: Context, source: Uri): String {
        val bitmap = decode(context, source) ?: throw UserError("Couldn't read that photo")
        val name = "r-" + UUID.randomUUID().toString().take(12) + ".jpg"
        file(context, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        bitmap.recycle()
        return name
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            // Applies the photo's rotation, and decodes straight to a smaller size.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    }.getOrNull()

    /** A fresh file + content Uri for the camera app to write into. */
    fun cameraTarget(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "shot-${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, f)
    }

    /** A content Uri other apps (WhatsApp, Gmail…) can read, for sharing a receipt. */
    fun shareUri(context: Context, fileName: String): Uri =
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file(context, fileName))
}
