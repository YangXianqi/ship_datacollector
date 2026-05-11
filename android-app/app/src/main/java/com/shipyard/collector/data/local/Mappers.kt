package com.shipyard.collector.data.local

import com.shipyard.collector.model.CaptureRecord
import com.shipyard.collector.model.FormSummary
import com.shipyard.collector.model.RecordStatus
import com.shipyard.collector.model.UploadBatchStatus
import com.shipyard.collector.model.UploadControllerState
import com.shipyard.collector.model.UploadMode

fun CaptureRecordEntity.toModel(): CaptureRecord = CaptureRecord(
    recordId = recordId,
    formId = formId,
    formName = formName,
    locationName = locationName,
    photoPaths = photoPaths,
    audioPath = audioPath,
    textNote = textNote,
    status = RecordStatus.valueOf(status),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    failureReason = failureReason
)

fun FormEntity.toSummary(
    pendingCount: Int,
    failedCount: Int
): FormSummary = FormSummary(
    id = formId,
    name = formName,
    pendingCount = pendingCount,
    failedCount = failedCount,
    defaultUploadMode = UploadMode.valueOf(defaultUploadMode)
)

fun UploadQueueStateEntity?.toModel(
    queuedCount: Int = 0,
    uploadingCount: Int = 0,
    failedCount: Int = 0
): UploadControllerState {
    if (this == null) return UploadControllerState()
    return UploadControllerState(
        batchId = batchId,
        status = UploadBatchStatus.valueOf(status),
        queuedCount = queuedCount,
        uploadingCount = uploadingCount,
        failedCount = failedCount,
        currentLocationName = currentLocationName,
        currentFileName = currentFileName,
        uploadedBytes = uploadedBytes,
        totalBytes = totalBytes,
        completedCount = completedCount,
        successCount = successCount,
        totalCount = totalCount,
        lastMessage = lastMessage
    )
}
