package com.shipyard.backend.service;

import com.shipyard.backend.api.ApiDtos;
import com.shipyard.backend.auth.AuthContext;
import com.shipyard.backend.persistence.entity.FormDefinitionEntity;
import com.shipyard.backend.persistence.entity.UploadFileEntity;
import com.shipyard.backend.persistence.entity.UploadRecordEntity;
import com.shipyard.backend.persistence.entity.UploadRecordStatus;
import com.shipyard.backend.persistence.repository.FormDefinitionRepository;
import com.shipyard.backend.persistence.repository.UploadFileRepository;
import com.shipyard.backend.persistence.repository.UploadRecordRepository;
import com.shipyard.backend.persistence.repository.UserFormPermissionRepository;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UploadService {

    private final UploadRecordRepository uploadRecordRepository;
    private final UploadFileRepository uploadFileRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final UserFormPermissionRepository userFormPermissionRepository;
    private final ChuangYunGateway chuangYunGateway;
    private final AttachmentStorageService attachmentStorageService;

    public UploadService(
        UploadRecordRepository uploadRecordRepository,
        UploadFileRepository uploadFileRepository,
        FormDefinitionRepository formDefinitionRepository,
        UserFormPermissionRepository userFormPermissionRepository,
        ChuangYunGateway chuangYunGateway,
        AttachmentStorageService attachmentStorageService
    ) {
        this.uploadRecordRepository = uploadRecordRepository;
        this.uploadFileRepository = uploadFileRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.userFormPermissionRepository = userFormPermissionRepository;
        this.chuangYunGateway = chuangYunGateway;
        this.attachmentStorageService = attachmentStorageService;
    }

    @Transactional
    public ApiDtos.UploadSessionResponse initUpload(AuthContext authContext, ApiDtos.UploadInitRequest request) {
        if (!authContext.user().isCanUpload()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有上传权限");
        }

        validateFormPermission(authContext, request.formId());
        validateFileDescriptors(request.files());

        UploadRecordEntity uploadRecord = uploadRecordRepository.findById(request.recordId()).orElse(null);
        if (uploadRecord == null) {
            Instant now = Instant.now();
            uploadRecord = new UploadRecordEntity(
                request.recordId(),
                authContext.user().getId(),
                request.formId(),
                request.formName(),
                request.locationName(),
                List.of(),
                null,
                request.textNote(),
                request.deviceId(),
                UploadRecordStatus.RECEIVED,
                "等待分片上传",
                0,
                now,
                now,
                null
            );
            uploadRecordRepository.save(uploadRecord);
            createOrUpdateFileStates(authContext, uploadRecord, request.files(), true);
        } else {
            if (!uploadRecord.getUserId().equals(authContext.user().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该记录已归属于其他账号");
            }
            reconcileUploadRecord(uploadRecord, request);
            if (uploadRecord.getStatus() == UploadRecordStatus.UPLOADED) {
                return toUploadSessionResponse(
                    uploadRecord,
                    uploadFileRepository.findByRecordIdOrderByCreatedAtAsc(request.recordId())
                );
            }
            createOrUpdateFileStates(authContext, uploadRecord, request.files(), false);
        }

        List<UploadFileEntity> fileStates = uploadFileRepository.findByRecordIdOrderByCreatedAtAsc(request.recordId());
        updateReceivingMessage(uploadRecord, fileStates);
        uploadRecordRepository.save(uploadRecord);
        return toUploadSessionResponse(uploadRecord, fileStates);
    }

    @Transactional
    public ApiDtos.UploadResponse upload(AuthContext authContext, ApiDtos.UploadRequest request) {
        if (!authContext.user().isCanUpload()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有上传权限");
        }

        validateFormPermission(authContext, request.formId());

        UploadRecordEntity existing = uploadRecordRepository.findById(request.recordId()).orElse(null);
        if (existing != null) {
            if (!existing.getUserId().equals(authContext.user().getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该记录已归属于其他账号");
            }
            if (existing.getStatus() == UploadRecordStatus.UPLOADED) {
                return toUploadResponse(existing);
            }
            return persistAndDispatch(existing);
        }

        Instant now = Instant.now();
        AttachmentStorageService.StoredAttachments storedAttachments = attachmentStorageService.store(
            request.recordId(),
            request.photoFiles(),
            request.audioFile()
        );
        UploadRecordEntity uploadRecord = new UploadRecordEntity(
            request.recordId(),
            authContext.user().getId(),
            request.formId(),
            request.formName(),
            request.locationName(),
            storedAttachments.photoFileNames(),
            storedAttachments.audioFileName(),
            request.textNote(),
            request.deviceId(),
            UploadRecordStatus.RECEIVED,
            "已接收，准备写入氚云",
            0,
            now,
            now,
            null
        );
        uploadRecordRepository.save(uploadRecord);
        return persistAndDispatch(uploadRecord);
    }

    @Transactional(readOnly = true)
    public ApiDtos.UploadDetailResponse getUpload(AuthContext authContext, String recordId) {
        UploadRecordEntity uploadRecord = requireOwnedRecord(authContext, recordId);
        List<UploadFileEntity> files = uploadFileRepository.findByRecordIdOrderByCreatedAtAsc(recordId);
        return new ApiDtos.UploadDetailResponse(
            uploadRecord.getRecordId(),
            uploadRecord.getFormId(),
            uploadRecord.getFormName(),
            uploadRecord.getLocationName(),
            uploadRecord.getStatus().name(),
            uploadRecord.getStatusMessage(),
            uploadRecord.getDeviceId(),
            uploadRecord.getAttemptCount(),
            uploadRecord.getCreatedAt(),
            uploadRecord.getUpdatedAt(),
            uploadRecord.getUploadedAt(),
            files.stream().allMatch(UploadFileEntity::isCompleted),
            files.stream().map(this::toFileStateResponse).toList()
        );
    }

    @Transactional
    public ApiDtos.UploadChunkResponse uploadChunk(
        AuthContext authContext,
        String recordId,
        String fileId,
        ApiDtos.UploadChunkRequest request
    ) {
        UploadRecordEntity uploadRecord = requireOwnedRecord(authContext, recordId);
        if (!authContext.user().isCanUpload()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有上传权限");
        }
        UploadFileEntity fileEntity = uploadFileRepository.findByRecordIdAndFileId(recordId, fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目标附件不存在"));

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(request.base64Data());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分片编码不合法", exception);
        }

        long uploadedBytes = attachmentStorageService.appendChunk(
            recordId,
            fileId,
            request.offset(),
            bytes,
            fileEntity.getTotalBytes()
        );
        fileEntity.setUploadedBytes(uploadedBytes);
        fileEntity.setCompleted(uploadedBytes >= fileEntity.getTotalBytes());
        fileEntity.setUpdatedAt(Instant.now());
        uploadFileRepository.save(fileEntity);

        List<UploadFileEntity> files = uploadFileRepository.findByRecordIdOrderByCreatedAtAsc(recordId);
        updateReceivingMessage(uploadRecord, files);
        uploadRecordRepository.save(uploadRecord);
        return new ApiDtos.UploadChunkResponse(
            recordId,
            fileId,
            fileEntity.getUploadedBytes(),
            fileEntity.getTotalBytes(),
            fileEntity.isCompleted(),
            fileEntity.getUpdatedAt()
        );
    }

    @Transactional
    public ApiDtos.UploadResponse completeUpload(AuthContext authContext, String recordId) {
        UploadRecordEntity uploadRecord = requireOwnedRecord(authContext, recordId);
        if (!authContext.user().isCanUpload()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有上传权限");
        }
        if (uploadRecord.getStatus() == UploadRecordStatus.UPLOADED) {
            return toUploadResponse(uploadRecord);
        }

        List<UploadFileEntity> fileStates = uploadFileRepository.findByRecordIdOrderByCreatedAtAsc(recordId);
        if (fileStates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前记录还没有任何已登记的附件");
        }
        if (fileStates.stream().anyMatch(file -> !file.isCompleted())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仍有附件未上传完成");
        }

        fileStates.forEach(file -> attachmentStorageService.finalizeChunkedFile(
            recordId,
            file.getFileId(),
            file.getStoredFileName()
        ));
        uploadRecord.setPhotoFileIds(fileStates.stream()
            .filter(file -> "PHOTO".equals(file.getRole()))
            .sorted(Comparator.comparing(UploadFileEntity::getFileId))
            .map(UploadFileEntity::getStoredFileName)
            .toList());
        uploadRecord.setAudioFileId(fileStates.stream()
            .filter(file -> "AUDIO".equals(file.getRole()))
            .map(UploadFileEntity::getStoredFileName)
            .findFirst()
            .orElse(null));
        uploadRecord.setStatusMessage("附件已接收，正在确认写入结果");
        return persistAndDispatch(uploadRecord);
    }

    @Transactional
    public ApiDtos.UploadResponse resume(AuthContext authContext, String recordId) {
        UploadRecordEntity uploadRecord = requireOwnedRecord(authContext, recordId);
        if (!authContext.user().isCanUpload()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有上传权限");
        }
        if (uploadRecord.getStatus() == UploadRecordStatus.CANCELLED) {
            uploadRecord.setStatus(UploadRecordStatus.RECEIVED);
            uploadRecord.setStatusMessage("重新进入上传流程");
        }
        List<UploadFileEntity> fileStates = uploadFileRepository.findByRecordIdOrderByCreatedAtAsc(recordId);
        if (fileStates.stream().anyMatch(file -> !file.isCompleted())) {
            return toUploadResponse(uploadRecord);
        }
        return persistAndDispatch(uploadRecord);
    }

    @Transactional
    public void cancel(AuthContext authContext, String recordId) {
        UploadRecordEntity uploadRecord = requireOwnedRecord(authContext, recordId);
        uploadRecord.setStatus(UploadRecordStatus.CANCELLED);
        uploadRecord.setStatusMessage("上传已取消，保留本地缓存");
        uploadRecord.setUpdatedAt(Instant.now());
        uploadRecordRepository.save(uploadRecord);
    }

    private void validateFormPermission(AuthContext authContext, String formId) {
        Set<String> permittedFormIds = userFormPermissionRepository.findByUserId(authContext.user().getId())
            .stream()
            .map(permission -> permission.getFormId())
            .collect(Collectors.toSet());
        if (!permittedFormIds.contains(formId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有该表单权限");
        }
        formDefinitionRepository.findById(formId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标表单不存在"));
    }

    private UploadRecordEntity requireOwnedRecord(AuthContext authContext, String recordId) {
        UploadRecordEntity uploadRecord = uploadRecordRepository.findById(recordId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "上传记录不存在"));
        if (!uploadRecord.getUserId().equals(authContext.user().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能访问其他账号的上传记录");
        }
        return uploadRecord;
    }

    private void validateFileDescriptors(List<ApiDtos.UploadFileDescriptor> files) {
        long photoCount = files.stream().filter(file -> "PHOTO".equals(file.role())).count();
        long audioCount = files.stream().filter(file -> "AUDIO".equals(file.role())).count();
        if (photoCount < 1 || photoCount > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单条记录需要 1 到 5 张图片");
        }
        if (audioCount > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单条记录最多 1 条语音");
        }
        boolean unknownRole = files.stream().anyMatch(file -> !Set.of("PHOTO", "AUDIO").contains(file.role()));
        if (unknownRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发现不支持的附件角色");
        }
    }

    private void reconcileUploadRecord(UploadRecordEntity uploadRecord, ApiDtos.UploadInitRequest request) {
        uploadRecord.setFormId(request.formId());
        uploadRecord.setFormName(request.formName());
        uploadRecord.setLocationName(request.locationName());
        uploadRecord.setTextNote(request.textNote());
        uploadRecord.setDeviceId(request.deviceId());
        uploadRecord.setUpdatedAt(Instant.now());
        if (uploadRecord.getStatus() == UploadRecordStatus.UPLOADED) {
            return;
        }
        Map<String, UploadFileEntity> existingFiles = uploadFileRepository.findByRecordIdOrderByCreatedAtAsc(request.recordId())
            .stream()
            .collect(Collectors.toMap(UploadFileEntity::getFileId, file -> file, (left, right) -> left, LinkedHashMap::new));
        boolean sameDefinition = existingFiles.size() == request.files().size()
            && request.files().stream().allMatch(file -> {
                UploadFileEntity existing = existingFiles.get(file.fileId());
                return existing != null
                    && existing.getFileName().equals(file.fileName())
                    && existing.getMimeType().equals(file.mimeType())
                    && existing.getTotalBytes() == file.totalBytes()
                    && existing.getRole().equals(file.role());
            });
        if (!sameDefinition) {
            attachmentStorageService.deleteRecordArtifacts(request.recordId());
            uploadFileRepository.deleteByRecordId(request.recordId());
            uploadRecord.setPhotoFileIds(List.of());
            uploadRecord.setAudioFileId(null);
            uploadRecord.setUploadedAt(null);
            uploadRecord.setStatus(UploadRecordStatus.RECEIVED);
            uploadRecord.setStatusMessage("检测到本地记录已修改，已重置服务器暂存附件");
            uploadRecordRepository.save(uploadRecord);
        }
    }

    private void createOrUpdateFileStates(
        AuthContext authContext,
        UploadRecordEntity uploadRecord,
        List<ApiDtos.UploadFileDescriptor> files,
        boolean creatingRecord
    ) {
        Instant now = Instant.now();
        Map<String, UploadFileEntity> existing = uploadFileRepository.findByRecordIdOrderByCreatedAtAsc(uploadRecord.getRecordId())
            .stream()
            .collect(Collectors.toMap(UploadFileEntity::getFileId, file -> file, (left, right) -> left, LinkedHashMap::new));

        List<UploadFileEntity> entities = files.stream().map(file -> {
            UploadFileEntity current = existing.get(file.fileId());
            if (current == null) {
                return new UploadFileEntity(
                    file.fileId(),
                    uploadRecord.getRecordId(),
                    authContext.user().getId(),
                    file.role(),
                    file.fileName(),
                    attachmentStorageService.buildStoredFileName(file.fileId(), file.fileName()),
                    file.mimeType(),
                    file.totalBytes(),
                    0,
                    false,
                    now,
                    now
                );
            }
            current.setRole(file.role());
            current.setFileName(file.fileName());
            current.setStoredFileName(attachmentStorageService.buildStoredFileName(file.fileId(), file.fileName()));
            current.setMimeType(file.mimeType());
            current.setTotalBytes(file.totalBytes());
            if (creatingRecord) {
                current.setUploadedBytes(0);
                current.setCompleted(false);
            }
            current.setUpdatedAt(now);
            return current;
        }).toList();
        uploadFileRepository.saveAll(entities);
    }

    private void updateReceivingMessage(UploadRecordEntity uploadRecord, List<UploadFileEntity> files) {
        long completedCount = files.stream().filter(UploadFileEntity::isCompleted).count();
        uploadRecord.setStatus(UploadRecordStatus.RECEIVED);
        uploadRecord.setStatusMessage("附件已接收 " + completedCount + "/" + files.size() + " 个");
        uploadRecord.setUpdatedAt(Instant.now());
    }

    private ApiDtos.UploadResponse persistAndDispatch(UploadRecordEntity uploadRecord) {
        uploadRecord.setAttemptCount(uploadRecord.getAttemptCount() + 1);
        uploadRecord.setUpdatedAt(Instant.now());

        ChuangYunGateway.GatewayResult gatewayResult = chuangYunGateway.write(uploadRecord);
        if (gatewayResult.success()) {
            uploadRecord.setStatus(UploadRecordStatus.UPLOADED);
            uploadRecord.setStatusMessage(gatewayResult.message());
            uploadRecord.setUploadedAt(Instant.now());
        } else {
            uploadRecord.setStatus(UploadRecordStatus.FAILED);
            uploadRecord.setStatusMessage(gatewayResult.message());
        }
        uploadRecordRepository.save(uploadRecord);
        return toUploadResponse(uploadRecord);
    }

    private ApiDtos.UploadResponse toUploadResponse(UploadRecordEntity uploadRecord) {
        return new ApiDtos.UploadResponse(
            uploadRecord.getRecordId(),
            uploadRecord.getStatus().name(),
            uploadRecord.getStatusMessage(),
            uploadRecord.getUpdatedAt(),
            uploadRecord.getAttemptCount()
        );
    }

    private ApiDtos.UploadSessionResponse toUploadSessionResponse(
        UploadRecordEntity uploadRecord,
        List<UploadFileEntity> files
    ) {
        return new ApiDtos.UploadSessionResponse(
            uploadRecord.getRecordId(),
            uploadRecord.getStatus().name(),
            uploadRecord.getStatusMessage(),
            uploadRecord.getUpdatedAt(),
            uploadRecord.getUploadedAt(),
            uploadRecord.getAttemptCount(),
            files.stream().allMatch(UploadFileEntity::isCompleted),
            files.stream().map(this::toFileStateResponse).toList()
        );
    }

    private ApiDtos.FileUploadStateResponse toFileStateResponse(UploadFileEntity file) {
        return new ApiDtos.FileUploadStateResponse(
            file.getFileId(),
            file.getFileName(),
            file.getMimeType(),
            file.getTotalBytes(),
            file.getUploadedBytes(),
            file.isCompleted(),
            file.getRole()
        );
    }
}
