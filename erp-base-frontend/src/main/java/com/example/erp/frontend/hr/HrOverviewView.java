package com.example.erp.frontend.hr;

import java.util.List;
import java.util.UUID;

public record HrOverviewView(long activeEmployeeCount, long onboardingCount, long teamCount,
        List<EmployeeView> employees, List<TeamView> teams) {
    public record EmployeeView(UUID id, String fullName, String teamName, UUID teamId, String jobTitle,
            String employmentStartDate, String status, boolean hasUserAccount, String userRoleName,
            UUID deputyEmployeeId, String deputyName) { }
    public record TeamView(UUID id, String name) { }
}