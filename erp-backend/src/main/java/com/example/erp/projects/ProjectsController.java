package com.example.erp.projects;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectsController {
    private final ProjectRepository projects;

    public ProjectsController(ProjectRepository projects) { this.projects = projects; }

    @GetMapping("/overview")
    public ProjectsOverview overview() {
        List<ProjectResponse> items = projects.findAllByOrderByDueDateAsc().stream().map(ProjectResponse::from).toList();
        return new ProjectsOverview(items.stream().filter(item -> item.status() != ProjectStatus.COMPLETED).count(),
                items.stream().filter(item -> item.status() == ProjectStatus.IN_PROGRESS).count(),
                items.stream().filter(item -> item.status() != ProjectStatus.COMPLETED)
                        .filter(item -> !item.dueDate().isAfter(LocalDate.now().plusDays(14))).count(), items);
    }

    @PatchMapping("/{id}/complete")
    public ProjectResponse complete(@PathVariable UUID id) {
        Project project = projects.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project was not found."));
        project.complete();
        return ProjectResponse.from(projects.save(project));
    }

    public record ProjectsOverview(long activeProjectCount, long inProgressCount, long dueSoonCount,
            List<ProjectResponse> projects) { }

    public record ProjectResponse(UUID id, String name, String ownerName, String department, LocalDate dueDate,
            int progress, ProjectStatus status) {
        static ProjectResponse from(Project project) {
            return new ProjectResponse(project.getId(), project.getName(), project.getOwnerName(),
                    project.getDepartment(), project.getDueDate(), project.getProgress(), project.getStatus());
        }
    }
}