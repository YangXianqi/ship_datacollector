package com.shipyard.backend.persistence.repository;

import com.shipyard.backend.persistence.entity.UserAccountEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, String> {
    Optional<UserAccountEntity> findByPhoneNumber(String phoneNumber);
}
