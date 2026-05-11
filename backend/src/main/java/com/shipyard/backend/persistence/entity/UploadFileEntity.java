package com.shipyard.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "upload_files")
public class UploadFileEntity {

    @Id
    @Column(length = 128)
    private String fileId;

    @Column(nullable = false, length = 128)
    private String recordId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false, length = 256)
    private String fileName;

    @Column(nullable = false, length = 128)
    private String storedFileName;

    @Column(nullable = false, length = 128)
    private String mimeType;

    @Column(nullable = false)
    private long totalBytes;

    @Column(nullable = false)
    private long uploadedBytes;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UploadFileEntity() {}

    public UploadFileEntity(
        String fileId,
        String recordId,
        String userId,
        String role,
        String fileName,
        String storedFileName,
        String mimeType,
        long totalBytes,
        long uploadedBytes,
        boolean completed,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.fileId = fileId;
        this.recordId = recordId;
        this.userId = userId;
        this.role = role;
        this.fileName = fileName;
        this.storedFileName = storedFileName;
        this.mimeType = mimeType;
        this.totalBytes = totalBytes;
        this.uploadedBytes = uploadedBytes;
        this.completed = completed;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getFileId() { return fileId; }
    public String getRecordId() { return recordId; }
    public String getUserId() { return userId; }
    public String getRole() { return role; }
    public String getFileName() { return fileName; }
    public String getStoredFileName() { return storedFileName; }
    public String getMimeType() { return mimeType; }
    public long getTotalBytes() { return totalBytes; }
    public long getUploadedBytes() { return uploadedBytes; }
    public boolean isCompleted() { return completed; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRole(String role) { this.role = role; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public void setUploadedBytes(long uploadedBytes) { this.uploadedBytes = uploadedBytes; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
