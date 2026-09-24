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

    @PatchMapping("/employees/{id}/activate")
    public EmployeeResponse activate(@PathVariable UUID id) {
        Employee employee = employees.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee was not found."));
        employee.activate();
        Employee updatedEmployee = employees.save(employee);
        return EmployeeResponse.from(updatedEmployee, users.existsByEmployee_Id(updatedEmployee.getId()));
    }

    public record HrOverview(long activeEmployeeCount, long onboardingCount, long teamCount,
            List<EmployeeResponse> employees) { }

    public record EmployeeResponse(UUID id, String fullName, String teamName, String jobTitle,
                        LocalDate employmentStartDate, EmployeeStatus status, boolean hasUserAccount) {
                static EmployeeResponse from(Employee employee, boolean hasUserAccount) {
            return new EmployeeResponse(employee.getId(), employee.getFullName(), employee.getTeamName(),
                                        employee.getJobTitle(), employee.getEmploymentStartDate(), employee.getStatus(), hasUserAccount);
        }
    }
}