package com.example.erp.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    List<InventoryItem> findAllByOrderByQuantityAsc();
    List<InventoryItem> findAllByProductIdOrderByQuantityDesc(UUID productId);
    boolean existsByProductId(UUID productId);
}