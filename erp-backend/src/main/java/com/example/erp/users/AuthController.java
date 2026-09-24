package com.example.erp.users;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;

    public AuthController(UserRepository users) { this.users = users; }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) invalidCredentials();
        ErpUser user = users.findByUsername(request.username().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(this::invalidCredentials);
        if (user.getStatus() != UserStatus.ACTIVE || !PasswordHasher.matches(request.password(), user.getPasswordHash())) {
            invalidCredentials();
        }
        user.recordLogin();
        users.save(user);
        return new LoginResponse(user.getId(), user.getEmployeeId(), user.getFullName(), user.getUsername(), user.getRoleName());
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
    }

    public record LoginRequest(String username, String password) { }
    public record LoginResponse(UUID id, UUID employeeId, String fullName, String username, String roleName) { }
}