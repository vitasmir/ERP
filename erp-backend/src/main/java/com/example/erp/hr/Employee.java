package com.example.erp.hr;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "employees")
public class Employee {
    @Id
    private UUID id;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "team_name")
    private String teamName;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "employment_start_date")
    private LocalDate employmentStartDate;

    @Enumerated(EnumType.STRING)
    private EmployeeStatus status;

    protected Employee() { }

    public Employee(UUID id, String fullName, String teamName, String jobTitle, LocalDate employmentStartDate) {
        this.id = id;
        this.fullName = fullName;
        this.teamName = teamName;
        this.jobTitle = jobTitle;
        this.employmentStartDate = employmentStartDate;
        this.status = EmployeeStatus.ONBOARDING;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getTeamName() { return teamName; }
    public String getJobTitle() { return jobTitle; }
    public LocalDate getEmploymentStartDate() { return employmentStartDate; }
    public EmployeeStatus getStatus() { return status; }

    public void activate() { status = EmployeeStatus.ACTIVE; }
    public void update(String fullName, String teamName, String jobTitle, LocalDate employmentStartDate) {
        this.fullName = fullName;
        this.teamName = teamName;
        this.jobTitle = jobTitle;
        this.employmentStartDate = employmentStartDate;
    }
    public void deactivate() { status = EmployeeStatus.INACTIVE; }
}