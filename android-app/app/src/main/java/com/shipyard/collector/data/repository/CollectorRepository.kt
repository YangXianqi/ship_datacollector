package com.shipyard.collector.data.repository

import androidx.room.withTransaction
import com.shipyard.collector.data.local.AppDatabase
import com.shipyard.collector.data.local.CaptureRecordEntity
import com.shipyard.collector.data.local.toModel
import com.shipyard.collector.data.local.toSummary
import com.shipyard.collector.model.CaptureRecord
import com.shipyard.collector.model.FormSummary
import com.shipyard.collector.model.RecordStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class CollectorRepository(
    private val database: AppDatabase
) {
    private val recordDao = database.captureRecordDao()
    private val formDao = database.formDao()

    val records: Flow<List<CaptureRecord>> = recordDao.observeAll().map { entities ->
        entities.map { it.toModel() }
    }

    val forms: Flow<List<FormSummary>> = combine(
        formDao.observeAll(),
        records
    ) { forms, records ->
        forms.map { form ->
            val pendingCount = records.count {
                it.formId == form.formId && it.status == RecordStatus.PENDING
            }
            val failedCount = records.count {
                it.formId == form.formId && it.status == RecordStatus.FAILED
            }
            form.toSummary(
                pendingCount = pendingCount,
                failedCount = failedCount
            )
        }
    }

    suspend fun saveRecord(
        formId: String,
        locationName: String,
        textNote: String,
        photoPaths: List<String>,
        audioPath: String?
    ): Result<CaptureRecord> = runCatching {
        val totalCount = recordDao.countAll()
        check(totalCount < 500) { "本地缓存已达 500 条，请先上传或删除部分记录" }
        check(photoPaths.isNotEmpty()) { "至少需要 1 张图片" }
        check(photoPaths.size <= 5) { "单条记录最多 5 张图片" }

        val form = formDao.getById(formId) ?: error("未找到目标表单")
        val now = System.currentTimeMillis()
        val record = CaptureRecordEntity(
            recordId = UUID.randomUUID().toString(),
            formId = form.formId,
            formName = form.formName,
            locationName = locationName.trim(),
            photoPaths = photoPaths,
            audioPath = audioPath,
            textNote = textNote.trim(),
            status = RecordStatus.PENDING.name,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            failureReason = null
        )
        recordDao.upsert(record)
        record.toModel()
    }

    suspend fun updateRecord(
        recordId: String,
        locationName: String,
        textNote: String,
        photoPaths: List<String>,
        audioPath: String?
    ): Result<CaptureRecord> = runCatching {
        check(photoPaths.isNotEmpty()) { "至少需要 1 张图片" }
        check(photoPaths.size <= 5) { "单条记录最多 5 张图片" }

        val existing = recordDao.getById(recordId) ?: error("未找到要更新的记录")
        check(existing.status == RecordStatus.PENDING.name || existing.status == RecordStatus.FAILED.name) {
            "当前状态不允许编辑"
        }

        deleteRemovedMedia(
            original = existing,
            nextPhotoPaths = photoPaths,
            nextAudioPath = audioPath
        )

        val updated = existing.copy(
            locationName = locationName.trim(),
            photoPaths = photoPaths,
            audioPath = audioPath,
            textNote = textNote.trim(),
            status = RecordStatus.PENDING.name,
            updatedAtEpochMillis = System.currentTimeMillis(),
            failureReason = null
        )
        recordDao.upsert(updated)
        updated.toModel()
    }

    suspend fun countUnresolvedRecords(): Int = recordDao.countUnresolved()

    suspend fun getRecord(recordId: String): CaptureRecord? {
        return recordDao.getById(recordId)?.toModel()
    }

    suspend fun deleteRecords(recordIds: Collection<String>) {
        if (recordIds.isEmpty()) return
        database.withTransaction {
            val records = recordDao.getByIds(recordIds.toList())
            deleteLocalMedia(records)
            recordDao.deleteByIds(recordIds.toList())
        }
    }

    suspend fun getRecordsByIds(recordIds: Collection<String>): List<CaptureRecord> {
        if (recordIds.isEmpty()) return emptyList()
        return recordDao.getByIds(recordIds.toList()).map { it.toModel() }
    }

    suspend fun markRecordPending(recordId: String) {
        recordDao.updateStatus(
            recordId = recordId,
            status = RecordStatus.PENDING.name,
            updatedAtEpochMillis = System.currentTimeMillis(),
            failureReason = null
        )
    }

    suspend fun clearUploadedRecords(recordIds: Collection<String>) {
        if (recordIds.isEmpty()) return
        database.withTransaction {
            val uploadedRecords = recordDao.getByIds(recordIds.toList())
                .filter { it.status == RecordStatus.UPLOADED.name }
            deleteLocalMedia(uploadedRecords)
            val uploadedIds = uploadedRecords.map { it.recordId }
            recordDao.deleteByIds(uploadedIds)
        }
    }

    private fun deleteLocalMedia(records: List<CaptureRecordEntity>) {
        records.forEach { record ->
            record.photoPaths.forEach { path -> File(path).takeIf(File::exists)?.delete() }
            record.audioPath?.let { path -> File(path).takeIf(File::exists)?.delete() }
        }
    }

    private fun deleteRemovedMedia(
        original: CaptureRecordEntity,
        nextPhotoPaths: List<String>,
        nextAudioPath: String?
    ) {
        original.photoPaths
            .filterNot(nextPhotoPaths::contains)
            .forEach { path -> File(path).takeIf(File::exists)?.delete() }

        original.audioPath
            ?.takeIf { it != nextAudioPath }
            ?.let { path -> File(path).takeIf(File::exists)?.delete() }
    }
}
