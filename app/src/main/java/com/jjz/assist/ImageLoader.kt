package com.jjz.assist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileInputStream

/**
 * 取图分两步走：先把分享过来的图复制到 App 自己的缓存里，再从本地文件解码。
 * 这样不依赖系统给的临时授权，授权过期、URI 失效都不会导致读不到图。
 */
object ImageLoader {

    fun decode(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? {
        val file = cacheCopy(context, uri) ?: return null
        return decodeFile(file, maxSide)
    }

    fun decodeFile(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        while (side / sample > maxSide) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
    }

    /** 复制到 cache/share 下，返回本地文件；读不到就返回 null */
    private fun cacheCopy(context: Context, uri: Uri): File? {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.lastModified() < System.currentTimeMillis() - 86400_000) it.delete() }
        val out = File(dir, "shared_${System.currentTimeMillis()}.img")

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }

        if (out.length() <= 0) {
            // 有些相册只肯给文件描述符，再试一次
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { fis ->
                        out.outputStream().use { fis.copyTo(it) }
                    }
                }
            }
        }
        return if (out.length() > 0) out else null
    }
}
