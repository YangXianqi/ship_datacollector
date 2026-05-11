package com.shipyard.backend.persistence.repository;

import com.shipyard.backend.persistence.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {}
