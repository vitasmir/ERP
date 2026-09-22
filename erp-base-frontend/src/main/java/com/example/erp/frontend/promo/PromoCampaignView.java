package com.example.erp.frontend.promo;

import java.math.BigDecimal;
import java.util.UUID;

public record PromoCampaignView(UUID id, String name, String status, String startsOn, String endsOn,
        BigDecimal regularPrice, BigDecimal promoPrice, int plannedQuantity, int actualQuantity,
        BigDecimal marketingContribution) { }