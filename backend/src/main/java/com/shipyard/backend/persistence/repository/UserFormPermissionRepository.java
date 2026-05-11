package com.shipyard.backend.persistence.repository;

import com.shipyard.backend.persistence.entity.UserFormPermissionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFormPermissionRepository extends JpaRepository<UserFormPermissionEntity, String> {
    List<UserFormPermissionEntity> findByUserId(String userId);
    void deleteByUserId(String userId);
}
