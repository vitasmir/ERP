package com.example.erp.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AccountInvoiceTests {
    @Test
    void pdfContainsUnicodeCustomerAndInvoiceTotals() throws Exception {
        AccountInvoice invoice = invoice();
        invoice.saveAsDraft();
        invoice.updateDraft("PDF-001", "\u010cesk\u00fd odb\u011bratel", LocalDate.now(), LocalDate.now(),
                java.util.List.of(new InvoiceLine("Consulting", BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("21"))));
        try (var document = org.apache.pdfbox.Loader.loadPDF(InvoicePdf.render(invoice))) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("\u010cesk\u00fd odb\u011bratel"));
            assertTrue(text.contains("121.00 CZK"));
        }
    }

    @Test
    void paymentIsRecordedAndOverpaymentDoesNotWriteLedger() {
        AccountInvoice invoice = invoice();
        AccountInvoiceRepository repository = org.mockito.Mockito.mock(AccountInvoiceRepository.class);
        InvoiceRecords records = org.mockito.Mockito.mock(InvoiceRecords.class);
        org.mockito.Mockito.when(repository.findById(invoice.getId())).thenReturn(java.util.Optional.of(invoice));
        AccountingController controller = new AccountingController(repository, records, null);
        controller.registerPayment(invoice.getId(), new AccountingController.PaymentRequest(new BigDecimal("40.00"),
                LocalDate.of(2026, 1, 20), "BANK-001"));
        org.mockito.Mockito.verify(records).payment(invoice.getId(), new BigDecimal("40.00"), LocalDate.of(2026, 1, 20), "BANK-001");
        assertThrows(IllegalArgumentException.class, () -> controller.registerPayment(invoice.getId(),
                new AccountingController.PaymentRequest(new BigDecimal("61.00"), LocalDate.of(2026, 1, 20), "BANK-002")));
        org.mockito.Mockito.verify(records, org.mockito.Mockito.times(1)).payment(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> controller.delete(invoice.getId()));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void csvEscapesQuotesAndFormulaCells() {
        assertEquals("\"Customer, \"\"A\"\"\"", AccountingController.csvCell("Customer, \"A\""));
        assertEquals("\"'=SUM(A1)\"", AccountingController.csvCell("=SUM(A1)"));
        assertEquals("\"'  +1\"", AccountingController.csvCell("  +1"));
    }

    @Test
    void overviewExcludesDraftsAndCancelledInvoicesFromReceivables() {
        AccountInvoice draft = invoice();
        draft.saveAsDraft();
        AccountInvoice cancelled = invoice();
        cancelled.cancel();
        AccountInvoice partial = invoice();
        partial.registerPayment(new BigDecimal("40.00"));
        AccountInvoiceRepository repository = org.mockito.Mockito.mock(AccountInvoiceRepository.class);
        org.mockito.Mockito.when(repository.findAllByOrderByDueDateAsc())
                .thenReturn(java.util.List.of(draft, cancelled, partial));

        AccountingController.AccountingOverview overview = new AccountingController(repository, null, null).overview();

        assertEquals(new BigDecimal("60.00"), overview.receivables());
        assertEquals(1, overview.openInvoiceCount());
    }

    @Test
    void invoiceLinesRoundTaxAndCannotChangeIssuedDocuments() {
        InvoiceLine line = new InvoiceLine("Service", new BigDecimal("2.5"), new BigDecimal("100"), new BigDecimal("21"));
        assertEquals(new BigDecimal("250.00"), line.getNetAmount());
        assertEquals(new BigDecimal("52.50"), line.getVatAmount());
        AccountInvoice invoice = invoice();
        invoice.saveAsDraft();
        invoice.updateDraft("NEW", "Customer", LocalDate.now(), LocalDate.now(), java.util.List.of(line));
        assertEquals(new BigDecimal("302.50"), invoice.getTotalAmount());
        invoice.issue();
        assertThrows(IllegalArgumentException.class, () -> invoice.updateDraft("NEW", "Customer", LocalDate.now(),
                LocalDate.now(), java.util.List.of(line)));
    }

    private AccountInvoice invoice() {
        return new AccountInvoice(UUID.randomUUID(), "TEST", "Customer", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), new BigDecimal("100.00"));
    }

    @Test
    void partialPaymentPreservesOverdueStatusAndFullPaymentClosesInvoice() {
        AccountInvoice invoice = invoice();
        invoice.registerPayment(new BigDecimal("40.00"));
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.statusOn(LocalDate.of(2026, 1, 31)));
        assertEquals(InvoiceStatus.OVERDUE, invoice.statusOn(LocalDate.of(2026, 2, 1)));
        assertEquals(new BigDecimal("60.00"), invoice.outstandingAmount());
        assertThrows(IllegalArgumentException.class, () -> invoice.registerPayment(new BigDecimal("61")));
        invoice.markPaid();
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void draftsAndCancelledInvoicesAreNotReceivablesAndRejectPayments() {
        AccountInvoice invoice = invoice();
        invoice.saveAsDraft();
        assertEquals(BigDecimal.ZERO, invoice.outstandingAmount());
        assertThrows(IllegalArgumentException.class, () -> invoice.registerPayment(BigDecimal.ONE));
        invoice.issue();
        assertEquals(new BigDecimal("100.00"), invoice.outstandingAmount());
        invoice.cancel();
        assertEquals(InvoiceStatus.CANCELLED, invoice.getStatus());
        assertEquals(BigDecimal.ZERO, invoice.outstandingAmount());
        assertThrows(IllegalArgumentException.class, () -> invoice.markPaid());
    }
}