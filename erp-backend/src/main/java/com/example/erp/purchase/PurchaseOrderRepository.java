package com.example.erp.purchase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findAllByOrderByExpectedDeliveryDateAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchase from PurchaseOrder purchase where purchase.id = :id")
    Optional<PurchaseOrder> findForUpdate(@Param("id") UUID id);
}