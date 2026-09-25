package com.example.erp.frontend.promo;

import java.math.BigDecimal;
import java.util.UUID;

public record PromoCampaignView(UUID id, String name, UUID productId, UUID supplierId, String status, String startsOn,
        String endsOn, BigDecimal regularPrice, BigDecimal promoPrice, BigDecimal supplierPurchasePrice,
        int plannedQuantity, int actualQuantity, BigDecimal marketingContribution, String imageUrl) { }