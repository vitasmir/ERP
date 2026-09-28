package com.example.erp.accounting;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.example.erp.sales.SalesOrder;
import com.example.erp.sales.SalesOrderRepository;

@Service
public class InvoicePdfService {
    private final InvoiceRecords records;
    private final SalesOrderRepository orders;

    public InvoicePdfService(InvoiceRecords records, SalesOrderRepository orders) {
        this.records = records;
        this.orders = orders;
    }

    public void createWhenPaid(AccountInvoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.PAID) return;
        String filename = invoice.getInvoiceNumber() + ".pdf";
        if (records.attachments(invoice.getId()).stream().anyMatch(item -> filename.equals(item.filename()))) return;
        String orderNumber = null;
        if (invoice.getSalesOrderId() != null) {
            orderNumber = orders.findById(invoice.getSalesOrderId()).map(SalesOrder::getOrderNumber).orElse(null);
        }
        try {
            records.attach(invoice.getId(), filename, "application/pdf", InvoicePdf.render(invoice, orderNumber));
            records.event(invoice.getId(), "PDF_CREATED", filename);
        } catch (IOException exception) {
            throw new IllegalStateException("Invoice PDF could not be created.", exception);
        }
    }
}