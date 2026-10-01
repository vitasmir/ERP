package com.example.erp.hr;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.example.erp.planning.PlanningRules;
import com.example.erp.planning.PlanningShift;
import com.example.erp.planning.PlanningShiftRepository;
import com.example.erp.planning.WorkforceRecords;
import com.example.erp.users.ApiAccess;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/hr")
@Transactional
public class HrController {
    private final EmployeeRepository employees;
                private final TeamRepository teams;
        private final com.example.erp.users.UserRepository users;
        private final WorkforceRecords records;
        private final PlanningShiftRepository shifts;
        private final WorkforceAccess access;
        private final ApiAccess sessions;

        @Autowired
        public HrController(EmployeeRepository employees, TeamRepository teams, com.example.erp.users.UserRepository users,
                        WorkforceRecords records, PlanningShiftRepository shifts, WorkforceAccess access, ApiAccess sessions) {
                this.employees = employees;
                this.teams = teams;
                this.users = users;
                this.records = records;
                this.shifts = shifts;
                this.access = access;
                this.sessions = sessions;
        }

        public HrController(EmployeeRepository employees, com.example.erp.users.UserRepository users,
                        WorkforceRecords records, PlanningShiftRepository shifts, WorkforceAccess access, ApiAccess sessions) {
                this(employees, null, users, records, shifts, access, sessions);
        }

    @GetMapping("/overview")
    public HrOverview overview() {
        List<EmployeeResponse> items = employees.findAllByOrderByEmploymentStartDateDesc().stream()
                .filter(access::canSee)
                .map(employee -> EmployeeResponse.from(employee, users.existsByEmployee_Id(employee.getId()))).toList();
        return new HrOverview(items.stream().filter(item -> item.status() == EmployeeStatus.ACTIVE).count(),
                items.stream().filter(item -> item.status() == EmployeeStatus.ONBOARDING).count(),
                items.stream().map(EmployeeResponse::teamName).distinct().count(), items, teams.findAllByOrderByNameAsc().stream()
                        .map(TeamResponse::from).toList());
    }

        @GetMapping("/teams")
        public List<TeamResponse> teamList() {
                return teams.findAllByOrderByNameAsc().stream().map(TeamResponse::from).toList();
        }

        @PostMapping("/teams")
        public TeamResponse createTeam(@Valid @RequestBody TeamRequest request) {
                String name = request.name().trim();
                if (teams.existsByNameIgnoreCase(name)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Team already exists.");
                return TeamResponse.from(teams.save(new Team(UUID.randomUUID(), name)));
        }

        @PutMapping("/teams/{id}")
        public TeamResponse updateTeam(@PathVariable UUID id, @Valid @RequestBody TeamRequest request) {
                Team team = teams.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team was not found."));
                String name = request.name().trim();
                if (teams.existsByNameIgnoreCaseAndIdNot(name, id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Team already exists.");
                team.rename(name);
                return TeamResponse.from(teams.save(team));
        }

        @DeleteMapping("/teams/{id}")
        public void deleteTeam(@PathVariable UUID id) {
                if (!teams.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team was not found.");
                if (employees.existsByTeam_Id(id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Team still has assigned employees.");
                teams.deleteById(id);
        }

        @PostMapping("/employees")
        public EmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
                records.lock();
                Employee employee = new Employee(UUID.randomUUID(), request.fullName(), request.teamName(), request.jobTitle(), request.employmentStartDate());
                Employee savedEmployee = employees.save(employee);
                assign(savedEmployee, request.teamId(), request.deputyEmployeeId());
                return response(employees.save(savedEmployee));
        }

        @PutMapping("/employees/{id}")
        public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody EmployeeRequest request) {
                records.lock();
                Employee employee = find(id);
                if (assignedShifts(employee).stream().anyMatch(shift -> shift.getStartAt().toLocalDate().isBefore(request.employmentStartDate()))) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Employment start conflicts with assigned shifts.");
                }
                employee.update(request.fullName(), request.teamName(), request.jobTitle(), request.employmentStartDate());
                assign(employee, request.teamId(), request.deputyEmployeeId());
                return response(employees.save(employee));
        }

    @PatchMapping("/employees/{id}/activate")
    public EmployeeResponse activate(@PathVariable UUID id) {
                records.lock();
        Employee employee = find(id);
                if (employee.getEmploymentStartDate().isAfter(LocalDate.now())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Employment has not started yet.");
                }
        employee.activate();
                users.findByEmployee_Id(id).ifPresent(user -> {
                        user.activateEmploymentAccount();
                        users.save(user);
                });
        Employee updatedEmployee = employees.save(employee);
        return employeeResponse(updatedEmployee);
    }

        @PatchMapping("/employees/{id}/deactivate")
        public EmployeeResponse deactivate(@PathVariable UUID id) {
                records.lock();
                Employee employee = find(id);
                if (employees.existsByDeputy_Id(id)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reassign employees that depend on this deputy first.");
                }
                if (assignedShifts(employee).stream().anyMatch(shift -> shift.getEndAt().isAfter(java.time.LocalDateTime.now()))) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reassign future shifts before ending employment.");
                }
                employee.deactivate();
                users.findByEmployee_Id(id).ifPresent(user -> {
                        user.suspendEmploymentAccount();
                        users.save(user);
                        sessions.revokeUser(user.getId());
                });
                return response(employees.save(employee));
        }

        @GetMapping("/employees/{id}/availability")
        public Availability availability(@PathVariable UUID id) {
                access.requireEmployee(id);
                Employee employee = find(id);
                java.util.Set<String> roles = new java.util.TreeSet<>(records.qualifications(id));
                roles.add(employee.getJobTitle());
                return new Availability(List.copyOf(roles), records.absences(id));
        }

        @PostMapping("/employees/{id}/qualifications")
        public void qualify(@PathVariable UUID id, @Valid @RequestBody QualificationRequest request) {
                records.lock();
                find(id);
                records.qualify(id, request.roleName());
        }

        @PostMapping("/employees/{id}/absences")
        public WorkforceRecords.Absence absence(@PathVariable UUID id, @Valid @RequestBody AbsenceRequest request) {
                records.lock();
                Employee employee = find(id);
                PlanningRules.validateTimes(request.startAt(), request.endAt());
                if (assignedShifts(employee).stream().anyMatch(shift -> PlanningRules.overlaps(request.startAt(), request.endAt(), shift.getStartAt(), shift.getEndAt()))) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reassign overlapping shifts before recording absence.");
                }
                if (records.absences(id).stream().anyMatch(absence -> PlanningRules.overlaps(request.startAt(), request.endAt(), absence.startAt(), absence.endAt()))) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Absence already covers this interval.");
                }
                return records.addAbsence(id, request.startAt(), request.endAt(), request.reason());
        }

        @DeleteMapping("/employees/{id}/absences/{absenceId}")
        public void removeAbsence(@PathVariable UUID id, @PathVariable UUID absenceId) {
                records.lock();
                find(id);
                if (!records.removeAbsence(id, absenceId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        private List<PlanningShift> assignedShifts(Employee employee) {
                return shifts.findAllByOrderByStartAtAsc().stream().filter(shift -> employee.getId().equals(shift.getEmployeeId())
                        || shift.getEmployeeId() == null && employee.getFullName().equals(shift.getEmployeeName())).toList();
        }

        public record Availability(List<String> qualifications, List<WorkforceRecords.Absence> absences) { }
        public record QualificationRequest(@NotBlank @Size(max = 150) String roleName) { }
        public record AbsenceRequest(@NotNull java.time.LocalDateTime startAt, @NotNull java.time.LocalDateTime endAt,
                        @NotBlank @Size(max = 240) String reason) { }

        private Employee find(UUID id) { return employees.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee was not found.")); }
        private void assign(Employee employee, UUID teamId, UUID deputyId) {
                Team team = teams.findById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team is required."));
                if (deputyId == null) {
                        employee.assign(team, null);
                        return;
                }
                if (employee.getId().equals(deputyId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee cannot be their own deputy.");
                Employee deputy = employees.findById(deputyId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deputy is required."));
                if (deputy.getStatus() == EmployeeStatus.INACTIVE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deputy must be active or onboarding.");
                if (!ApiAccess.normalize(employee.getJobTitle()).equals(ApiAccess.normalize(deputy.getJobTitle()))) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee and deputy must have the same role.");
                }
                employee.assign(team, deputy);
                if (deputy.getDeputy() == null) deputy.assignDeputy(employee);
        }
        private EmployeeResponse response(Employee employee) {
                return employeeResponse(employee);
        }

        private EmployeeResponse employeeResponse(Employee employee) {
                return EmployeeResponse.from(employee, users.existsByEmployee_Id(employee.getId()));
        }

                public record EmployeeRequest(@NotBlank @Size(max = 200) String fullName, @Size(max = 150) String teamName,
                        @NotBlank @Size(max = 150) String jobTitle,
                                                @NotNull LocalDate employmentStartDate, @NotNull UUID teamId, UUID deputyEmployeeId) { }

    public record HrOverview(long activeEmployeeCount, long onboardingCount, long teamCount,
                        List<EmployeeResponse> employees, List<TeamResponse> teams) { }

        public record TeamRequest(@NotBlank @Size(max = 150) String name) { }
        public record TeamResponse(UUID id, String name) {
                static TeamResponse from(Team team) { return new TeamResponse(team.getId(), team.getName()); }
        }

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
        public record EmployeeResponse(UUID id, String fullName, String teamName, UUID teamId, String jobTitle,
                                                LocalDate employmentStartDate, EmployeeStatus status, boolean hasUserAccount,
                                                String userRoleName,
                                                UUID deputyEmployeeId, String deputyName) {
                static EmployeeResponse from(Employee employee, boolean hasUserAccount) {
                        return new EmployeeResponse(employee.getId(), employee.getFullName(), employee.getTeamName(),
                                                                                employee.getTeam() == null ? null : employee.getTeam().getId(), employee.getJobTitle(),
                                                                                employee.getEmploymentStartDate(), employee.getStatus(), hasUserAccount,
                                                                                employee.getJobTitle(),
                                                                                employee.getDeputy() == null ? null : employee.getDeputy().getId(),
                                                                                employee.getDeputy() == null ? "" : employee.getDeputy().getFullName());
        }
    }
}