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

    @Column(name = "username")
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(name = "last_access_at")
    private LocalDateTime lastAccessAt;

    protected ErpUser() { }

        public static ErpUser create(Employee employee, String fullName, String roleName, String companyName,
            String username, String passwordHash, UserStatus status) {
        ErpUser user = new ErpUser();
        user.id = UUID.randomUUID();
        user.employee = employee;
        user.fullName = fullName;
        user.roleName = roleName;
        user.companyName = companyName;
        user.username = username;
        user.passwordHash = passwordHash;
        user.status = status;
        return user;
    }

    public void update(Employee employee, String fullName, String roleName, String companyName,
            String passwordHash, UserStatus status) {
        this.employee = employee;
        this.fullName = fullName;
        this.roleName = roleName;
        this.companyName = companyName;
        if (passwordHash != null) this.passwordHash = passwordHash;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getEmployeeId() { return employee == null ? null : employee.getId(); }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getRoleName() { return roleName; }
    public String getCompanyName() { return companyName; }
    public UserStatus getStatus() { return status; }
    public LocalDateTime getLastAccessAt() { return lastAccessAt; }
    public void recordLogin() { lastAccessAt = LocalDateTime.now(); }
}