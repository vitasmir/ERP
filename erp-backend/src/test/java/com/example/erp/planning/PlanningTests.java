package com.example.erp.planning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.hr.Employee;
import com.example.erp.hr.EmployeeRepository;
import com.example.erp.hr.WorkforceAccess;

class PlanningTests {
    private final PlanningShiftRepository shifts = mock(PlanningShiftRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final WorkforceRecords records = mock(WorkforceRecords.class);
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final PlanningRules rules = new PlanningRules(shifts, employees, records);
    private final PlanningController controller = new PlanningController(shifts, rules, records, access, employees);
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 8, 0);
    private final Employee employee = new Employee(UUID.randomUUID(), "Employee", "Team", "Cashier", start.toLocalDate());

    @BeforeEach
    void setUp() {
        employee.activate();
        when(employees.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(employees.findAllByOrderByEmploymentStartDateDesc()).thenReturn(List.of(employee));
        when(records.workplaces()).thenReturn(List.of(new WorkforceRecords.Workplace("Store", 10)));
    }

    @Test
    void planningRolesComeFromTheCompleteRoleCatalog() {
        when(records.roles()).thenReturn(List.of("Buyer", "Driver", "Planner"));
        assertEquals(List.of("Buyer", "Driver", "Planner"), controller.roles());
    }

    @Test
    void overlappingShiftIsRejectedWithoutWriting() {
        PlanningShift existing = shift(start, start.plusHours(8));
        when(shifts.findAllByOrderByStartAtAsc()).thenReturn(List.of(existing));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.create(request(start.plusHours(1), start.plusHours(2))));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(shifts, never()).saveAndFlush(any());
    }

    @Test
    void adjacentShiftAndEditingSameShiftAreAllowed() {
        PlanningShift existing = shift(start, start.plusHours(8));
        when(shifts.findAllByOrderByStartAtAsc()).thenReturn(List.of(existing));
        when(shifts.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(shifts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertDoesNotThrow(() -> controller.create(request(start.plusHours(8), start.plusHours(9))));
        assertDoesNotThrow(() -> controller.update(existing.getId(), request(start, start.plusHours(8))));
    }

    @Test
    void assignmentAndPublicationAlsoCheckOverlap() {
        PlanningShift existing = shift(start, start.plusHours(8));
        PlanningShift conflicting = shift(start.plusHours(1), start.plusHours(2));
        when(shifts.findAllByOrderByStartAtAsc()).thenReturn(List.of(existing, conflicting));
        when(shifts.findById(conflicting.getId())).thenReturn(Optional.of(conflicting));
        assertThrows(ResponseStatusException.class, () -> controller.publish(conflicting.getId()));
        assertThrows(ResponseStatusException.class, () -> controller.assign(conflicting.getId(),
                new PlanningController.AssignmentRequest("Employee", employee.getId(), 0L)));
        assertEquals(PlanningShiftStatus.DRAFT, conflicting.getStatus());
        verify(shifts, never()).saveAndFlush(any());
    }

    private PlanningShift shift(LocalDateTime from, LocalDateTime to) {
        return new PlanningShift(UUID.randomUUID(), "Employee", "Cashier", "Store", from, to);
    }

    private PlanningController.ShiftRequest request(LocalDateTime from, LocalDateTime to) {
        return new PlanningController.ShiftRequest("Employee", "Cashier", "Store", from, to, employee.getId(), 0L);
    }

    @Test
    void absenceInactiveEmployeeAndMissingQualificationBlockAssignment() {
        when(records.absences(employee.getId())).thenReturn(List.of(new WorkforceRecords.Absence(UUID.randomUUID(),
                employee.getId(), start, start.plusHours(2), "Leave")));
        assertThrows(ResponseStatusException.class, () -> controller.create(request(start, start.plusHours(8))));
        when(records.absences(employee.getId())).thenReturn(List.of());
        employee.deactivate();
        assertThrows(ResponseStatusException.class, () -> controller.create(request(start, start.plusHours(8))));
        employee.activate();
        assertThrows(ResponseStatusException.class, () -> controller.create(new PlanningController.ShiftRequest(null,
                "Driver", "Store", start, start.plusHours(8), employee.getId(), null)));
        verify(shifts, never()).saveAndFlush(any());
    }

    @Test
    void capacityCountsConcurrentSlotsRatherThanEveryIntersectingSlot() {
        PlanningShift early = shift(start, start.plusHours(1));
        PlanningShift late = shift(start.plusHours(1), start.plusHours(2));
        assertDoesNotThrow(() -> PlanningRules.validateCapacity(List.of(early, late), "Store", 1));
        PlanningShift spanning = shift(start, start.plusHours(2));
        assertDoesNotThrow(() -> PlanningRules.validateCapacity(List.of(early, late, spanning), "Store", 2));
        assertThrows(ResponseStatusException.class, () -> PlanningRules.validateCapacity(List.of(early, late, spanning), "Store", 1));
    }

    @Test
    void publicationAndLaterEditsCreateAuditAndNotification() {
        PlanningShift shift = shift(start, start.plusHours(8));
        when(shifts.findById(shift.getId())).thenReturn(Optional.of(shift));
        when(shifts.findAllByOrderByStartAtAsc()).thenReturn(List.of(shift));
        controller.publish(shift.getId());
        controller.publish(shift.getId());
        verify(records).event(org.mockito.ArgumentMatchers.eq(shift.getId()), org.mockito.ArgumentMatchers.eq("PUBLISHED"), any(), any());
        controller.update(shift.getId(), request(start.plusHours(1), start.plusHours(9)));
        verify(records).event(org.mockito.ArgumentMatchers.eq(shift.getId()), org.mockito.ArgumentMatchers.eq("CHANGED_AFTER_PUBLICATION"), any(), any());
        verify(records, org.mockito.Mockito.times(2)).notifyEmployee(org.mockito.ArgumentMatchers.eq(shift.getId()),
                org.mockito.ArgumentMatchers.eq(employee.getId()), any());
    }

    @Test
    void staleUpdateDoesNotMutateShift() {
        PlanningShift shift = shift(start, start.plusHours(8));
        when(shifts.findById(shift.getId())).thenReturn(Optional.of(shift));
        assertThrows(ResponseStatusException.class, () -> controller.update(shift.getId(), new PlanningController.ShiftRequest(null,
                "Cashier", "Store", start.plusHours(1), start.plusHours(9), employee.getId(), 99L)));
        assertEquals(start, shift.getStartAt());
        verify(shifts, never()).saveAndFlush(any());
    }
}