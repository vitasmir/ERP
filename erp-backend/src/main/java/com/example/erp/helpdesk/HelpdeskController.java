package com.example.erp.helpdesk;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/helpdesk")
public class HelpdeskController {
    private final HelpdeskTicketRepository tickets;

    public HelpdeskController(HelpdeskTicketRepository tickets) { this.tickets = tickets; }

    @GetMapping("/overview")
    public HelpdeskOverview overview() {
        List<TicketResponse> items = tickets.findAllByOrderByDueAtAsc().stream().map(TicketResponse::from).toList();
        return new HelpdeskOverview(items.stream().filter(item -> item.status() != TicketStatus.RESOLVED).count(),
                items.stream().filter(item -> item.status() != TicketStatus.RESOLVED)
                        .filter(item -> item.priority() == TicketPriority.HIGH).count(),
                items.stream().filter(item -> item.status() != TicketStatus.RESOLVED)
                        .filter(item -> !item.dueAt().isAfter(LocalDateTime.now().plusHours(8))).count(), items);
    }

    @PatchMapping("/tickets/{id}/resolve")
    public TicketResponse resolve(@PathVariable UUID id) {
        HelpdeskTicket ticket = tickets.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Helpdesk ticket was not found."));
        ticket.resolve();
        return TicketResponse.from(tickets.save(ticket));
    }

    public record HelpdeskOverview(long openTicketCount, long highPriorityCount, long dueSoonCount,
            List<TicketResponse> tickets) { }

    public record TicketResponse(UUID id, String ticketNumber, String subject, String requesterName,
            String assignedTeam, TicketPriority priority, LocalDateTime dueAt, TicketStatus status) {
        static TicketResponse from(HelpdeskTicket ticket) {
            return new TicketResponse(ticket.getId(), ticket.getTicketNumber(), ticket.getSubject(),
                    ticket.getRequesterName(), ticket.getAssignedTeam(), ticket.getPriority(), ticket.getDueAt(),
                    ticket.getStatus());
        }
    }
}