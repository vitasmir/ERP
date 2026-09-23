package com.example.erp.pos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pos_transactions")
public class PosTransaction {
    @Id
    private UUID id;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "item_count")
    private int itemCount;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    private PosTransactionStatus status;

    protected PosTransaction() { }

    public UUID getId() { return id; }
    public String getReceiptNumber() { return receiptNumber; }
    public String getStoreName() { return storeName; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public int getItemCount() { return itemCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public PosTransactionStatus getStatus() { return status; }

    public void pay(String method) {
        paymentMethod = method;
        status = PosTransactionStatus.PAID;
    }
}