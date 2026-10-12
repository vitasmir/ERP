package com.example.erp.purchase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.users.ErpUser;

@Component
public class PurchaseReceipts {
    private final JdbcTemplate jdbc;

    public PurchaseReceipts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean alreadyReceived(UUID receiptId, UUID orderId, String signature) {
        List<Boolean> matches = jdbc.query("""
                SELECT purchase_order_id, request_signature FROM purchase_receipts WHERE id = ?
                """, (row, index) -> orderId.equals(row.getObject("purchase_order_id", UUID.class))
                && signature.equals(row.getString("request_signature")), receiptId);
        if (matches.isEmpty()) return false;
        if (!matches.get(0)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Identifikátor příjemky již byl použit pro jiný příjem.");
        }
        return true;
    }

    public void record(UUID receiptId, UUID orderId, String signature, List<ReceiptLine> lines, ErpUser actor) {
        int inserted = jdbc.update("""
                INSERT INTO purchase_receipts (id, purchase_order_id, request_signature, actor_id, actor_name)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                """, receiptId, orderId, signature, actor.getId(), actor.getFullName());
        if (inserted != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identifikátor příjemky již byl použit.");
        }
        for (ReceiptLine line : lines) {
            jdbc.update("""
                    INSERT INTO purchase_receipt_lines
                        (receipt_id, purchase_order_line_id, product_id, quantity, unit_price)
                    VALUES (?, ?, ?, ?, ?)
                    """, receiptId, line.lineId(), line.productId(), line.quantity(), line.unitPrice());
        }
    }

    public List<Receipt> forOrder(UUID orderId) {
        return jdbc.query("""
                SELECT id, actor_name, created_at FROM purchase_receipts
                WHERE purchase_order_id = ? ORDER BY created_at DESC, id
                """, (row, index) -> {
                    UUID id = row.getObject("id", UUID.class);
                    List<ReceiptLine> lines = jdbc.query("""
                            SELECT purchase_order_line_id, product_id, quantity, unit_price
                            FROM purchase_receipt_lines WHERE receipt_id = ? ORDER BY id
                            """, (line, lineIndex) -> new ReceiptLine(
                            line.getObject("purchase_order_line_id", UUID.class),
                            line.getObject("product_id", UUID.class), line.getInt("quantity"),
                            line.getBigDecimal("unit_price")), id);
                    return new Receipt(id, row.getString("actor_name"),
                            row.getTimestamp("created_at").toInstant().toString(), lines);
                }, orderId);
    }

    public record ReceiptLine(UUID lineId, UUID productId, int quantity, BigDecimal unitPrice) { }
    public record Receipt(UUID id, String actorName, String createdAt, List<ReceiptLine> lines) { }
}
