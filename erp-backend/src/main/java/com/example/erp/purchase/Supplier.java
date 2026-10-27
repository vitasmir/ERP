package com.example.erp.purchase;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier {
    @Id
    private UUID id;
    private String name;

    protected Supplier() { }

    public UUID getId() { return id; }
    public String getName() { return name; }
}