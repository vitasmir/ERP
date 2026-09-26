package com.example.erp.planning;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "planning_shifts")
public class PlanningShift {
    @Id
    private UUID id;

    @Column(name = "employee_name")
    private String employeeName;

    @Column(name = "role_name")
    private String roleName;

    private String department;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    private PlanningShiftStatus status;

    protected PlanningShift() { }

    public PlanningShift(UUID id, String employeeName, String roleName, String department,
            LocalDateTime startAt, LocalDateTime endAt) {
        this.id = id;
        this.employeeName = employeeName;
        this.roleName = roleName;
        this.department = department;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = PlanningShiftStatus.DRAFT;
    }

    public UUID getId() { return id; }
    public String getEmployeeName() { return employeeName; }
    public String getRoleName() { return roleName; }
    public String getDepartment() { return department; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public PlanningShiftStatus getStatus() { return status; }

    public void publish() { status = PlanningShiftStatus.PUBLISHED; }
    public void update(String employeeName, String roleName, String department, LocalDateTime startAt,
            LocalDateTime endAt) {
        this.employeeName = employeeName;
        this.roleName = roleName;
        this.department = department;
        this.startAt = startAt;
        this.endAt = endAt;
    }
    public void assign(String employeeName) { this.employeeName = employeeName; }
}