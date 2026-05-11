package com.shipyard.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "upload_records")
public class UploadRecordEntity {

    @Id
    @Column(length = 128)
    private String recordId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String formId;

    @Column(nullable = false, length = 128)
    private String formName;

    @Column(nullable = false, length = 256)
    private String locationName;

    @Convert(converter = StringListConverter.class)
    @Column(nullable = false, length = 4096)
    private List<String> photoFileIds;

    @Column(length = 256)
    private String audioFileId;

    @Column(length = 1000)
    private String textNote;

    @Column(length = 128)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UploadRecordStatus status;

    @Column(length = 512)
    private String statusMessage;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant uploadedAt;

    protected UploadRecordEntity() {}

    public UploadRecordEntity(
        String recordId,
        String userId,
        String formId,
        String formName,
        String locationName,
        List<String> photoFileIds,
        String audioFileId,
        String textNote,
        String deviceId,
        UploadRecordStatus status,
        String statusMessage,
        int attemptCount,
        Instant createdAt,
        Instant updatedAt,
        Instant uploadedAt
    ) {
        this.recordId = recordId;
        this.userId = userId;
        this.formId = formId;
        this.formName = formName;
        this.locationName = locationName;
        this.photoFileIds = photoFileIds;
        this.audioFileId = audioFileId;
        this.textNote = textNote;
        this.deviceId = deviceId;
        this.status = status;
        this.statusMessage = statusMessage;
        this.attemptCount = attemptCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.uploadedAt = uploadedAt;
    }

    public String getRecordId() { return recordId; }
    public String getUserId() { return userId; }
    public String getFormId() { return formId; }
    public String getFormName() { return formName; }
    public String getLocationName() { return locationName; }
    public List<String> getPhotoFileIds() { return photoFileIds; }
    public String getAudioFileId() { return audioFileId; }
    public String getTextNote() { return textNote; }
    public String getDeviceId() { return deviceId; }
    public UploadRecordStatus getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getUploadedAt() { return uploadedAt; }

    public void setFormId(String formId) { this.formId = formId; }
    public void setFormName(String formName) { this.formName = formName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public void setPhotoFileIds(List<String> photoFileIds) { this.photoFileIds = photoFileIds; }
    public void setAudioFileId(String audioFileId) { this.audioFileId = audioFileId; }
    public void setTextNote(String textNote) { this.textNote = textNote; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setStatus(UploadRecordStatus status) { this.status = status; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
