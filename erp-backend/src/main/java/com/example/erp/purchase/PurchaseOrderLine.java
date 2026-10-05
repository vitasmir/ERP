package com.example.erp.purchase;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {
    @Id
    private UUID id;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "product_id")
    private UUID productId;

    private int quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "received_quantity")
    private int receivedQuantity;

    protected PurchaseOrderLine() { }

    public static PurchaseOrderLine create(UUID purchaseOrderId, UUID productId, int quantity, BigDecimal unitPrice) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.id = UUID.randomUUID();
        line.purchaseOrderId = purchaseOrderId;
        line.productId = productId;
        line.quantity = quantity;
        line.unitPrice = unitPrice;
        line.receivedQuantity = 0;
        return line;
    }

    public UUID getId() { return id; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getReceivedQuantity() { return receivedQuantity; }

    public int remainingQuantity() {
        return quantity - receivedQuantity;
    }

    public void receive(int quantity) {
        if (quantity <= 0 || quantity > remainingQuantity()) {
            throw new IllegalArgumentException("Received quantity is invalid.");
        }
        receivedQuantity += quantity;
    }
}
