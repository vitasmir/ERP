package com.example.erp.inventory;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.catalog.Product;
import com.example.erp.catalog.ProductRepository;
import com.example.erp.users.ErpUser;

@Service
@Transactional
public class InventoryStockService {
    private final InventoryItemRepository items;
    private final ProductRepository products;
    private final InventoryRecords records;

    public InventoryStockService(InventoryItemRepository items, ProductRepository products, InventoryRecords records) {
        this.items = items;
        this.products = products;
        this.records = records;
    }

    public InventoryItem receive(UUID id, int quantity, int reorderLevel, BigDecimal unitCost,
            String reference, String note, ErpUser actor) {
        Product product = lockProduct(productId(id));
        InventoryItem item = lockItem(id);
        checkReceipt(item, quantity);
        item.updateStockSettings(reorderLevel, unitCost);
        item.receive(quantity);
        items.saveAndFlush(item);
        records.movement(item, product, "RECEIPT", quantity, reference(reference, "Příjem skladu"), note, actor);
        return item;
    }

    public InventoryItem dispatch(UUID id, int quantity, String reference, String note, ErpUser actor) {
        Product product = lockProduct(productId(id));
        InventoryItem item = lockItem(id);
        if (quantity <= 0 || quantity > item.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Výdej převyšuje dostupnou zásobu.");
        }
        item.dispatch(quantity);
        items.saveAndFlush(item);
        records.movement(item, product, "ISSUE", quantity, reference.trim(), note, actor);
        return item;
    }

    public InventoryItem receivePurchase(UUID productId, UUID warehouseId, String locationName,
            int quantity, String reference, ErpUser actor) {
        Product product = lockProduct(productId);
        InventoryItem item = items.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseGet(() -> InventoryItem.create(productId, warehouseId, locationName));
        checkReceipt(item, quantity);
        item.receive(quantity);
        items.saveAndFlush(item);
        records.movement(item, product, "RECEIPT", quantity, reference, "Příjem nákupní objednávky.", actor);
        return item;
    }

    private void checkReceipt(InventoryItem item, int quantity) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Množství příjmu musí být kladné.");
        }
        if ((long) item.getQuantity() + quantity > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Příjem by překročil maximální skladové množství.");
        }
    }

    private UUID productId(UUID id) {
        return items.findProductIdById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skladová položka nebyla nalezena."));
    }

    private InventoryItem lockItem(UUID id) {
        return items.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skladová položka nebyla nalezena."));
    }

    private Product lockProduct(UUID id) {
        return products.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produkt nebyl nalezen."));
    }

    private String reference(String value, String legacyReference) {
        return value == null || value.isBlank() ? legacyReference : value.trim();
    }
}
