package com.shipyard.collector.data.repository

import androidx.room.withTransaction
import com.shipyard.collector.data.local.AppDatabase
import com.shipyard.collector.data.local.UploadQueueEntryEntity
import com.shipyard.collector.data.local.UploadQueueStateEntity
import com.shipyard.collector.data.local.toModel
import com.shipyard.collector.data.remote.CollectorApi
import com.shipyard.collector.model.CaptureRecord
import com.shipyard.collector.data.remote.UploadResponse
import com.shipyard.collector.model.RecordStatus
import com.shipyard.collector.model.UploadBatchStatus
import com.shipyard.collector.model.UploadControllerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class UploadQueueRepository(
    private val database: AppDatabase,
    private val authRepository: AuthRepository,
    private val collectorApi: CollectorApi
) {
    private val recordDao = database.captureRecordDao()
    private val queueDao = database.uploadQueueDao()

    val queueState: Flow<UploadControllerState> = queueDao.observeState().flatMapLatest { state ->
        val batchId = state?.batchId
        if (batchId.isNullOrBlank()) {
            flowOf(state.toModel())
        } else {
            queueDao.observeEntriesForBatch(batchId).map { entries ->
                state.toModel(
                    queuedCount = entries.count { it.status == QueueEntryStatus.QUEUED.name },
                    uploadingCount = entries.count { it.status == QueueEntryStatus.IN_PROGRESS.name },
                    failedCount = entries.count { it.status == QueueEntryStatus.FAILED.name }
                )
            }
        }
    }

    suspend fun enqueue(recordIds: Collection<String>): Result<UploadControllerState> = runCatching {
        check(recordIds.isNotEmpty()) { "请选择要上传的记录" }
        val currentState = queueDao.getState()
        check(currentState?.status != UploadBatchStatus.RUNNING.name) { "当前已有上传任务在执行" }

        val now = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()
        val entries = recordIds.distinct().map { recordId ->
            UploadQueueEntryEntity(
                recordId = recordId,
                batchId = batchId,
                status = QueueEntryStatus.QUEUED.name,
                attemptCount = 0,
                enqueuedAtEpochMillis = now,
                updatedAtEpochMillis = now,
                lastError = null
            )
        }

        database.withTransaction {
            currentState?.batchId?.let { queueDao.deleteBatch(it) }
            queueDao.upsertEntries(entries)
            queueDao.upsertState(
                UploadQueueStateEntity(
                    batchId = batchId,
                    status = UploadBatchStatus.RUNNING.name,
                    updatedAtEpochMillis = now,
                    currentLocationName = null,
                    currentFileName = null,
                    uploadedBytes = 0,
                    totalBytes = 0,
                    completedCount = 0,
                    successCount = 0,
                    totalCount = entries.size,
                    lastMessage = "已加入上传队列，共 ${entries.size} 条"
                )
            )
        }

        UploadControllerState(
            batchId = batchId,
            status = UploadBatchStatus.RUNNING,
            queuedCount = entries.size
        )
    }

    suspend fun pauseActiveBatch() {
        val state = queueDao.getState() ?: return
        queueDao.upsertState(
            state.copy(
                status = UploadBatchStatus.PAUSED.name,
                updatedAtEpochMillis = System.currentTimeMillis(),
                lastMessage = "上传已暂停，可稍后继续"
            )
        )
    }

    suspend fun resumeActiveBatch(): Boolean {
        val state = queueDao.getState() ?: return false
        if (state.batchId.isNullOrBlank()) return false
        queueDao.upsertState(
            state.copy(
                status = UploadBatchStatus.RUNNING.name,
                updatedAtEpochMillis = System.currentTimeMillis(),
                lastMessage = "继续上传中"
            )
        )
        return true
    }

    suspend fun cancelActiveBatch() {
        val state = queueDao.getState() ?: return
        database.withTransaction {
            val inProgress = state.batchId?.let { queueDao.getInProgressEntry(it) }
            if (inProgress != null) {
                recordDao.updateStatus(
                    recordId = inProgress.recordId,
                    status = RecordStatus.PENDING.name,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    failureReason = null
                )
            }
            state.batchId?.let { queueDao.deleteBatch(it) }
            queueDao.upsertState(
                UploadQueueStateEntity(
                    batchId = null,
                    status = UploadBatchStatus.IDLE.name,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    currentLocationName = null,
                    currentFileName = null,
                    uploadedBytes = 0,
                    totalBytes = 0,
                    completedCount = 0,
                    successCount = 0,
                    totalCount = 0,
                    lastMessage = "本批次已取消，本地缓存已保留"
                )
            )
        }
    }

    suspend fun requeueInProgressIfNeeded() {
        val state = queueDao.getState() ?: return
        val batchId = state.batchId ?: return
        val entry = queueDao.getInProgressEntry(batchId) ?: return
        val now = System.currentTimeMillis()
        database.withTransaction {
            queueDao.updateEntry(
                recordId = entry.recordId,
                status = QueueEntryStatus.QUEUED.name,
                attemptCount = entry.attemptCount,
                updatedAtEpochMillis = now,
                lastError = entry.lastError
            )
            recordDao.updateStatus(
                recordId = entry.recordId,
                status = RecordStatus.PENDING.name,
                updatedAtEpochMillis = now,
                failureReason = null
            )
            queueDao.upsertState(
                state.copy(
                    updatedAtEpochMillis = now,
                    currentLocationName = null,
                    currentFileName = null,
                    uploadedBytes = 0,
                    totalBytes = 0,
                    lastMessage = "上传已暂停，可继续上传"
                )
            )
        }
    }

    suspend fun processNextQueuedRecord(): ProcessNextResult {
        val state = queueDao.getState() ?: return ProcessNextResult.Idle
        val batchId = state.batchId ?: return ProcessNextResult.Idle
        if (state.status != UploadBatchStatus.RUNNING.name) return ProcessNextResult.Paused

        val nextEntry = queueDao.getNextQueuedEntry(batchId)
        if (nextEntry == null) {
            val outstanding = queueDao.countOutstandingEntries(batchId)
            if (outstanding == 0) {
                finishBatch(batchId)
                return ProcessNextResult.Completed
            }
            return ProcessNextResult.Idle
        }

        val session = authRepository.session.firstOrNull()
        if (session == null) {
            pauseActiveBatch()
            return ProcessNextResult.Failed("请重新登录后再上传")
        }
        val record = recordDao.getById(nextEntry.recordId)?.toModel()
        if (record == null) {
            queueDao.updateEntry(
                recordId = nextEntry.recordId,
                status = QueueEntryStatus.FAILED.name,
                attemptCount = nextEntry.attemptCount + 1,
                updatedAtEpochMillis = System.currentTimeMillis(),
                lastError = "未找到待上传记录"
            )
            return ProcessNextResult.Failed("未找到待上传记录")
        }

        val now = System.currentTimeMillis()
        database.withTransaction {
            queueDao.updateEntry(
                recordId = nextEntry.recordId,
                status = QueueEntryStatus.IN_PROGRESS.name,
                attemptCount = nextEntry.attemptCount + 1,
                updatedAtEpochMillis = now,
                lastError = null
            )
            recordDao.updateStatus(
                recordId = nextEntry.recordId,
                status = RecordStatus.UPLOADING.name,
                updatedAtEpochMillis = now,
                failureReason = null
            )
            queueDao.upsertState(
                state.copy(
                    updatedAtEpochMillis = now,
                    currentLocationName = record.locationName,
                    currentFileName = null,
                    uploadedBytes = 0,
                    totalBytes = record.totalAttachmentBytes(),
                    lastMessage = "正在上传 ${record.locationName}"
                )
            )
        }

        val response = runCatching {
            collectorApi.uploadRecord(session, record) { progress ->
                updateProgress(
                    locationName = record.locationName,
                    fileName = progress.currentFileName,
                    uploadedBytes = progress.uploadedBytes,
                    totalBytes = progress.totalBytes,
                    lastMessage = progress.message ?: "正在上传 ${record.locationName}"
                )
            }
        }.getOrElse { throwable ->
            UploadResponse(
                success = false,
                errorMessage = throwable.message ?: "上传中断，请稍后继续上传"
            )
        }
        val updatedAt = System.currentTimeMillis()
        return if (response.success) {
            database.withTransaction {
                queueDao.updateEntry(
                    recordId = nextEntry.recordId,
                    status = QueueEntryStatus.COMPLETED.name,
                    attemptCount = nextEntry.attemptCount + 1,
                    updatedAtEpochMillis = updatedAt,
                    lastError = null
                )
                recordDao.updateStatus(
                    recordId = nextEntry.recordId,
                    status = RecordStatus.UPLOADED.name,
                    updatedAtEpochMillis = updatedAt,
                    failureReason = null
                )
                val currentState = queueDao.getState()
                if (currentState != null) {
                    queueDao.upsertState(
                        currentState.copy(
                            updatedAtEpochMillis = updatedAt,
                            currentLocationName = record.locationName,
                            currentFileName = null,
                            uploadedBytes = currentState.totalBytes,
                            totalBytes = currentState.totalBytes,
                            completedCount = currentState.completedCount + 1,
                            successCount = currentState.successCount + 1,
                            lastMessage = response.successMessage ?: "${record.locationName} 上传成功，可清理本地缓存"
                        )
                    )
                }
            }
            ProcessNextResult.Uploaded(record.locationName)
        } else {
            database.withTransaction {
                queueDao.updateEntry(
                    recordId = nextEntry.recordId,
                    status = QueueEntryStatus.FAILED.name,
                    attemptCount = nextEntry.attemptCount + 1,
                    updatedAtEpochMillis = updatedAt,
                    lastError = response.errorMessage
                )
                recordDao.updateStatus(
                    recordId = nextEntry.recordId,
                    status = RecordStatus.FAILED.name,
                    updatedAtEpochMillis = updatedAt,
                    failureReason = response.errorMessage ?: "上传失败"
                )
                val currentState = queueDao.getState()
                if (currentState != null) {
                    queueDao.upsertState(
                        currentState.copy(
                            updatedAtEpochMillis = updatedAt,
                            currentLocationName = record.locationName,
                            currentFileName = null,
                            uploadedBytes = 0,
                            totalBytes = 0,
                            completedCount = currentState.completedCount + 1,
                            lastMessage = response.errorMessage ?: "${record.locationName} 上传失败"
                        )
                    )
                }
            }
            ProcessNextResult.Failed(response.errorMessage ?: "上传失败")
        }
    }

    private suspend fun finishBatch(batchId: String) {
        val state = queueDao.getState()
        val entries = queueDao.observeEntriesForBatch(batchId).firstOrNull().orEmpty()
        val successCount = entries.count { it.status == QueueEntryStatus.COMPLETED.name }
        val failedCount = entries.count { it.status == QueueEntryStatus.FAILED.name }
        val totalCount = entries.size
        val message = if (failedCount == 0) {
            "本批次上传完成，共 $successCount/$totalCount 条成功，可勾选已上传后清理缓存"
        } else {
            "本批次完成：成功 $successCount 条，失败 $failedCount 条，失败记录仍保留在本地"
        }
        database.withTransaction {
            queueDao.deleteBatch(batchId)
            queueDao.upsertState(
                UploadQueueStateEntity(
                    batchId = null,
                    status = UploadBatchStatus.IDLE.name,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    currentLocationName = null,
                    currentFileName = null,
                    uploadedBytes = 0,
                    totalBytes = 0,
                    completedCount = state?.completedCount ?: successCount + failedCount,
                    successCount = state?.successCount ?: successCount,
                    totalCount = state?.totalCount ?: totalCount,
                    lastMessage = message
                )
            )
        }
    }

    private suspend fun updateProgress(
        locationName: String,
        fileName: String?,
        uploadedBytes: Long,
        totalBytes: Long,
        lastMessage: String
    ) {
        val state = queueDao.getState() ?: return
        queueDao.upsertState(
            state.copy(
                updatedAtEpochMillis = System.currentTimeMillis(),
                currentLocationName = locationName,
                currentFileName = fileName,
                uploadedBytes = uploadedBytes,
                totalBytes = totalBytes,
                lastMessage = lastMessage
            )
        )
    }

    private fun CaptureRecord.totalAttachmentBytes(): Long {
        return photoPaths.sumOf(::fileSize) + (audioPath?.let(::fileSize) ?: 0L)
    }

    private fun fileSize(path: String): Long = java.io.File(path).takeIf { it.exists() }?.length() ?: 0L

    private enum class QueueEntryStatus {
        QUEUED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    sealed interface ProcessNextResult {
        data object Idle : ProcessNextResult
        data object Paused : ProcessNextResult
        data object Completed : ProcessNextResult
        data class Uploaded(val locationName: String) : ProcessNextResult
        data class Failed(val reason: String) : ProcessNextResult
    }
}
