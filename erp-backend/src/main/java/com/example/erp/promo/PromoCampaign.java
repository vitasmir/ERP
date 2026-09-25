package com.example.erp.promo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "promo_campaigns")
public class PromoCampaign {
    @Id
    private UUID id;
    private String name;
    @Column(name = "product_id") private UUID productId;
    @Column(name = "supplier_id") private UUID supplierId;
    @Enumerated(EnumType.STRING) private CampaignStatus status;
    @Column(name = "starts_on") private LocalDate startsOn;
    @Column(name = "ends_on") private LocalDate endsOn;
    @Column(name = "regular_price") private BigDecimal regularPrice;
    @Column(name = "promo_price") private BigDecimal promoPrice;
    @Column(name = "supplier_purchase_price") private BigDecimal supplierPurchasePrice;
    @Column(name = "planned_quantity") private int plannedQuantity;
    @Column(name = "actual_quantity") private int actualQuantity;
    @Column(name = "marketing_contribution") private BigDecimal marketingContribution;
    @Column(name = "created_at") private LocalDateTime createdAt;

    protected PromoCampaign() { }

    public PromoCampaign(UUID id, String name, UUID productId, UUID supplierId, LocalDate startsOn, LocalDate endsOn,
            BigDecimal regularPrice, BigDecimal promoPrice, BigDecimal supplierPurchasePrice, int plannedQuantity,
            BigDecimal marketingContribution) {
        this.id = id;
        this.name = name;
        this.productId = productId;
        this.supplierId = supplierId;
        this.status = CampaignStatus.PLANNED;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.regularPrice = regularPrice;
        this.promoPrice = promoPrice;
        this.supplierPurchasePrice = supplierPurchasePrice;
        this.plannedQuantity = plannedQuantity;
        this.actualQuantity = 0;
        this.marketingContribution = marketingContribution;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public CampaignStatus getStatus() { return status; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public BigDecimal getRegularPrice() { return regularPrice; }
    public BigDecimal getPromoPrice() { return promoPrice; }
    public int getPlannedQuantity() { return plannedQuantity; }
    public int getActualQuantity() { return actualQuantity; }
    public BigDecimal getMarketingContribution() { return marketingContribution; }

    public void changeStatus(CampaignStatus status) { this.status = status; }
}