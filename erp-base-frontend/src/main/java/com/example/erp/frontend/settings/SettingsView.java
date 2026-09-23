package com.example.erp.frontend.settings;

public record SettingsView(String companyName, String companyEmail, String currencyCode, String timezone,
        int fiscalYearStartMonth, int defaultPaymentTermsDays, String updatedAt) { }