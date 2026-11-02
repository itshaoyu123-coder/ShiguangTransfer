/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GalleryDateSheet(days: Map<LocalDate?, Int>, selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit,
    loading: Boolean, checked: Int, total: Int, message: String?, onReadDates: () -> Unit, onStopDates: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("按拍摄日期筛选", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("手动开启后，优先补全当前浏览位置及附近照片的日期，再继续其余已扫描照片。滚动、预览和导入优先；不会下载整张原片。", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = if (loading) onStopDates else onReadDates) { Text(if (loading) "暂停读取日期" else "补全拍摄日期") }
            if (total > 0) Text("$checked / $total 张", modifier = Modifier.padding(top = 12.dp), fontSize = 12.sp)
        }
        if (message != null) Text(if (loading) "当前位置附近优先 · 滚动、预览和导入时让路" else message, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                ListItem(headlineContent = { Text("全部日期") }, supportingContent = { Text("${days.values.sum()} 个文件") },
                    trailingContent = { if (selected == null) Icon(Icons.Outlined.Check, "已选") }, modifier = Modifier.clickable { onSelect(null) })
            }
            for (date in days.keys.filterNotNull().sortedDescending()) item(key = date.toString()) {
                ListItem(headlineContent = { Text(date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))) }, supportingContent = { Text("${days[date]} 个文件") },
                    trailingContent = { if (selected == date.toString()) Icon(Icons.Outlined.Check, "已选") }, modifier = Modifier.clickable { onSelect(date.toString()) })
            }
            if (days.containsKey(null)) item {
                ListItem(headlineContent = { Text("日期未知") }, supportingContent = { Text("${days[null]} 个文件") },
                    trailingContent = { if (selected == UNKNOWN_GALLERY_DATE) Icon(Icons.Outlined.Check, "已选") }, modifier = Modifier.clickable { onSelect(UNKNOWN_GALLERY_DATE) })
            }
        }
    }
}
