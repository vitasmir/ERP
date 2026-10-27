package com.example.erp.frontend.projects;

import java.util.List;
import java.util.UUID;

public record ProjectsOverviewView(long activeProjectCount, long inProgressCount, long dueSoonCount,
        List<ProjectView> projects) {
    public record ProjectView(UUID id, String name, String ownerName, String department, String dueDate,
            int progress, String status) { }
}