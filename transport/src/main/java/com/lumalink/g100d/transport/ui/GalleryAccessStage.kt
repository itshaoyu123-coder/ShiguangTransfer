package com.lumalink.g100d.transport.ui

enum class GalleryAccessStage(val label: String) {
    WAITING("待授权"), AUTHORIZING("授权中"), READING("读取胶卷"), READY("已连接"), FAILED("访问未完成"),
}
