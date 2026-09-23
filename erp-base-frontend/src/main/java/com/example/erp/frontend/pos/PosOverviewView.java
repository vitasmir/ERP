package com.example.erp.frontend.pos;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PosOverviewView(BigDecimal paidToday, long openTransactionCount, int itemCount,
        List<PosTransactionView> transactions) {
    public record PosTransactionView(UUID id, String receiptNumber, String storeName, String openedAt, int itemCount,
            BigDecimal totalAmount, String paymentMethod, String status) { }
}