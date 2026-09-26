package com.example.erp.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/hr")
public class HrController {
    private final EmployeeRepository employees;
        private final com.example.erp.users.UserRepository users;

        public HrController(EmployeeRepository employees, com.example.erp.users.UserRepository users) {
                this.employees = employees;
                this.users = users;
        }

    @GetMapping("/overview")
    public HrOverview overview() {
        List<EmployeeResponse> items = employees.findAllByOrderByEmploymentStartDateDesc().stream()
                .map(employee -> EmployeeResponse.from(employee, users.existsByEmployee_Id(employee.getId()))).toList();
        return new HrOverview(items.stream().filter(item -> item.status() == EmployeeStatus.ACTIVE).count(),
                items.stream().filter(item -> item.status() == EmployeeStatus.ONBOARDING).count(),
                items.stream().map(EmployeeResponse::teamName).distinct().count(), items);
    }

        @PostMapping("/employees")
        public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
                return response(employees.save(new Employee(UUID.randomUUID(), request.fullName(), request.teamName(),
                                request.jobTitle(), request.employmentStartDate())));
        }

        @PutMapping("/employees/{id}")
        public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest request) {
                Employee employee = find(id);
                employee.update(request.fullName(), request.teamName(), request.jobTitle(), request.employmentStartDate());
                return response(employees.save(employee));
        }

    @PatchMapping("/employees/{id}/activate")
    public EmployeeResponse activate(@PathVariable UUID id) {
        Employee employee = find(id);
        employee.activate();
        Employee updatedEmployee = employees.save(employee);
        return EmployeeResponse.from(updatedEmployee, users.existsByEmployee_Id(updatedEmployee.getId()));
    }

        @PatchMapping("/employees/{id}/deactivate")
        public EmployeeResponse deactivate(@PathVariable UUID id) {
                Employee employee = find(id);
                employee.deactivate();
                return response(employees.save(employee));
        }

        private Employee find(UUID id) { return employees.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee was not found.")); }
        private EmployeeResponse response(Employee employee) {
                return EmployeeResponse.from(employee, users.existsByEmployee_Id(employee.getId()));
        }

        public record EmployeeRequest(@NotBlank String fullName, @NotBlank String teamName, @NotBlank String jobTitle,
                        @NotNull LocalDate employmentStartDate) { }

    public record HrOverview(long activeEmployeeCount, long onboardingCount, long teamCount,
            List<EmployeeResponse> employees) { }

    /**
     * Represents the response for an employee in the HR overview.
     *
     * @param id                the unique identifier of the employee
     * @param fullName          the full name of the employee
     * @param teamName          the name of the team the employee belongs to
     * @param jobTitle          the job title of the employee
     * @param employmentStartDate the start date of the employee's employment
     * @param status            the current status of the employee
     * @param hasUserAccount    whether the employee has an associated user account
     */
    public record EmployeeResponse(UUID id, String fullName, String teamName, String jobTitle,
                        LocalDate employmentStartDate, EmployeeStatus status, boolean hasUserAccount) {
                static EmployeeResponse from(Employee employee, boolean hasUserAccount) {
            return new EmployeeResponse(employee.getId(), employee.getFullName(), employee.getTeamName(),
                                        employee.getJobTitle(), employee.getEmploymentStartDate(), employee.getStatus(), hasUserAccount);
        }
    }
}