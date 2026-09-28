package com.example.erp.accounting;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.sales.SalesOrder;
import com.example.erp.sales.SalesOrderRepository;
import com.example.erp.sales.SalesOrderStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/accounting")
@Transactional
public class AccountingController {
    private final AccountInvoiceRepository invoices;
        private final InvoiceRecords records;
        private final SalesOrderRepository orders;
        private final InvoicePdfService pdfs;

        public AccountingController(AccountInvoiceRepository invoices, InvoiceRecords records, SalesOrderRepository orders) {
                this(invoices, records, orders, null);
        }

        @Autowired
        public AccountingController(AccountInvoiceRepository invoices, InvoiceRecords records, SalesOrderRepository orders,
                InvoicePdfService pdfs) {
                this.invoices = invoices;
                this.records = records;
                this.orders = orders;
                this.pdfs = pdfs;
        }

    @GetMapping("/overview")
    public AccountingOverview overview() {
        List<AccountInvoice> records = invoices.findAllByOrderByDueDateAsc();
        List<InvoiceResponse> items = records.stream().map(InvoiceResponse::from).toList();
        BigDecimal receivables = records.stream().map(AccountInvoice::outstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = items.stream().filter(item -> item.status() == InvoiceStatus.OVERDUE)
                .map(item -> item.totalAmount().subtract(item.paidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AccountingOverview(receivables, overdue,
                records.stream().filter(item -> item.outstandingAmount().signum() > 0).count(), items);
    }

        @PostMapping("/invoices")
        @ResponseStatus(HttpStatus.CREATED)
        public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
                if (invoices.existsByInvoiceNumber(request.invoiceNumber())) conflict("Invoice number already exists.");
                AccountInvoice invoice = new AccountInvoice(UUID.randomUUID(), request.invoiceNumber(), request.partnerName(),
                                request.issueDate(), request.dueDate(), BigDecimal.ZERO);
                invoice.saveAsDraft();
                invoice.updateDraft(request.invoiceNumber(), request.partnerName(), request.issueDate(), request.dueDate(), request.lines());
                invoices.saveAndFlush(invoice);
                records.event(invoice.getId(), "CREATED", "Draft created");
                return InvoiceResponse.from(invoice);
        }

        @GetMapping("/invoices/{id}")
        public InvoiceDetail detail(@PathVariable UUID id) {
                AccountInvoice invoice = find(id);
                return new InvoiceDetail(InvoiceResponse.from(invoice), invoice.getVersion(), invoice.getSalesOrderId(),
                                invoice.getLines(), records.payments(id), records.events(id), records.attachments(id));
        }

        @PutMapping("/invoices/{id}")
        public InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request) {
                AccountInvoice invoice = find(id);
                if (invoice.getVersion() != request.version()) conflict("Invoice has changed. Reload before editing.");
                if (invoices.existsByInvoiceNumberAndIdNot(request.invoice().invoiceNumber(), id)) conflict("Invoice number already exists.");
                CreateInvoiceRequest draft = request.invoice();
                invoice.updateDraft(draft.invoiceNumber(), draft.partnerName(), draft.issueDate(), draft.dueDate(), draft.lines());
                invoices.saveAndFlush(invoice);
                records.event(id, "UPDATED", "Draft updated");
                return InvoiceResponse.from(invoice);
        }

        @DeleteMapping("/invoices/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete(@PathVariable UUID id) {
                AccountInvoice invoice = find(id);
                if (invoice.getStatus() != InvoiceStatus.DRAFT) conflict("Only drafts can be deleted. Cancel issued invoices instead.");
                records.deleteDraft(id);
                invoices.delete(invoice);
        }

        @PatchMapping("/invoices/{id}/issue")
        public InvoiceResponse issue(@PathVariable UUID id) {
                AccountInvoice invoice = find(id);
                invoice.issue();
                records.event(id, "ISSUED", "Invoice issued");
                return InvoiceResponse.from(invoices.save(invoice));
        }

        @PatchMapping("/invoices/{id}/cancel")
        public InvoiceResponse cancel(@PathVariable UUID id) {
                AccountInvoice invoice = find(id);
                invoice.cancel();
                records.event(id, "CANCELLED", "Invoice cancelled");
                return InvoiceResponse.from(invoices.save(invoice));
        }

        @PostMapping("/orders/{orderId}/invoice")
        public InvoiceResponse fromOrder(@PathVariable UUID orderId, @Valid @RequestBody OrderInvoiceRequest request) {
                SalesOrder order = orders.findById(orderId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order was not found."));
                if (order.getStatus() != SalesOrderStatus.CONFIRMED) conflict("Only confirmed orders can be invoiced.");
                if (invoices.findBySalesOrderId(orderId).isPresent()) conflict("Order already has an invoice.");
                InvoiceResponse created = create(new CreateInvoiceRequest(request.invoiceNumber(), order.getCustomerName(),
                                request.issueDate(), request.dueDate(), List.of(new InvoiceLine("Order " + order.getOrderNumber(),
                                                BigDecimal.ONE, order.getTotalAmount(), BigDecimal.ZERO))));
                AccountInvoice invoice = find(created.id());
                invoice.linkSalesOrder(orderId);
                invoice.issue();
                records.event(invoice.getId(), "ISSUED_FROM_ORDER", order.getOrderNumber());
                return InvoiceResponse.from(invoices.save(invoice));
        }

    @PatchMapping("/invoices/{id}/paid")
    public InvoiceResponse markPaid(@PathVariable UUID id) {
        AccountInvoice invoice = find(id);
        return registerPayment(id, new PaymentRequest(invoice.outstandingAmount(), LocalDate.now(), "Manual settlement"));
    }

    @PatchMapping("/invoices/{id}/payment")
    public InvoiceResponse registerPayment(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        AccountInvoice invoice = find(id);
        invoice.registerPayment(request.amount());
        invoices.saveAndFlush(invoice);
        records.payment(id, request.amount(), request.paidOn(), request.reference());
        records.event(id, "PAYMENT", request.amount().toPlainString() + " / " + request.reference());
        if (pdfs != null) pdfs.createWhenPaid(invoice);
        return InvoiceResponse.from(invoice);
    }

    private AccountInvoice find(UUID id) {
        return invoices.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice was not found."));
    }

        @PostMapping(value = "/invoices/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @ResponseStatus(HttpStatus.CREATED)
        public UUID attach(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
                AccountInvoice invoice = find(id);
                if (invoice.getStatus() == InvoiceStatus.CANCELLED) conflict("Cancelled invoices cannot receive attachments.");
                if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment must contain 1 byte to 5 MiB.");
                }
                String filename = file.getOriginalFilename();
                if (filename == null || filename.isBlank() || filename.length() > 240
                                || filename.chars().anyMatch(character -> character < 32) || filename.contains("/") || filename.contains("\\")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename.");
                }
                UUID attachmentId = records.attach(id, filename, MediaType.APPLICATION_OCTET_STREAM_VALUE, file.getBytes());
                records.event(id, "ATTACHMENT_ADDED", filename);
                return attachmentId;
        }

        @GetMapping("/invoices/{id}/attachments/{attachmentId}")
        public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID attachmentId) {
                find(id);
                InvoiceRecords.Attachment attachment = records.attachments(id).stream().filter(item -> item.id().equals(attachmentId))
                                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment was not found."));
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .header("X-Content-Type-Options", "nosniff")
                                .header("Content-Disposition", ContentDisposition.attachment().filename(attachment.filename(), StandardCharsets.UTF_8).build().toString())
                                .body(records.content(id, attachmentId));
        }

        @DeleteMapping("/invoices/{id}/attachments/{attachmentId}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void removeAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
                if (find(id).getStatus() != InvoiceStatus.DRAFT) conflict("Only draft attachments can be removed.");
                if (!records.removeAttachment(id, attachmentId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment was not found.");
                records.event(id, "ATTACHMENT_REMOVED", attachmentId.toString());
        }

        @GetMapping(value = "/invoices.csv", produces = "text/csv")
        public ResponseEntity<byte[]> csv() {
                StringBuilder csv = new StringBuilder("\uFEFFInvoice,Customer,Issued,Due,Total,Paid,Outstanding,Status\r\n");
                for (AccountInvoice invoice : invoices.findAllByOrderByDueDateAsc()) {
                        csv.append(csvCell(invoice.getInvoiceNumber())).append(',').append(csvCell(invoice.getPartnerName())).append(',')
                                        .append(invoice.getIssueDate()).append(',').append(invoice.getDueDate()).append(',')
                                        .append(invoice.getTotalAmount()).append(',').append(invoice.getPaidAmount()).append(',')
                                        .append(invoice.outstandingAmount()).append(',').append(invoice.getStatus()).append("\r\n");
                }
                return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=invoices.csv")
                                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(csv.toString().getBytes(StandardCharsets.UTF_8));
        }

        static String csvCell(String value) {
                String safe = value.stripLeading();
                if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) value = "'" + value;
                return "\"" + value.replace("\"", "\"\"") + "\"";
        }

    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }

    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ProblemDetail invalid(IllegalArgumentException exception) {
        return org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

        @GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
        public ResponseEntity<byte[]> pdf(@PathVariable UUID id) throws IOException {
                AccountInvoice invoice = find(id);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                                .header("Content-Disposition", ContentDisposition.attachment().filename("invoice-" + id + ".pdf").build().toString())
                                .body(InvoicePdf.render(invoice));
        }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public org.springframework.http.ProblemDetail concurrentChange(RuntimeException exception) {
        return org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Invoice changed or its number is already used.");
    }

    public record CreateInvoiceRequest(@NotBlank @Size(max = 40) String invoiceNumber,
            @NotBlank @Size(max = 160) String partnerName, @NotNull LocalDate issueDate, @NotNull LocalDate dueDate,
            @NotEmpty @Size(max = 200) List<@NotNull @Valid InvoiceLine> lines) { }

    public record UpdateInvoiceRequest(@NotNull Long version, @NotNull @Valid CreateInvoiceRequest invoice) { }

    public record OrderInvoiceRequest(@NotBlank @Size(max = 40) String invoiceNumber,
            @NotNull LocalDate issueDate, @NotNull LocalDate dueDate) { }

    public record PaymentRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull @PastOrPresent LocalDate paidOn, @NotBlank @Size(max = 160) String reference) { }

    public record InvoiceDetail(InvoiceResponse invoice, long version, UUID salesOrderId, List<InvoiceLine> lines,
            List<InvoiceRecords.Payment> payments, List<InvoiceRecords.Event> events, List<InvoiceRecords.Attachment> attachments) { }

    public record AccountingOverview(BigDecimal receivables, BigDecimal overdue, long openInvoiceCount,
            List<InvoiceResponse> invoices) { }

                public record InvoiceResponse(UUID id, long version, String invoiceNumber, String partnerName, LocalDate issueDate,
                        LocalDate dueDate, BigDecimal totalAmount, BigDecimal paidAmount, InvoiceStatus status,
                        List<InvoiceLineView> lines) {
        static InvoiceResponse from(AccountInvoice invoice) {
            return new InvoiceResponse(invoice.getId(), invoice.getVersion(), invoice.getInvoiceNumber(), invoice.getPartnerName(),
                    invoice.getIssueDate(), invoice.getDueDate(), invoice.getTotalAmount(), invoice.getPaidAmount(),
                                        invoice.getStatus(), invoice.getLines().stream().map(InvoiceLineView::from).toList());
        }
    }

        public record InvoiceLineView(UUID productId, String description, BigDecimal quantity, BigDecimal unitPrice,
                        String imageUrl) {
                static InvoiceLineView from(InvoiceLine line) {
                        return new InvoiceLineView(line.getProductId(), line.getDescription(), line.getQuantity(),
                                        line.getUnitPrice(), line.getImageUrl());
                }
        }
}