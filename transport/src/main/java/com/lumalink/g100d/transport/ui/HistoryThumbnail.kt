/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicBoolean

/** Local files only. Never uses the camera protocol or the transfer stream. */
internal class HistoryThumbnailLoader(private val context: Context) {
    private val permits = Semaphore(2)
    private val cache = object : LruCache<HistoryThumbnailKey, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: HistoryThumbnailKey, value: Bitmap) = value.byteCount
    }
    fun clear() = cache.evictAll()
    suspend fun load(key: HistoryThumbnailKey): Bitmap? {
        cache.get(key)?.let { return it }
        return permits.withPermit {
            withContext(Dispatchers.IO) {
                cache.get(key)?.let { return@withContext it }
                coroutineScope {
                    val signal = CancellationSignal()
                    val finished = AtomicBoolean(false)
                    val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { if (!finished.get()) signal.cancel() }
                    }
                    try {
                        val uri = importedContentUri(context, key.uri)
                        val systemThumbnail = try { context.contentResolver.loadThumbnail(uri, Size(192, 192), signal) }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { null }
                        currentCoroutineContext().ensureActive()
                        val decoded = systemThumbnail ?: ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                            val (width, height) = historyThumbnailSize(info.size.width, info.size.height)
                            decoder.setTargetSize(width, height)
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                        currentCoroutineContext().ensureActive()
                        val (width, height) = historyThumbnailSize(decoded.width, decoded.height)
                        val bitmap = if (width == decoded.width && height == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, width, height, true)
                        cache.put(key, bitmap)
                        bitmap
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { null }
                    finally { finished.set(true); cancellation.cancel() }
                }
            }
        }
    }
}

@Composable internal fun HistoryThumbnail(item: TransferItem, loader: HistoryThumbnailLoader) {
    val key = historyThumbnailKey(item)
    val bitmap by produceState<Bitmap?>(null, key, loader) {
        value = null
        if (key != null) value = loader.load(key)
    }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFFE8E8DF)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), item.fileName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(if (item.mimeType.startsWith("video/")) Icons.Outlined.PlayCircle else Icons.Outlined.Image,
            "${item.fileName} 暂无缩略图", Modifier.size(24.dp), tint = Color(0xFF9CA797))
    }
}
