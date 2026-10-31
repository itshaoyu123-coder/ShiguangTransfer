/* Modified 2026-10-30 by Shiguang Transfer contributors; derived from LumaLink progressive.3, Apache-2.0. */
package com.lumalink.g100d.transport.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumalink.g100d.core.data.TransferEntity
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.core.data.TransferState
import com.lumalink.g100d.transport.transfer.TransferService
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransferItem(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val bytesDownloaded: Long,
    val speedBytesPerSecond: Long,
    val state: TransferState,
    val errorMessage: String?,
    val localUri: String?,
    val mimeType: String,
    val updatedAt: Long,
    val createdAt: Long = updatedAt,
    val cameraPath: String = "",
) {
    val progress: Float?
        get() = if (sizeBytes > 0L) {
            (bytesDownloaded.toFloat() / sizeBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    val etaSeconds: Long?
        get() = if (speedBytesPerSecond > 0L && sizeBytes > bytesDownloaded) {
            (sizeBytes - bytesDownloaded) / speedBytesPerSecond
        } else {
            null
        }
}

data class TransfersUiState(
    val items: List<TransferItem> = emptyList(),
    val completedImports: Set<ImportedPhotoKey> = emptySet(),
) {
    val activeCount: Int
        get() = items.count { it.state == TransferState.QUEUED || it.state == TransferState.RUNNING }

    val currentSpeed: Long
        get() = items.filter { it.state == TransferState.RUNNING }.sumOf { it.speedBytesPerSecond }

    val completedToday: Int
        get() {
            val startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            return items.count { it.state == TransferState.COMPLETED && it.updatedAt >= startOfDay }
        }
}

class TransfersViewModel(
    private val repository: TransferRepository,
    private val appContext: Context,
) : ViewModel() {
    private val _actionMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage
    fun clearActionMessage() { _actionMessage.value = null }
    private val deletingIds = mutableSetOf<String>()

    val uiState: StateFlow<TransfersUiState> = repository.transfers
        .map { entities ->
            val items = entities.map(TransferEntity::toItem)
            TransfersUiState(items, completedImportKeys(items))
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransfersUiState(),
        )

    fun cancel(id: String) {
        viewModelScope.launch {
            repository.markCanceled(id)
            TransferService.cancel(appContext, id)
        }
    }

    fun retry(id: String) {
        if (id in deletingIds) return
        viewModelScope.launch {
            repository.retry(id)
            TransferService.retry(appContext, id)
        }
    }

    fun delete(id: String, deleteLocalFile: Boolean) {
        deleteMany(listOf(id), deleteLocalFile)
    }

    fun deleteMany(ids: List<String>, deleteLocalFile: Boolean) {
        viewModelScope.launch {
            var removed = 0
            val failures = mutableListOf<String>()
            for (id in ids.distinct()) {
                if (!deletingIds.add(id)) continue
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { deleteRecord(id, deleteLocalFile) }
                    removed++
                } catch (error: kotlinx.coroutines.CancellationException) { throw error }
                catch (error: Exception) { failures += error.message ?: "删除失败" }
                finally { deletingIds.remove(id) }
            }
            _actionMessage.value = if (failures.isEmpty()) "已删除 $removed 条记录" else "已删除 $removed 条；${failures.size} 条保留：${failures.first()}"
        }
    }

    private suspend fun deleteRecord(id: String, deleteLocalFile: Boolean) {
        val entity = repository.findById(id)
        require(entity == null || (entity.state != TransferState.RUNNING && entity.state != TransferState.QUEUED)) { "请先取消进行中的导入，再删除记录" }
        if (deleteLocalFile) {
            entity?.localUri?.let { deleteLocalUri(it) }
        }
        repository.delete(id)
    }

    private fun deleteLocalUri(value: String) {
            val uri = Uri.parse(value)
            if (uri.scheme == "file") {
                val file = java.io.File(requireNotNull(uri.path)).canonicalFile
                val roots = listOfNotNull(appContext.filesDir, appContext.cacheDir, appContext.getExternalFilesDir(null), appContext.externalCacheDir)
                require(roots.any { file.toPath().startsWith(it.canonicalFile.toPath()) }) { "无法删除应用目录之外的文件" }
                check(!file.exists() || file.delete()) { "手机文件未删除，记录已保留" }
            } else {
                require(uri.scheme == "content") { "不支持的文件地址" }
                check(appContext.contentResolver.delete(uri, null, null) > 0) { "手机文件未删除，可能已被移动；可选择仅删除记录" }
            }
    }

    fun clearFinished() {
        viewModelScope.launch { repository.clearFinished() }
    }

    companion object {
        fun factory(repository: TransferRepository, context: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TransfersViewModel(repository, context.applicationContext)
                }
            }
    }
}

private fun TransferEntity.toItem(): TransferItem = TransferItem(
    id = id,
    fileName = fileName,
    sizeBytes = sizeBytes,
    bytesDownloaded = bytesDownloaded,
    speedBytesPerSecond = speedBytesPerSecond,
    state = state,
    errorMessage = errorMessage,
    localUri = localUri,
    mimeType = mimeType,
    updatedAt = updatedAt,
    createdAt = createdAt,
    cameraPath = cameraPath,
)
