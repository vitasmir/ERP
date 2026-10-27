package com.example.erp.helpdesk;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, UUID> {
    List<HelpdeskTicket> findAllByOrderByDueAtAsc();
}