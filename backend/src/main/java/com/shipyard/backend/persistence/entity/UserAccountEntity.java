package com.shipyard.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_accounts")
public class UserAccountEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 32)
    private String phoneNumber;

    @Column(nullable = false, length = 128)
    private String displayName;

    @Column(nullable = false, length = 256)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private boolean admin;

    @Column(nullable = false)
    private boolean canUpload;

    @Column(nullable = false)
    private boolean canDeleteCache;

    protected UserAccountEntity() {}

    public UserAccountEntity(
        String id,
        String phoneNumber,
        String displayName,
        String passwordHash,
        boolean enabled,
        boolean admin,
        boolean canUpload,
        boolean canDeleteCache
    ) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.admin = admin;
        this.canUpload = canUpload;
        this.canDeleteCache = canDeleteCache;
    }

    public String getId() { return id; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public boolean isAdmin() { return admin; }
    public boolean isCanUpload() { return canUpload; }
    public boolean isCanDeleteCache() { return canDeleteCache; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public void setCanUpload(boolean canUpload) { this.canUpload = canUpload; }
    public void setCanDeleteCache(boolean canDeleteCache) { this.canDeleteCache = canDeleteCache; }
}
