/* Modified 2026-10-30 by Shiguang Transfer contributors.
 * Based on LumaLink progressive.3, Apache-2.0. See docs/CHANGES_SHIGUANG_0.2.0.md. */
package com.lumalink.g100d.transport.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumalink.g100d.core.data.NewTransfer
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.core.diagnostics.Diagnostics
import com.lumalink.g100d.protocol.CameraAccessGate
import com.lumalink.g100d.protocol.withConnectionCancellation
import com.lumalink.g100d.protocol.CameraFile
import com.lumalink.g100d.protocol.CameraSessionStore
import com.lumalink.g100d.protocol.LumixEndpoints
import com.lumalink.g100d.protocol.LumixResult
import com.lumalink.g100d.protocol.MediaKind
import com.lumalink.g100d.protocol.captureDateFromHeader
import com.lumalink.g100d.protocol.readCaptureDatePrefix
import com.lumalink.g100d.transport.session.CameraConnectionState
import com.lumalink.g100d.transport.session.CameraSession
import com.lumalink.g100d.transport.session.ConnectionManager
import com.lumalink.g100d.transport.session.WifiCandidate
import com.lumalink.g100d.transport.session.WifiScanner
import com.lumalink.g100d.transport.session.CameraProfileStore
import com.lumalink.g100d.transport.transfer.TransferService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_PREVIEW_BYTES = 80 * 1024 * 1024
private const val MAX_THUMBNAIL_BYTES = 8 * 1024 * 1024

enum class FileViewMode {
    GRID,
    LIST,
}

enum class FileFilter(val label: String) {
    ALL("全部"),
    PHOTOS("照片"),
    VIDEOS("视频"),
    JPG("JPG"),
    RAW("RAW");

    fun accepts(file: CameraFile): Boolean = when (this) {
        ALL -> true
        PHOTOS -> file.kind == MediaKind.JPEG || file.kind == MediaKind.RAW
        VIDEOS -> file.kind == MediaKind.VIDEO
        JPG -> file.kind == MediaKind.JPEG
        RAW -> file.kind == MediaKind.RAW
    }
}

data class CameraPreviewUiState(
    val file: CameraFile? = null,
    val bitmap: Bitmap? = null,
    val capturedAtEpochSeconds: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val original: Boolean = false,
    val highQuality: Boolean = false,
    val originalRequested: Boolean = false,
    val bytesRead: Long = 0,
    val totalBytes: Long = 0,
)

private data class FetchedImage(
    val bitmap: Bitmap?,
    val original: Boolean = false,
    val capturedAtEpochSeconds: Long? = null,
)

private data class FetchedBytes(
    val bytes: ByteArray,
    val capturedAtEpochSeconds: Long? = null,
)

data class ConnectUiState(
    val ssid: String = "",
    val password: String = "",
    val baseUrl: String = LumixEndpoints.DEFAULT_BASE_URL,
    val candidates: List<WifiCandidate> = emptyList(),
    val scanning: Boolean = false,
    val currentDirectory: String = "/DCIM",
    val files: List<CameraFile> = emptyList(),
    val filesLoading: Boolean = false,
    val scanTotal: Int? = null,
    val scannedCount: Int = 0,
    val scanComplete: Boolean = false,
    val scanWarning: String? = null,
    val filesError: String? = null,
    val selectedPaths: Set<String> = emptySet(),
    val message: String? = null,
    val permissionError: String? = null,
    val savedCameraName: String? = null,
    val autoConnect: Boolean = true,
    val scanPaused: Boolean = false,
    val viewMode: FileViewMode = FileViewMode.GRID,
    val fileFilter: FileFilter = FileFilter.ALL,
    val previewEnabled: Boolean = true,
    val previewQueue: List<CameraFile> = emptyList(),
    val previewIndex: Int = 0,
    val thumbnails: Map<String, Bitmap> = emptyMap(),
    val captureDates: Map<String, Long> = emptyMap(),
    val datesLoading: Boolean = false,
    val datesChecked: Int = 0,
    val datesTotal: Int = 0,
    val datesMessage: String? = null,
    val preview: CameraPreviewUiState = CameraPreviewUiState(),
)

class ConnectViewModel(
    private val connectionManager: ConnectionManager,
    private val wifiScanner: WifiScanner,
    private val transferRepository: TransferRepository,
    private val appContext: Context,
) : ViewModel() {

    val connection: StateFlow<CameraConnectionState> = connectionManager.state

    private val profileStore = CameraProfileStore(appContext)
    private val initialProfile = profileStore.read()
    private val _uiState = MutableStateFlow(ConnectUiState(
        ssid = initialProfile?.ssid.orEmpty(), password = initialProfile?.password.orEmpty(),
        baseUrl = initialProfile?.baseUrl ?: LumixEndpoints.DEFAULT_BASE_URL,
        savedCameraName = initialProfile?.ssid, autoConnect = initialProfile?.autoConnect ?: true,
    ))
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private val thumbnailJobs = mutableMapOf<String, Job>()
    private var scanJob: Job? = null
    private var scanGeneration = 0L
    private var previewJob: Job? = null
    private var dateJob: Job? = null
    private var previewGeneration = 0L
    private val thumbnailCache = SharedPreviewCache<FetchedImage>(96)
    private val originalPreviewCache = SharedPreviewCache<FetchedImage>(2)
    private var authorizationJob: Job? = null
    private var heartbeatJob: Job? = null
    private var isForeground = false
    private var scanProtected = false
    fun setScanProtection(active: Boolean) { scanProtected = active }
    private fun protectScan() {
        if (!isForeground || scanProtected) return
        runCatching { com.lumalink.g100d.transport.scan.ScanService.start(appContext) }
            .onFailure {
                Diagnostics.warn("扫描后台保护启动失败：${it.message}")
                _uiState.update { state -> state.copy(message = "系统未允许后台扫描，请保持应用在前台后重试") }
            }
    }
    private val thumbnailRequests = mutableSetOf<String>()
    private var sessionKey: Any? = null
    // Only transfer IDs created in this camera session may update its gallery.
    // Never match old import records to a new card by name/path/size alone.
    private val dateTransferFiles = mutableMapOf<String, CameraFile>()
    private var accessReady = false
    private var accessRecoveryAttempted = false
    private var accessRequestInFlight = false
    private var autoConnectAttempted = false
    private var manuallyDisconnected = false

    init {
        viewModelScope.launch {
            transferRepository.transfers.collect { records ->
                // Local metadata work starts after the transfer queue releases the camera.
                while (CameraAccessGate.transferActive) delay(200)
                for (record in records) {
                    val file = dateTransferFiles[record.id] ?: continue
                    if (record.state != com.lumalink.g100d.core.data.TransferState.COMPLETED) continue
                    val protocol = sessionKey ?: continue
                    val generation = scanGeneration
                    if (captureDay(file, _uiState.value.captureDates) != null) {
                        dateTransferFiles.remove(record.id)
                        continue
                    }
                    if (file.kind != MediaKind.JPEG && file.kind != MediaKind.RAW) {
                        dateTransferFiles.remove(record.id)
                        continue
                    }
                    val date = withContext(Dispatchers.IO) {
                        try {
                            record.localUri?.let { uri ->
                                appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                                    val context = currentCoroutineContext()
                                    readCaptureDatePrefix(input) { context.ensureActive() }.date
                                }
                            }
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { null }
                    }
                    // A disconnect/refresh during disk IO must not update another gallery.
                    if (protocol === sessionKey && generation == scanGeneration && dateTransferFiles[record.id] == file) {
                        if (date != null) _uiState.update { it.copy(captureDates = it.captureDates + (file.path to date)) }
                        dateTransferFiles.remove(record.id)
                    }
                }
            }
        }
        viewModelScope.launch {
            connection.collect { state ->
                // A new camera session (or disconnect) must not reuse the
                // previous camera's files, thumbnails or access state.
                val key = (state as? CameraConnectionState.Connected)?.session?.protocol
                if (key !== sessionKey) {
                    sessionKey = key
                    dateTransferFiles.clear()
                    heartbeatJob?.cancel()
                    heartbeatJob = if (key == null) null else viewModelScope.launch {
                        while (true) {
                            delay(3_000)
                            if ((!isForeground && !scanProtected) || !accessReady || CameraAccessGate.transferActive || accessRequestInFlight) continue
                            CameraAccessGate.withCamera {
                                if ((isForeground || scanProtected) && accessReady && !CameraAccessGate.transferActive && !accessRequestInFlight && sessionKey === key)
                                    key.keepSessionActive()
                            }
                        }
                    }
                    accessReady = false
                    accessRecoveryAttempted = false
                    stopDateRead()
                    scanGeneration++
                    scanJob?.cancel()
                    cancelThumbnails()
                    previewJob?.cancel()
                    previewGeneration++
                    thumbnailCache.clear()
                    originalPreviewCache.clear()
                    authorizationJob?.cancel()
                    accessRequestInFlight = false
                    // Never carry the previous camera's X-SESSION_ID into the
                    // next body (G100D would reject its Browse with 403).
                    CameraSessionStore.clear()
                    _uiState.update {
                        it.copy(
                            currentDirectory = "/DCIM",
                            files = emptyList(),
                            filesLoading = false,
                            filesError = null,
                            fileFilter = FileFilter.ALL,
                            previewEnabled = true,
                            scanTotal = null,
                            scannedCount = 0,
                            scanComplete = false,
                            scanWarning = null,
                            previewQueue = emptyList(),
                            previewIndex = 0,
                            selectedPaths = emptySet(),
                            thumbnails = emptyMap(),
                            captureDates = emptyMap(),
                            preview = CameraPreviewUiState(),
                            message = null,
                        )
                    }
                    // Modified 2026-10-30: the gallery owns a single access/browse
                    // flow, avoiding a race with a second background authorization.
                }
            }
        }
    }

    fun updateSsid(value: String) = _uiState.update { it.copy(ssid = value) }

    fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value) }

    fun scan() {
        if (_uiState.value.scanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(scanning = true, permissionError = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) { wifiScanner.scan() }
            }
            result.onSuccess { candidates ->
                _uiState.update { it.copy(candidates = candidates, scanning = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        scanning = false,
                        permissionError = if (error is SecurityException) {
                            "需要附近 Wi-Fi 或定位权限才能扫描相机热点"
                        } else {
                            "扫描失败：${error.message ?: "未知错误"}"
                        },
                    )
                }
            }
        }
    }

    fun connect() {
        val ui = _uiState.value
        if (ui.ssid.isBlank()) {
            _uiState.update { it.copy(permissionError = "请输入相机屏幕上显示的 SSID") }
            return
        }
        manuallyDisconnected = false
        _uiState.update { it.copy(permissionError = null) }
        connectionManager.connect(ui.ssid, ui.password, ui.baseUrl)
    }

    fun useCurrentWifi() {
        connectionManager.useCurrentWifi(_uiState.value.baseUrl)
    }

    fun disconnect() {
        manuallyDisconnected = true
        connectionManager.disconnect()
    }
    fun updateAutoConnect(value: Boolean) {
        _uiState.update { it.copy(autoConnect = value) }
        profileStore.read()?.let { profileStore.save(it.copy(autoConnect = value)) }
    }
    fun rememberSuccessfulConnection() {
        val current = _uiState.value
        val session = connectionManager.session.value ?: return
        if (current.ssid.isBlank()) return
        runCatching { profileStore.save(com.lumalink.g100d.transport.session.SavedCamera(current.ssid, current.password, session.baseUrl, current.autoConnect)) }
            .onSuccess { _uiState.update { it.copy(savedCameraName = current.ssid) } }
            .onFailure { Diagnostics.warn("无法保存相机记录：${it.javaClass.simpleName}") }
    }
    fun forgetSavedCamera() {
        profileStore.forget()
        manuallyDisconnected = true
        _uiState.update { it.copy(savedCameraName = null, ssid = "", password = "") }
    }
    fun onForeground(hasPermission: Boolean) {
        isForeground = true
        if (_uiState.value.filesLoading && !_uiState.value.scanPaused) protectScan()
        if (!hasPermission || autoConnectAttempted || manuallyDisconnected || !_uiState.value.autoConnect || profileStore.read() == null) return
        if (connection.value is CameraConnectionState.Connecting || connection.value is CameraConnectionState.Handshaking || connection.value is CameraConnectionState.Connected) return
        autoConnectAttempted = true
        connect()
    }
    fun onBackground() {
        isForeground = false; autoConnectAttempted = false
        cancelThumbnails()
        if (_uiState.value.preview.loading) {
            previewJob?.cancel(); previewGeneration++
            _uiState.update { it.copy(preview = it.preview.copy(loading = false)) }
        }
    }
    fun pauseScan() { _uiState.update { it.copy(scanPaused = true) } }
    fun continueScan() {
        _uiState.update { it.copy(scanPaused = false) }
        if (scanJob?.isActive == true) protectScan()
        if (scanJob?.isActive != true && !_uiState.value.scanComplete) loadDirectory(_uiState.value.currentDirectory, resume = true)
    }

    /**
     * Single recovery entry point for the file browser. It decides whether a
     * fresh access grant is needed; callers only need the refresh button.
     */
    fun refreshCameraFiles() {
        if (CameraAccessGate.transferActive) {
            _uiState.update { it.copy(message = "传输进行中，完成后再刷新") }
            return
        }
        accessRecoveryAttempted = false
        if (accessReady) {
            loadDirectory(_uiState.value.currentDirectory)
        } else {
            requestAccessAndBrowse()
        }
    }

    fun reportPermissionDenied() {
        _uiState.update {
            it.copy(permissionError = "未授予 Wi-Fi 权限，无法连接相机")
        }
    }

    fun loadDirectory(directory: String, resume: Boolean = false) {
        stopDateRead()
        if (CameraAccessGate.transferActive) {
            _uiState.update { it.copy(message = "传输进行中，暂不刷新相机文件") }
            return
        }
        val session = connectionManager.session.value ?: run {
            _uiState.update { it.copy(filesError = "相机未连接") }
            return
        }
        val generation = ++scanGeneration
        if (!resume) { thumbnailCache.clear(); originalPreviewCache.clear() }
        scanJob?.cancel()
        cancelThumbnails()
        closePreview()
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(currentDirectory = directory, filesLoading = true, filesError = null,
                    files = if (resume) it.files else emptyList(), selectedPaths = if (resume) it.selectedPaths else emptySet(),
                    thumbnails = if (resume) it.thumbnails else emptyMap(), captureDates = if (resume) it.captureDates else emptyMap(),
                    scanTotal = if (resume) it.scanTotal else null, scannedCount = if (resume) it.scannedCount else 0,
                    scanComplete = false, scanWarning = null, scanPaused = false)
            }
            protectScan()
            try {
                val result = session.protocol.browseNewest(
                    resume = resume,
                    beforePage = {
                        // A cached full-screen image does not own the camera anymore.
                        while (_uiState.value.preview.loading || _uiState.value.scanPaused || CameraAccessGate.transferActive) {
                            delay(100)
                        }
                        currentCoroutineContext().ensureActive()
                    },
                    onProgress = { progress ->
                        // Serialize the generation check with refresh/disconnect on Main.
                        withContext(Dispatchers.Main.immediate) {
                            if (scanGeneration == generation && connectionManager.session.value?.protocol === session.protocol) {
                                if (progress.files.isNotEmpty()) accessReady = true
                                _uiState.update { state ->
                                    state.copy(files = progress.files, scanTotal = progress.total,
                                        scannedCount = progress.scannedCount, scanComplete = progress.complete,
                                        scanWarning = progress.warning,
                                        filesError = if (progress.warning == null) null else state.filesError)
                                }
                            }
                        }
                    },
                )
                if (scanGeneration != generation || connectionManager.session.value?.protocol !== session.protocol) return@launch
                when (result) {
                    is LumixResult.Success -> {
                        accessReady = true
                        accessRecoveryAttempted = false
                    }
                    is LumixResult.Failure -> {
                        if (_uiState.value.files.isEmpty() && !accessRecoveryAttempted && !CameraAccessGate.transferActive) {
                            accessReady = false
                            accessRecoveryAttempted = true
                            requestAccessAndBrowse()
                        } else {
                            _uiState.update { it.copy(filesError = result.error.message) }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } finally {
                if (scanGeneration == generation) _uiState.update { it.copy(filesLoading = false) }
            }
        }
    }

    fun setViewMode(mode: FileViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setFileFilter(filter: FileFilter) {
        _uiState.update {
            it.copy(
                fileFilter = filter,
                selectedPaths = emptySet(),
                preview = CameraPreviewUiState(),
                previewQueue = emptyList(),
                previewIndex = 0,
            )
        }
    }

    fun toggleSelectAll(files: List<CameraFile>) {
        val paths = files.filterNot { it.isDirectory }.map { it.path }.toSet()
        if (paths.isEmpty()) return
        _uiState.update { state ->
            val allSelected = state.selectedPaths.containsAll(paths)
            state.copy(
                selectedPaths = if (allSelected) {
                    state.selectedPaths - paths
                } else {
                    state.selectedPaths + paths
                },
            )
        }
    }

    fun clearGallerySelection() { _uiState.update { it.copy(selectedPaths = emptySet()) } }

    fun setPreviewEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (enabled && state.files.isEmpty()) {
            _uiState.update { it.copy(message = "读到第一批照片后即可开启预览") }
            return
        }
        _uiState.update { it.copy(previewEnabled = enabled) }
        if (!enabled) {
            cancelThumbnails()
            closePreview()
        }
    }

    fun openPreview(file: CameraFile, visibleFiles: List<CameraFile> = _uiState.value.files) {
        if (file.isDirectory || !_uiState.value.previewEnabled) return
        val queue = visibleFiles.filterNot { it.isDirectory }
        val index = queue.indexOfFirst { it.path == file.path }
        if (index >= 0) loadPreview(queue, index) else loadPreview(listOf(file), 0)
    }

    fun openPreviewAll(files: List<CameraFile>) {
        if (!_uiState.value.previewEnabled) return
        val queue = files.filterNot { it.isDirectory }
        if (queue.isEmpty()) return
        loadPreview(queue, 0)
    }

    fun previousPreview() {
        val state = _uiState.value
        if (!state.previewEnabled || state.previewIndex <= 0) return
        loadPreview(state.previewQueue, state.previewIndex - 1)
    }

    fun nextPreview() {
        val state = _uiState.value
        if (!state.previewEnabled || state.previewIndex >= state.previewQueue.lastIndex) return
        loadPreview(state.previewQueue, state.previewIndex + 1)
    }

    fun previewAt(index: Int) {
        val state = _uiState.value
        if (state.previewEnabled && index != state.previewIndex && index in state.previewQueue.indices)
            loadPreview(state.previewQueue, index)
    }

    private fun loadPreview(queue: List<CameraFile>, index: Int, original: Boolean = false) {
        val file = queue.getOrNull(index) ?: return
        if (file.isDirectory) return
        previewJob?.cancel()
        val generation = ++previewGeneration
        val enhance = enhancesPreview(file, original)
        val qualityFile = qualityPreviewFile(file, original)
        val readsOriginal = enhance && qualityFile.path == file.path
        val cachedOriginal = originalPreviewCache.get(file.path)?.takeIf { !original || it.original }
        val cachedQuick = thumbnailCache.get(quickPreviewKey(file))
        val initialBitmap = cachedOriginal?.bitmap ?: cachedQuick?.bitmap
            ?: _uiState.value.preview.takeIf { it.file?.path == file.path }?.bitmap
            ?: _uiState.value.thumbnails[file.path]
        val plan = previewPlan(enhance, initialBitmap != null, cachedOriginal?.bitmap != null)
        val needsNetwork = plan == PreviewPlan.FETCH_QUICK || plan == PreviewPlan.FETCH_ORIGINAL
        _uiState.update { it.copy(previewQueue = queue, previewIndex = index,
            preview = CameraPreviewUiState(file = file, bitmap = initialBitmap,
                capturedAtEpochSeconds = file.capturedAtEpochSeconds ?: it.captureDates[file.path]
                    ?: it.captureDates[file.path.substringBeforeLast('.') + ".JPG"]
                    ?: cachedOriginal?.capturedAtEpochSeconds ?: cachedQuick?.capturedAtEpochSeconds,
                loading = needsNetwork, original = cachedOriginal?.original == true, highQuality = cachedOriginal?.bitmap != null, originalRequested = readsOriginal,
                totalBytes = if (readsOriginal) file.sizeBytes else 0)) }
        // A cached preview must not trigger authorization, a Browse, or an 11 MB original GET.
        if (!needsNetwork) return
        cancelThumbnails()
        if (CameraAccessGate.transferActive) {
            _uiState.update { it.copy(preview = it.preview.copy(loading = false, error = "正在导入文件；可先看缓存图，稍后再读取原图")) }
            return
        }
        previewJob = viewModelScope.launch {
            val session = connectionManager.session.value ?: run {
                _uiState.update { it.copy(preview = it.preview.copy(loading = false, error = "相机未连接")) }
                return@launch
            }
            val fetched = CameraAccessGate.withGalleryCamera {
                withContext(Dispatchers.IO) {
                    if (!enhance) thumbnailCache.get(quickPreviewKey(file))?.let { return@withContext it }
                    if (!enhance) fetchThumbnail(session, file) else {
                        if (initialBitmap == null) {
                            val quick = fetchThumbnail(session, file)
                            withContext(Dispatchers.Main.immediate) {
                                if (previewGeneration == generation) _uiState.update { it.copy(preview = it.preview.copy(bitmap = quick?.bitmap)) }
                            }
                        }
                        val data = fetchBytes(session, qualityFile, MAX_PREVIEW_BYTES) { bytes, total ->
                            withContext(Dispatchers.Main.immediate) {
                                if (previewGeneration == generation && connectionManager.session.value?.protocol === session.protocol)
                                    _uiState.update { it.copy(preview = it.preview.copy(bytesRead = bytes, totalBytes = total)) }
                            }
                        }
                        data?.let { bytes -> FetchedImage(bitmap = decodePreview(bytes.bytes, 2048),
                            original = readsOriginal, capturedAtEpochSeconds = exifDate(bytes.bytes)) }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (previewGeneration != generation || connectionManager.session.value?.protocol !== session.protocol) return@launch
            if (enhance && fetched?.bitmap != null) originalPreviewCache.put(file.path, fetched)
            val capturedAt = fetched?.capturedAtEpochSeconds
            _uiState.update { state -> state.copy(
                captureDates = if (capturedAt != null) state.captureDates + (file.path to capturedAt) else state.captureDates,
                preview = state.preview.copy(bitmap = fetched?.bitmap ?: state.preview.bitmap ?: initialBitmap,
                    capturedAtEpochSeconds = capturedAt ?: state.preview.capturedAtEpochSeconds, loading = false,
                    original = fetched?.original == true, highQuality = enhance && fetched?.bitmap != null,
                    error = if (fetched?.bitmap == null) {
                        if (enhance) "清晰预览暂未读取成功，已保留快速预览；可以重试或直接导入" else "快速预览暂不可用，可重试或直接导入原文件"
                    } else null)) }
        }
    }

    fun loadOriginalPreview() {
        val state = _uiState.value
        val file = state.preview.file ?: return
        if (file.kind != MediaKind.JPEG || state.preview.loading) return
        loadPreview(state.previewQueue.ifEmpty { listOf(file) }, state.previewIndex, original = true)
    }

    fun closePreview() {
        previewJob?.cancel()
        previewGeneration++
        _uiState.update {
            it.copy(
                preview = CameraPreviewUiState(),
                previewQueue = emptyList(),
                previewIndex = 0,
            )
        }
    }

    private fun cancelThumbnails() {
        thumbnailJobs.values.toList().forEach { it.cancel() }
        thumbnailJobs.clear()
        thumbnailRequests.clear()
    }

    fun releaseThumbnail(file: CameraFile) {
        thumbnailJobs.remove(file.path)?.cancel()
        thumbnailRequests.remove(file.path)
    }

    fun requestThumbnail(file: CameraFile) {
        if (file.isDirectory || !_uiState.value.previewEnabled) return
        if (_uiState.value.thumbnails.containsKey(file.path)) return
        thumbnailCache.get(quickPreviewKey(file))?.let { cached ->
            _uiState.update { state -> state.copy(
                thumbnails = cached.bitmap?.let { bitmap ->
                    (state.thumbnails + (file.path to bitmap)).entries.toList().takeLast(160).associate { it.key to it.value }
                } ?: state.thumbnails,
                captureDates = cached.capturedAtEpochSeconds?.let { state.captureDates + (file.path to it) } ?: state.captureDates,
            ) }
            return
        }
        if (!thumbnailRequests.add(file.path)) return
        val generation = scanGeneration
        thumbnailJobs[file.path] = viewModelScope.launch {
            try {
                val session = connectionManager.session.value ?: return@launch
                val fetched = CameraAccessGate.withGalleryCameraWhenAvailable {
                    // An already open full preview has priority over prefetches.
                    if (_uiState.value.preview.loading) return@withGalleryCameraWhenAvailable null
                    withContext(Dispatchers.IO) {
                        thumbnailCache.get(quickPreviewKey(file))?.let { return@withContext it }
                        fetchThumbnail(session, file)
                    }
                } ?: return@launch
                currentCoroutineContext().ensureActive()
                if (scanGeneration != generation || connectionManager.session.value?.protocol !== session.protocol) return@launch
                _uiState.update {
                    it.copy(
                        thumbnails = fetched.bitmap?.let { bitmap ->
                            // Bound the bitmap cache; visible entries will reload when revisited.
                            (it.thumbnails + (file.path to bitmap)).entries.toList().takeLast(160)
                                .associate { entry -> entry.key to entry.value }
                        } ?: it.thumbnails,
                        captureDates = fetched.capturedAtEpochSeconds?.let { date ->
                            it.captureDates + (file.path to date)
                        } ?: it.captureDates,
                    )
                }
            } finally {
                // A replacement request for this path must retain its own bookkeeping.
                if (scanGeneration == generation && thumbnailJobs[file.path] === currentCoroutineContext()[Job]) {
                    thumbnailRequests.remove(file.path)
                    thumbnailJobs.remove(file.path)
                }
            }
        }
    }

    private suspend fun fetchThumbnail(
        session: CameraSession,
        file: CameraFile,
    ): FetchedImage? {
        val key = quickPreviewKey(file)
        thumbnailCache.get(key)?.let { return it }
        val thumb = CameraFile(
            path = key,
            name = "preview.jpg",
            sizeBytes = 0,
        )
        val data = fetchBytes(session, thumb, MAX_THUMBNAIL_BYTES) ?: return null
        val fetched = FetchedImage(
            bitmap = decodePreview(data.bytes, maxDimension = 420),
            capturedAtEpochSeconds = exifDate(data.bytes),
        )
        currentCoroutineContext().ensureActive()
        if (connectionManager.session.value?.protocol === session.protocol && fetched.bitmap != null) thumbnailCache.put(key, fetched)
        return fetched
    }

    private suspend fun fetchBytes(
        session: CameraSession,
        file: CameraFile,
        maxBytes: Int,
        onProgress: (suspend (Long, Long) -> Unit)? = null,
    ): FetchedBytes? {
        val result = session.protocol.openPreviewConnection(file)
        if (result is LumixResult.Failure) return null
        val connection = (result as LumixResult.Success).value
        val started = System.nanoTime()
        Diagnostics.info("preview-stage body-start file=${file.name}")
        return withConnectionCancellation(connection) { try {
            if (connection.responseCode !in 200..299) return@withConnectionCancellation null
            if (connection.contentLengthLong > maxBytes) return@withConnectionCancellation null
            val lastModified = connection.getHeaderFieldDate("Last-Modified", 0L)
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(128 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                var lastProgress = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return@withConnectionCancellation null
                    out.write(buffer, 0, read)
                    val now = System.nanoTime()
                    if (onProgress != null && now - lastProgress > 250_000_000L) {
                        onProgress(total.toLong(), connection.contentLengthLong.takeIf { it > 0 } ?: file.sizeBytes)
                        lastProgress = now
                    }
                }
                out.toByteArray()
            }
            Diagnostics.info("preview-stage body-complete file=${file.name} bytes=${bytes.size} elapsedMs=${(System.nanoTime()-started)/1_000_000}")
            FetchedBytes(
                bytes = bytes,
                capturedAtEpochSeconds = lastModified
                    .takeIf { it > 0L }
                    ?.div(1_000L),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Diagnostics.warn("preview-stage body-failed file=${file.name} elapsedMs=${(System.nanoTime()-started)/1_000_000} error=${error.javaClass.simpleName}: ${error.message}")
            null
        } finally {
            runCatching { connection.disconnect() }
        } }
    }

    fun stopDateRead() {
        dateJob?.cancel()
        dateJob = null
        _uiState.update { it.copy(datesLoading = false,
            datesMessage = if (it.datesLoading) "日期读取已暂停，已读取的日期已保留，可再次补全。" else it.datesMessage) }
    }

    fun readMissingDates() {
        if (dateJob?.isActive == true) return
        val session = connectionManager.session.value ?: return
        val candidates = dateReadCandidates(_uiState.value.files, _uiState.value.captureDates)
        if (candidates.isEmpty()) {
            _uiState.update { it.copy(datesLoading = false, datesChecked = 0, datesTotal = 0,
                datesMessage = "当前已扫描照片无需补全日期；视频暂不支持读取拍摄日期。") }
            return
        }
        val generation = scanGeneration
        dateJob = viewModelScope.launch {
            _uiState.update { it.copy(datesLoading = true, datesChecked = 0, datesTotal = candidates.size,
                datesMessage = "正在补全已扫描照片的日期；预览和导入优先") }
            try {
                for ((index, file) in candidates.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    if (generation != scanGeneration || connectionManager.session.value?.protocol !== session.protocol) break
                    var attempted = false
                    var date: Long? = null
                    while (!attempted) {
                        currentCoroutineContext().ensureActive()
                        CameraAccessGate.withScanCamera {
                            if (_uiState.value.preview.file == null) {
                                attempted = true
                                date = withContext(Dispatchers.IO) { fetchCaptureDate(session, file) }
                            }
                        }
                        if (!attempted) delay(200)
                    }
                    if (generation != scanGeneration || connectionManager.session.value?.protocol !== session.protocol) break
                    _uiState.update { state -> state.copy(datesChecked = index + 1,
                        captureDates = date?.let { state.captureDates + (file.path to it) } ?: state.captureDates) }
                    // Yield between headers so directory pages and the heartbeat keep running.
                    delay(100)
                }
                _uiState.update { it.copy(datesMessage = "本轮日期读取完成；仍未知的照片未在文件头提供可读取日期。新扫描的照片可再次补全。") }
            } finally {
                if (dateJob === currentCoroutineContext()[Job]) _uiState.update { it.copy(datesLoading = false) }
            }
        }
    }

    private suspend fun fetchCaptureDate(session: CameraSession, file: CameraFile): Long? {
        val limit = 128 * 1024
        val result = session.protocol.openPreviewConnection(file, rangeEnd = limit - 1L)
        if (result !is LumixResult.Success) return null
        val connection = result.value
        return withConnectionCancellation(connection) {
            try {
                if (connection.responseCode != 200 && connection.responseCode != 206) return@withConnectionCancellation null
                if (connection.responseCode == 206 && !connection.getHeaderField("Content-Range").orEmpty().startsWith("bytes 0-"))
                    return@withConnectionCancellation null
                // The camera may ignore Range. Never read the entire original in that case.
                val context = currentCoroutineContext()
                val header = readCaptureDatePrefix(connection.inputStream) { context.ensureActive() }
                Diagnostics.info("capture-date header bytes=${header.bytesRead} found=${header.date != null} status=${connection.responseCode}")
                header.date
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                Diagnostics.warn("capture-date header failed=${error.javaClass.simpleName}")
                null
            } finally { connection.disconnect() }
        }
    }

    private fun exifDate(bytes: ByteArray): Long? {
        captureDateFromHeader(bytes)?.let { return it }
        return runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return null
            LocalDateTime.parse(
                raw,
                DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US),
            ).atZone(ZoneId.systemDefault()).toEpochSecond()
        }.getOrNull()
    }

    private fun decodePreview(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    private fun thumbnailPath(path: String): String {
        val name = path.substringAfterLast('/')
        val base = name.substringBeforeLast('.', name)
        val thumb = if (base.startsWith("DO")) {
            "DT" + base.substring(2) + ".JPG"
        } else {
            base + ".JPG"
        }
        return path.substringBeforeLast('/') + "/" + thumb
    }

    override fun onCleared() {
        dateJob?.cancel()
        scanGeneration++
        scanJob?.cancel()
        cancelThumbnails()
        previewJob?.cancel()
        authorizationJob?.cancel()
        super.onCleared()
    }

    fun openCameraFiles() {
        // Reopening the browser must not rescan hundreds of camera items.
        // Refresh is the explicit full reload; the existing list is reused.
        if (scanJob?.isActive == true || (accessReady && _uiState.value.files.isNotEmpty())) return
        if (accessReady) {
            loadDirectory("/DCIM")
        } else {
            requestAccessAndBrowse()
        }
    }

    private fun authorizeInBackground(session: CameraSession) {
        if (accessRequestInFlight) return
        accessRequestInFlight = true
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        message = "正在请求相机访问，请在相机屏幕上确认连接…",
                        filesError = null,
                    )
                }
                when (
                    val result = session.protocol.requestCameraAccess { status ->
                        _uiState.update { state -> state.copy(message = status) }
                    }
                ) {
                    is LumixResult.Success -> {
                        session.protocol.readModel()?.let { connectionManager.updateModel(it) }
                        accessReady = true
                        _uiState.update {
                            it.copy(message = "已获得访问权限，点击浏览文件或刷新")
                        }
                    }

                    is LumixResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                message = "自动授权失败，点击刷新重试：${result.error.message}",
                            )
                        }
                    }
                }
            } finally {
                accessRequestInFlight = false
            }
        }
    }

    /**
     * The G100D drops the Wi-Fi session if its own menu is touched, so the
     * user must trigger this fixed playmode command from the app. It is never
     * sent automatically.
     */
    fun requestAccessAndBrowse() {
        if (accessRequestInFlight) {
            _uiState.update { it.copy(message = "正在请求相机访问，请稍候…") }
            return
        }
        if (CameraAccessGate.transferActive) {
            _uiState.update { it.copy(message = "传输进行中，完成后再请求相机访问") }
            return
        }
        val session = connectionManager.session.value
        if (session == null) {
            _uiState.update { it.copy(filesError = "相机未连接") }
            return
        }
        accessRequestInFlight = true
        authorizationJob = viewModelScope.launch {
            try {
            CameraAccessGate.withCamera {
            Diagnostics.info("用户确认请求相机访问，等待相机屏幕确认")
            _uiState.update {
                it.copy(
                    filesLoading = true,
                    filesError = null,
                    message = "正在请求访问，请在相机屏幕上确认连接…",
                )
            }
            when (
                val result = session.protocol.requestCameraAccess { status ->
                    _uiState.update { state -> state.copy(message = status) }
                }
            ) {
                is LumixResult.Success -> {
                    session.protocol.readModel()?.let { connectionManager.updateModel(it) }
                    delay(500)
                    val playback = session.protocol.enterPlaybackMode()
                    if (playback is LumixResult.Failure) {
                        // S5M2 can browse the play-mode UPnP directory even
                        // when playmode reports err_busy; try it anyway.
                        Diagnostics.info("playmode 未成功，继续尝试 Browse：${playback.error.message}")
                        _uiState.update {
                            it.copy(message = "playmode 返回 busy，正在尝试直接读取文件")
                        }
                    } else {
                        delay(1_000)
                        _uiState.update { it.copy(message = "已获得访问权限，正在读取文件") }
                    }
                    accessRecoveryAttempted = true
                    loadDirectory("/DCIM")
                }

                is LumixResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            filesLoading = false,
                            filesError = "访问请求失败：${result.error.message ?: "未知错误"}",
                        )
                    }
                }
            }
            }
            } finally {
                if (connectionManager.session.value?.protocol === session.protocol) accessRequestInFlight = false
            }
        }
    }

    fun enterPlaybackAndBrowse() {
        val session = connectionManager.session.value
        if (session == null) {
            _uiState.update { it.copy(filesError = "相机未连接") }
            return
        }
        viewModelScope.launch {
            Diagnostics.info("用户确认发送 playmode")
            _uiState.update {
                it.copy(filesLoading = true, filesError = null, message = "正在切换到播放模式…")
            }
            when (val result = session.protocol.enterPlaybackMode()) {
                is LumixResult.Success -> {
                    delay(1_200)
                    when (val info = session.protocol.readContentInfo()) {
                        is LumixResult.Success -> {
                            Diagnostics.info("get_content_info: ${info.value.take(2_000)}")
                            val count = Regex("<total_content_number>(\\d+)</total_content_number>")
                                .find(info.value)
                                ?.groupValues
                                ?.getOrNull(1)
                                ?.toIntOrNull()
                            _uiState.update {
                                it.copy(
                                    message = if (count != null) {
                                        "相机报告 $count 个文件，正在读取列表"
                                    } else {
                                        "已切换到播放模式，正在读取文件"
                                    },
                                )
                            }
                        }

                        is LumixResult.Failure -> {
                            Diagnostics.info("get_content_info 失败：${info.error.message}")
                            _uiState.update {
                                it.copy(message = "已切换到播放模式，正在读取文件")
                            }
                        }
                    }
                    accessRecoveryAttempted = true
                    loadDirectory("/DCIM")
                }

                is LumixResult.Failure -> {
                    Diagnostics.info("playmode 失败，继续尝试 Browse：${result.error.message}")
                    _uiState.update {
                        it.copy(
                            message = "playmode 返回 busy，正在尝试直接读取文件",
                            filesError = null,
                        )
                    }
                    accessRecoveryAttempted = true
                    loadDirectory("/DCIM")
                }
            }
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentDirectory
        if (current == "/DCIM" || current == "/") {
            loadDirectory("/DCIM")
            return
        }
        loadDirectory(current.substringBeforeLast('/', "/DCIM"))
    }

    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val next = state.selectedPaths.toMutableSet()
            if (!next.add(path)) next.remove(path)
            state.copy(selectedPaths = next)
        }
    }

    fun enqueueSelected() {
        val state = _uiState.value
        val selected = state.files.filter { !it.isDirectory && it.path in state.selectedPaths }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(message = "请先选择要下载的文件") }
            return
        }
        enqueueFiles(selected)
    }

    fun enqueueFile(file: CameraFile) { enqueueFiles(listOf(file)) }

    private fun enqueueFiles(selected: List<CameraFile>) {
        val enqueueSession = connectionManager.session.value
        if (enqueueSession == null) {
            _uiState.update { it.copy(message = "请先连接相机，再导入文件") }
            return
        }
        viewModelScope.launch {
            CameraAccessGate.transferActive = true
            try {
                // Visible thumbnail jobs wait for transfer completion; offscreen ones cancel on dispose.
                closePreview()
                val ids = transferRepository.enqueue(
                    selected.map { file ->
                        NewTransfer(
                            cameraPath = file.path,
                            fileName = file.name,
                            sizeBytes = file.sizeBytes,
                            mimeType = mimeTypeFor(file.kind),
                        )
                    },
                )
                if (connectionManager.session.value?.protocol === enqueueSession.protocol) {
                    ids.zip(selected).forEach { (id, file) -> dateTransferFiles[id] = file }
                }
                TransferService.start(appContext)
            } catch (error: Throwable) {
                CameraAccessGate.transferActive = false
                _uiState.update { it.copy(message = "加入传输队列失败：${error.message ?: "未知错误"}") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    selectedPaths = it.selectedPaths - selected.map { file -> file.path }.toSet(),
                    message = "已加入传输队列（${selected.size} 个文件）",
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearFileError() {
        _uiState.update { it.copy(filesError = null) }
    }

    private fun mimeTypeFor(kind: MediaKind): String = when (kind) {
        MediaKind.JPEG -> "image/jpeg"
        MediaKind.RAW -> "application/octet-stream"
        MediaKind.VIDEO -> "video/mp4"
        MediaKind.UNKNOWN -> "application/octet-stream"
    }

    /**
     * G100D names are P1002205.JPG / 100-2205 / DO01002205.JPG. Strip
     * separators so the numeric sequence sorts the same in every form.
     */
    private fun cameraFileNumber(file: CameraFile): Long? =
        file.name.filter { it.isDigit() }
            .takeIf { it.isNotEmpty() }
            ?.toLongOrNull()

    companion object {
        fun factory(
            connectionManager: ConnectionManager,
            wifiScanner: WifiScanner,
            transferRepository: TransferRepository,
            context: Context,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConnectViewModel(
                    connectionManager = connectionManager,
                    wifiScanner = wifiScanner,
                    transferRepository = transferRepository,
                    appContext = context.applicationContext,
                )
            }
        }
    }
}
