package com.shipyard.collector.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "capture_records")
data class CaptureRecordEntity(
    @PrimaryKey val recordId: String,
    val formId: String,
    val formName: String,
    val locationName: String,
    val photoPaths: List<String>,
    val audioPath: String?,
    val textNote: String,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val failureReason: String?
)

@Entity(tableName = "forms")
data class FormEntity(
    @PrimaryKey val formId: String,
    val formName: String,
    val defaultUploadMode: String
)

@Entity(tableName = "upload_queue_entries")
data class UploadQueueEntryEntity(
    @PrimaryKey val recordId: String,
    val batchId: String,
    val status: String,
    val attemptCount: Int,
    val enqueuedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastError: String?
)

@Entity(tableName = "upload_queue_state")
data class UploadQueueStateEntity(
    @PrimaryKey val id: Int = 0,
    val batchId: String?,
    val status: String,
    val updatedAtEpochMillis: Long,
    val currentLocationName: String?,
    val currentFileName: String?,
    val uploadedBytes: Long,
    val totalBytes: Long,
    val completedCount: Int,
    val successCount: Int,
    val totalCount: Int,
    val lastMessage: String?
)

class ListConverters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split("||")
    }

    @TypeConverter
    fun toString(value: List<String>): String = value.joinToString("||")
}

@Dao
interface CaptureRecordDao {
    @Query("SELECT * FROM capture_records ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<CaptureRecordEntity>>

    @Query("SELECT * FROM capture_records WHERE recordId = :recordId LIMIT 1")
    suspend fun getById(recordId: String): CaptureRecordEntity?

    @Query("SELECT * FROM capture_records WHERE recordId IN (:recordIds)")
    suspend fun getByIds(recordIds: List<String>): List<CaptureRecordEntity>

    @Query("SELECT COUNT(*) FROM capture_records")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM capture_records WHERE status IN ('PENDING', 'FAILED', 'UPLOADING')")
    suspend fun countUnresolved(): Int

    @Upsert
    suspend fun upsert(record: CaptureRecordEntity)

    @Query(
        """
        UPDATE capture_records
        SET status = :status,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            failureReason = :failureReason
        WHERE recordId = :recordId
        """
    )
    suspend fun updateStatus(
        recordId: String,
        status: String,
        updatedAtEpochMillis: Long,
        failureReason: String?
    )

    @Query(
        """
        UPDATE capture_records
        SET status = :status,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            failureReason = NULL
        WHERE recordId IN (:recordIds)
        """
    )
    suspend fun updateStatuses(
        recordIds: List<String>,
        status: String,
        updatedAtEpochMillis: Long
    )

    @Query("DELETE FROM capture_records WHERE recordId IN (:recordIds)")
    suspend fun deleteByIds(recordIds: List<String>)
}

@Dao
interface FormDao {
    @Query("SELECT * FROM forms ORDER BY formName ASC")
    fun observeAll(): Flow<List<FormEntity>>

    @Query("SELECT * FROM forms WHERE formId = :formId LIMIT 1")
    suspend fun getById(formId: String): FormEntity?

    @Upsert
    suspend fun upsertAll(forms: List<FormEntity>)

    @Query("DELETE FROM forms")
    suspend fun clearAll()
}

@Dao
interface UploadQueueDao {
    @Query("SELECT * FROM upload_queue_state WHERE id = 0")
    fun observeState(): Flow<UploadQueueStateEntity?>

    @Query("SELECT * FROM upload_queue_state WHERE id = 0")
    suspend fun getState(): UploadQueueStateEntity?

    @Upsert
    suspend fun upsertState(state: UploadQueueStateEntity)

    @Query("SELECT * FROM upload_queue_entries WHERE batchId = :batchId ORDER BY enqueuedAtEpochMillis ASC")
    fun observeEntriesForBatch(batchId: String): Flow<List<UploadQueueEntryEntity>>

    @Query(
        """
        SELECT * FROM upload_queue_entries
        WHERE batchId = :batchId AND status = 'QUEUED'
        ORDER BY enqueuedAtEpochMillis ASC
        LIMIT 1
        """
    )
    suspend fun getNextQueuedEntry(batchId: String): UploadQueueEntryEntity?

    @Query(
        """
        SELECT * FROM upload_queue_entries
        WHERE batchId = :batchId AND status = 'IN_PROGRESS'
        ORDER BY updatedAtEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun getInProgressEntry(batchId: String): UploadQueueEntryEntity?

    @Query(
        """
        SELECT COUNT(*) FROM upload_queue_entries
        WHERE batchId = :batchId AND status IN ('QUEUED', 'IN_PROGRESS')
        """
    )
    suspend fun countOutstandingEntries(batchId: String): Int

    @Upsert
    suspend fun upsertEntries(entries: List<UploadQueueEntryEntity>)

    @Query(
        """
        UPDATE upload_queue_entries
        SET status = :status,
            attemptCount = :attemptCount,
            updatedAtEpochMillis = :updatedAtEpochMillis,
            lastError = :lastError
        WHERE recordId = :recordId
        """
    )
    suspend fun updateEntry(
        recordId: String,
        status: String,
        attemptCount: Int,
        updatedAtEpochMillis: Long,
        lastError: String?
    )

    @Query("DELETE FROM upload_queue_entries WHERE batchId = :batchId")
    suspend fun deleteBatch(batchId: String)
}

@Database(
    entities = [
        CaptureRecordEntity::class,
        FormEntity::class,
        UploadQueueEntryEntity::class,
        UploadQueueStateEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(ListConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun captureRecordDao(): CaptureRecordDao
    abstract fun formDao(): FormDao
    abstract fun uploadQueueDao(): UploadQueueDao
}
