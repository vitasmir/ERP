package com.example.erp.users;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;
    private final ApiAccess access;

    public AuthController(UserRepository users, ApiAccess access) { this.users = users; this.access = access; }

    @PostMapping("/login")
    @Transactional
    public LoginResponse login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) throw invalidCredentials();
        ErpUser user = users.findByUsername(request.username().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(this::invalidCredentials);
        if (user.getStatus() != UserStatus.ACTIVE || !PasswordHasher.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        user.recordLogin();
        users.save(user);
        return new LoginResponse(user.getId(), user.getEmployeeId(), user.getFullName(), user.getUsername(), user.getRoleName(), access.issueToken(user.getId()));
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String authorization) { access.revoke(authorization); }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
    }

    public record LoginRequest(String username, String password) { }
    public record LoginResponse(UUID id, UUID employeeId, String fullName, String username, String roleName, String token) { }
}