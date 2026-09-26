package com.example.erp.frontend.planning;

import java.util.List;
import java.util.UUID;

public record PlanningOverviewView(long shiftCount, long openShiftCount, long draftShiftCount, long plannedHours,
        List<ShiftView> shifts) {
    public record ShiftView(UUID id, String employeeName, String roleName, String department, String startAt,
            String endAt, String status, UUID employeeId, long version) { }
}