package com.shipyard.collector.data.remote

import com.shipyard.collector.model.CaptureRecord
import com.shipyard.collector.model.FormSummary
import com.shipyard.collector.model.LoginSession

interface CollectorApi {
    suspend fun login(phoneNumber: String, password: String): LoginResponse

    suspend fun uploadRecord(
        session: LoginSession,
        record: CaptureRecord,
        onProgress: suspend (UploadProgress) -> Unit = {}
    ): UploadResponse
}

data class LoginResponse(
    val session: LoginSession,
    val forms: List<FormSummary>
)

data class UploadResponse(
    val success: Boolean,
    val errorMessage: String?,
    val successMessage: String? = null
)

data class UploadProgress(
    val currentFileName: String?,
    val uploadedBytes: Long,
    val totalBytes: Long,
    val uploadedFileCount: Int,
    val totalFileCount: Int,
    val message: String? = null
)
