package com.shipyard.collector.data.remote

import com.shipyard.collector.model.CaptureRecord
import com.shipyard.collector.model.FormSummary
import com.shipyard.collector.model.LoginSession
import com.shipyard.collector.model.UploadMode
import kotlinx.coroutines.delay
import java.util.UUID

class MockCollectorApi : CollectorApi {
    override suspend fun login(phoneNumber: String, password: String): LoginResponse {
        delay(500)
        require(phoneNumber.length == 11) { "请输入 11 位手机号" }
        require(password.length >= 6) { "密码至少 6 位" }

        val now = System.currentTimeMillis()
        return LoginResponse(
            session = LoginSession(
                userId = UUID.nameUUIDFromBytes(phoneNumber.toByteArray()).toString(),
                phoneNumber = phoneNumber,
                displayName = "工人${phoneNumber.takeLast(4)}",
                authToken = "mock-token-$phoneNumber",
                canUpload = true,
                canDeleteCache = true,
                offlineExpiryEpochMillis = now + 30L * 24 * 60 * 60 * 1000
            ),
            forms = listOf(
                FormSummary("hull", "Hull Inspection", 0, 0, UploadMode.COMPRESSED),
                FormSummary("engine", "Engine Compartment", 0, 0, UploadMode.ORIGINAL)
            )
        )
    }

    override suspend fun uploadRecord(
        session: LoginSession,
        record: CaptureRecord,
        onProgress: suspend (UploadProgress) -> Unit
    ): UploadResponse {
        delay(900)
        onProgress(
            UploadProgress(
                currentFileName = record.photoPaths.firstOrNull()?.substringAfterLast('/'),
                uploadedBytes = 1,
                totalBytes = 1,
                uploadedFileCount = record.photoPaths.size + if (record.audioPath == null) 0 else 1,
                totalFileCount = record.photoPaths.size + if (record.audioPath == null) 0 else 1,
                message = "模拟上传中"
            )
        )
        return when {
            session.authToken.isBlank() -> UploadResponse(false, "登录态已失效，请重新登录")
            record.locationName.contains("fail", ignoreCase = true) -> {
                UploadResponse(false, "模拟上传失败，请在网络稳定后重试")
            }

            else -> UploadResponse(true, null, "模拟上传成功")
        }
    }
}
