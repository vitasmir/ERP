package com.example.erp.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class InventoryItemTests {
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
