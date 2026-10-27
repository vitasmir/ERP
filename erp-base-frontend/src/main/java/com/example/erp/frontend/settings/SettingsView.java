package com.example.erp.frontend.settings;

import java.math.BigDecimal;

public record SettingsView(String companyName, String companyEmail, String currencyCode, String timezone,
        int fiscalYearStartMonth, int defaultPaymentTermsDays, BigDecimal deliveryFee,
        BigDecimal eshopMarginPercent, BigDecimal eshopRoundingUnit, BigDecimal eshopDefaultVatRate,
        String updatedAt) { }