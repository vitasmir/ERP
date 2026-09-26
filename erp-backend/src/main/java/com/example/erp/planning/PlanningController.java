package com.example.erp.planning;

import java.time.Duration;
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
@RequestMapping("/api/v1/planning")
public class PlanningController {
    private final PlanningShiftRepository shifts;

    public PlanningController(PlanningShiftRepository shifts) { this.shifts = shifts; }

    @GetMapping("/overview")
    public PlanningOverview overview() {
        List<ShiftResponse> items = shifts.findAllByOrderByStartAtAsc().stream().map(ShiftResponse::from).toList();
        return new PlanningOverview(items.size(), items.stream().filter(item -> item.employeeName() == null).count(),
                items.stream().filter(item -> item.status() == PlanningShiftStatus.DRAFT).count(),
                items.stream().mapToLong(item -> Duration.between(item.startAt(), item.endAt()).toHours()).sum(), items);
    }

    @PostMapping("/shifts")
    public ShiftResponse create(@Valid @RequestBody ShiftRequest request) {
        validateTimes(request.startAt(), request.endAt());
        return ShiftResponse.from(shifts.save(new PlanningShift(UUID.randomUUID(), request.employeeName(), request.roleName(),
                request.department(), request.startAt(), request.endAt())));
    }

    @PutMapping("/shifts/{id}")
    public ShiftResponse update(@PathVariable UUID id, @Valid @RequestBody ShiftRequest request) {
        validateTimes(request.startAt(), request.endAt());
        PlanningShift shift = find(id);
        shift.update(request.employeeName(), request.roleName(), request.department(), request.startAt(), request.endAt());
        return ShiftResponse.from(shifts.save(shift));
    }

    @PatchMapping("/shifts/{id}/publish")
    public ShiftResponse publish(@PathVariable UUID id) {
        PlanningShift shift = find(id);
        shift.publish();
        return ShiftResponse.from(shifts.save(shift));
    }

    @PatchMapping("/shifts/{id}/assign")
    public ShiftResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignmentRequest request) {
        PlanningShift shift = find(id);
        shift.assign(request.employeeName());
        return ShiftResponse.from(shifts.save(shift));
    }

    private PlanningShift find(UUID id) { return shifts.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planning shift was not found.")); }
    private void validateTimes(java.time.LocalDateTime startAt, java.time.LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shift end must be after start.");
    }

    public record ShiftRequest(String employeeName, @NotBlank String roleName, @NotBlank String department,
            @NotNull java.time.LocalDateTime startAt, @NotNull java.time.LocalDateTime endAt) { }
    public record AssignmentRequest(@NotBlank String employeeName) { }

    public record PlanningOverview(long shiftCount, long openShiftCount, long draftShiftCount, long plannedHours,
            List<ShiftResponse> shifts) { }

    public record ShiftResponse(UUID id, String employeeName, String roleName, String department,
            java.time.LocalDateTime startAt, java.time.LocalDateTime endAt, PlanningShiftStatus status) {
        static ShiftResponse from(PlanningShift shift) {
            return new ShiftResponse(shift.getId(), shift.getEmployeeName(), shift.getRoleName(), shift.getDepartment(),
                    shift.getStartAt(), shift.getEndAt(), shift.getStatus());
        }
    }
}