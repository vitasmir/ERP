package com.example.erp.planning;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.hr.Employee;
import com.example.erp.hr.EmployeeRepository;
import com.example.erp.hr.EmployeeStatus;

@Component
public class PlanningRules {
    private final PlanningShiftRepository shifts;
    private final EmployeeRepository employees;
    private final WorkforceRecords records;

    public PlanningRules(PlanningShiftRepository shifts, EmployeeRepository employees, WorkforceRecords records) {
        this.shifts = shifts;
        this.employees = employees;
        this.records = records;
    }

    public Employee validate(UUID shiftId, UUID employeeId, String employeeName, String roleName, String department,
            LocalDateTime startAt, LocalDateTime endAt) {
        validateTimes(startAt, endAt);
        List<PlanningShift> existing = shifts.findAllByOrderByStartAtAsc().stream()
                .filter(shift -> !shift.getId().equals(shiftId)).toList();
        WorkforceRecords.Workplace workplace = records.workplaces().stream().filter(item -> item.name().equals(department))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown workplace."));
        List<PlanningShift> proposed = new ArrayList<>(existing);
        proposed.add(new PlanningShift(UUID.randomUUID(), null, roleName, department, startAt, endAt));
        validateCapacity(proposed, department, workplace.capacity());
        Employee employee = resolve(employeeId, employeeName);
        if (employee == null) return null;
        if (employee.getStatus() != EmployeeStatus.ACTIVE || employee.getEmploymentStartDate().isAfter(startAt.toLocalDate())) {
            conflict("Employee is not active for this shift.");
        }
        if (!employee.getJobTitle().equals(roleName) && !records.qualifications(employee.getId()).contains(roleName)) {
            conflict("Employee is not qualified for this role.");
        }
        if (existing.stream().filter(shift -> employee.getId().equals(shift.getEmployeeId())
                || shift.getEmployeeId() == null && employee.getFullName().equals(shift.getEmployeeName()))
                .anyMatch(shift -> overlaps(startAt, endAt, shift.getStartAt(), shift.getEndAt()))) {
            conflict("Employee already has an overlapping shift.");
        }
        if (records.absences(employee.getId()).stream().anyMatch(absence -> overlaps(startAt, endAt, absence.startAt(), absence.endAt()))) {
            conflict("Employee is absent during this shift.");
        }
        return employee;
    }

    private Employee resolve(UUID employeeId, String employeeName) {
        if (employeeId != null) return employees.findById(employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee was not found."));
        if (employeeName == null || employeeName.isBlank()) return null;
        List<Employee> matches = employees.findAllByOrderByEmploymentStartDateDesc().stream()
                .filter(employee -> employee.getFullName().equalsIgnoreCase(employeeName.strip())).toList();
        if (matches.size() != 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an employee using their HR identifier.");
        return matches.getFirst();
    }

    public static void validateTimes(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End must be after start.");
        }
    }

    public static boolean overlaps(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime otherStart, LocalDateTime otherEnd) {
        return startAt.isBefore(otherEnd) && endAt.isAfter(otherStart);
    }

    public static void validateCapacity(List<PlanningShift> shifts, String department, int capacity) {
        List<Boundary> boundaries = new ArrayList<>();
        for (PlanningShift shift : shifts) {
            if (shift.getDepartment().equals(department)) {
                boundaries.add(new Boundary(shift.getStartAt(), 1));
                boundaries.add(new Boundary(shift.getEndAt(), -1));
            }
        }
        boundaries.sort(Comparator.comparing(Boundary::time).thenComparingInt(Boundary::change));
        int concurrent = 0;
        for (Boundary boundary : boundaries) {
            concurrent += boundary.change();
            if (concurrent > capacity) conflict("Workplace capacity exceeded.");
        }
    }

    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record Boundary(LocalDateTime time, int change) { }
}