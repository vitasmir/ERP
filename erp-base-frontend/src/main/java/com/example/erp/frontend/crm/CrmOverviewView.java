package com.example.erp.frontend.crm;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CrmOverviewView(BigDecimal pipeline, BigDecimal forecast, long openLeadCount, List<LeadView> leads) {
    public record LeadView(UUID id, String name, String customerName, BigDecimal expectedRevenue, int probability, String stage, String expectedCloseDate) { }
}