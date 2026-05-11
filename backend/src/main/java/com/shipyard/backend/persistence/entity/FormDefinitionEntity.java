package com.shipyard.backend.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "form_definitions")
public class FormDefinitionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String defaultUploadMode;

    protected FormDefinitionEntity() {}

    public FormDefinitionEntity(String id, String name, String defaultUploadMode) {
        this.id = id;
        this.name = name;
        this.defaultUploadMode = defaultUploadMode;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDefaultUploadMode() { return defaultUploadMode; }
}
