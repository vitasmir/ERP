package com.example.erp.purchase;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findAllByPurchaseOrderIdOrderById(UUID purchaseOrderId);
    void deleteAllByPurchaseOrderId(UUID purchaseOrderId);
}
