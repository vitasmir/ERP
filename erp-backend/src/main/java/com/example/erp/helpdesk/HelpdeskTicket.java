package com.example.erp.helpdesk;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "helpdesk_tickets")
public class HelpdeskTicket {
    @Id
    private UUID id;

    @Column(name = "ticket_number")
    private String ticketNumber;

    private String subject;

    @Column(name = "requester_name")
    private String requesterName;

    @Column(name = "assigned_team")
    private String assignedTeam;

    @Enumerated(EnumType.STRING)
    private TicketPriority priority;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    protected HelpdeskTicket() { }

    public UUID getId() { return id; }
    public String getTicketNumber() { return ticketNumber; }
    public String getSubject() { return subject; }
    public String getRequesterName() { return requesterName; }
    public String getAssignedTeam() { return assignedTeam; }
    public TicketPriority getPriority() { return priority; }
    public LocalDateTime getDueAt() { return dueAt; }
    public TicketStatus getStatus() { return status; }

    public void resolve() { status = TicketStatus.RESOLVED; }
}