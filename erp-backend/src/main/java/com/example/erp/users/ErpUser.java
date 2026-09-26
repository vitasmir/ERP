package com.example.erp.users;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.erp.hr.Employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;

    @Column(nullable = false, length = 7)
    private String color;

    protected ErpUser() { }

    public static ErpUser create(Employee employee, String fullName, String roleName, String companyName, UserStatus status, String color) {
        ErpUser user = new ErpUser();
        user.id = UUID.randomUUID();
        user.employee = employee;
        user.fullName = fullName;
        user.roleName = roleName;
        user.companyName = companyName;
        user.status = status;
        user.color = color;
        return user;
    }

    public void update(Employee employee, String fullName, String roleName, String companyName, UserStatus status, String color) {
        this.employee = employee;
        this.fullName = fullName;
        this.roleName = roleName;
        this.companyName = companyName;
        this.status = status;
        this.color = color;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employee == null ? null : employee.getId(); }
    public String getFullName() { return fullName; }
    public String getRoleName() { return roleName; }
    public String getCompanyName() { return companyName; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getLastAccessAt() { return lastAccessAt; }
    public String getColor() { return color; }

    public void recordLogin() { lastAccessAt = LocalDateTime.now(); }
}