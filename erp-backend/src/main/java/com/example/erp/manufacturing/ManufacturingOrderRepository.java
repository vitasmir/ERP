package com.example.erp.manufacturing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ManufacturingOrderRepository extends JpaRepository<ManufacturingOrder, UUID> {
    List<ManufacturingOrder> findAllByOrderByPlannedDateAsc();
}