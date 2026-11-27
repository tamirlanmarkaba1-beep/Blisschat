package com.bliss.blisschat.data.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

data class StickerPack(val id: String, val name: String, val stickers: List<File>)

class StickerStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "stickers").apply { mkdirs() }
    private val _packs = MutableStateFlow(load())
    val packs: StateFlow<List<StickerPack>> = _packs.asStateFlow()

    private fun load(): List<StickerPack> =
        root.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.lastModified() }.map { dir ->
            StickerPack(
                dir.name,
                File(dir, "name.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty().ifBlank { "Набор" },
                dir.listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }.toList()
            )
        }

    private fun refresh() {
        _packs.value = load()
    }

    fun createPack(name: String): StickerPack {
        val id = UUID.randomUUID().toString().take(8)
        val dir = File(root, id).apply { mkdirs() }
        File(dir, "name.txt").writeText(name.trim().take(30).ifBlank { "Мой набор" })
        refresh()
        return _packs.value.first { it.id == id }
    }

    fun rename(packId: String, name: String) {
        File(File(root, packId), "name.txt").writeText(name.trim().take(30).ifBlank { "Мой набор" })
        refresh()
    }

    fun deletePack(packId: String) {
        File(root, packId).deleteRecursively()
        refresh()
    }

    fun removeSticker(file: File) {
        file.delete()
        refresh()
    }

    suspend fun addFromUri(packId: String, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = app.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
            var bmp = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Не удалось открыть изображение")
            val rotation = runCatching {
                resolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            }.getOrDefault(0f)
            var side = 512
            var bytes: ByteArray
            do {
                val scale = side.toFloat() / maxOf(bmp.width, bmp.height)
                val m = Matrix().apply {
                    if (scale < 1f) postScale(scale, scale)
                    if (rotation != 0f && side == 512) postRotate(rotation)
                }
                val scaled = if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                bytes = out.toByteArray()
                if (bytes.size > 300_000) {
                    bmp = scaled
                    side = (side * 0.8).toInt()
                } else break
            } while (side >= 160)
            saveBytes(packId, bytes)
        }
    }

    fun addBytes(packId: String, data: ByteArray): Result<Unit> = runCatching {
        val bmp = BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Это не изображение")
        val scale = 512f / maxOf(bmp.width, bmp.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        saveBytes(packId, out.toByteArray())
    }

    private fun saveBytes(packId: String, bytes: ByteArray) {
        val dir = File(root, packId)
        require(dir.exists()) { "Набор не найден" }
        require(dir.listFiles { f -> f.extension == "png" }.orEmpty().size < 500) { "В наборе не больше 500 стикеров" }
        File(dir, "${System.currentTimeMillis()}_${(Math.random() * 100000).toInt()}.png").writeBytes(bytes)
        refresh()
    }
}
