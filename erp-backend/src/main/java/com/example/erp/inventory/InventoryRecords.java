package com.example.erp.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.catalog.Product;
import com.example.erp.users.ErpUser;

@Component
public class InventoryRecords {
    private final JdbcTemplate jdbc;

    public InventoryRecords(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void movement(InventoryItem item, Product product, String type, int quantity,
            String reference, String note, ErpUser actor) {
        jdbc.update("""
                INSERT INTO inventory_movements (inventory_item_id, product_id, product_name, sku, unit,
                    location_name, movement_type, quantity, balance_after, unit_cost, reference, note,
                    actor_id, actor_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, item.getId(), product.getId(), product.getName(), product.getSku(), product.getUnit(),
                item.getLocationName(), type, quantity, item.getQuantity(), item.getUnitCost(), reference,
                note == null ? "" : note.trim(), actor.getId(), actor.getFullName());
    }

    public boolean hasMovements(UUID productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM inventory_movements WHERE product_id = ?)", Boolean.class, productId));
    }

    public MovementPage movements(UUID itemId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Neplatná stránka nebo velikost stránky.");
        }
        String filter = itemId == null ? "" : " WHERE inventory_item_id = ?";
        Long count = itemId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements", Long.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements" + filter, Long.class, itemId);
        String sql = "SELECT * FROM inventory_movements" + filter + " ORDER BY id DESC LIMIT ? OFFSET ?";
        Object[] parameters = itemId == null ? new Object[] {size, (long) page * size}
                : new Object[] {itemId, size, (long) page * size};
        List<Movement> rows = jdbc.query(sql, (row, index) -> new Movement(
                row.getLong("id"), row.getObject("inventory_item_id", UUID.class),
                row.getString("product_name"), row.getString("sku"), row.getString("unit"),
                row.getString("location_name"), row.getString("movement_type"), row.getInt("quantity"),
                row.getInt("balance_after"), row.getBigDecimal("unit_cost"), row.getString("reference"),
                row.getString("note"), row.getString("actor_name"),
                row.getTimestamp("created_at").toInstant().toString()), parameters);
        return new MovementPage(rows, page, size, count);
    }

    public record Movement(long id, UUID inventoryItemId, String productName, String sku, String unit,
            String locationName, String type, int quantity, int balanceAfter, BigDecimal unitCost,
            String reference, String note, String actorName, String createdAt) { }

    public record MovementPage(List<Movement> items, int page, int size, long totalElements) { }
}
