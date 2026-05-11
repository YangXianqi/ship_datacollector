package com.shipyard.backend.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;

public final class ApiDtos {

    private ApiDtos() {}

    public record LoginRequest(
        @NotBlank String phoneNumber,
        @NotBlank String password
    ) {}

    public record LoginResponse(
        String userId,
        String phoneNumber,
        String displayName,
        String token,
        Instant offlineExpiryAt,
        boolean canUpload,
        boolean canDeleteCache,
        boolean admin,
        List<FormResponse> forms
    ) {}

    public record UserContextResponse(
        String userId,
        String phoneNumber,
        String displayName,
        boolean canUpload,
        boolean canDeleteCache,
        boolean admin
    ) {}

    public record FormResponse(
        String formId,
        String formName,
        String defaultUploadMode,
        int pendingCount,
        int failedCount
    ) {}

    public record PolicyResponse(
        int localRecordLimit,
        int offlineDays,
        boolean backgroundUploadEnabled,
        boolean manualUploadRequired
    ) {}

    public record UploadRequest(
        @NotBlank String recordId,
        @NotBlank String formId,
        @NotBlank String formName,
        @NotBlank String locationName,
        @NotEmpty List<AttachmentPayload> photoFiles,
        AttachmentPayload audioFile,
        String textNote,
        String deviceId
    ) {}

    public record UploadInitRequest(
        @NotBlank String recordId,
        @NotBlank String formId,
        @NotBlank String formName,
        @NotBlank String locationName,
        @NotEmpty List<UploadFileDescriptor> files,
        String textNote,
        String deviceId
    ) {}

    public record UploadFileDescriptor(
        @NotBlank String fileId,
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @Positive long totalBytes,
        @NotBlank String role
    ) {}

    public record AttachmentPayload(
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @NotBlank String base64Data
    ) {}

    public record UploadChunkRequest(
        long offset,
        @NotBlank String base64Data
    ) {}

    public record UploadResponse(
        String recordId,
        String status,
        String message,
        Instant updatedAt,
        int attemptCount
    ) {}

    public record UploadSessionResponse(
        String recordId,
        String status,
        String message,
        Instant updatedAt,
        Instant uploadedAt,
        int attemptCount,
        boolean allFilesUploaded,
        List<FileUploadStateResponse> files
    ) {}

    public record FileUploadStateResponse(
        String fileId,
        String fileName,
        String mimeType,
        long totalBytes,
        long uploadedBytes,
        boolean completed,
        String role
    ) {}

    public record UploadChunkResponse(
        String recordId,
        String fileId,
        long uploadedBytes,
        long totalBytes,
        boolean completed,
        Instant updatedAt
    ) {}

    public record UploadDetailResponse(
        String recordId,
        String formId,
        String formName,
        String locationName,
        String status,
        String message,
        String deviceId,
        int attemptCount,
        Instant createdAt,
        Instant updatedAt,
        Instant uploadedAt,
        boolean allFilesUploaded,
        List<FileUploadStateResponse> files
    ) {}

    public record AdminUserResponse(
        String userId,
        String phoneNumber,
        String displayName,
        boolean enabled,
        boolean admin,
        boolean canUpload,
        boolean canDeleteCache,
        List<String> formIds
    ) {}

    public record CreateUserRequest(
        @NotBlank String phoneNumber,
        @NotBlank String displayName,
        @NotBlank String password,
        boolean enabled,
        boolean admin,
        boolean canUpload,
        boolean canDeleteCache,
        @NotEmpty List<String> formIds
    ) {}

    public record ResetPasswordRequest(
        @NotBlank String password
    ) {}

    public record UpdateUserStatusRequest(
        boolean enabled
    ) {}

    public record UpdateUserPermissionsRequest(
        boolean admin,
        boolean canUpload,
        boolean canDeleteCache,
        @NotEmpty List<String> formIds
    ) {}
}
