package com.shipyard.backend.config;

import com.shipyard.backend.persistence.entity.FormDefinitionEntity;
import com.shipyard.backend.persistence.entity.UserAccountEntity;
import com.shipyard.backend.persistence.repository.FormDefinitionRepository;
import com.shipyard.backend.persistence.repository.UserAccountRepository;
import com.shipyard.backend.service.AdminService;
import com.shipyard.backend.service.PasswordService;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedDataConfig {

    @Bean
    CommandLineRunner seedDemoData(
        FormDefinitionRepository formDefinitionRepository,
        UserAccountRepository userAccountRepository,
        AdminService adminService,
        PasswordService passwordService
    ) {
        return args -> {
            if (formDefinitionRepository.count() == 0) {
                formDefinitionRepository.saveAll(List.of(
                    new FormDefinitionEntity("hull", "Hull Inspection", "COMPRESSED"),
                    new FormDefinitionEntity("engine", "Engine Compartment", "ORIGINAL")
                ));
            }

            if (userAccountRepository.count() == 0) {
                UserAccountEntity admin = new UserAccountEntity(
                    UUID.randomUUID().toString(),
                    "13900000000",
                    "System Admin",
                    passwordService.hash("admin123"),
                    true,
                    true,
                    true,
                    true
                );
                UserAccountEntity worker = new UserAccountEntity(
                    UUID.randomUUID().toString(),
                    "13800000000",
                    "Worker Demo",
                    passwordService.hash("worker123"),
                    true,
                    false,
                    true,
                    true
                );
                userAccountRepository.saveAll(List.of(admin, worker));
                adminService.replacePermissions(admin, List.of("hull", "engine"));
                adminService.replacePermissions(worker, List.of("hull", "engine"));
            }
        };
    }
}
