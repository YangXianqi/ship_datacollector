package com.shipyard.backend.persistence.repository;

import com.shipyard.backend.persistence.entity.UploadFileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadFileRepository extends JpaRepository<UploadFileEntity, String> {
    List<UploadFileEntity> findByRecordIdOrderByCreatedAtAsc(String recordId);

    Optional<UploadFileEntity> findByRecordIdAndFileId(String recordId, String fileId);

    void deleteByRecordId(String recordId);
}
