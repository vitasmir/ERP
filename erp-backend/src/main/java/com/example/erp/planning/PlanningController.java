package com.example.erp.planning;

import java.time.Duration;
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

    @PatchMapping("/shifts/{id}/publish")
    public ShiftResponse publish(@PathVariable UUID id) {
        PlanningShift shift = shifts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planning shift was not found."));
        shift.publish();
        return ShiftResponse.from(shifts.save(shift));
    }

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