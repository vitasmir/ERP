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

import com.example.erp.hr.Employee;
import com.example.erp.hr.EmployeeRepository;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository users;
    private final EmployeeRepository employees;

    public UserController(UserRepository users, EmployeeRepository employees) {
        this.users = users;
        this.employees = employees;
    }

    @GetMapping
    public List<UserResponse> list() {
        return users.findAllByOrderByFullNameAsc().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/employee-options")
    public List<EmployeeOption> employeeOptions() {
        return employees.findAllByOrderByEmploymentStartDateDesc().stream()
                .map(employee -> new EmployeeOption(employee.getId(), employee.getFullName(), employee.getTeamName(),
                        users.existsByEmployee_Id(employee.getId())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody UserRequest request) {
        UserData data = validate(request, null);
        return UserResponse.from(users.save(ErpUser.create(data.employee(), data.fullName(), data.roleName(),
            data.companyName(), data.username(), data.passwordHash(), data.status())));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @RequestBody UserRequest request) {
        UserData data = validate(request, id);
        ErpUser user = findUser(id);
        user.update(data.employee(), data.fullName(), data.roleName(), data.companyName(), data.username(),
            data.passwordHash(), data.status());
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

    private UserData validate(UserRequest request, UUID currentUserId) {
        if (request == null || isBlank(request.fullName()) || isBlank(request.roleName()) || isBlank(request.companyName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User fields are required.");
        }
        UserStatus status;
        try {
            status = isBlank(request.status()) ? UserStatus.ACTIVE : UserStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown user status.");
        }
        Employee employee = findEmployee(request.employeeId());
        if ((currentUserId == null
                ? users.existsByEmployee_Id(employee.getId())
                : users.existsByEmployee_IdAndIdNot(employee.getId(), currentUserId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee already has a user account.");
        }
        String username = normalizeUsername(request.username());
        if ((currentUserId == null ? users.existsByUsername(username) : users.existsByUsernameAndIdNot(username, currentUserId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use.");
        }
        String passwordHash = passwordHash(request.password(), currentUserId == null);
        return new UserData(employee, request.fullName().trim(), request.roleName().trim(), request.companyName().trim(),
            username, passwordHash, status);
    }

    private Employee findEmployee(String employeeId) {
        if (isBlank(employeeId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee is required.");
        }
        try {
            return employees.findById(UUID.fromString(employeeId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee was not found."));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee.");
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private String normalizeUsername(String username) {
        if (isBlank(username) || !username.trim().matches("[a-zA-Z0-9._-]{3,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must contain 3 to 64 letters, numbers, dots, hyphens, or underscores.");
        }
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String passwordHash(String password, boolean required) {
        if (isBlank(password)) {
            if (required) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required.");
            return null;
        }
        if (password.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at least 10 characters.");
        }
        return PasswordHasher.hash(password);
    }

    public record UserRequest(String employeeId, String fullName, String roleName, String companyName, String username,
            String password, String status) { }

    private record UserData(Employee employee, String fullName, String roleName, String companyName, String username,
            String passwordHash, UserStatus status) { }

    public record UserResponse(UUID id, String fullName, String roleName, String companyName,
            UserStatus status, String lastAccessAt, UUID employeeId, String username) {
        static UserResponse from(ErpUser user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getRoleName(), user.getCompanyName(),
                    user.getStatus(), user.getLastAccessAt() == null ? null : user.getLastAccessAt().toString(),
                    user.getEmployeeId(), user.getUsername());
        }
    }

    public record EmployeeOption(UUID id, String fullName, String teamName, boolean hasAccount) { }
}