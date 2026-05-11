package com.shipyard.backend.auth;

import com.shipyard.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTH_CONTEXT_ATTR = "shipyardAuthContext";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path.equals("/api/auth/login") || path.startsWith("/actuator")) {
            return true;
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        AuthContext authContext = authService.resolve(authHeader);
        if (path.startsWith("/api/admin") && !authContext.user().isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }

        request.setAttribute(AUTH_CONTEXT_ATTR, authContext);
        return true;
    }
}
