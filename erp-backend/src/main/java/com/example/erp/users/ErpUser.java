package com.example.erp.users;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_users")
public class ErpUser {
    @Id
    private UUID id;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "company_name")
    private String companyName;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;

    protected ErpUser() { }

    public static ErpUser create(String fullName, String roleName, String companyName, UserStatus status) {
        ErpUser user = new ErpUser();
        user.id = UUID.randomUUID();
        user.fullName = fullName;
        user.roleName = roleName;
        user.companyName = companyName;
        user.status = status;
        return user;
    }

    public void update(String fullName, String roleName, String companyName, UserStatus status) {
        this.fullName = fullName;
        this.roleName = roleName;
        this.companyName = companyName;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getRoleName() { return roleName; }
    public String getCompanyName() { return companyName; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getLastAccessAt() { return lastAccessAt; }
}