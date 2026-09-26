package com.example.erp.users;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_definitions")
public class RoleDefinition {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 1)
    private String initial;

    @Column(nullable = false, length = 240)
    private String description;

    @Column(name = "can_read", nullable = false)
    private boolean canRead;

    @Column(name = "can_edit", nullable = false)
    private boolean canEdit;

    @Column(name = "can_manage", nullable = false)
    private boolean canManage;

    protected RoleDefinition() { }

    public static RoleDefinition create(String name, String initial, String description,
            boolean canRead, boolean canEdit, boolean canManage) {
        RoleDefinition role = new RoleDefinition();
        role.id = UUID.randomUUID();
        role.update(name, initial, description, canRead, canEdit, canManage);
        return role;
    }

    public void update(String name, String initial, String description,
            boolean canRead, boolean canEdit, boolean canManage) {
        this.name = name;
        this.initial = initial;
        this.description = description;
        this.canRead = canRead;
        this.canEdit = canEdit;
        this.canManage = canManage;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getInitial() { return initial; }
    public String getDescription() { return description; }
    public boolean isCanRead() { return canRead; }
    public boolean isCanEdit() { return canEdit; }
    public boolean isCanManage() { return canManage; }
}
