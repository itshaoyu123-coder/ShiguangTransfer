/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumalink.g100d.core.data.TransferState
import com.lumalink.g100d.core.util.formatBytes
import java.time.format.DateTimeFormatter

@Composable internal fun ImportHistory(ui: TransfersUiState, connected: Boolean, onRetry: (String) -> Unit,
    onCancel: (String) -> Unit, onDelete: (List<String>, Boolean) -> Unit, onOpenConnection: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val thumbnails = remember(context.applicationContext) { HistoryThumbnailLoader(context.applicationContext) }
    DisposableEffect(thumbnails) { onDispose { thumbnails.clear() } }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var deleteItems by remember { mutableStateOf(emptyList<TransferItem>()) }
    var deleteFiles by remember { mutableStateOf(false) }
    var labResult by remember { mutableStateOf<LabResult?>(null) }
    val groups = remember(ui.items) { historyDays(ui.items) }
    BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }
    fun openLab(items: List<TransferItem>) {
        if (items.isEmpty() || items.any { it.state != TransferState.COMPLETED || it.localUri == null || labPhotoMime(it.fileName) == null }) {
            Toast.makeText(context, "请选择已导入完成的照片", Toast.LENGTH_LONG).show(); return
        }
        val result = sendToLumixLab(context, items)
        if (result != LabResult.EDIT_REQUESTED) labResult = result
    }
    fun confirmDelete(items: List<TransferItem>) {
        if (items.any { !canDeleteHistory(it) }) {
            Toast.makeText(context, "请先取消进行中的导入，再删除记录", Toast.LENGTH_LONG).show(); return
        }
        deleteFiles = false; deleteItems = items
    }
    if (ui.items.isEmpty()) Column(modifier.fillMaxWidth().padding(30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.SouthWest, null, Modifier.size(50.dp), tint = Color(0xFF356C58))
        Spacer(Modifier.height(22.dp)); Text("喜欢的照片，留在手机", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp)); Text("在胶卷中选择照片，点击「导入所选」。\n完成后可在这里打开照片或交给 LUMIX Lab。", fontSize = 12.sp, lineHeight = 22.sp)
    } else Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${ui.activeCount} 项进行中 · 今天已导入 ${ui.completedToday} 项", fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { selecting = !selecting; selected = emptySet() }) { Text(if (selecting) "完成选择" else "选择") }
        }
        if (selecting) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("已选 ${selected.size} 项", fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { openLab(ui.items.filter { it.id in selected }) }, enabled = selected.isNotEmpty()) { Text("LUMIX Lab", fontSize = 12.sp) }
            TextButton(onClick = { confirmDelete(ui.items.filter { it.id in selected }) }, enabled = selected.isNotEmpty()) { Text("删除", fontSize = 12.sp) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (group in groups) {
                item(key = "day-${group.date}") {
                    Text(group.date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")) + " · ${group.items.size} 项",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 3.dp))
                }
                items(group.items, key = { it.id }) { item ->
                    var menu by remember(item.id) { mutableStateOf(false) }
                    Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selecting) Checkbox(checked = item.id in selected,
                                    onCheckedChange = { selected = if (it) selected + item.id else selected - item.id })
                                HistoryThumbnail(item, thumbnails)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.fileName, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(when(item.state) {
                                        TransferState.QUEUED -> "等待导入"
                                        TransferState.RUNNING -> "${formatBytes(item.bytesDownloaded)} · ${formatBytes(item.speedBytesPerSecond)}/s"
                                        TransferState.COMPLETED -> "已保存至手机 · ${formatBytes(item.bytesDownloaded)}"
                                        TransferState.FAILED -> "导入失败"
                                        TransferState.CANCELED -> "已取消"
                                    }, fontSize = 10.sp, color = Color(0xFF777F76))
                                }
                                if (!selecting) {
                                    if (item.state == TransferState.RUNNING || item.state == TransferState.QUEUED)
                                        IconButton(onClick = { onCancel(item.id) }) { Icon(Icons.Outlined.Close, "取消导入", Modifier.size(18.dp)) }
                                    else Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "${item.fileName} 的操作") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            if (item.state == TransferState.COMPLETED && item.localUri != null) {
                                                DropdownMenuItem(text = { Text("打开照片 / 文件") }, onClick = {
                                                    menu = false
                                                    runCatching {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(importedContentUri(context, item.localUri), item.mimeType)
                                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                                                    }.onFailure { Toast.makeText(context, "文件不可用，或没有支持此格式的应用", Toast.LENGTH_LONG).show() }
                                                })
                                                if (labPhotoMime(item.fileName) != null) DropdownMenuItem(text = { Text("用 LUMIX Lab 编辑") }, onClick = { menu = false; openLab(listOf(item)) })
                                            }
                                            if (item.state == TransferState.FAILED || item.state == TransferState.CANCELED)
                                                DropdownMenuItem(text = { Text(if (connected) "重新导入" else "重连相机") }, onClick = { menu = false; if (connected) onRetry(item.id) else onOpenConnection() })
                                            DropdownMenuItem(text = { Text("选择这条记录") }, onClick = { menu = false; selecting = true; selected = setOf(item.id) })
                                            DropdownMenuItem(text = { Text("删除记录…") }, onClick = { menu = false; confirmDelete(listOf(item)) })
                                        }
                                    }
                                }
                            }
                            if (item.state == TransferState.RUNNING) LinearProgressIndicator(progress = { item.progress ?: 0f }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(3.dp))
                            item.errorMessage?.takeIf { item.state == TransferState.FAILED }?.let { Text(it, fontSize = 11.sp, color = Color(0xFF8B533B), modifier = Modifier.padding(top = 8.dp)) }
                        }
                    }
                }
            }
        }
    }
    if (deleteItems.isNotEmpty()) AlertDialog(onDismissRequest = { deleteItems = emptyList() },
        title = { Text("删除 ${deleteItems.size} 条导入记录？") },
        text = { Column {
            Text("默认只删除记录，已导入手机的文件保留。相机存储卡原片不会删除。", fontSize = 13.sp)
            if (deleteItems.any { it.localUri != null }) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                Text("同时删除手机上的已导入文件", fontSize = 12.sp)
            }
        } },
        confirmButton = { TextButton(onClick = { onDelete(deleteItems.map { it.id }, deleteFiles); selected = selected - deleteItems.map { it.id }.toSet(); deleteItems = emptyList() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleteItems = emptyList() }) { Text("取消") } })
    labResult?.let { result -> AlertDialog(onDismissRequest = { labResult = null }, title = { Text("LUMIX Lab") },
        text = { Text(when(result) {
            LabResult.NOT_INSTALLED -> "手机未安装 LUMIX Lab。安装后可再次尝试。"
            LabResult.SINGLE_PHOTO_REQUIRED -> "直接编辑一次只能选择一张照片，请单选后再试。"
            LabResult.MANUAL_IMPORT -> "当前版本没有可用的直接编辑入口，暂时不能把这张照片带到编辑页。可打开 Lab 后从手机图库选择。"
            else -> "暂时无法交给 Lab，文件可能已被移走或无法读取。可以在 Lab 中从手机图库选择。"
        }) },
        confirmButton = { TextButton(onClick = {
            if (result == LabResult.NOT_INSTALLED) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$LUMIX_LAB_PACKAGE"))) }
            else if (!launchLumixLab(context)) Toast.makeText(context, "无法打开 LUMIX Lab", Toast.LENGTH_LONG).show()
            labResult = null
        }) { Text(if (result == LabResult.NOT_INSTALLED) "安装 Lab" else "打开 Lab 手动选择") } },
        dismissButton = { TextButton(onClick = { labResult = null }) { Text("取消") } }) }
}
