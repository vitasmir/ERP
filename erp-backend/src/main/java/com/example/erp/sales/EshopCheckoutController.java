package com.example.erp.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.accounting.AccountInvoice;
import com.example.erp.accounting.InvoiceLine;
import com.example.erp.accounting.InvoicePdfService;
import com.example.erp.accounting.InvoiceRecords;
import com.example.erp.accounting.InvoiceStatus;
import com.example.erp.catalog.PricingService;
import com.example.erp.catalog.Product;
import com.example.erp.catalog.ProductRepository;
import com.example.erp.inventory.InventoryItem;
import com.example.erp.inventory.InventoryItemRepository;
import com.example.erp.pos.PosTransaction;
import com.example.erp.pos.PosTransactionRepository;

import jakarta.persistence.EntityManager;

@RestController
@RequestMapping("/api/v1/sales")
public class EshopCheckoutController {
    private final SalesOrderRepository orders;
    private final InvoiceRecords invoiceRecords;
    private final PosTransactionRepository transactions;
    private final ProductRepository products;
    private final InventoryItemRepository inventory;
    private final InvoicePdfService pdfs;
    private final EntityManager entityManager;
    private final PricingService pricing;

    public EshopCheckoutController(SalesOrderRepository orders,
            InvoiceRecords invoiceRecords, PosTransactionRepository transactions, ProductRepository products,
            InventoryItemRepository inventory, InvoicePdfService pdfs, EntityManager entityManager, PricingService pricing) {
        this.orders = orders;
        this.invoiceRecords = invoiceRecords;
        this.transactions = transactions;
        this.products = products;
        this.inventory = inventory;
        this.pdfs = pdfs;
        this.entityManager = entityManager;
        this.pricing = pricing;
    }

    @PostMapping("/orders/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CheckoutResponse checkout(@RequestBody CheckoutRequest request) {
        validate(request);
        BigDecimal total = BigDecimal.ZERO;
        int itemCount = 0;
        List<InvoiceLine> invoiceLines = new java.util.ArrayList<>();
        for (CheckoutLine line : request.lines()) {
            Product product = products.findById(line.productId())
                    .orElseThrow(() -> badRequest("Produkt již není dostupný."));
            int available = inventory.findAllByProductIdOrderByQuantityDesc(product.getId()).stream()
                    .mapToInt(InventoryItem::getQuantity).sum();
            if (!product.isActive() || available < line.quantity()) {
                throw badRequest("Produkt " + product.getName() + " již není v požadovaném množství dostupný.");
            }
            BigDecimal quantity = BigDecimal.valueOf(line.quantity());
                BigDecimal vatRate = product.getVatRate() == null ? BigDecimal.ZERO : product.getVatRate();
                BigDecimal grossUnitPrice = product.getPurchasePrice() == null
                    ? product.getPrice() : pricing.sellingPrice(product.getPurchasePrice(), vatRate);
            itemCount += line.quantity();
            invoiceLines.add(new InvoiceLine(product.getId(), product.getImageUrl(), product.getName(), quantity,
                    pricing.netPrice(grossUnitPrice, vatRate), vatRate));
        }
        total = invoiceLines.stream().map(InvoiceLine::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) throw badRequest("Hodnota objednávky musí být kladná.");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String orderNumber = "ESH-" + suffix;
        String invoiceNumber = "FV-" + suffix;
        SalesOrder order = SalesOrder.create(orderNumber, request.customerName().trim(), request.orderDate(),
                request.deliveryDate(), total);
        orders.save(order);

        AccountInvoice invoice = new AccountInvoice(UUID.randomUUID(), invoiceNumber, request.customerName().trim(),
                request.orderDate(), request.deliveryDate(), BigDecimal.ZERO);
        invoice.saveAsDraft();
        entityManager.persist(invoice);
        invoice.updateDraft(invoiceNumber, request.customerName().trim(), request.orderDate(), request.deliveryDate(), invoiceLines);
        invoice.linkSalesOrder(order.getId());
        invoice.issue();
        entityManager.flush();
        invoiceRecords.event(invoice.getId(), "ISSUED_FROM_ESHOP", orderNumber);

        boolean paid = "CARD".equals(request.paymentMethod());
        PosTransaction transaction = PosTransaction.fromEshop("ESH-" + suffix, itemCount, total, request.paymentMethod(), paid);
        transactions.save(transaction);
        if (paid) {
            invoice.registerPayment(total);
            entityManager.flush();
            invoiceRecords.payment(invoice.getId(), total, LocalDate.now(), "Platba kartou v e-shopu");
            invoiceRecords.event(invoice.getId(), "PAYMENT", "Platba kartou v e-shopu");
            pdfs.createIfMissing(invoice);
        }
        return new CheckoutResponse(order.getId(), invoice.getId(), orderNumber, invoiceNumber, invoice.getStatus());
    }

    private void validate(CheckoutRequest request) {
        if (request == null || request.customerName() == null || request.customerName().isBlank()
                || request.customerName().length() > 160 || request.orderDate() == null || request.deliveryDate() == null
                || request.deliveryDate().isBefore(request.orderDate()) || request.lines() == null || request.lines().isEmpty()
                || !("CARD".equals(request.paymentMethod()) || "CASH".equals(request.paymentMethod()))) {
            throw badRequest("Objednávka z e-shopu obsahuje neplatné údaje.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record CheckoutRequest(String customerName, LocalDate orderDate, LocalDate deliveryDate,
            String paymentMethod, List<CheckoutLine> lines) { }

    public record CheckoutLine(UUID productId, int quantity) { }

    public record CheckoutResponse(UUID orderId, UUID invoiceId, String orderNumber, String invoiceNumber,
            InvoiceStatus invoiceStatus) { }
}