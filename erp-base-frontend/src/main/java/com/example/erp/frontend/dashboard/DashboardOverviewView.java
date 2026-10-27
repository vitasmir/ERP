package com.example.erp.frontend.dashboard;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record DashboardOverviewView(BigDecimal receivables, BigDecimal overdue, BigDecimal pipeline,
        BigDecimal forecast, long activeCampaignCount, List<InvoiceItem> invoices, List<LeadItem> leads,
        List<CampaignItem> campaigns) {
    public record InvoiceItem(UUID id, String invoiceNumber, String partnerName, String dueDate,
            BigDecimal outstandingAmount, String status) { }

    public record LeadItem(UUID id, String name, String customerName, BigDecimal expectedRevenue,
            int probability, String expectedCloseDate, String stage) { }

    public record CampaignItem(UUID id, String name, String startsOn, String endsOn, String status) { }
}