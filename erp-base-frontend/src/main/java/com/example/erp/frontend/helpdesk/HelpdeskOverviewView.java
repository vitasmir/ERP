package com.example.erp.frontend.helpdesk;

import java.util.List;
import java.util.UUID;

public record HelpdeskOverviewView(long openTicketCount, long highPriorityCount, long dueSoonCount,
        List<TicketView> tickets) {
    public record TicketView(UUID id, String ticketNumber, String subject, String requesterName,
            String assignedTeam, String priority, String dueAt, String status) { }
}