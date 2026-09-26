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

    @Column(name = "can_insert", nullable = false)
    private boolean canInsert;

    @Column(name = "can_manage", nullable = false)
    private boolean canManage;

    @Column(nullable = false, length = 7)
    private String color;

    protected RoleDefinition() { }

    public static RoleDefinition create(String name, String initial, String description,
            boolean canRead, boolean canInsert, boolean canEdit, boolean canManage, String color) {
        RoleDefinition role = new RoleDefinition();
        role.id = UUID.randomUUID();
        role.update(name, initial, description, canRead, canInsert, canEdit, canManage, color);
        return role;
    }

    public void update(String name, String initial, String description,
            boolean canRead, boolean canInsert, boolean canEdit, boolean canManage, String color) {
        this.name = name;
        this.initial = initial;
        this.description = description;
        this.canRead = canRead;
        this.canInsert = canInsert;
        this.canEdit = canEdit;
        this.canManage = canManage;
        this.color = color;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getInitial() { return initial; }
    public String getDescription() { return description; }
    public boolean isCanRead() { return canRead; }
    public boolean isCanInsert() { return canInsert; }
    public boolean isCanEdit() { return canEdit; }
    public boolean isCanManage() { return canManage; }
    public String getColor() { return color; }
}
