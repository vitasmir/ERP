package com.example.erp.inventory;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "warehouses")
public class Warehouse {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private WarehouseOwnerType ownerType;

    @Column(name = "supplier_id")
    private UUID supplierId;

    private boolean active;

    protected Warehouse() { }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public WarehouseOwnerType getOwnerType() { return ownerType; }
    public UUID getSupplierId() { return supplierId; }
    public boolean isActive() { return active; }
}
