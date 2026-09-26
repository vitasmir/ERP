package com.example.erp.planning;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.hr.Employee;
import com.example.erp.hr.EmployeeRepository;
import com.example.erp.hr.WorkforceAccess;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/planning")
@Transactional
public class PlanningController {
    private final PlanningShiftRepository shifts;
    private final PlanningRules rules;
    private final WorkforceRecords records;
    private final WorkforceAccess access;
    private final EmployeeRepository employees;

    public PlanningController(PlanningShiftRepository shifts, PlanningRules rules, WorkforceRecords records,
            WorkforceAccess access, EmployeeRepository employees) {
        this.shifts = shifts;
        this.rules = rules;
        this.records = records;
        this.access = access;
        this.employees = employees;
    }

    @GetMapping("/overview")
    public PlanningOverview overview() {
        List<ShiftResponse> items = shifts.findAllByOrderByStartAtAsc().stream().filter(access::canSee).map(ShiftResponse::from).toList();
        return new PlanningOverview(items.size(), items.stream().filter(item -> item.employeeName() == null).count(),
                items.stream().filter(item -> item.status() == PlanningShiftStatus.DRAFT).count(),
                items.stream().mapToLong(item -> Duration.between(item.startAt(), item.endAt()).toHours()).sum(), items);
    }

    @PostMapping("/shifts")
    public ShiftResponse create(@Valid @RequestBody ShiftRequest request) {
        records.lock();
        Employee employee = validate(null, request);
        access.requireAssignment(employee == null ? null : employee.getId());
        PlanningShift shift = new PlanningShift(UUID.randomUUID(), employee == null ? null : employee.getFullName(), request.roleName(),
            request.department(), request.startAt(), request.endAt());
        shift.linkEmployee(employee == null ? null : employee.getId());
        shifts.saveAndFlush(shift);
        records.event(shift.getId(), "CREATED", snapshot(shift), access.actor());
        return ShiftResponse.from(shift);
    }

    @PutMapping("/shifts/{id}")
    public ShiftResponse update(@PathVariable UUID id, @Valid @RequestBody ShiftRequest request) {
        records.lock();
        PlanningShift shift = find(id);
        access.requireShift(shift);
        if (request.version() == null || request.version() != shift.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shift changed. Reload before editing.");
        }
        Employee employee = validate(id, request);
        UUID employeeId = employee == null ? null : employee.getId();
        access.requireAssignment(employeeId);
        String before = snapshot(shift);
        UUID previousEmployee = shift.getEmployeeId();
        shift.update(employee == null ? null : employee.getFullName(), request.roleName(), request.department(), request.startAt(), request.endAt());
        shift.linkEmployee(employeeId);
        shifts.saveAndFlush(shift);
        records.event(id, shift.getStatus() == PlanningShiftStatus.PUBLISHED ? "CHANGED_AFTER_PUBLICATION" : "UPDATED",
                before + " -> " + snapshot(shift), access.actor());
        if (shift.getStatus() == PlanningShiftStatus.PUBLISHED) {
            notifyChange(shift, previousEmployee);
        }
        return ShiftResponse.from(shift);
    }

    @PatchMapping("/shifts/{id}/publish")
    public ShiftResponse publish(@PathVariable UUID id) {
        records.lock();
        PlanningShift shift = find(id);
        access.requireShift(shift);
        if (shift.getStatus() == PlanningShiftStatus.PUBLISHED) return ShiftResponse.from(shift);
        Employee employee = rules.validate(id, shift.getEmployeeId(), shift.getEmployeeName(), shift.getRoleName(), shift.getDepartment(),
            shift.getStartAt(), shift.getEndAt());
        if (employee == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Assign an employee before publication.");
        access.requireAssignment(employee.getId());
        shift.linkEmployee(employee.getId());
        shift.assign(employee.getFullName());
        shift.publish();
        shifts.saveAndFlush(shift);
        records.event(id, "PUBLISHED", snapshot(shift), access.actor());
        records.notifyEmployee(id, employee.getId(), "Published: " + snapshot(shift));
        return ShiftResponse.from(shift);
    }

    @PostMapping("/publish")
    public List<ShiftResponse> publishPlan(@Valid @RequestBody PublishRequest request) {
        records.lock();
        return request.shiftIds().stream().distinct().map(this::publish).toList();
    }

    @PatchMapping("/shifts/{id}/assign")
    public ShiftResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignmentRequest request) {
        records.lock();
        PlanningShift shift = find(id);
        return update(id, new ShiftRequest(request.employeeName(), shift.getRoleName(), shift.getDepartment(),
                shift.getStartAt(), shift.getEndAt(), request.employeeId(), request.version()));
    }

    @DeleteMapping("/shifts/{id}")
    public void delete(@PathVariable UUID id) {
        records.lock();
        PlanningShift shift = find(id);
        access.requireShift(shift);
        access.requireAssignment(shift.getEmployeeId());
        if (shift.getStatus() != PlanningShiftStatus.DRAFT) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only drafts can be deleted.");
        records.deleteDraftEvents(id);
        shifts.delete(shift);
    }

    @GetMapping("/shifts/{id}/events")
    public List<WorkforceRecords.Event> events(@PathVariable UUID id) {
        access.requireShift(find(id));
        return records.events(id);
    }

    @GetMapping("/employees")
    public List<EmployeeOption> employeeOptions() {
        return employees.findAllByOrderByEmploymentStartDateDesc().stream().filter(access::canSee)
                .map(employee -> new EmployeeOption(employee.getId(), employee.getFullName(), employee.getTeamName(), employee.getJobTitle())).toList();
    }

    @GetMapping("/workplaces")
    public List<WorkforceRecords.Workplace> workplaces() { return records.workplaces(); }

    @PostMapping("/workplaces")
    public void saveWorkplace(@Valid @RequestBody WorkplaceRequest request) {
        if (!access.managesAll()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        records.lock();
        PlanningRules.validateCapacity(shifts.findAllByOrderByStartAtAsc(), request.name(), request.capacity());
        records.saveWorkplace(request.name(), request.capacity());
    }

    @GetMapping("/notifications")
    public List<WorkforceRecords.Notification> notifications() { return records.notifications(access.currentUser().getEmployeeId()); }

    @PatchMapping("/notifications/{id}/read")
    public void markRead(@PathVariable UUID id) {
        if (!records.markRead(access.currentUser().getEmployeeId(), id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private PlanningShift find(UUID id) { return shifts.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planning shift was not found.")); }
    private Employee validate(UUID id, ShiftRequest request) {
        return rules.validate(id, request.employeeId(), request.employeeName(), request.roleName(), request.department(), request.startAt(), request.endAt());
    }

    private void notifyChange(PlanningShift shift, UUID previousEmployee) {
        records.notifyEmployee(shift.getId(), shift.getEmployeeId(), "Changed: " + snapshot(shift));
        if (previousEmployee != null && !previousEmployee.equals(shift.getEmployeeId())) {
            records.notifyEmployee(shift.getId(), previousEmployee, "Assignment removed: " + shift.getStartAt() + " / " + shift.getDepartment());
        }
    }

    private String snapshot(PlanningShift shift) {
        return shift.getEmployeeId() + " / " + shift.getRoleName() + " / " + shift.getDepartment() + " / " + shift.getStartAt() + " - " + shift.getEndAt();
    }

    public record ShiftRequest(@Size(max = 200) String employeeName, @NotBlank @Size(max = 150) String roleName,
            @NotBlank @Size(max = 150) String department, @NotNull java.time.LocalDateTime startAt,
            @NotNull java.time.LocalDateTime endAt, UUID employeeId, Long version) { }
    public record AssignmentRequest(@Size(max = 200) String employeeName, UUID employeeId, @NotNull Long version) { }
    public record WorkplaceRequest(@NotBlank @Size(max = 150) String name, @Min(1) @Max(10000) int capacity) { }
    public record EmployeeOption(UUID id, String fullName, String teamName, String jobTitle) { }
    public record PublishRequest(@jakarta.validation.constraints.NotEmpty @Size(max = 500) List<@NotNull UUID> shiftIds) { }

    public record PlanningOverview(long shiftCount, long openShiftCount, long draftShiftCount, long plannedHours,
            List<ShiftResponse> shifts) { }

    public record ShiftResponse(UUID id, String employeeName, String roleName, String department,
            java.time.LocalDateTime startAt, java.time.LocalDateTime endAt, PlanningShiftStatus status, UUID employeeId, long version) {
        static ShiftResponse from(PlanningShift shift) {
            return new ShiftResponse(shift.getId(), shift.getEmployeeName(), shift.getRoleName(), shift.getDepartment(),
                    shift.getStartAt(), shift.getEndAt(), shift.getStatus(), shift.getEmployeeId(), shift.getVersion());
        }
    }
}