package com.shipyard.collector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.shipyard.collector.CollectorApplication
import com.shipyard.collector.data.repository.UploadQueueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UploadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var uploadQueueRepository: UploadQueueRepository
    private var processingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        uploadQueueRepository = (application as CollectorApplication).appContainer.uploadQueueRepository
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("准备处理上传队列"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START,
            ACTION_RESUME -> startProcessingLoop()

            ACTION_PAUSE -> serviceScope.launch {
                uploadQueueRepository.pauseActiveBatch()
                processingJob?.cancel()
                uploadQueueRepository.requeueInProgressIfNeeded()
                stopSelf()
            }

            ACTION_CANCEL -> serviceScope.launch {
                processingJob?.cancel()
                uploadQueueRepository.cancelActiveBatch()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProcessingLoop() {
        if (processingJob?.isActive == true) return

        processingJob = serviceScope.launch {
            try {
                while (isActive) {
                    when (val result = uploadQueueRepository.processNextQueuedRecord()) {
                        is UploadQueueRepository.ProcessNextResult.Uploaded -> {
                            updateNotification("已上传：${result.locationName}")
                        }

                        is UploadQueueRepository.ProcessNextResult.Failed -> {
                            updateNotification("上传失败：${result.reason}")
                        }

                        UploadQueueRepository.ProcessNextResult.Paused,
                        UploadQueueRepository.ProcessNextResult.Idle,
                        UploadQueueRepository.ProcessNextResult.Completed -> {
                            if (result == UploadQueueRepository.ProcessNextResult.Completed) {
                                updateNotification("本批次已完成，可回到应用清理已上传缓存")
                            }
                            stopSelf()
                            break
                        }
                    }
                }
            } catch (_: CancellationException) {
                uploadQueueRepository.requeueInProgressIfNeeded()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "上传状态",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("船厂离线采集")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "upload_status"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.shipyard.collector.action.START_UPLOAD"
        private const val ACTION_RESUME = "com.shipyard.collector.action.RESUME_UPLOAD"
        private const val ACTION_PAUSE = "com.shipyard.collector.action.PAUSE_UPLOAD"
        private const val ACTION_CANCEL = "com.shipyard.collector.action.CANCEL_UPLOAD"

        fun start(context: Context) {
            launch(context, ACTION_START)
        }

        fun resume(context: Context) {
            launch(context, ACTION_RESUME)
        }

        fun pause(context: Context) {
            launch(context, ACTION_PAUSE)
        }

        fun cancel(context: Context) {
            launch(context, ACTION_CANCEL)
        }

        private fun launch(context: Context, action: String) {
            val intent = Intent(context, UploadForegroundService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
