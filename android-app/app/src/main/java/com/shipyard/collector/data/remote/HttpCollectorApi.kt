package com.shipyard.collector.data.remote

import android.util.Base64
import android.util.Log
import com.shipyard.collector.BuildConfig
import com.shipyard.collector.model.CaptureRecord
import com.shipyard.collector.model.FormSummary
import com.shipyard.collector.model.LoginSession
import com.shipyard.collector.model.UploadMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.min

class HttpCollectorApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL
) : CollectorApi {

    override suspend fun login(phoneNumber: String, password: String): LoginResponse = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("phoneNumber", phoneNumber.trim())
            .put("password", password)

        val response = request(
            path = "/auth/login",
            method = "POST",
            body = body.toString()
        )

        val payload = JSONObject(response.body)
        LoginResponse(
            session = LoginSession(
                userId = payload.getString("userId"),
                phoneNumber = payload.getString("phoneNumber"),
                displayName = payload.getString("displayName"),
                authToken = payload.getString("token"),
                canUpload = payload.optBoolean("canUpload", true),
                canDeleteCache = payload.optBoolean("canDeleteCache", true),
                offlineExpiryEpochMillis = parseInstantToMillis(payload.getString("offlineExpiryAt"))
            ),
            forms = payload.optJSONArray("forms").toFormSummaries()
        )
    }

    override suspend fun uploadRecord(
        session: LoginSession,
        record: CaptureRecord,
        onProgress: suspend (UploadProgress) -> Unit
    ): UploadResponse = withContext(Dispatchers.IO) {
        val traceContext = RequestTraceContext.forRecord(record.recordId)
        val fileSpecs = buildFileSpecs(record)
        val totalBytes = fileSpecs.sumOf { it.totalBytes }
        val sessionState = initUpload(session, record, fileSpecs, traceContext)
        if (sessionState.status.equals("UPLOADED", ignoreCase = true) && sessionState.uploadedAtEpochMillis != null) {
            onProgress(
                UploadProgress(
                    currentFileName = null,
                    uploadedBytes = totalBytes,
                    totalBytes = totalBytes,
                    uploadedFileCount = fileSpecs.size,
                    totalFileCount = fileSpecs.size,
                    message = sessionState.message
                )
            )
            return@withContext UploadResponse(true, null, sessionState.message)
        }

        val uploadedBytesByFile = fileSpecs.associate { spec ->
            spec.fileId to (sessionState.files.firstOrNull { it.fileId == spec.fileId }?.uploadedBytes ?: 0L)
        }.toMutableMap()
        emitAggregateProgress(fileSpecs, uploadedBytesByFile, null, sessionState.message, onProgress)

        fileSpecs.forEach { spec ->
            val initialUploadedBytes = uploadedBytesByFile[spec.fileId] ?: 0L
            uploadFileChunks(
                session = session,
                recordId = record.recordId,
                fileSpec = spec,
                initialUploadedBytes = initialUploadedBytes,
                traceContext = traceContext.withFile(fileId = spec.fileId)
            ) { uploadedBytes ->
                uploadedBytesByFile[spec.fileId] = uploadedBytes
                emitAggregateProgress(
                    fileSpecs = fileSpecs,
                    uploadedBytesByFile = uploadedBytesByFile,
                    currentFileName = spec.fileName,
                    message = "正在上传 ${record.locationName}",
                    onProgress = onProgress
                )
            }
        }

        request(
            path = "/uploads/${record.recordId}/complete",
            method = "POST",
            bearerToken = session.authToken,
            traceContext = traceContext
        )

        val detail = fetchUploadDetail(session, record.recordId, traceContext)
        val success = detail.status.equals("UPLOADED", ignoreCase = true) && detail.uploadedAtEpochMillis != null
        val message = detail.message.ifBlank { if (success) "上传成功" else "上传失败" }
        onProgress(
            UploadProgress(
                currentFileName = null,
                uploadedBytes = if (success) totalBytes else 0,
                totalBytes = totalBytes,
                uploadedFileCount = if (success) fileSpecs.size else detail.files.count { it.completed },
                totalFileCount = fileSpecs.size,
                message = message
            )
        )
        UploadResponse(
            success = success,
            errorMessage = if (success) null else message,
            successMessage = if (success) message else null
        )
    }

    private fun initUpload(
        session: LoginSession,
        record: CaptureRecord,
        fileSpecs: List<UploadFileSpec>,
        traceContext: RequestTraceContext
    ): UploadSessionState {
        val body = JSONObject()
            .put("recordId", record.recordId)
            .put("formId", record.formId)
            .put("formName", record.formName)
            .put("locationName", record.locationName)
            .put("textNote", record.textNote)
            .put("deviceId", "android-device")
            .put("files", JSONArray(fileSpecs.map(UploadFileSpec::toInitJson)))

        val response = request(
            path = "/uploads/init",
            method = "POST",
            bearerToken = session.authToken,
            body = body.toString(),
            traceContext = traceContext
        )
        return parseUploadSession(JSONObject(response.body))
    }

    private suspend fun uploadFileChunks(
        session: LoginSession,
        recordId: String,
        fileSpec: UploadFileSpec,
        initialUploadedBytes: Long,
        traceContext: RequestTraceContext,
        onUploadedBytes: suspend (Long) -> Unit
    ) {
        if (initialUploadedBytes >= fileSpec.totalBytes) {
            onUploadedBytes(fileSpec.totalBytes)
            return
        }

        RandomAccessFile(fileSpec.file, "r").use { input ->
            var uploadedBytes = initialUploadedBytes
            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            while (uploadedBytes < fileSpec.totalBytes) {
                input.seek(uploadedBytes)
                val bytesToRead = min(buffer.size.toLong(), fileSpec.totalBytes - uploadedBytes).toInt()
                val bytesRead = input.read(buffer, 0, bytesToRead)
                if (bytesRead <= 0) {
                    throw IllegalStateException("读取待上传文件失败: ${fileSpec.file.name}")
                }
                val encoded = Base64.encodeToString(buffer.copyOf(bytesRead), Base64.NO_WRAP)
                val chunkBody = JSONObject()
                    .put("offset", uploadedBytes)
                    .put("base64Data", encoded)

                val response = request(
                    path = "/uploads/$recordId/files/${fileSpec.fileId}/chunks",
                    method = "POST",
                    bearerToken = session.authToken,
                    body = chunkBody.toString(),
                    traceContext = traceContext
                )
                val payload = JSONObject(response.body)
                val confirmedUploadedBytes = payload.getLong("uploadedBytes")
                if (confirmedUploadedBytes < uploadedBytes) {
                    throw IllegalStateException("服务器返回的上传偏移异常")
                }
                uploadedBytes = confirmedUploadedBytes
                onUploadedBytes(uploadedBytes)
            }
        }
    }

    private fun fetchUploadDetail(
        session: LoginSession,
        recordId: String,
        traceContext: RequestTraceContext
    ): UploadSessionState {
        val response = request(
            path = "/uploads/$recordId",
            method = "GET",
            bearerToken = session.authToken,
            traceContext = traceContext
        )
        return parseUploadSession(JSONObject(response.body))
    }

    private suspend fun emitAggregateProgress(
        fileSpecs: List<UploadFileSpec>,
        uploadedBytesByFile: Map<String, Long>,
        currentFileName: String?,
        message: String?,
        onProgress: suspend (UploadProgress) -> Unit
    ) {
        val uploadedBytes = uploadedBytesByFile.entries.sumOf { entry ->
            val total = fileSpecs.firstOrNull { it.fileId == entry.key }?.totalBytes ?: 0L
            entry.value.coerceAtMost(total)
        }
        val uploadedFileCount = fileSpecs.count { spec ->
            (uploadedBytesByFile[spec.fileId] ?: 0L) >= spec.totalBytes
        }
        onProgress(
            UploadProgress(
                currentFileName = currentFileName,
                uploadedBytes = uploadedBytes,
                totalBytes = fileSpecs.sumOf { it.totalBytes },
                uploadedFileCount = uploadedFileCount,
                totalFileCount = fileSpecs.size,
                message = message
            )
        )
    }

    private fun parseUploadSession(payload: JSONObject): UploadSessionState {
        val files = payload.optJSONArray("files").toFileStates()
        return UploadSessionState(
            status = payload.optString("status"),
            message = payload.optString("message"),
            uploadedAtEpochMillis = payload.optString("uploadedAt")
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let(::parseInstantToMillis),
            files = files
        )
    }

    private fun JSONArray?.toFormSummaries(): List<FormSummary> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                add(
                    FormSummary(
                        id = item.getString("formId"),
                        name = item.getString("formName"),
                        pendingCount = item.optInt("pendingCount", 0),
                        failedCount = item.optInt("failedCount", 0),
                        defaultUploadMode = UploadMode.valueOf(
                            item.optString("defaultUploadMode", UploadMode.COMPRESSED.name)
                        )
                    )
                )
            }
        }
    }

    private fun JSONArray?.toFileStates(): List<UploadFileState> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                add(
                    UploadFileState(
                        fileId = item.getString("fileId"),
                        uploadedBytes = item.optLong("uploadedBytes", 0L),
                        totalBytes = item.optLong("totalBytes", 0L),
                        completed = item.optBoolean("completed", false)
                    )
                )
            }
        }
    }

    private fun buildFileSpecs(record: CaptureRecord): List<UploadFileSpec> {
        val photoFiles = record.photoPaths.mapIndexed { index, path ->
            File(path).toUploadFileSpec(
                fileId = "photo-${index + 1}",
                role = "PHOTO",
                fallbackMimeType = "image/jpeg"
            )
        }
        val audioFiles = listOfNotNull(
            record.audioPath?.let { path ->
                File(path).toUploadFileSpec(
                    fileId = "audio-1",
                    role = "AUDIO",
                    fallbackMimeType = "audio/mp4"
                )
            }
        )
        return photoFiles + audioFiles
    }

    private fun File.toUploadFileSpec(
        fileId: String,
        role: String,
        fallbackMimeType: String
    ): UploadFileSpec {
        require(exists()) { "文件不存在: $name" }
        return UploadFileSpec(
            fileId = fileId,
            file = this,
            fileName = name,
            mimeType = guessMimeType(extension, fallbackMimeType),
            totalBytes = length(),
            role = role
        )
    }

    private fun request(
        path: String,
        method: String,
        bearerToken: String? = null,
        body: String? = null,
        traceContext: RequestTraceContext? = null
    ): RawResponse {
        val normalizedBase = baseUrl.trimEnd('/')
        val requestLabel = "$method $path"
        var connection: HttpURLConnection? = null

        try {
            connection = (URL("$normalizedBase$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (!bearerToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                }
                traceContext?.traceId?.let { setRequestProperty(TRACE_ID_HEADER, it) }
                traceContext?.recordId?.let { setRequestProperty(RECORD_ID_HEADER, it) }
                traceContext?.fileId?.let { setRequestProperty(FILE_ID_HEADER, it) }
                doInput = true
                if (body != null) {
                    doOutput = true
                    outputStream.use { output ->
                        output.write(body.toByteArray())
                    }
                }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream.readTextSafely()
            if (code !in 200..299) {
                val errorMessage = extractErrorMessage(responseBody, code)
                logApiFailure(
                    requestLabel = requestLabel,
                    statusCode = code,
                    message = errorMessage,
                    traceContext = traceContext,
                    throwable = null
                )
                throw IllegalStateException("接口 $requestLabel 失败：$errorMessage")
            }
            return RawResponse(code, responseBody)
        } catch (throwable: Exception) {
            if (throwable is IllegalStateException && throwable.message?.startsWith("接口 ") == true) {
                throw throwable
            }
            val message = throwable.message ?: throwable.javaClass.simpleName
            logApiFailure(
                requestLabel = requestLabel,
                statusCode = null,
                message = message,
                traceContext = traceContext,
                throwable = throwable
            )
            throw IllegalStateException("接口 $requestLabel 异常：$message", throwable)
        } finally {
            connection?.disconnect()
        }
    }

    private fun logApiFailure(
        requestLabel: String,
        statusCode: Int?,
        message: String,
        traceContext: RequestTraceContext?,
        throwable: Throwable?
    ) {
        val traceId = traceContext?.traceId ?: "-"
        val recordId = traceContext?.recordId ?: "-"
        val fileId = traceContext?.fileId ?: "-"
        val logMessage = "API failure request=$requestLabel status=${statusCode ?: "-"} traceId=$traceId recordId=$recordId fileId=$fileId message=$message"
        if (throwable == null) {
            Log.e(LOG_TAG, logMessage)
        } else {
            Log.e(LOG_TAG, logMessage, throwable)
        }
    }

    private fun guessMimeType(extension: String, fallback: String): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "mp3" -> "audio/mpeg"
        else -> fallback
    }

    private fun parseInstantToMillis(value: String): Long = java.time.Instant.parse(value).toEpochMilli()

    private fun extractErrorMessage(body: String, statusCode: Int): String {
        if (body.isBlank()) return "请求失败，状态码 $statusCode"
        return runCatching {
            val json = JSONObject(body)
            json.optString("message").ifBlank {
                json.optString("error").ifBlank { "请求失败，状态码 $statusCode" }
            }
        }.getOrDefault(body)
    }

    private fun InputStream?.readTextSafely(): String {
        if (this == null) return ""
        return BufferedReader(InputStreamReader(this)).use { reader ->
            reader.readText()
        }
    }

    private data class UploadFileSpec(
        val fileId: String,
        val file: File,
        val fileName: String,
        val mimeType: String,
        val totalBytes: Long,
        val role: String
    ) {
        fun toInitJson(): JSONObject = JSONObject()
            .put("fileId", fileId)
            .put("fileName", fileName)
            .put("mimeType", mimeType)
            .put("totalBytes", totalBytes)
            .put("role", role)
    }

    private data class UploadFileState(
        val fileId: String,
        val uploadedBytes: Long,
        val totalBytes: Long,
        val completed: Boolean
    )

    private data class UploadSessionState(
        val status: String,
        val message: String,
        val uploadedAtEpochMillis: Long?,
        val files: List<UploadFileState>
    )

    private data class RawResponse(
        val statusCode: Int,
        val body: String
    )

    private data class RequestTraceContext(
        val traceId: String,
        val recordId: String?,
        val fileId: String?
    ) {
        fun withFile(fileId: String): RequestTraceContext = copy(fileId = fileId)

        companion object {
            fun forRecord(recordId: String): RequestTraceContext = RequestTraceContext(
                traceId = "upload-$recordId-${UUID.randomUUID().toString().take(8)}",
                recordId = recordId,
                fileId = null
            )
        }
    }

    companion object {
        private const val LOG_TAG = "HttpCollectorApi"
        private const val CHUNK_SIZE_BYTES = 192 * 1024
        private const val TRACE_ID_HEADER = "X-Trace-Id"
        private const val RECORD_ID_HEADER = "X-Record-Id"
        private const val FILE_ID_HEADER = "X-File-Id"
    }
}
