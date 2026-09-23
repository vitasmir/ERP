package com.example.erp.frontend.marketing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MarketingOverviewView(long runningCampaignCount, long plannedCampaignCount, int totalLeadCount,
        BigDecimal totalSpent, List<CampaignView> campaigns) {
    public record CampaignView(UUID id, String name, String audience, String channel, String ownerName,
            BigDecimal budget, BigDecimal spent, int leadCount, String status, String plannedStartDate) { }
}