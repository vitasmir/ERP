package com.example.erp.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class AccountInvoiceTests {
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