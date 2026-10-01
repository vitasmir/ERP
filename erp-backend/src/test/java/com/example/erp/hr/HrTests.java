package com.example.erp.hr;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.planning.PlanningShift;
import com.example.erp.planning.PlanningShiftRepository;
import com.example.erp.planning.WorkforceRecords;
import com.example.erp.users.ApiAccess;
import com.example.erp.users.ErpUser;
import com.example.erp.users.UserRepository;
import com.example.erp.users.UserStatus;

class HrTests {
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final TeamRepository teams = mock(TeamRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WorkforceRecords records = mock(WorkforceRecords.class);
    private final PlanningShiftRepository shifts = mock(PlanningShiftRepository.class);
    private final WorkforceAccess access = mock(WorkforceAccess.class);
    private final ApiAccess sessions = mock(ApiAccess.class);
    private final HrController controller = new HrController(employees, users, records, shifts, access, sessions);
    private final Employee employee = new Employee(UUID.randomUUID(), "Employee", "Team", "Cashier", LocalDate.now());

    @Test
    void employmentActivationAndDepartureSynchronizeAccountAndRevokeSessions() {
        ErpUser user = ErpUser.create(employee, "Employee", "Company", UserStatus.INVITED, "#ffffff");
        when(employees.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(employees.save(employee)).thenReturn(employee);
        when(users.findByEmployee_Id(employee.getId())).thenReturn(Optional.of(user));
        controller.activate(employee.getId());
        assertEquals(EmployeeStatus.ACTIVE, employee.getStatus());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        controller.deactivate(employee.getId());
        assertEquals(EmployeeStatus.INACTIVE, employee.getStatus());
        assertEquals(UserStatus.SUSPENDED, user.getStatus());
        verify(sessions).revokeUser(user.getId());
    }

    @Test
    void futureStartCannotActivateAccount() {
        employee.update("Employee", "Team", "Cashier", LocalDate.now().plusDays(1));
        when(employees.findById(employee.getId())).thenReturn(Optional.of(employee));
        assertThrows(ResponseStatusException.class, () -> controller.activate(employee.getId()));
        assertEquals(EmployeeStatus.ONBOARDING, employee.getStatus());
        verify(users, never()).save(any());
    }

    @Test
    void assigningDeputyAlsoAssignsCreatorWhenDeputyHasNone() {
        Team team = new Team(UUID.randomUUID(), "Team");
        Employee deputy = new Employee(UUID.randomUUID(), "Deputy", "Team", "Cashier", LocalDate.now());
        when(teams.findById(team.getId())).thenReturn(Optional.of(team));
        when(employees.findById(deputy.getId())).thenReturn(Optional.of(deputy));
        when(employees.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HrController controllerWithTeams = new HrController(employees, teams, users, records, shifts, access, sessions);
        HrController.EmployeeResponse response = controllerWithTeams.create(new HrController.EmployeeRequest(
                "Employee", "", "Cashier", LocalDate.now(), team.getId(), deputy.getId()));

        assertEquals(response.id(), deputy.getDeputy().getId());
    }

    @Test
    void assigningDeputyDoesNotReplaceTheirExistingDeputy() {
        Team team = new Team(UUID.randomUUID(), "Team");
        Employee deputy = new Employee(UUID.randomUUID(), "Deputy", "Team", "Cashier", LocalDate.now());
        Employee existingDeputy = new Employee(UUID.randomUUID(), "Existing", "Team", "Cashier", LocalDate.now());
        deputy.assignDeputy(existingDeputy);
        when(teams.findById(team.getId())).thenReturn(Optional.of(team));
        when(employees.findById(deputy.getId())).thenReturn(Optional.of(deputy));
        when(employees.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HrController controllerWithTeams = new HrController(employees, teams, users, records, shifts, access, sessions);
        controllerWithTeams.create(new HrController.EmployeeRequest(
                "Employee", "", "Cashier", LocalDate.now(), team.getId(), deputy.getId()));

        assertEquals(existingDeputy.getId(), deputy.getDeputy().getId());
    }

    @Test
    void overviewSeparatesEmployeeRoleFromAccountExistence() {
        Employee withoutAccount = new Employee(UUID.randomUUID(), "Without account", "Team", "Logistika", LocalDate.now());
        Employee withAccount = new Employee(UUID.randomUUID(), "With account", "Team", "Nákupčí", LocalDate.now());
        when(employees.findAllByOrderByEmploymentStartDateDesc()).thenReturn(List.of(withoutAccount, withAccount));
        when(access.canSee(any(Employee.class))).thenReturn(true);
        when(users.existsByEmployee_Id(withoutAccount.getId())).thenReturn(false);
        when(users.existsByEmployee_Id(withAccount.getId())).thenReturn(true);
        when(teams.findAllByOrderByNameAsc()).thenReturn(List.of());

        HrController controllerWithTeams = new HrController(employees, teams, users, records, shifts, access, sessions);
        List<HrController.EmployeeResponse> overview = controllerWithTeams.overview().employees();

        assertFalse(overview.get(0).hasUserAccount());
        assertEquals("Logistika", overview.get(0).userRoleName());
        assertTrue(overview.get(1).hasUserAccount());
        assertEquals("Nákupčí", overview.get(1).userRoleName());
    }

    @Test
    void absenceAndDepartureCannotInvalidateAssignedShift() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        PlanningShift shift = new PlanningShift(UUID.randomUUID(), employee.getFullName(), "Cashier", "Store", start, start.plusHours(8));
        shift.linkEmployee(employee.getId());
        when(shifts.findAllByOrderByStartAtAsc()).thenReturn(List.of(shift));
        when(employees.findById(employee.getId())).thenReturn(Optional.of(employee));
        assertThrows(ResponseStatusException.class, () -> controller.absence(employee.getId(),
                new HrController.AbsenceRequest(start, start.plusHours(2), "Leave")));
        assertThrows(ResponseStatusException.class, () -> controller.deactivate(employee.getId()));
        verify(records, never()).addAbsence(any(), any(), any(), any());
    }

    @Test
    void adjacentAbsenceIsAcceptedButOverlappingAbsenceIsRejected() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        when(employees.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(records.absences(employee.getId())).thenReturn(List.of(new WorkforceRecords.Absence(UUID.randomUUID(), employee.getId(),
                start, start.plusHours(2), "Leave")));
        controller.absence(employee.getId(), new HrController.AbsenceRequest(start.plusHours(2), start.plusHours(4), "Leave"));
        verify(records).addAbsence(employee.getId(), start.plusHours(2), start.plusHours(4), "Leave");
        assertThrows(ResponseStatusException.class, () -> controller.absence(employee.getId(),
                new HrController.AbsenceRequest(start.plusHours(1), start.plusHours(3), "Leave")));
    }

    @Test
    void employeesSeeOnlyOwnPublishedShiftsAndTeamLeadsStayWithinTheirTeam() {
        Employee colleague = new Employee(UUID.randomUUID(), "Colleague", "Team", "Cashier", LocalDate.now());
        Employee outsider = new Employee(UUID.randomUUID(), "Other", "Other team", "Cashier", LocalDate.now());
        when(employees.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(employees.findById(colleague.getId())).thenReturn(Optional.of(colleague));
        when(employees.findById(outsider.getId())).thenReturn(Optional.of(outsider));
        WorkforceAccess policy = spy(new WorkforceAccess(employees));
        ErpUser user = ErpUser.create(employee, "Employee", "Company", UserStatus.ACTIVE, "#ffffff");
        doReturn(user).when(policy).currentUser();
        assertTrue(policy.canSee(employee));
        assertFalse(policy.canSee(colleague));
        PlanningShift own = new PlanningShift(UUID.randomUUID(), "Employee", "Cashier", "Store",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        own.linkEmployee(employee.getId());
        assertFalse(policy.canSee(own));
        own.publish();
        assertTrue(policy.canSee(own));
        own.linkEmployee(colleague.getId());
        assertFalse(policy.canSee(own));
        assertThrows(ResponseStatusException.class, () -> policy.requireAssignment(employee.getId()));

        employee.update("Employee", "Team", "Team lead", LocalDate.now());
        assertTrue(policy.canSee(colleague));
        assertTrue(policy.canSee(own));
        assertFalse(policy.canSee(outsider));
        assertThrows(ResponseStatusException.class, () -> policy.requireAssignment(outsider.getId()));

        employee.update("Employee", "Team", "HR", LocalDate.now());
        assertTrue(policy.canSee(outsider));
        assertDoesNotThrow(() -> policy.requireAssignment(outsider.getId()));
    }
}