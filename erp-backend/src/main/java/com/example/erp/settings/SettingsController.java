package com.example.erp.settings;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private static final UUID SETTINGS_ID = UUID.fromString("15000000-0000-0000-0000-000000000001");
    private final ErpSettingsRepository settings;

    public SettingsController(ErpSettingsRepository settings) { this.settings = settings; }

    @GetMapping
    public SettingsResponse get() { return SettingsResponse.from(load()); }

    @PatchMapping
    public SettingsResponse update(@RequestBody UpdateSettingsRequest request) {
        if (request.companyName().isBlank() || request.companyEmail().isBlank() || request.currencyCode().isBlank()
                || request.timezone().isBlank() || request.fiscalYearStartMonth() < 1 || request.fiscalYearStartMonth() > 12
                || request.defaultPaymentTermsDays() < 0 || request.deliveryFee() == null
                || request.deliveryFee().compareTo(BigDecimal.ZERO) < 0 || request.eshopMarginPercent() == null
                || request.eshopMarginPercent().compareTo(BigDecimal.ZERO) < 0
                || request.eshopMarginPercent().compareTo(new BigDecimal("99.99")) >= 0
                || request.eshopRoundingUnit() == null
                || !request.eshopRoundingUnit().equals(BigDecimal.ONE)
                    && !request.eshopRoundingUnit().equals(BigDecimal.TEN)
                    && !request.eshopRoundingUnit().equals(new BigDecimal("100"))
                || request.eshopDefaultVatRate() == null
                || request.eshopDefaultVatRate().compareTo(BigDecimal.ZERO) < 0
                || request.eshopDefaultVatRate().compareTo(new BigDecimal("100")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settings contain invalid values.");
        }
        ErpSettings configuration = load();
        configuration.update(request.companyName().trim(), request.companyEmail().trim(), request.currencyCode().trim(),
            request.timezone().trim(), request.fiscalYearStartMonth(), request.defaultPaymentTermsDays(),
            request.deliveryFee(), request.eshopMarginPercent(), request.eshopRoundingUnit(), request.eshopDefaultVatRate());
        return SettingsResponse.from(settings.save(configuration));
    }

    private ErpSettings load() {
        return settings.findById(SETTINGS_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ERP settings were not found."));
    }

    public record UpdateSettingsRequest(String companyName, String companyEmail, String currencyCode, String timezone,
            int fiscalYearStartMonth, int defaultPaymentTermsDays, BigDecimal deliveryFee,
            BigDecimal eshopMarginPercent, BigDecimal eshopRoundingUnit, BigDecimal eshopDefaultVatRate) { }

    public record SettingsResponse(String companyName, String companyEmail, String currencyCode, String timezone,
            int fiscalYearStartMonth, int defaultPaymentTermsDays, BigDecimal deliveryFee,
            BigDecimal eshopMarginPercent, BigDecimal eshopRoundingUnit, BigDecimal eshopDefaultVatRate,
            LocalDateTime updatedAt) {
        static SettingsResponse from(ErpSettings configuration) {
            return new SettingsResponse(configuration.getCompanyName(), configuration.getCompanyEmail(),
                    configuration.getCurrencyCode(), configuration.getTimezone(), configuration.getFiscalYearStartMonth(),
                    configuration.getDefaultPaymentTermsDays(), configuration.getDeliveryFee(),
                    configuration.getEshopMarginPercent(), configuration.getEshopRoundingUnit(),
                    configuration.getEshopDefaultVatRate(), configuration.getUpdatedAt());
        }
    }
}