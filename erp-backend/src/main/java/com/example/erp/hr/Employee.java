package com.example.erp.hr;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deputy_employee_id")
    private Employee deputy;

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
    public String getTeamName() { return team == null ? teamName : team.getName(); }
    public Team getTeam() { return team; }
    public Employee getDeputy() { return deputy; }
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
    public void assign(Team team, Employee deputy) {
        this.team = team;
        this.teamName = team.getName();
        this.deputy = deputy;
    }
    public void assignDeputy(Employee deputy) { this.deputy = deputy; }
    public void deactivate() { status = EmployeeStatus.INACTIVE; }
}