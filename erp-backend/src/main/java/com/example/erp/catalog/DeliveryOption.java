package com.example.erp.catalog;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "delivery_options")
public class DeliveryOption {
    @Id
    private UUID id;
    private String code;
    private String label;
    private int preparationDays;
    private boolean enabled;

    protected DeliveryOption() { }

    public String getCode() { return code; }
    public String getLabel() { return label; }
    public int getPreparationDays() { return preparationDays; }
    public boolean isEnabled() { return enabled; }
}