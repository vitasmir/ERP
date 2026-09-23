package com.example.erp.projects;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    private UUID id;
    private String name;

    @Column(name = "owner_name")
    private String ownerName;

    private String department;

    @Column(name = "due_date")
    private LocalDate dueDate;

    private int progress;

    @Enumerated(EnumType.STRING)
    private ProjectStatus status;

    protected Project() { }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getOwnerName() { return ownerName; }
    public String getDepartment() { return department; }
    public LocalDate getDueDate() { return dueDate; }
    public int getProgress() { return progress; }
    public ProjectStatus getStatus() { return status; }

    public void complete() {
        progress = 100;
        status = ProjectStatus.COMPLETED;
    }
}