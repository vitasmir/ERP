package com.example.erp.users;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleDefinitionController {
    private final RoleDefinitionRepository roles;

    public RoleDefinitionController(RoleDefinitionRepository roles) {
        this.roles = roles;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roles.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@RequestBody RoleRequest request) {
        RoleData data = validate(request);
        if (roles.findByNameIgnoreCase(data.name()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists.");
        }
        return toResponse(roles.save(RoleDefinition.create(data.name(), data.initial(), data.description(),
            data.canRead(), data.canEdit(), data.canManage(), data.color())));
    }

    @PutMapping("/{id}")
    public RoleResponse update(@PathVariable UUID id, @RequestBody RoleRequest request) {
        RoleDefinition role = roles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role was not found."));
        RoleData data = validate(request);
        roles.findByNameIgnoreCase(data.name()).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists.");
        });
        role.update(data.name(), data.initial(), data.description(), data.canRead(), data.canEdit(), data.canManage(), data.color());
        return toResponse(roles.save(role));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        if (!roles.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role was not found.");
        }
        roles.deleteById(id);
    }

    private RoleData validate(RoleRequest request) {
        if (request == null || isBlank(request.name()) || isBlank(request.initial())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role name and initial are required.");
        }
        String initial = request.initial().trim().toUpperCase();
        if (initial.length() != 1 || !Character.isLetter(initial.charAt(0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role initial must be one letter.");
        }
        return new RoleData(request.name().trim(), initial,
                isBlank(request.description()) ? "Nová pracovní role v ERP systému." : request.description().trim(),
            request.canRead(), request.canEdit(), request.canManage(), validColor(request.color(), "#D9ED62"));
    }

    private RoleResponse toResponse(RoleDefinition role) {
        return new RoleResponse(role.getId(), role.getName(), role.getInitial(), role.getDescription(),
            role.isCanRead(), role.isCanEdit(), role.isCanManage(), role.getColor(), roles.countByName(role.getName()));
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

        private String validColor(String value, String fallback) {
        return value != null && value.matches("#[0-9A-Fa-f]{6}") ? value.toUpperCase() : fallback;
        }

    public record RoleRequest(String name, String initial, String description,
            boolean canRead, boolean canEdit, boolean canManage, String color) { }

    private record RoleData(String name, String initial, String description,
            boolean canRead, boolean canEdit, boolean canManage, String color) { }

    public record RoleResponse(UUID id, String name, String initial, String description,
            boolean canRead, boolean canEdit, boolean canManage, String color, long userCount) { }
}
