package com.shipyard.backend.service;

import com.shipyard.backend.api.ApiDtos;
import com.shipyard.backend.auth.AuthContext;
import com.shipyard.backend.persistence.entity.AuthSessionEntity;
import com.shipyard.backend.persistence.entity.UserAccountEntity;
import com.shipyard.backend.persistence.repository.AuthSessionRepository;
import com.shipyard.backend.persistence.repository.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordService passwordService;
    private final UserContextService userContextService;

    public AuthService(
        UserAccountRepository userAccountRepository,
        AuthSessionRepository authSessionRepository,
        PasswordService passwordService,
        UserContextService userContextService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordService = passwordService;
        this.userContextService = userContextService;
    }

    @Transactional
    public ApiDtos.LoginResponse login(String phoneNumber, String password) {
        UserAccountEntity user = userAccountRepository.findByPhoneNumber(phoneNumber)
            .filter(UserAccountEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误"));

        if (!passwordService.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }

        Instant now = Instant.now();
        Instant expiry = now.plus(30, ChronoUnit.DAYS);
        String token = UUID.randomUUID().toString();
        authSessionRepository.save(new AuthSessionEntity(token, user.getId(), now, expiry, now));

        return new ApiDtos.LoginResponse(
            user.getId(),
            user.getPhoneNumber(),
            user.getDisplayName(),
            token,
            expiry,
            user.isCanUpload(),
            user.isCanDeleteCache(),
            user.isAdmin(),
            userContextService.forms(new AuthContext(user, null))
        );
    }

    @Transactional
    public AuthContext resolve(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少有效登录凭证");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        AuthSessionEntity session = authSessionRepository.findById(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录态不存在"));

        if (session.getExpiresAt().isBefore(Instant.now())) {
            authSessionRepository.delete(session);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录态已过期");
        }

        UserAccountEntity user = userAccountRepository.findById(session.getUserId())
            .filter(UserAccountEntity::isEnabled)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不可用"));

        session.setLastSeenAt(Instant.now());
        authSessionRepository.save(session);
        return new AuthContext(user, session);
    }
}
