package com.shipyard.backend.persistence.repository;

import com.shipyard.backend.persistence.entity.FormDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormDefinitionRepository extends JpaRepository<FormDefinitionEntity, String> {}
