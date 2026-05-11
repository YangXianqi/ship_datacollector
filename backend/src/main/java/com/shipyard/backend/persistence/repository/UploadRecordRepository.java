package com.shipyard.backend.persistence.repository;

import com.shipyard.backend.persistence.entity.UploadRecordEntity;
import com.shipyard.backend.persistence.entity.UploadRecordStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRecordRepository extends JpaRepository<UploadRecordEntity, String> {
    long countByUserIdAndFormIdAndStatus(String userId, String formId, UploadRecordStatus status);
    List<UploadRecordEntity> findByUserIdAndFormIdIn(String userId, Collection<String> formIds);
}
