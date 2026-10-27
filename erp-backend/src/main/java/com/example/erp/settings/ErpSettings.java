package com.example.erp.settings;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "erp_settings")
public class ErpSettings {
    @Id
    private UUID id;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_email")
    private String companyEmail;

    @Column(name = "currency_code")
    private String currencyCode;

    private String timezone;

    @Column(name = "fiscal_year_start_month")
    private int fiscalYearStartMonth;

    @Column(name = "default_payment_terms_days")
    private int defaultPaymentTermsDays;

    @Column(name = "delivery_fee")
    private BigDecimal deliveryFee;

    @Column(name = "eshop_margin_percent")
    private BigDecimal eshopMarginPercent;

    @Column(name = "eshop_rounding_unit")
    private BigDecimal eshopRoundingUnit;

    @Column(name = "eshop_default_vat_rate")
    private BigDecimal eshopDefaultVatRate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected ErpSettings() { }

    public UUID getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getCompanyEmail() { return companyEmail; }
    public String getCurrencyCode() { return currencyCode; }
    public String getTimezone() { return timezone; }
    public int getFiscalYearStartMonth() { return fiscalYearStartMonth; }
    public int getDefaultPaymentTermsDays() { return defaultPaymentTermsDays; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getEshopMarginPercent() { return eshopMarginPercent; }
    public BigDecimal getEshopRoundingUnit() { return eshopRoundingUnit; }
    public BigDecimal getEshopDefaultVatRate() { return eshopDefaultVatRate; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void update(String companyName, String companyEmail, String currencyCode, String timezone,
            int fiscalYearStartMonth, int defaultPaymentTermsDays, BigDecimal deliveryFee,
            BigDecimal eshopMarginPercent, BigDecimal eshopRoundingUnit, BigDecimal eshopDefaultVatRate) {
        this.companyName = companyName;
        this.companyEmail = companyEmail;
        this.currencyCode = currencyCode;
        this.timezone = timezone;
        this.fiscalYearStartMonth = fiscalYearStartMonth;
        this.defaultPaymentTermsDays = defaultPaymentTermsDays;
        this.deliveryFee = deliveryFee;
        this.eshopMarginPercent = eshopMarginPercent;
        this.eshopRoundingUnit = eshopRoundingUnit;
        this.eshopDefaultVatRate = eshopDefaultVatRate;
        this.updatedAt = LocalDateTime.now();
    }
}