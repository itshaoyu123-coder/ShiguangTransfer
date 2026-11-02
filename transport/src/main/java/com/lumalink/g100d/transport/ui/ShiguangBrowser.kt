/* Copyright 2026 Shiguang Transfer contributors. Licensed under Apache-2.0.
 * Original photo-first UI. Uses the attributed LumaLink progressive ViewModels. */
package com.lumalink.g100d.transport.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumalink.g100d.core.data.TransferState
import com.lumalink.g100d.core.diagnostics.Diagnostics
import com.lumalink.g100d.core.util.formatBytes
import com.lumalink.g100d.protocol.CameraFile
import com.lumalink.g100d.protocol.MediaKind
import com.lumalink.g100d.transport.session.CameraConnectionState

private val Ink = Color(0xFF202922)
private val Sage = Color(0xFF275D4C)
private val Muted = Color(0xFF71796F)
private val Paper = Color(0xFFF6F4EF)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShiguangBrowser(viewModel: ConnectViewModel, transfers: TransfersViewModel) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val queue by transfers.uiState.collectAsStateWithLifecycle()
    val transferMessage by transfers.actionMessage.collectAsStateWithLifecycle()
    val connected = connection is CameraConnectionState.Connected
    val busy = connection is CameraConnectionState.Connecting || connection is CameraConnectionState.Handshaking
    var showConnection by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    var selectedGalleryDate by rememberSaveable { mutableStateOf<String?>(null) }
    var showGalleryDates by remember { mutableStateOf(false) }
    val dateByPath = remember(ui.files, ui.captureDates) { ui.files.associate { it.path to captureDay(it, ui.captureDates) } }
    val baseFiles = remember(ui.files, ui.fileFilter) { ui.files.filter { !it.isDirectory && ui.fileFilter.accepts(it) } }
    val dateCounts = remember(baseFiles, dateByPath) { baseFiles.groupingBy { dateByPath[it.path] }.eachCount() }
    val visibleFiles = remember(baseFiles, dateByPath, selectedGalleryDate) { baseFiles.filter { matchesGalleryDate(dateByPath[it.path], selectedGalleryDate) } }
    val gridState = rememberLazyGridState()
    val tabPager = rememberPagerState(initialPage = if (showQueue) 1 else 0) { 2 }
    var tabSwipesEnabled by remember { mutableStateOf(true) }
    val gestureDensity = LocalDensity.current
    val gestureLayout = androidx.compose.ui.platform.LocalLayoutDirection.current
    val gestureEdge = maxOf(with(gestureDensity) { 32.dp.toPx() },
        WindowInsets.systemGestures.getLeft(gestureDensity, gestureLayout).toFloat(), WindowInsets.systemGestures.getRight(gestureDensity, gestureLayout).toFloat())
    LaunchedEffect(showQueue) {
        val target = if (showQueue) 1 else 0
        if (tabPager.settledPage != target) tabPager.animateScrollToPage(target)
    }
    LaunchedEffect(tabPager) { snapshotFlow { tabPager.settledPage }.collect { showQueue = it == 1 } }
    var thumbnailLoadMode by remember { mutableStateOf(ThumbnailLoadMode.LOAD) }
    val rowSpacingPx = with(LocalDensity.current) { 11.dp.toPx() }
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    LaunchedEffect(gridState, ui.fileFilter, selectedGalleryDate, showQueue, resumed, ui.preview.file != null, rowSpacingPx) {
        thumbnailLoadMode = ThumbnailLoadMode.LOAD
        viewModel.updateGalleryMotion(ThumbnailLoadMode.LOAD, false, emptyList())
        if (!resumed || showQueue || ui.preview.file != null) return@LaunchedEffect
        val policy = ScrollLoadPolicy()
        while (true) {
            if (tabPager.isScrollInProgress) {
                thumbnailLoadMode = ThumbnailLoadMode.HOLD
                viewModel.updateGalleryMotion(ThumbnailLoadMode.HOLD, true, emptyList())
                kotlinx.coroutines.delay(50)
                continue
            }
            val layout = gridState.layoutInfo
            val rowHeight = (layout.visibleItemsInfo.firstOrNull()?.size?.height ?: 1) + rowSpacingPx
            val position = gridState.firstVisibleItemIndex / 3 + gridState.firstVisibleItemScrollOffset / rowHeight.toDouble()
            val scrolling = gridState.isScrollInProgress
            val mode = policy.sample(position, android.os.SystemClock.elapsedRealtime(), scrolling)
            viewModel.updateGalleryMotion(mode, scrolling, layout.visibleItemsInfo.mapNotNull { it.key as? String })
            thumbnailLoadMode = mode
            kotlinx.coroutines.delay(50)
        }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.updateGalleryMotion(ThumbnailLoadMode.LOAD, false, emptyList()) } }
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = ui.preview.file == null && !showConnection && !showAbout && !showDiagnostics && !showLicenses && !showGalleryDates) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (showQueue) { showQueue = false; lastBackAt = 0L }
        else if (lastBackAt > 0 && now - lastBackAt < 2000) {
            (context as? android.app.Activity)?.moveTaskToBack(true); lastBackAt = 0L
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(context, "再返回一次回到桌面", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasPermission()) { if (pendingAction == 2) viewModel.useCurrentWifi() else viewModel.connect() }
        else viewModel.reportPermissionDenied()
        pendingAction = 0
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun connect(current: Boolean = false) {
        if (hasPermission()) { if (current) viewModel.useCurrentWifi() else viewModel.connect() }
        else {
            pendingAction = if (current) 2 else 1
            permissions.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
                else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    fun requestNotice() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) { resumed = true; lastBackAt = 0L; viewModel.onForeground(hasPermission()) }
            if (event == Lifecycle.Event.ON_STOP) { resumed = false; viewModel.onBackground() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { viewModel.onForeground(hasPermission()) }
    LaunchedEffect((connection as? CameraConnectionState.Connected)?.session?.protocol) {
        if (connected) {
            showConnection = false
            viewModel.rememberSuccessfulConnection()
            viewModel.openCameraFiles()
        }
    }
    LaunchedEffect(transferMessage) { transferMessage?.let { transfers.clearActionMessage(); snackbar.showSnackbar(it) } }
    LaunchedEffect(ui.filesLoading) { if (ui.filesLoading) requestNotice() }
    LaunchedEffect(ui.message) { ui.message?.let { viewModel.clearMessage(); snackbar.showSnackbar(it) } }

    if (ui.preview.file == null) {
    Scaffold(containerColor = Paper, contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            AnimatedVisibility(visible = !showQueue && ui.selectedPaths.isNotEmpty(),
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 2 }) {
            Surface(color = Paper, shadowElevation = 10.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("已选 ${ui.selectedPaths.size} 张", fontWeight = FontWeight.SemiBold)
                        Text("保存到「拾光传输」目录", color = Muted, fontSize = 11.sp)
                    }
                    Button(onClick = { requestNotice(); viewModel.enqueueSelected(); showQueue = true }, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Outlined.SouthWest, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("导入所选")
                    }
                }
            }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SHIGUANG", fontSize = 10.sp, letterSpacing = 2.5.sp, fontWeight = FontWeight.SemiBold, color = Sage, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAbout = true }) { Icon(Icons.Outlined.MoreHoriz, "更多", tint = Ink) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.Bottom) {
                AnimatedContent(targetState = showQueue, modifier = Modifier.weight(1f),
                    transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 5 }) togetherWith
                        (fadeOut(tween(100)) + slideOutVertically(tween(160)) { -it / 5 }) }, label = "pageTitle") { history ->
                    Text(if (history) "带走的光" else "拾光", fontSize = 36.sp, lineHeight = 46.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                Row(Modifier.padding(bottom = 9.dp).clip(CircleShape).clickable { showConnection = true }.background(Color.White).padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if (connected) Sage else Color(0xFFB2B7AD), CircleShape)); Spacer(Modifier.width(7.dp))
                    Text(when { connected -> "${cameraDisplayName((connection as? CameraConnectionState.Connected)?.session?.model?.model)} · ${ui.accessStage.label}"; connection is CameraConnectionState.Handshaking -> "正在识别相机"; busy -> "正在连接 Wi-Fi"; ui.savedCameraName != null -> "我的相机 · 重连"; else -> "添加相机" }, fontSize = 11.sp, color = Ink)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showQueue = false }, contentPadding = PaddingValues(end = 18.dp)) {
                    Text("相机胶卷", color = if (!showQueue) Ink else Muted, fontWeight = if (!showQueue) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp)
                }
                TextButton(onClick = { showQueue = true }) { Text("导入记录${if (queue.activeCount > 0) " · ${queue.activeCount}" else ""}", color = if (showQueue) Ink else Muted, fontSize = 16.sp) }
                Spacer(Modifier.weight(1f))
                if (connected && !showQueue) IconButton(onClick = { viewModel.refreshCameraFiles() }) { Icon(Icons.Outlined.Refresh, "重新扫描", tint = Sage) }
            }
            HorizontalPager(state = tabPager, beyondViewportPageCount = 0, userScrollEnabled = tabSwipesEnabled,
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(gestureEdge) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        tabSwipesEnabled = down.position.x >= gestureEdge && down.position.x <= size.width - gestureEdge
                        try {
                            do { val event = awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
                        } finally { tabSwipesEnabled = true }
                    }
                }) { page ->
            Column(Modifier.fillMaxSize()) {
            if (page == 1) {
                ImportHistory(queue, connected, onRetry = transfers::retry, onCancel = transfers::cancel, onDelete = transfers::deleteMany,
                    onOpenConnection = { showConnection = true }, modifier = Modifier.weight(1f))
            } else {
                if (connected || ui.files.isNotEmpty()) {
                    val status = when {
                        ui.scanComplete -> "${ui.files.size} 个原文件 · 扫描完成"
                        ui.scanPaused -> "扫描已暂停 · 已载入 ${ui.files.size} 个文件"
                        ui.preview.loading -> "正在取回预览 · 已载入 ${ui.files.size} 个文件"
                        queue.activeCount > 0 -> "正在导入 · 扫描随后继续"
                        ui.filesLoading -> "已发现 ${ui.files.size} 个文件 · ${if (ui.scanTotal == null) "按相机顺序读取" else "继续读取更早照片"}"
                        ui.filesError != null -> "扫描暂未完成 · 已保留 ${ui.files.size} 个文件"
                        else -> "正在准备最新照片"
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(status, fontSize = 11.sp, color = Muted)
                            ui.scanTotal?.let { Text("对象进度 ${ui.scannedCount} / $it", fontSize = 10.sp, color = Muted) }
                        }
                        if (!ui.scanComplete && ui.files.isNotEmpty()) TextButton(onClick = {
                            if (ui.filesLoading && !ui.scanPaused) viewModel.pauseScan() else viewModel.continueScan()
                        }) { Text(if (ui.filesLoading && !ui.scanPaused) "暂停" else "继续扫描", fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (ui.filesLoading && !ui.scanPaused) {
                        if (ui.scanTotal != null && ui.scanTotal!! > 0) {
                            val progress by animateFloatAsState((ui.scannedCount.toFloat() / ui.scanTotal!!).coerceIn(0f, 1f), tween(280), label = "scanProgress")
                            LinearProgressIndicator(progress = { progress }, color = Sage, trackColor = Sage.copy(alpha = .1f), modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(3.dp).clip(CircleShape))
                        }
                        else LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp).height(2.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (filter in listOf(FileFilter.ALL, FileFilter.JPG, FileFilter.RAW, FileFilter.VIDEOS)) FilterChip(selected = ui.fileFilter == filter,
                            onClick = { viewModel.setFileFilter(filter) }, label = { Text(filter.label, fontSize = 11.sp) }, shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Sage, selectedLabelColor = Color.White, containerColor = Color.Transparent, labelColor = Muted))
                        Spacer(Modifier.weight(1f))
                        if (visibleFiles.isNotEmpty()) TextButton(onClick = { viewModel.toggleSelectAll(visibleFiles) }, contentPadding = PaddingValues(4.dp)) { Text("全选", fontSize = 11.sp) }
                    }
                }
                if (ui.files.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showGalleryDates = true }) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp))
                        Text("拍摄日期 · " + when(selectedGalleryDate) { null -> "全部"; UNKNOWN_GALLERY_DATE -> "未知"; else -> selectedGalleryDate!! }, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${visibleFiles.size} 个文件", fontSize = 10.sp, color = Muted)
                }
                val failure = (connection as? CameraConnectionState.Error)?.message ?: ui.permissionError ?: ui.filesError ?: ui.scanWarning
                if (failure != null) Surface(color = Color(0xFFF3E8DB), modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(failure, fontSize = 11.sp, color = Color(0xFF76512D), modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { showDiagnostics = true }) { Text("诊断", fontSize = 11.sp) }
                    }
                }
                if (visibleFiles.isEmpty()) {
                    if (ui.files.isNotEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("此分类暂未发现文件", fontSize = 14.sp, color = Muted)
                    } else EmptyRoll(connected, busy || ui.filesLoading, ui.savedCameraName,
                        onConnect = { if (ui.savedCameraName != null) connect() else showConnection = true }, modifier = Modifier.weight(1f))
                } else {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), state = gridState, contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(11.dp), modifier = Modifier.weight(1f)) {
                        items(visibleFiles, key = { it.path }) { file ->
                            val selected = file.path in ui.selectedPaths
                            val selectionScale by animateFloatAsState(if (selected) .96f else 1f,
                                spring(dampingRatio = .8f, stiffness = 550f), label = "photoSelection")
                            val selectionColor by animateColorAsState(if (selected) Sage else Color.Black.copy(alpha = .25f), tween(160), label = "selectionColor")
                            val bitmap = ui.thumbnails[file.path]
                            val imageAlpha by animateFloatAsState(if (bitmap == null) 0f else 1f, tween(160), label = "thumbnailReveal")
                            val pageActive = tabPager.currentPage == 0 && !tabPager.isScrollInProgress && !showQueue
                            LaunchedEffect(file.path, ui.preview.loading, resumed, thumbnailLoadMode, pageActive) {
                                if (resumed && pageActive && !ui.preview.loading && thumbnailLoadMode == ThumbnailLoadMode.LOAD) viewModel.requestThumbnail(file)
                                else if (!pageActive) viewModel.releaseThumbnail(file)
                            }
                            DisposableEffect(file.path) { onDispose { viewModel.releaseThumbnail(file) } }
                            Column {
                                Box(Modifier.fillMaxWidth().aspectRatio(.83f).graphicsLayer { scaleX = selectionScale; scaleY = selectionScale }
                                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFFE8E8DF))
                                    .border(if (selected) 2.dp else 0.dp, if (selected) Sage else Color.Transparent, RoundedCornerShape(10.dp))
                                    .combinedClickable(onClick = { if (ui.selectedPaths.isNotEmpty()) viewModel.toggleSelection(file.path) else viewModel.openPreview(file, visibleFiles) }, onLongClick = { viewModel.toggleSelection(file.path) })) {
                                    if (bitmap != null) Image(bitmap.asImageBitmap(), file.name, Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha }, contentScale = ContentScale.Crop)
                                    else Icon(if (file.kind == MediaKind.VIDEO) Icons.Outlined.PlayCircle else Icons.Outlined.Image, null, Modifier.align(Alignment.Center).size(24.dp), tint = Color(0xFF9CA797))
                                    if (importKey(file) in queue.completedImports) Row(
                                        Modifier.align(Alignment.TopStart).padding(6.dp).background(Sage.copy(alpha = .95f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Check, "${file.name} 已导入手机", Modifier.size(12.dp), tint = Color.White)
                                        Spacer(Modifier.width(3.dp)); Text("已导入", color = Color.White, fontSize = 9.sp)
                                    }
                                    if (file.kind == MediaKind.RAW || file.kind == MediaKind.VIDEO) Text(if (file.kind == MediaKind.RAW) "RAW" else "视频", color = Color.White, fontSize = 9.sp,
                                        modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(3.dp)).padding(5.dp, 2.dp))
                                    Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(25.dp).graphicsLayer { scaleX = selectionScale; scaleY = selectionScale }.clip(CircleShape)
                                        .background(selectionColor)
                                        .border(1.dp, Color.White.copy(alpha = .85f), CircleShape).clickable { viewModel.toggleSelection(file.path) }, contentAlignment = Alignment.Center) {
                                        if (file.path in ui.selectedPaths) Icon(Icons.Outlined.Check, "已选择 ${file.name}", Modifier.size(17.dp), tint = Color.White)
                                        else Icon(Icons.Outlined.Add, "选择 ${file.name}", Modifier.size(14.dp), tint = Color.White)
                                    }
                                }
                                Text(file.name, fontSize = 9.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp))
                                Text(dateByPath[file.path]?.toString() ?: "日期未知", fontSize = 9.sp, color = Muted, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
    }
    if (showConnection) ModalBottomSheet(onDismissRequest = { showConnection = false }, containerColor = Paper) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("你的相机", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("第一次填好 Wi-Fi，之后让拾光帮你重连。", color = Muted, fontSize = 13.sp)
            OutlinedTextField(value = ui.ssid, onValueChange = viewModel::updateSsid, label = { Text("相机 Wi-Fi 名称（SSID）") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = ui.password, onValueChange = viewModel::updatePassword, label = { Text("Wi-Fi 密码（开放网络留空）") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = ui.baseUrl, onValueChange = viewModel::updateBaseUrl, label = { Text("相机地址") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("打开时自动重连", fontSize = 14.sp); Text("成功连接后记住这台相机", fontSize = 11.sp, color = Muted) }
                Switch(checked = ui.autoConnect, onCheckedChange = viewModel::updateAutoConnect)
            }
            Text("请先开启相机 Wi-Fi。首次连接需允许系统弹窗及相机访问；连接后会进入播放模式。", fontSize = 11.sp, color = Muted)
            if (connected) Text("Wi-Fi 已建立 · ${ui.accessStage.label}，可浏览后才会显示已连接。", fontSize = 11.sp, color = Muted)
            val error = (connection as? CameraConnectionState.Error)?.message ?: ui.permissionError
                ?: ui.filesError?.takeIf { ui.accessStage == GalleryAccessStage.FAILED }
            error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { connect() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(if (busy) "正在连接…" else "连接并打开胶卷") }
            TextButton(onClick = { connect(current = true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("已手动连好 Wi-Fi？使用当前网络", fontSize = 12.sp) }
            if (ui.savedCameraName != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { viewModel.forgetSavedCamera() }) { Text("忘记相机记录", fontSize = 11.sp) }
                if (connected) TextButton(onClick = { viewModel.disconnect(); showConnection = false }) { Text("断开连接", fontSize = 11.sp) }
            }
        }
    }
    if (showGalleryDates) GalleryDateSheet(dateCounts, selectedGalleryDate, onSelect = { selectedGalleryDate = it; viewModel.clearGallerySelection(); showGalleryDates = false }, onDismiss = { showGalleryDates = false },
        loading = ui.datesLoading, checked = ui.datesChecked, total = ui.datesTotal, message = ui.datesMessage,
        onReadDates = viewModel::readMissingDates, onStopDates = viewModel::stopDateRead)
    if (ui.preview.file != null) FullPreview(ui, onClose = viewModel::closePreview, onToggle = { viewModel.toggleSelection(ui.preview.file!!.path) }, onOriginal = viewModel::loadOriginalPreview, onPageSelected = viewModel::previewAt,
        onImport = { requestNotice(); viewModel.enqueueFile(ui.preview.file!!); showQueue = true })
    if (showAbout) AlertDialog(onDismissRequest = { showAbout = false }, title = { Text("拾光传输 · 0.2.18") },
        text = { Text("把喜欢的照片，轻松带到手机。\n\n拾光传输专注松下相机的照片浏览与无线导入，让选片、预览和保存更顺手。\n\n当前为测试版，不同相机与手机的兼容性仍在持续完善。") },
        confirmButton = { TextButton(onClick = { showAbout = false; showDiagnostics = true }) { Text("诊断日志") } },
        dismissButton = { TextButton(onClick = { showAbout = false; showLicenses = true }) { Text("开源声明") } })
    if (showDiagnostics) {
        val log = remember { Diagnostics.asText() }
        AlertDialog(onDismissRequest = { showDiagnostics = false }, title = { Text("诊断日志") },
            text = { Text(log.ifBlank { "暂无日志" }, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), fontSize = 11.sp) },
            confirmButton = { TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("拾光诊断", log)) }) { Text("复制") } },
            dismissButton = { TextButton(onClick = { showDiagnostics = false }) { Text("关闭") } })
    }
    if (showLicenses) {
        val notices = remember { runCatching { context.assets.open("OPEN_SOURCE_NOTICES.txt").bufferedReader().use { it.readText() } }.getOrDefault("LumaLink contributors · Apache-2.0\nhttps://github.com/Angelnable/LumaLink") }
        AlertDialog(onDismissRequest = { showLicenses = false }, title = { Text("开源许可与署名") }, text = { Text(notices, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), fontSize = 10.sp) }, confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("关闭") } })
    }
}

@Composable private fun EmptyRoll(connected: Boolean, busy: Boolean, saved: String?, onConnect: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(150.dp, 122.dp)) {
            drawRoundRect(Color(0xFFE0E6DA), Offset(size.width*.13f, size.height*.16f), Size(size.width*.7f, size.height*.7f), CornerRadius(18f))
            drawRoundRect(Sage, Offset(size.width*.06f, size.height*.25f), Size(size.width*.78f, size.height*.58f), CornerRadius(15f))
            drawCircle(Paper, size.width*.18f, Offset(size.width*.45f, size.height*.54f))
            drawCircle(Sage, size.width*.11f, Offset(size.width*.45f, size.height*.54f))
            drawCircle(Color(0xFFCEDAB8), size.width*.035f, Offset(size.width*.73f, size.height*.38f))
        }
        Spacer(Modifier.height(22.dp))
        Text(if (connected) "正在寻找最新的光。" else "把最新的照片，\n先带到眼前。", fontSize = 26.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(14.dp))
        Text(if (connected) "照片会分批出现，读到后就能预览和导入。" else "开启相机的 Wi-Fi，连接后即刻看图。\n不必等整张存储卡扫描完。", color = Muted, fontSize = 12.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(25.dp))
        if (busy) CircularProgressIndicator(Modifier.size(25.dp), strokeWidth = 2.dp)
        else if (!connected) Button(onClick = onConnect, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(50.dp).fillMaxWidth(.8f)) { Text(if (saved != null) "连接已记住的相机" else "连接我的相机") }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun FullPreview(ui: ConnectUiState, onClose: () -> Unit, onToggle: () -> Unit, onOriginal: () -> Unit, onPageSelected: (Int) -> Unit, onImport: () -> Unit) {
    val preview = ui.preview
    val file = preview.file ?: return
    val image = preview.bitmap ?: ui.thumbnails[file.path]
    var scale by remember(file.path) { mutableFloatStateOf(1f) }
    var pan by remember(file.path) { mutableStateOf(Offset.Zero) }
    val background = Color(0xFF101310)
    val secondary = Color(0xFFAEB7AE)
    val pagerState = rememberPagerState(initialPage = ui.previewIndex) { maxOf(1, ui.previewQueue.size) }
    val pagerScope = rememberCoroutineScope()
    LaunchedEffect(pagerState.settledPage) { if (pagerState.settledPage != ui.previewIndex) onPageSelected(pagerState.settledPage) }
    val window = (LocalView.current.context as? android.app.Activity)?.window
    DisposableEffect(window) {
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        onDispose {
            if (oldStatus != null) controller?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller?.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    SideEffect { window?.let {
        androidx.core.view.WindowCompat.getInsetsController(it, it.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    } }
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回胶卷", tint = Color.White) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(file.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        preview.loading && preview.originalRequested -> if (preview.bytesRead > 0) "原图 ${formatBytes(preview.bytesRead)} / ${formatBytes(preview.totalBytes)}" else "正在连接原图 · ${formatBytes(file.sizeBytes)}"
                        preview.loading -> "正在读取清晰预览"
                        preview.original -> "原图预览 · ${formatBytes(file.sizeBytes)}"
                        preview.highQuality -> "清晰预览 · 相机大预览"
                        else -> "快速预览 · 原文件 ${formatBytes(file.sizeBytes)}"
                    }, color = secondary, fontSize = 10.sp, maxLines = 2)
                }
                if (file.kind == MediaKind.JPEG && !preview.original) TextButton(onClick = onOriginal, enabled = !preview.loading) {
                    Text("读取原图", fontSize = 11.sp, color = if (preview.loading) secondary else Color(0xFFD4E5C8))
                }
            }
            if (preview.loading) {
                if (preview.totalBytes > 0 && preview.bytesRead > 0) LinearProgressIndicator(
                    progress = { (preview.bytesRead.toFloat() / preview.totalBytes).coerceIn(0f, 1f) },
                    color = Color(0xFF92B5A3), trackColor = background, modifier = Modifier.fillMaxWidth().height(2.dp))
                else LinearProgressIndicator(color = Color(0xFF92B5A3), trackColor = background, modifier = Modifier.fillMaxWidth().height(2.dp))
            } else Spacer(Modifier.height(2.dp))
            val transform = rememberTransformableState { zoom, movement, _ ->
                val nextScale = (scale * zoom).coerceIn(1f, 5f)
                scale = nextScale
                pan = if (nextScale <= 1f) Offset.Zero else pan + movement
            }
            HorizontalPager(state = pagerState, userScrollEnabled = scale <= 1f,
                key = { ui.previewQueue.getOrNull(it)?.path ?: file.path },
                modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) { page ->
                val pageFile = ui.previewQueue.getOrNull(page) ?: file
                val current = pageFile.path == file.path
                val pageImage = if (current) image else ui.thumbnails[pageFile.path]
                Box(Modifier.fillMaxSize().clipToBounds()
                    .pointerInput(pageFile.path) { detectTapGestures(onDoubleTap = {
                        if (current) { scale = if (scale > 1f) 1f else 2f; pan = Offset.Zero }
                    }) }.transformable(transform, canPan = { scale > 1f }), contentAlignment = Alignment.Center) {
                    if (pageImage != null) Image(pageImage.asImageBitmap(), pageFile.name,
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = if (current) scale else 1f; scaleY = if (current) scale else 1f
                            translationX = if (current) pan.x else 0f; translationY = if (current) pan.y else 0f
                        }, contentScale = ContentScale.Fit)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        if (current && preview.loading) { CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.height(14.dp)) }
                        Text(if (!current || preview.loading) "正在取回预览" else preview.error ?: "预览暂不可用，仍可导入原文件", color = secondary, fontSize = 12.sp)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(background).padding(horizontal = 18.dp, vertical = 10.dp)) {
                if (preview.error != null && image != null) Text(preview.error, color = Color(0xFFE4BD9D), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                if (ui.previewQueue.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pagerScope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } }, enabled = ui.previewIndex > 0) { Icon(Icons.Outlined.ChevronLeft, "上一张", tint = if (ui.previewIndex > 0) Color.White else secondary) }
                    Text("${ui.previewIndex + 1} / ${ui.previewQueue.size}", color = secondary, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { pagerScope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1)) } }, enabled = ui.previewIndex < ui.previewQueue.lastIndex) { Icon(Icons.Outlined.ChevronRight, "下一张", tint = if (ui.previewIndex < ui.previewQueue.lastIndex) Color.White else secondary) }
                }
                Text(if (image != null) "左右滑动切换 · 双击或双指缩放" else "预览失败不会影响原文件选择", color = secondary, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onToggle, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text(if (file.path in ui.selectedPaths) "取消选择" else "选择此图", fontSize = 13.sp) }
                    Button(onClick = onImport, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp)) { Text("导入原文件", fontSize = 13.sp) }
                }
            }
        }
    }
}
