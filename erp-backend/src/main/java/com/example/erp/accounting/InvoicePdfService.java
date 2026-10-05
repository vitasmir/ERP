package com.example.erp.accounting;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.example.erp.catalog.ProductRepository;
import com.example.erp.sales.SalesOrder;
import com.example.erp.sales.SalesOrderRepository;

@Service
public class InvoicePdfService {
    private final InvoiceRecords records;
    private final SalesOrderRepository orders;
    private final ProductRepository products;

    public InvoicePdfService(InvoiceRecords records, SalesOrderRepository orders, ProductRepository products) {
        this.records = records;
        this.orders = orders;
        this.products = products;
    }

    public void createIfMissing(AccountInvoice invoice) {
        String filename = invoice.getInvoiceNumber() + ".pdf";
        if (records.attachments(invoice.getId()).stream().anyMatch(item -> filename.equals(item.filename()))) return;
        String orderNumber = null;
        if (invoice.getSalesOrderId() != null) {
            orderNumber = orders.findById(invoice.getSalesOrderId()).map(SalesOrder::getOrderNumber).orElse(null);
        }
        try {
            java.util.Map<java.util.UUID, String> units = new java.util.HashMap<>();
            invoice.getLines().stream().map(InvoiceLine::getProductId).filter(java.util.Objects::nonNull)
                    .forEach(productId -> products.findById(productId).ifPresent(product -> units.put(productId, product.getUnit())));
            records.attach(invoice.getId(), filename, "application/pdf", InvoicePdf.render(invoice, orderNumber, units));
            records.event(invoice.getId(), "PDF_CREATED", filename);
        } catch (IOException exception) {
            throw new IllegalStateException("Invoice PDF could not be created.", exception);
        }
    }
}