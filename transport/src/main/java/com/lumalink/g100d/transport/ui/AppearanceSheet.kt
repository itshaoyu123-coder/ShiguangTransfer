/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumalink.g100d.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppearanceSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selected = LocalGradientPalette.current
    var top by rememberSaveable { mutableStateOf(gradientHex(selected.top)) }
    var middle by rememberSaveable { mutableStateOf(gradientHex(selected.middle)) }
    var bottom by rememberSaveable { mutableStateOf(gradientHex(selected.bottom)) }
    val parsed = listOf(top, middle, bottom).map(::parseGradientHex)
    val valid = parsed.all { it != null }
    val preview = if (valid) GradientPalette("custom", "自定义", parsed[0]!!, parsed[1]!!, parsed[2]!!) else selected
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFFFAFAF7)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("外观与背景", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text("柔和的色彩，让照片成为主角。", color = Color(0xFF405657), fontSize = 13.sp)
            for (row in GradientPresets.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { palette ->
                    Surface(onClick = {
                        top = gradientHex(palette.top); middle = gradientHex(palette.middle); bottom = gradientHex(palette.bottom)
                        saveGradientPalette(context, palette)
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(if (selected.id == palette.id) 2.dp else 1.dp,
                            if (selected.id == palette.id) Color(0xFF24585A) else Color.White)) {
                        Box(Modifier.height(92.dp).background(Brush.verticalGradient(palette.colors())).padding(12.dp), contentAlignment = Alignment.BottomStart) {
                            Text(palette.name + if (selected.id == palette.id) "  ✓" else "", fontSize = 13.sp, color = Color(0xFF1B3435), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text("自定义渐变", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().height(88.dp).background(Brush.verticalGradient(preview.colors()), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Text("拾光 · 背景预览", color = Color(0xFF1B3435), fontSize = 16.sp)
            }
            listOf("顶部色" to top, "中间色" to middle, "底部色" to bottom).forEachIndexed { index, (label, value) ->
                OutlinedTextField(value = value, onValueChange = { input ->
                    if (input.length <= 7) when (index) { 0 -> top = input; 1 -> middle = input; else -> bottom = input }
                }, label = { Text(label) }, placeholder = { Text("#B4D3D1") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = value.isNotBlank() && parseGradientHex(value) == null,
                    supportingText = if (value.isNotBlank() && parseGradientHex(value) == null) ({ Text("请输入六位颜色，如 #B4D3D1") }) else null,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
            Text("也可以点选柔和色，再应用背景。", fontSize = 12.sp, color = Color(0xFF405657))
            var editingStop by rememberSaveable { mutableIntStateOf(0) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("顶部", "中间", "底部").forEachIndexed { index, label ->
                    FilterChip(selected = editingStop == index, onClick = { editingStop = index }, label = { Text(label) })
                }
            }
            val swatches = listOf(0xFFB4D3D1L, 0xFFBCD1E8L, 0xFFD8D0E7L, 0xFFE6C6AAL, 0xFFE8C1A9L, 0xFFC2D6C8L, 0xFFF3EFE6L)
            for (row in swatches.chunked(4)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value ->
                    Surface(onClick = { when (editingStop) { 0 -> top = gradientHex(value); 1 -> middle = gradientHex(value); else -> bottom = gradientHex(value) } },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "选择颜色 ${gradientHex(value)}" },
                        shape = RoundedCornerShape(12.dp), color = Color(value),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) {
                        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                            if (parsed.getOrNull(editingStop) == value) Text("✓", color = Color(0xFF1B3435))
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Text("支持 #RRGGBB 颜色。过深的颜色会自动柔化，保证文字清晰。", fontSize = 12.sp, color = Color(0xFF405657))
            Button(onClick = { if (valid) saveGradientPalette(context, preview) }, enabled = valid,
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) {
                Text(if (selected == preview) "已应用自定义背景" else "应用自定义背景")
            }
            TextButton(onClick = {
                val default = GradientPresets.first()
                top = gradientHex(default.top); middle = gradientHex(default.middle); bottom = gradientHex(default.bottom)
                saveGradientPalette(context, default)
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("恢复默认 · 海岸晨雾") }
        }
    }
}
