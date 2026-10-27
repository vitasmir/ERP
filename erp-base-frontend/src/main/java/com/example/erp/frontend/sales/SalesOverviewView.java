package com.example.erp.frontend.sales;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SalesOverviewView(BigDecimal quoteValue, BigDecimal confirmedValue, long quoteCount,
        List<SalesOrderView> orders) {
    public record SalesOrderView(UUID id, String orderNumber, String customerName, String orderDate,
            String deliveryDate, BigDecimal totalAmount, String status) { }
}