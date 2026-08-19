package com.pandian.tobacco

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File

object ImageStorage {
    fun saveNormalized(
        context: Context,
        source: Uri,
        directoryName: String,
        fileName: String
    ): String? = runCatching {
        val directory = File(context.filesDir, directoryName).apply { mkdirs() }
        val target = File(directory, "${fileName.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.img")
        val orientation = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            copySource(context, source, target)
        } else {
            val bitmap = decodeSampled(context, source, 2048) ?: error("无法解析照片")
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
                }
            }
            val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            target.outputStream().buffered().use { output ->
                check(corrected.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "照片保存失败" }
            }
            if (corrected !== bitmap) bitmap.recycle()
            corrected.recycle()
        }
        target.absolutePath
    }.getOrNull()

    private fun copySource(context: Context, source: Uri, target: File) {
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取所选照片")
    }

    private fun decodeSampled(context: Context, source: Uri, maximumSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maximumSide || bounds.outHeight / sample > maximumSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        return context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
