package com.shipyard.backend.api;

import com.shipyard.backend.auth.AuthContext;
import com.shipyard.backend.auth.AuthInterceptor;
import com.shipyard.backend.service.AuthService;
import com.shipyard.backend.service.UploadService;
import com.shipyard.backend.service.UserContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class ShipyardApiController {

    private final AuthService authService;
    private final UserContextService userContextService;
    private final UploadService uploadService;

    public ShipyardApiController(
        AuthService authService,
        UserContextService userContextService,
        UploadService uploadService
    ) {
        this.authService = authService;
        this.userContextService = userContextService;
        this.uploadService = uploadService;
    }

    @PostMapping("/auth/login")
    public ApiDtos.LoginResponse login(@Valid @RequestBody ApiDtos.LoginRequest request) {
        return authService.login(request.phoneNumber(), request.password());
    }

    @GetMapping("/me")
    public ApiDtos.UserContextResponse me(HttpServletRequest request) {
        return userContextService.me(currentUser(request));
    }

    @GetMapping("/me/forms")
    public List<ApiDtos.FormResponse> forms(HttpServletRequest request) {
        return userContextService.forms(currentUser(request));
    }

    @GetMapping("/me/policy")
    public ApiDtos.PolicyResponse policy() {
        return userContextService.policy();
    }

    @PostMapping("/uploads")
    public ApiDtos.UploadResponse upload(
        HttpServletRequest request,
        @Valid @RequestBody ApiDtos.UploadRequest uploadRequest
    ) {
        return uploadService.upload(currentUser(request), uploadRequest);
    }

    @PostMapping("/uploads/init")
    public ApiDtos.UploadSessionResponse initUpload(
        HttpServletRequest request,
        @Valid @RequestBody ApiDtos.UploadInitRequest uploadRequest
    ) {
        return uploadService.initUpload(currentUser(request), uploadRequest);
    }

    @PostMapping("/uploads/{recordId}/files/{fileId}/chunks")
    public ApiDtos.UploadChunkResponse uploadChunk(
        HttpServletRequest request,
        @PathVariable String recordId,
        @PathVariable String fileId,
        @Valid @RequestBody ApiDtos.UploadChunkRequest chunkRequest
    ) {
        return uploadService.uploadChunk(currentUser(request), recordId, fileId, chunkRequest);
    }

    @PostMapping("/uploads/{recordId}/complete")
    public ApiDtos.UploadResponse completeUpload(
        HttpServletRequest request,
        @PathVariable String recordId
    ) {
        return uploadService.completeUpload(currentUser(request), recordId);
    }

    @GetMapping("/uploads/{recordId}")
    public ApiDtos.UploadDetailResponse getUpload(
        HttpServletRequest request,
        @PathVariable String recordId
    ) {
        return uploadService.getUpload(currentUser(request), recordId);
    }

    @PostMapping("/uploads/{recordId}/resume")
    public ApiDtos.UploadResponse resume(
        HttpServletRequest request,
        @PathVariable String recordId
    ) {
        return uploadService.resume(currentUser(request), recordId);
    }

    @PostMapping("/uploads/{recordId}/cancel")
    public Map<String, String> cancel(
        HttpServletRequest request,
        @PathVariable String recordId
    ) {
        uploadService.cancel(currentUser(request), recordId);
        return Map.of("recordId", recordId, "status", "cancelled");
    }

    private AuthContext currentUser(HttpServletRequest request) {
        return (AuthContext) request.getAttribute(AuthInterceptor.AUTH_CONTEXT_ATTR);
    }
}
