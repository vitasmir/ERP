package com.example.erp.accounting;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Embeddable
public class InvoiceLine {
    @NotBlank @Size(max = 240)
    private String description;

    @NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3)
    private BigDecimal quantity;

    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
    @Column(name = "vat_rate")
    private BigDecimal vatRate;

    protected InvoiceLine() { }

    public InvoiceLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
    }

    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getVatRate() { return vatRate; }

    public BigDecimal getNetAmount() {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getVatAmount() {
        return getNetAmount().multiply(vatRate).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalAmount() { return getNetAmount().add(getVatAmount()); }
}