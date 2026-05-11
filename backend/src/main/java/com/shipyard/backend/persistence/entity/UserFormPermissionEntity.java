package com.shipyard.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_form_permissions")
public class UserFormPermissionEntity {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String formId;

    protected UserFormPermissionEntity() {}

    public UserFormPermissionEntity(String id, String userId, String formId) {
        this.id = id;
        this.userId = userId;
        this.formId = formId;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getFormId() { return formId; }
}
