package com.shipyard.backend.auth;

import com.shipyard.backend.persistence.entity.AuthSessionEntity;
import com.shipyard.backend.persistence.entity.UserAccountEntity;

public record AuthContext(
    UserAccountEntity user,
    AuthSessionEntity session
) {}
