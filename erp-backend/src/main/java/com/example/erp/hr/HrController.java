package com.example.erp.hr;

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
@RequestMapping("/api/v1/hr")
public class HrController {
    private final EmployeeRepository employees;

    public HrController(EmployeeRepository employees) { this.employees = employees; }

    @GetMapping("/overview")
    public HrOverview overview() {
        List<EmployeeResponse> items = employees.findAllByOrderByEmploymentStartDateDesc().stream()
                .map(EmployeeResponse::from).toList();
        return new HrOverview(items.stream().filter(item -> item.status() == EmployeeStatus.ACTIVE).count(),
                items.stream().filter(item -> item.status() == EmployeeStatus.ONBOARDING).count(),
                items.stream().map(EmployeeResponse::teamName).distinct().count(), items);
    }

    @PatchMapping("/employees/{id}/activate")
    public EmployeeResponse activate(@PathVariable UUID id) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee was not found."));
        employee.activate();
        return EmployeeResponse.from(employees.save(employee));
    }

    public record HrOverview(long activeEmployeeCount, long onboardingCount, long teamCount,
            List<EmployeeResponse> employees) { }

    public record EmployeeResponse(UUID id, String fullName, String teamName, String jobTitle,
            LocalDate employmentStartDate, EmployeeStatus status) {
        static EmployeeResponse from(Employee employee) {
            return new EmployeeResponse(employee.getId(), employee.getFullName(), employee.getTeamName(),
                    employee.getJobTitle(), employee.getEmploymentStartDate(), employee.getStatus());
        }
    }
}