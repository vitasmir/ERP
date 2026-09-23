package com.example.erp.settings;

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
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void update(String companyName, String companyEmail, String currencyCode, String timezone,
            int fiscalYearStartMonth, int defaultPaymentTermsDays) {
        this.companyName = companyName;
        this.companyEmail = companyEmail;
        this.currencyCode = currencyCode;
        this.timezone = timezone;
        this.fiscalYearStartMonth = fiscalYearStartMonth;
        this.defaultPaymentTermsDays = defaultPaymentTermsDays;
        this.updatedAt = LocalDateTime.now();
    }
}