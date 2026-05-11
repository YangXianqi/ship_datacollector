package com.shipyard.backend.api;

import com.shipyard.backend.auth.AuthContext;
import com.shipyard.backend.auth.AuthInterceptor;
import com.shipyard.backend.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminService adminService;

    public AdminApiController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public List<ApiDtos.AdminUserResponse> users(HttpServletRequest request) {
        return adminService.listUsers(requireAdmin(request));
    }

    @PostMapping("/users")
    public ApiDtos.AdminUserResponse createUser(
        HttpServletRequest request,
        @Valid @RequestBody ApiDtos.CreateUserRequest createUserRequest
    ) {
        return adminService.createUser(requireAdmin(request), createUserRequest);
    }

    @PostMapping("/users/{userId}/reset-password")
    public ApiDtos.AdminUserResponse resetPassword(
        HttpServletRequest request,
        @PathVariable String userId,
        @Valid @RequestBody ApiDtos.ResetPasswordRequest resetPasswordRequest
    ) {
        return adminService.resetPassword(requireAdmin(request), userId, resetPasswordRequest);
    }

    @PostMapping("/users/{userId}/status")
    public ApiDtos.AdminUserResponse updateStatus(
        HttpServletRequest request,
        @PathVariable String userId,
        @Valid @RequestBody ApiDtos.UpdateUserStatusRequest statusRequest
    ) {
        return adminService.updateStatus(requireAdmin(request), userId, statusRequest);
    }

    @PostMapping("/users/{userId}/permissions")
    public ApiDtos.AdminUserResponse updatePermissions(
        HttpServletRequest request,
        @PathVariable String userId,
        @Valid @RequestBody ApiDtos.UpdateUserPermissionsRequest permissionsRequest
    ) {
        return adminService.updatePermissions(requireAdmin(request), userId, permissionsRequest);
    }

    @GetMapping("/forms")
    public List<ApiDtos.FormResponse> forms(HttpServletRequest request) {
        return adminService.listForms(requireAdmin(request));
    }

    private AuthContext requireAdmin(HttpServletRequest request) {
        return (AuthContext) request.getAttribute(AuthInterceptor.AUTH_CONTEXT_ATTR);
    }
}
