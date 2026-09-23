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
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository users;

    public UserController(UserRepository users) { this.users = users; }

    @GetMapping
    public List<UserResponse> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody UserRequest request) {
        UserData data = validate(request);
        return UserResponse.from(users.save(ErpUser.create(data.fullName(), data.roleName(), data.companyName(), data.status())));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @RequestBody UserRequest request) {
        UserData data = validate(request);
        ErpUser user = findUser(id);
        user.update(data.fullName(), data.roleName(), data.companyName(), data.status());
        return UserResponse.from(users.save(user));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        users.delete(findUser(id));
    }

    private ErpUser findUser(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found."));
    }

    private UserData validate(UserRequest request) {
        if (request == null || isBlank(request.fullName()) || isBlank(request.roleName()) || isBlank(request.companyName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User fields are required.");
        }
        UserStatus status;
        try {
            status = isBlank(request.status()) ? UserStatus.ACTIVE : UserStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown user status.");
        }
        return new UserData(request.fullName().trim(), request.roleName().trim(), request.companyName().trim(), status);
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    public record UserRequest(String fullName, String roleName, String companyName, String status) { }

    private record UserData(String fullName, String roleName, String companyName, UserStatus status) { }

    public record UserResponse(UUID id, String fullName, String roleName, String companyName,
            UserStatus status, String lastAccessAt) {
        static UserResponse from(ErpUser user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getRoleName(), user.getCompanyName(),
                    user.getStatus(), user.getLastAccessAt() == null ? null : user.getLastAccessAt().toString());
        }
    }
}