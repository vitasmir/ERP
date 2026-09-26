package com.example.erp.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/accounting")
public class AccountingController {
    private final AccountInvoiceRepository invoices;

    public AccountingController(AccountInvoiceRepository invoices) { this.invoices = invoices; }

    @GetMapping("/overview")
    public AccountingOverview overview() {
        List<InvoiceResponse> items = invoices.findAllByOrderByDueDateAsc().stream().map(InvoiceResponse::from).toList();
        BigDecimal receivables = items.stream().filter(item -> item.status() != InvoiceStatus.PAID)
                .map(item -> item.totalAmount().subtract(item.paidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = items.stream().filter(item -> item.status() == InvoiceStatus.OVERDUE)
                .map(item -> item.totalAmount().subtract(item.paidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AccountingOverview(receivables, overdue, items.stream().filter(item -> item.status() == InvoiceStatus.OPEN).count(), items);
    }

        @PostMapping("/invoices")
        public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
                if (request.dueDate().isBefore(request.issueDate())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date must not precede issue date.");
                }
                return InvoiceResponse.from(invoices.save(new AccountInvoice(UUID.randomUUID(), request.invoiceNumber(),
                                request.partnerName(), request.issueDate(), request.dueDate(), request.totalAmount())));
        }

    @PatchMapping("/invoices/{id}/paid")
    public InvoiceResponse markPaid(@PathVariable UUID id) {
        AccountInvoice invoice = invoices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice was not found."));
        invoice.markPaid();
        return InvoiceResponse.from(invoices.save(invoice));
    }

        @PatchMapping("/invoices/{id}/payment")
        public InvoiceResponse registerPayment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
                AccountInvoice invoice = invoices.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice was not found."));
                try {
                        invoice.registerPayment(request.amount());
                } catch (IllegalArgumentException exception) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
                }
                return InvoiceResponse.from(invoices.save(invoice));
        }

        public record CreateInvoiceRequest(@NotBlank String invoiceNumber, @NotBlank String partnerName,
                        @NotNull LocalDate issueDate, @NotNull @FutureOrPresent LocalDate dueDate,
                        @NotNull @DecimalMin("0.01") BigDecimal totalAmount) { }

        public record PaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount) { }

    public record AccountingOverview(BigDecimal receivables, BigDecimal overdue, long openInvoiceCount,
            List<InvoiceResponse> invoices) { }

    public record InvoiceResponse(UUID id, String invoiceNumber, String partnerName, LocalDate issueDate,
            LocalDate dueDate, BigDecimal totalAmount, BigDecimal paidAmount, InvoiceStatus status) {
        static InvoiceResponse from(AccountInvoice invoice) {
            return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getPartnerName(),
                    invoice.getIssueDate(), invoice.getDueDate(), invoice.getTotalAmount(), invoice.getPaidAmount(),
                    invoice.getStatus());
        }
    }
}