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
@Composable internal fun GalleryDateSheet(days: Map<LocalDate?, Int>, selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("按拍摄日期筛选", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text("仅筛选已扫描的照片；没有拍摄日期的文件单列，不用导入时间代替。", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
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
