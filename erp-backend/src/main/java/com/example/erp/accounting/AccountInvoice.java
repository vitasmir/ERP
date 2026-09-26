package com.example.erp.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_invoices")
public class AccountInvoice {
    @Id
    private UUID id;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "partner_name")
    private String partnerName;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    protected AccountInvoice() { }

    public AccountInvoice(UUID id, String invoiceNumber, String partnerName, LocalDate issueDate,
            LocalDate dueDate, BigDecimal totalAmount) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.partnerName = partnerName;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.totalAmount = totalAmount;
        this.paidAmount = BigDecimal.ZERO;
        this.status = InvoiceStatus.OPEN;
    }

    public UUID getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getPartnerName() { return partnerName; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public InvoiceStatus getStatus() { return status; }

    public void markPaid() {
        this.paidAmount = totalAmount;
        this.status = InvoiceStatus.PAID;
    }

    public void registerPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(totalAmount.subtract(paidAmount)) > 0) {
            throw new IllegalArgumentException("Payment must be positive and not exceed the outstanding amount.");
        }
        paidAmount = paidAmount.add(amount);
        status = paidAmount.compareTo(totalAmount) == 0 ? InvoiceStatus.PAID : InvoiceStatus.OPEN;
    }
}