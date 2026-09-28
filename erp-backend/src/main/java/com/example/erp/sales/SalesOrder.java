package com.example.erp.sales;

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
@Table(name = "sales_orders")
public class SalesOrder {
    @Id
    private UUID id;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private SalesOrderStatus status;

    protected SalesOrder() { }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getCustomerName() { return customerName; }
    public LocalDate getOrderDate() { return orderDate; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public SalesOrderStatus getStatus() { return status; }

    public static SalesOrder create(String orderNumber, String customerName, LocalDate orderDate,
            LocalDate deliveryDate, BigDecimal totalAmount) {
        SalesOrder order = new SalesOrder();
        order.id = UUID.randomUUID();
        order.orderNumber = orderNumber;
        order.customerName = customerName;
        order.orderDate = orderDate;
        order.deliveryDate = deliveryDate;
        order.totalAmount = totalAmount;
        order.status = SalesOrderStatus.CONFIRMED;
        return order;
    }

    public void update(String orderNumber, String customerName, LocalDate orderDate, LocalDate deliveryDate,
            BigDecimal totalAmount) {
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.orderDate = orderDate;
        this.deliveryDate = deliveryDate;
        this.totalAmount = totalAmount;
    }

    public void confirm() { status = SalesOrderStatus.CONFIRMED; }
}