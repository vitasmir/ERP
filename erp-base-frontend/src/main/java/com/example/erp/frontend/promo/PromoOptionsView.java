package com.example.erp.frontend.promo;

import java.util.List;
import java.util.UUID;

public record PromoOptionsView(List<ProductOption> products, List<SupplierOption> suppliers) {
    public record ProductOption(UUID id, String name, String unit) { }
    public record SupplierOption(UUID id, String name) { }
}