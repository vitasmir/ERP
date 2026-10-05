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

    @Column(name = "source_warehouse_id")
    private UUID sourceWarehouseId;

    @Column(name = "destination_warehouse_id")
    private UUID destinationWarehouseId;

    @Column(name = "product_id")
    private UUID productId;

    private Integer quantity;

    @Column(name = "received_quantity")
    private int receivedQuantity;

    protected PurchaseOrder() { }

    public static PurchaseOrder create(String orderNumber, String supplierName, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, BigDecimal totalAmount) {
        return create(orderNumber, supplierName, requestedOn, expectedDeliveryDate, totalAmount,
            null, null, null, null);
        }

        public static PurchaseOrder create(String orderNumber, String supplierName, LocalDate requestedOn,
            LocalDate expectedDeliveryDate, BigDecimal totalAmount, UUID sourceWarehouseId,
            UUID destinationWarehouseId, UUID productId, Integer quantity) {
        PurchaseOrder order = new PurchaseOrder();
        order.id = UUID.randomUUID();
        order.orderNumber = orderNumber;
        order.supplierName = supplierName;
        order.requestedOn = requestedOn;
        order.expectedDeliveryDate = expectedDeliveryDate;
        order.totalAmount = totalAmount;
        order.status = PurchaseOrderStatus.REQUESTED;
        order.sourceWarehouseId = sourceWarehouseId;
        order.destinationWarehouseId = destinationWarehouseId;
        order.productId = productId;
        order.quantity = quantity;
        order.receivedQuantity = 0;
        return order;
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getSupplierName() { return supplierName; }
    public LocalDate getRequestedOn() { return requestedOn; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public PurchaseOrderStatus getStatus() { return status; }
    public UUID getSourceWarehouseId() { return sourceWarehouseId; }
    public UUID getDestinationWarehouseId() { return destinationWarehouseId; }
    public UUID getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public int getReceivedQuantity() { return receivedQuantity; }

    public void updateRequested(String supplierName, LocalDate requestedOn, LocalDate expectedDeliveryDate,
            BigDecimal totalAmount, UUID sourceWarehouseId, UUID destinationWarehouseId,
            UUID productId, int quantity) {
        if (status != PurchaseOrderStatus.REQUESTED) {
            throw new IllegalStateException("Only requested purchases can be edited.");
        }
        this.supplierName = supplierName;
        this.requestedOn = requestedOn;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.totalAmount = totalAmount;
        this.sourceWarehouseId = sourceWarehouseId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public void order() { status = PurchaseOrderStatus.ORDERED; }

    public void receive(int receivedQuantity) {
        if (status != PurchaseOrderStatus.ORDERED) {
            throw new IllegalArgumentException("Only ordered purchases can be received.");
        }
        if (quantity == null || receivedQuantity <= 0 || receivedQuantity > quantity - this.receivedQuantity) {
            throw new IllegalArgumentException("Received quantity is invalid.");
        }
        this.receivedQuantity += receivedQuantity;
        if (this.receivedQuantity == quantity) status = PurchaseOrderStatus.RECEIVED;
    }
}