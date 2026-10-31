/* Copyright 2026 Shiguang Transfer contributors. Licensed under Apache-2.0. */
package com.lumalink.g100d.transport.session

internal fun cameraCredentialsError(ssid: String, password: String): String? = when {
    ssid.trim().isEmpty() -> "请输入相机屏幕上的 Wi-Fi 名称"
    ssid.trim().toByteArray(Charsets.UTF_8).size > 32 -> "Wi-Fi 名称超过 32 字节"
    password.isNotEmpty() && (password.length !in 8..63 || password.any { it.code !in 32..126 }) -> "Wi-Fi 密码应为 8–63 位可打印英文字符；开放网络请留空"
    else -> null
}
