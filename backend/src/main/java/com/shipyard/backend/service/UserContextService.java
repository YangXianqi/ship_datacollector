package com.shipyard.backend.service;

import com.shipyard.backend.api.ApiDtos;
import com.shipyard.backend.auth.AuthContext;
import com.shipyard.backend.persistence.entity.FormDefinitionEntity;
import com.shipyard.backend.persistence.entity.UploadRecordStatus;
import com.shipyard.backend.persistence.repository.FormDefinitionRepository;
import com.shipyard.backend.persistence.repository.UploadRecordRepository;
import com.shipyard.backend.persistence.repository.UserFormPermissionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserContextService {

    private final UserFormPermissionRepository userFormPermissionRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final UploadRecordRepository uploadRecordRepository;

    public UserContextService(
        UserFormPermissionRepository userFormPermissionRepository,
        FormDefinitionRepository formDefinitionRepository,
        UploadRecordRepository uploadRecordRepository
    ) {
        this.userFormPermissionRepository = userFormPermissionRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.uploadRecordRepository = uploadRecordRepository;
    }

    public ApiDtos.UserContextResponse me(AuthContext authContext) {
        return new ApiDtos.UserContextResponse(
            authContext.user().getId(),
            authContext.user().getPhoneNumber(),
            authContext.user().getDisplayName(),
            authContext.user().isCanUpload(),
            authContext.user().isCanDeleteCache(),
            authContext.user().isAdmin()
        );
    }

    public List<ApiDtos.FormResponse> forms(AuthContext authContext) {
        Set<String> permittedFormIds = userFormPermissionRepository.findByUserId(authContext.user().getId())
            .stream()
            .map(permission -> permission.getFormId())
            .collect(Collectors.toSet());

        return formDefinitionRepository.findAllById(permittedFormIds)
            .stream()
            .sorted(Comparator.comparing(FormDefinitionEntity::getName))
            .map(form -> new ApiDtos.FormResponse(
                form.getId(),
                form.getName(),
                form.getDefaultUploadMode(),
                (int) uploadRecordRepository.countByUserIdAndFormIdAndStatus(
                    authContext.user().getId(),
                    form.getId(),
                    UploadRecordStatus.RECEIVED
                ),
                (int) uploadRecordRepository.countByUserIdAndFormIdAndStatus(
                    authContext.user().getId(),
                    form.getId(),
                    UploadRecordStatus.FAILED
                )
            ))
            .toList();
    }

    public ApiDtos.PolicyResponse policy() {
        return new ApiDtos.PolicyResponse(500, 30, true, true);
    }
}
