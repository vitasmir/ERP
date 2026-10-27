package com.example.erp.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    List<InventoryItem> findAllByOrderByQuantityAsc();
    List<InventoryItem> findAllByProductIdOrderByQuantityDesc(UUID productId);
    Optional<InventoryItem> findByProductIdAndLocationName(UUID productId, String locationName);
    Optional<InventoryItem> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);
    boolean existsByProductId(UUID productId);
    void deleteAllByProductId(UUID productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryItem item where item.id = :id")
    Optional<InventoryItem> findForUpdate(@Param("id") UUID id);

    @Query("select item.productId from InventoryItem item where item.id = :id")
    Optional<UUID> findProductIdById(@Param("id") UUID id);
}