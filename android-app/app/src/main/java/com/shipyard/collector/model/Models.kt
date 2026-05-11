package com.shipyard.collector.model

enum class RecordStatus {
    PENDING,
    UPLOADING,
    FAILED,
    UPLOADED
}

enum class NetworkType {
    OFFLINE,
    CELLULAR,
    WIFI
}

enum class UploadMode {
    COMPRESSED,
    ORIGINAL
}

enum class UploadBatchStatus {
    IDLE,
    RUNNING,
    PAUSED
}

data class UserProfile(
    val userId: String,
    val phoneNumber: String,
    val displayName: String,
    val canUpload: Boolean,
    val canDeleteCache: Boolean,
    val offlineExpiryEpochMillis: Long
)

data class FormSummary(
    val id: String,
    val name: String,
    val pendingCount: Int,
    val failedCount: Int,
    val defaultUploadMode: UploadMode
)

data class CaptureRecord(
    val recordId: String,
    val formId: String,
    val formName: String,
    val locationName: String,
    val photoPaths: List<String>,
    val audioPath: String?,
    val textNote: String,
    val status: RecordStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val failureReason: String? = null
)

data class LoginSession(
    val userId: String,
    val phoneNumber: String,
    val displayName: String,
    val authToken: String,
    val canUpload: Boolean,
    val canDeleteCache: Boolean,
    val offlineExpiryEpochMillis: Long
) {
    fun toUserProfile(): UserProfile = UserProfile(
        userId = userId,
        phoneNumber = phoneNumber,
        displayName = displayName,
        canUpload = canUpload,
        canDeleteCache = canDeleteCache,
        offlineExpiryEpochMillis = offlineExpiryEpochMillis
    )
}

data class UploadControllerState(
    val batchId: String? = null,
    val status: UploadBatchStatus = UploadBatchStatus.IDLE,
    val queuedCount: Int = 0,
    val uploadingCount: Int = 0,
    val failedCount: Int = 0,
    val currentLocationName: String? = null,
    val currentFileName: String? = null,
    val uploadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val completedCount: Int = 0,
    val successCount: Int = 0,
    val totalCount: Int = 0,
    val lastMessage: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes <= 0L) 0 else ((uploadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
}
