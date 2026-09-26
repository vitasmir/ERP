package com.example.erp.hr;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.planning.PlanningShift;
import com.example.erp.planning.PlanningShiftStatus;
import com.example.erp.users.ApiAccess;
import com.example.erp.users.ErpUser;

@Component
public class WorkforceAccess {
    private final EmployeeRepository employees;

    public WorkforceAccess(EmployeeRepository employees) { this.employees = employees; }

    public ErpUser currentUser() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object user = attributes == null ? null : attributes.getAttribute("erpUser", RequestAttributes.SCOPE_REQUEST);
        if (user instanceof ErpUser current) return current;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    public boolean managesAll() {
        String role = currentUser().getRoleName();
        return ApiAccess.isAdmin(role) || Set.of("hr", "personalista", "planner", "planovac").contains(ApiAccess.normalize(role));
    }

    public boolean isTeamLead() {
        return Set.of("team lead", "vedouci tymu").contains(ApiAccess.normalize(currentUser().getRoleName()));
    }

    public boolean canSee(Employee employee) {
        if (managesAll()) return true;
        if (employee.getId().equals(currentUser().getEmployeeId())) return true;
        return isTeamLead() && employees.findById(currentUser().getEmployeeId())
                .map(leader -> leader.getTeamName().equals(employee.getTeamName())).orElse(false);
    }

    public void requireEmployee(UUID employeeId) {
        Employee employee = employees.findById(employeeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!canSee(employee)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public boolean canSee(PlanningShift shift) {
        if (managesAll()) return true;
        if (!isTeamLead() && shift.getStatus() != PlanningShiftStatus.PUBLISHED) return false;
        return shift.getEmployeeId() != null && employees.findById(shift.getEmployeeId()).map(this::canSee).orElse(false);
    }

    public void requireShift(PlanningShift shift) {
        if (!canSee(shift)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public void requireAssignment(UUID employeeId) {
        if (managesAll()) return;
        if (!isTeamLead() || employeeId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        requireEmployee(employeeId);
    }

    public String actor() { return currentUser().getUsername(); }
}