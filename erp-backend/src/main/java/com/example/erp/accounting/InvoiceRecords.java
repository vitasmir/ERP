package com.example.erp.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceRecords {
    private final JdbcTemplate jdbc;

    public InvoiceRecords(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void payment(UUID invoiceId, BigDecimal amount, LocalDate paidOn, String reference) {
        jdbc.update("INSERT INTO invoice_payments (id, invoice_id, amount, paid_on, reference) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), invoiceId, amount, paidOn, reference);
    }

    public List<Payment> payments(UUID invoiceId) {
        return jdbc.query("SELECT * FROM invoice_payments WHERE invoice_id = ? ORDER BY created_at, id",
                (row, index) -> new Payment(row.getObject("id", UUID.class), row.getBigDecimal("amount"),
                        row.getObject("paid_on", LocalDate.class), row.getString("reference")), invoiceId);
    }

    public void event(UUID invoiceId, String action, String details) {
        org.springframework.web.context.request.RequestAttributes attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        Object user = attributes == null ? null : attributes.getAttribute("erpUser", org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        String actor = user instanceof com.example.erp.users.ErpUser current ? current.getUsername() : "system";
        jdbc.update("INSERT INTO invoice_events (id, invoice_id, action, details, actor) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), invoiceId, action, details, actor);
    }

    public List<Event> events(UUID invoiceId) {
        return jdbc.query("SELECT * FROM invoice_events WHERE invoice_id = ? ORDER BY occurred_at, id",
                (row, index) -> new Event(row.getString("action"), row.getString("details"),
                        row.getObject("occurred_at", LocalDateTime.class), row.getString("actor")), invoiceId);
    }

    public UUID attach(UUID invoiceId, String filename, String contentType, byte[] content) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO invoice_attachments (id, invoice_id, filename, content_type, content) VALUES (?, ?, ?, ?, ?)",
                id, invoiceId, filename, contentType, content);
        return id;
    }

    public List<Attachment> attachments(UUID invoiceId) {
        return jdbc.query("SELECT id, filename, content_type, octet_length(content) AS size FROM invoice_attachments WHERE invoice_id = ? ORDER BY created_at, id",
                (row, index) -> new Attachment(row.getObject("id", UUID.class), row.getString("filename"),
                        row.getString("content_type"), row.getLong("size")), invoiceId);
    }

    public byte[] content(UUID invoiceId, UUID attachmentId) {
        return jdbc.query("SELECT content FROM invoice_attachments WHERE invoice_id = ? AND id = ?",
                (row, index) -> row.getBytes("content"), invoiceId, attachmentId).stream().findFirst().orElse(null);
    }

    public boolean removeAttachment(UUID invoiceId, UUID attachmentId) {
        return jdbc.update("DELETE FROM invoice_attachments WHERE invoice_id = ? AND id = ?", invoiceId, attachmentId) == 1;
    }

    public void deleteDraft(UUID invoiceId) {
        jdbc.update("DELETE FROM invoice_attachments WHERE invoice_id = ?", invoiceId);
        jdbc.update("DELETE FROM invoice_events WHERE invoice_id = ?", invoiceId);
    }

    public record Payment(UUID id, BigDecimal amount, LocalDate paidOn, String reference) { }
    public record Event(String action, String details, LocalDateTime occurredAt, String actor) { }
    public record Attachment(UUID id, String filename, String contentType, long size) { }
}