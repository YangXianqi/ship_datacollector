package com.shipyard.backend.service;

import com.shipyard.backend.api.ApiDtos;
import com.shipyard.backend.auth.AuthContext;
import com.shipyard.backend.persistence.entity.FormDefinitionEntity;
import com.shipyard.backend.persistence.entity.UserAccountEntity;
import com.shipyard.backend.persistence.entity.UserFormPermissionEntity;
import com.shipyard.backend.persistence.repository.FormDefinitionRepository;
import com.shipyard.backend.persistence.repository.UploadRecordRepository;
import com.shipyard.backend.persistence.repository.UserAccountRepository;
import com.shipyard.backend.persistence.repository.UserFormPermissionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final UserFormPermissionRepository userFormPermissionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final PasswordService passwordService;
    private final UserContextService userContextService;

    public AdminService(
        UserAccountRepository userAccountRepository,
        UserFormPermissionRepository userFormPermissionRepository,
        FormDefinitionRepository formDefinitionRepository,
        PasswordService passwordService,
        UserContextService userContextService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userFormPermissionRepository = userFormPermissionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.passwordService = passwordService;
        this.userContextService = userContextService;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.AdminUserResponse> listUsers(AuthContext adminContext) {
        ensureAdmin(adminContext);
        return userAccountRepository.findAll().stream()
            .sorted(Comparator.comparing(UserAccountEntity::getPhoneNumber))
            .map(this::toAdminUserResponse)
            .toList();
    }

    @Transactional
    public ApiDtos.AdminUserResponse createUser(AuthContext adminContext, ApiDtos.CreateUserRequest request) {
        ensureAdmin(adminContext);
        userAccountRepository.findByPhoneNumber(request.phoneNumber()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "手机号已存在");
        });
        validateFormsExist(request.formIds());

        UserAccountEntity user = new UserAccountEntity(
            UUID.randomUUID().toString(),
            request.phoneNumber(),
            request.displayName(),
            passwordService.hash(request.password()),
            request.enabled(),
            request.admin(),
            request.canUpload(),
            request.canDeleteCache()
        );
        userAccountRepository.save(user);
        replacePermissions(user, request.formIds());
        return toAdminUserResponse(user);
    }

    @Transactional
    public ApiDtos.AdminUserResponse resetPassword(
        AuthContext adminContext,
        String userId,
        ApiDtos.ResetPasswordRequest request
    ) {
        ensureAdmin(adminContext);
        UserAccountEntity user = requireUser(userId);
        user.setPasswordHash(passwordService.hash(request.password()));
        userAccountRepository.save(user);
        return toAdminUserResponse(user);
    }

    @Transactional
    public ApiDtos.AdminUserResponse updateStatus(
        AuthContext adminContext,
        String userId,
        ApiDtos.UpdateUserStatusRequest request
    ) {
        ensureAdmin(adminContext);
        UserAccountEntity user = requireUser(userId);
        user.setEnabled(request.enabled());
        userAccountRepository.save(user);
        return toAdminUserResponse(user);
    }

    @Transactional
    public ApiDtos.AdminUserResponse updatePermissions(
        AuthContext adminContext,
        String userId,
        ApiDtos.UpdateUserPermissionsRequest request
    ) {
        ensureAdmin(adminContext);
        validateFormsExist(request.formIds());

        UserAccountEntity user = requireUser(userId);
        user.setAdmin(request.admin());
        user.setCanUpload(request.canUpload());
        user.setCanDeleteCache(request.canDeleteCache());
        userAccountRepository.save(user);
        replacePermissions(user, request.formIds());
        return toAdminUserResponse(user);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.FormResponse> listForms(AuthContext adminContext) {
        ensureAdmin(adminContext);
        return formDefinitionRepository.findAll().stream()
            .sorted(Comparator.comparing(FormDefinitionEntity::getName))
            .map(form -> new ApiDtos.FormResponse(
                form.getId(),
                form.getName(),
                form.getDefaultUploadMode(),
                0,
                0
            ))
            .toList();
    }

    @Transactional
    public void replacePermissions(UserAccountEntity user, List<String> formIds) {
        userFormPermissionRepository.deleteByUserId(user.getId());
        List<UserFormPermissionEntity> permissions = formIds.stream()
            .distinct()
            .map(formId -> new UserFormPermissionEntity(UUID.randomUUID().toString(), user.getId(), formId))
            .toList();
        userFormPermissionRepository.saveAll(permissions);
    }

    private void ensureAdmin(AuthContext authContext) {
        if (authContext == null || !authContext.user().isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
    }

    private void validateFormsExist(List<String> formIds) {
        List<String> distinctIds = formIds.stream().distinct().toList();
        if (formDefinitionRepository.findAllById(distinctIds).size() != distinctIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "存在无效表单");
        }
    }

    private UserAccountEntity requireUser(String userId) {
        return userAccountRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"));
    }

    private ApiDtos.AdminUserResponse toAdminUserResponse(UserAccountEntity user) {
        List<String> formIds = userFormPermissionRepository.findByUserId(user.getId())
            .stream()
            .map(UserFormPermissionEntity::getFormId)
            .sorted()
            .collect(Collectors.toList());
        return new ApiDtos.AdminUserResponse(
            user.getId(),
            user.getPhoneNumber(),
            user.getDisplayName(),
            user.isEnabled(),
            user.isAdmin(),
            user.isCanUpload(),
            user.isCanDeleteCache(),
            formIds
        );
    }
}
