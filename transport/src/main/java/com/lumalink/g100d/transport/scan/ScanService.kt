/* Copyright 2026 Shiguang Transfer contributors. Apache-2.0. */
package com.lumalink.g100d.transport.scan

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lumalink.g100d.transport.R
import com.lumalink.g100d.transport.ui.ConnectViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface ScanServiceHost { val scanCamera: ConnectViewModel }

/** Protects the application-owned scan, independently of Activity visibility. */
class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private val camera get() = (application as ScanServiceHost).scanCamera
    private data class Progress(val active: Boolean, val objects: Int, val total: Int?, val files: Int)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "相机目录扫描", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification(Progress(true, 0, null, 0)), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (intent?.action == PAUSE) { camera.pauseScan(); stopSelf(); return START_NOT_STICKY }
        if (collector?.isActive == true) return START_NOT_STICKY
        camera.setScanProtection(true)
        collector = scope.launch {
            camera.uiState.map { Progress(it.filesLoading && !it.scanPaused, it.scannedCount, it.scanTotal, it.files.size) }
                .distinctUntilChanged().collect { progress ->
                    if (!progress.active) { stopSelf(); return@collect }
                    if (wake == null) {
                        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Shiguang:scan").apply {
                            setReferenceCounted(false); acquire(2 * 60 * 60 * 1000L)
                        }
                        wifi = applicationContext.getSystemService(WifiManager::class.java).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Shiguang:scan").apply {
                            setReferenceCounted(false); acquire()
                        }
                    }
                    manager.notify(ID, notification(progress))
                }
        }
        scope.launch {
            while (true) { delay(60 * 60 * 1000L); if (camera.uiState.value.filesLoading && !camera.uiState.value.scanPaused) wake?.acquire(2 * 60 * 60 * 1000L) }
        }
        return START_NOT_STICKY
    }
    private fun notification(p: Progress): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 701, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val pause = PendingIntent.getService(this, 702, Intent(this, ScanService::class.java).setAction(PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_lumalink)
            .setContentTitle("拾光 · 正在扫描相机")
            .setContentText("已读取 ${p.objects}${p.total?.let { " / $it" }.orEmpty()} 项 · ${p.files} 个文件")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(p.total ?: 0, p.objects, p.total == null).addAction(0, "暂停扫描", pause).build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) { camera.pauseScan(); stopSelf() }
    override fun onDestroy() {
        camera.setScanProtection(false)
        if (camera.uiState.value.filesLoading && !camera.uiState.value.scanPaused) camera.pauseScan()
        scope.cancel()
        runCatching { wake?.let { if (it.isHeld) it.release() } }
        runCatching { wifi?.let { if (it.isHeld) it.release() } }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "shiguang_scan"
        private const val ID = 701
        private const val PAUSE = "app.shiguang.PAUSE_SCAN"
        fun start(context: Context) { ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java)) }
    }
}
