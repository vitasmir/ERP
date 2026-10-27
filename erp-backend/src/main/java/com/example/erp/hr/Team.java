package com.example.erp.hr;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hr_teams")
public class Team {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    protected Team() { }

    public Team(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void rename(String name) { this.name = name; }
}
