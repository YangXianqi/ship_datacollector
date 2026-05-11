package com.shipyard.collector.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class LocalMediaManager(private val context: Context) {

    fun createPhotoCaptureTarget(): MediaTarget {
        val file = File(photoDir(), "photo_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        file.parentFile?.mkdirs()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return MediaTarget(file.absolutePath, uri)
    }

    fun createAudioFile(): File {
        val file = File(audioDir(), "audio_${System.currentTimeMillis()}_${UUID.randomUUID()}.m4a")
        file.parentFile?.mkdirs()
        return file
    }

    fun copyImportedImage(source: Uri): String {
        val extension = extensionFromMime(context.contentResolver.getType(source)) ?: "jpg"
        val destination = File(photoDir(), "import_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
        destination.parentFile?.mkdirs()
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "无法读取选中的图片" }
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
        return destination.absolutePath
    }

    fun deleteIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        File(path).takeIf(File::exists)?.delete()
    }

    fun rotateImage(path: String, degrees: Float) {
        transformImage(path) { source ->
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }

    fun cropImageCenter(path: String) {
        transformImage(path) { source ->
            val edge = minOf(source.width, source.height)
            val x = (source.width - edge) / 2
            val y = (source.height - edge) / 2
            Bitmap.createBitmap(source, x, y, edge, edge)
        }
    }

    private fun photoDir(): File = File(context.filesDir, "media/photos")
    private fun audioDir(): File = File(context.filesDir, "media/audio")

    private fun extensionFromMime(mimeType: String?): String? = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        else -> "jpg"
    }

    private fun transformImage(path: String, block: (Bitmap) -> Bitmap) {
        val sourceFile = File(path)
        require(sourceFile.exists()) { "找不到图片文件" }

        val source = BitmapFactory.decodeFile(path) ?: error("无法读取图片")
        val transformed = try {
            block(source)
        } finally {
            if (!source.isRecycled) {
                source.recycle()
            }
        }

        try {
            val extension = sourceFile.extension.lowercase()
            val format = when (extension) {
                "png" -> Bitmap.CompressFormat.PNG
                "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.JPEG
            }
            FileOutputStream(sourceFile, false).use { output ->
                check(transformed.compress(format, 92, output)) { "写入图片失败" }
            }
        } finally {
            if (!transformed.isRecycled) {
                transformed.recycle()
            }
        }
    }

    data class MediaTarget(
        val filePath: String,
        val uri: Uri
    )
}
