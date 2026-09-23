package com.example.erp.manufacturing;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "manufacturing_orders")
public class ManufacturingOrder {
    @Id
    private UUID id;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "work_center")
    private String workCenter;

    @Column(name = "planned_quantity")
    private int plannedQuantity;

    @Column(name = "completed_quantity")
    private int completedQuantity;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Enumerated(EnumType.STRING)
    private ManufacturingOrderStatus status;

    protected ManufacturingOrder() { }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getProductName() { return productName; }
    public String getWorkCenter() { return workCenter; }
    public int getPlannedQuantity() { return plannedQuantity; }
    public int getCompletedQuantity() { return completedQuantity; }
    public LocalDate getPlannedDate() { return plannedDate; }
    public ManufacturingOrderStatus getStatus() { return status; }

    public void complete() {
        completedQuantity = plannedQuantity;
        status = ManufacturingOrderStatus.COMPLETED;
    }
}