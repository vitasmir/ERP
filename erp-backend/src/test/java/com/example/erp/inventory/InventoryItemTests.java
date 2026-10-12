package com.example.erp.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InventoryItemTests {
    @Test
    void purchaseReceiptsUseWeightedAverageAndResetCostAfterStockIsConsumed() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), "Test warehouse");
        item.receivePurchase(2, new BigDecimal("10.00"));
        assertEquals(new BigDecimal("10.00"), item.getUnitCost());
        item.receivePurchase(3, new BigDecimal("20.00"));
        assertEquals(5, item.getQuantity());
        assertEquals(new BigDecimal("16.00"), item.getUnitCost());
        item.receivePurchase(1, new BigDecimal("21.00"));
        assertEquals(new BigDecimal("16.83"), item.getUnitCost());
        item.dispatch(6);
        item.receivePurchase(1, new BigDecimal("25.00"));
        assertEquals(new BigDecimal("25.00"), item.getUnitCost());
    }

    @Test
    void invalidPurchaseReceiptDoesNotChangeStockOrCost() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), "Test warehouse");
        item.receivePurchase(1, new BigDecimal("10.00"));
        assertThrows(IllegalArgumentException.class, () -> item.receivePurchase(0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> item.receivePurchase(1, new BigDecimal("-1")));
        assertThrows(ArithmeticException.class, () -> item.receivePurchase(Integer.MAX_VALUE, BigDecimal.ONE));
        assertEquals(1, item.getQuantity());
        assertEquals(new BigDecimal("10.00"), item.getUnitCost());
    }

    @Test
    void receiptValueIsRoundedOnlyAfterAllRowsForProductAreCombined() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), "Test warehouse");
        item.receivePurchase(1, BigDecimal.ZERO);
        item.receivePurchaseValue(2, new BigDecimal("0.01"));
        assertEquals(3, item.getQuantity());
        assertEquals(new BigDecimal("0.00"), item.getUnitCost());
    }

    @Test
    void quantitiesCannotBeNegativeOrOverflow() {
        InventoryItem item = InventoryItem.create(UUID.randomUUID(), "Test warehouse");
        assertThrows(IllegalArgumentException.class, () -> item.receive(0));
        assertThrows(IllegalArgumentException.class, () -> item.receive(-1));
        item.receive(Integer.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> item.receive(1));
        assertEquals(Integer.MAX_VALUE, item.getQuantity());
        item.dispatch(Integer.MAX_VALUE);
        assertEquals(0, item.getQuantity());
        assertThrows(IllegalArgumentException.class, () -> item.dispatch(1));
        assertEquals(0, item.getQuantity());
    }
}
