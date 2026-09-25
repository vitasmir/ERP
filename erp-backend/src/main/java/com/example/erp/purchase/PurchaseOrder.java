package com.example.erp.purchase;

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
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    private UUID id;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "requested_on")
    private LocalDate requestedOn;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private PurchaseOrderStatus status;

    protected PurchaseOrder() { }

    public static PurchaseOrder create(String orderNumber, String supplierName, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, BigDecimal totalAmount) {
        PurchaseOrder order = new PurchaseOrder();
        order.id = UUID.randomUUID();
        order.orderNumber = orderNumber;
        order.supplierName = supplierName;
        order.requestedOn = requestedOn;
        order.expectedDeliveryDate = expectedDeliveryDate;
        order.totalAmount = totalAmount;
        order.status = PurchaseOrderStatus.REQUESTED;
        return order;
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getSupplierName() { return supplierName; }
    public LocalDate getRequestedOn() { return requestedOn; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public PurchaseOrderStatus getStatus() { return status; }

    public void order() { status = PurchaseOrderStatus.ORDERED; }
}