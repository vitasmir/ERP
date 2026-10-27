package com.example.erp.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.example.erp.settings.ErpSettings;
import com.example.erp.settings.ErpSettingsRepository;

@Service
public class PricingService {
    private static final BigDecimal ONE = BigDecimal.ONE;
    private final ErpSettingsRepository settings;

    public PricingService(ErpSettingsRepository settings) {
        this.settings = settings;
    }

    public BigDecimal sellingPrice(BigDecimal purchasePrice, BigDecimal vatRate) {
        ErpSettings configuration = settings.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("ERP settings were not found."));
        return sellingPrice(purchasePrice, vatRate, configuration.getEshopMarginPercent());
        }

        public BigDecimal sellingPrice(BigDecimal purchasePrice, BigDecimal vatRate, BigDecimal marginPercent) {
        ErpSettings configuration = settings.findAll().stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("ERP settings were not found."));
        BigDecimal marginFactor = ONE.subtract(marginPercent.movePointLeft(2));
        BigDecimal netPrice = purchasePrice.divide(marginFactor, 8, RoundingMode.HALF_UP);
        BigDecimal grossPrice = netPrice.multiply(ONE.add(vatRate.movePointLeft(2)));
        BigDecimal roundingUnit = configuration.getEshopRoundingUnit();
        return grossPrice.divide(roundingUnit, 0, RoundingMode.CEILING).multiply(roundingUnit)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    public BigDecimal netPrice(BigDecimal grossPrice, BigDecimal vatRate) {
        return grossPrice.divide(ONE.add(vatRate.movePointLeft(2)), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal defaultVatRate() {
        return settings.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("ERP settings were not found."))
                .getEshopDefaultVatRate();
    }

    public BigDecimal currentMargin() {
        return settings.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("ERP settings were not found."))
                .getEshopMarginPercent();
    }
}
