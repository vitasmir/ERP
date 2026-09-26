package com.example.erp.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Version;
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

    @Version
    private long version;

    @Column(name = "sales_order_id")
    private UUID salesOrderId;

    @ElementCollection
    @CollectionTable(name = "invoice_lines", joinColumns = @JoinColumn(name = "invoice_id"))
    @OrderColumn(name = "line_index")
    private List<InvoiceLine> lines = new ArrayList<>();

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
    public long getVersion() { return version; }
    public UUID getSalesOrderId() { return salesOrderId; }
    public List<InvoiceLine> getLines() { return List.copyOf(lines); }

    public void linkSalesOrder(UUID salesOrderId) { this.salesOrderId = salesOrderId; }

    public void updateDraft(String invoiceNumber, String partnerName, LocalDate issueDate,
            LocalDate dueDate, List<InvoiceLine> items) {
        if (status != InvoiceStatus.DRAFT) throw new IllegalArgumentException("Only drafts can be edited.");
        if (dueDate.isBefore(issueDate)) throw new IllegalArgumentException("Due date must not precede issue date.");
        if (items.isEmpty()) throw new IllegalArgumentException("At least one invoice line is required.");
        BigDecimal total = items.stream().map(InvoiceLine::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0 || total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Invoice total must be positive and fit the supported currency range.");
        }
        this.invoiceNumber = invoiceNumber;
        this.partnerName = partnerName;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.lines.clear();
        this.lines.addAll(items);
        this.totalAmount = total;
    }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getPartnerName() { return partnerName; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public InvoiceStatus getStatus() { return statusOn(LocalDate.now()); }

    public InvoiceStatus statusOn(LocalDate date) {
        if (status == InvoiceStatus.DRAFT || status == InvoiceStatus.CANCELLED) return status;
        if (paidAmount.compareTo(totalAmount) >= 0) return InvoiceStatus.PAID;
        if (dueDate.isBefore(date)) return InvoiceStatus.OVERDUE;
        return paidAmount.signum() > 0 ? InvoiceStatus.PARTIALLY_PAID : InvoiceStatus.OPEN;
    }

    public BigDecimal outstandingAmount() {
        return status == InvoiceStatus.DRAFT || status == InvoiceStatus.CANCELLED
                ? BigDecimal.ZERO : totalAmount.subtract(paidAmount);
    }

    public void saveAsDraft() {
        if (paidAmount.signum() != 0) throw new IllegalArgumentException("Paid invoices cannot become drafts.");
        status = InvoiceStatus.DRAFT;
    }

    public void issue() {
        if (status != InvoiceStatus.DRAFT) throw new IllegalArgumentException("Only drafts can be issued.");
        status = InvoiceStatus.OPEN;
    }

    public void cancel() {
        if (paidAmount.signum() != 0) throw new IllegalArgumentException("Refund payments before cancellation.");
        status = InvoiceStatus.CANCELLED;
    }

    public void markPaid() {
        registerPayment(totalAmount.subtract(paidAmount));
    }

    public void registerPayment(BigDecimal amount) {
        if (status == InvoiceStatus.DRAFT || status == InvoiceStatus.CANCELLED) {
            throw new IllegalArgumentException("Only issued invoices accept payments.");
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(totalAmount.subtract(paidAmount)) > 0) {
            throw new IllegalArgumentException("Payment must be positive and not exceed the outstanding amount.");
        }
        paidAmount = paidAmount.add(amount);
        status = paidAmount.compareTo(totalAmount) == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
    }
}