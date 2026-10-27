package com.example.erp.frontend.accounting;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AccountingOverviewView(BigDecimal receivables, BigDecimal overdue, long openInvoiceCount,
        List<InvoiceView> invoices) {
        public record InvoiceView(UUID id, long version, String invoiceNumber, String partnerName, String issueDate, String dueDate,
            BigDecimal totalAmount, BigDecimal paidAmount, String status, List<InvoiceLineView> lines) { }

        public record InvoiceLineView(UUID productId, String description, BigDecimal quantity, BigDecimal unitPrice,
            String imageUrl) { }
}