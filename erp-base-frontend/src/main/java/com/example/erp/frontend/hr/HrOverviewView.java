package com.example.erp.frontend.hr;

import java.util.List;
import java.util.UUID;

public record HrOverviewView(long activeEmployeeCount, long onboardingCount, long teamCount,
        List<EmployeeView> employees) {
    public record EmployeeView(UUID id, String fullName, String teamName, String jobTitle,
            String employmentStartDate, String status, boolean hasUserAccount) { }
}