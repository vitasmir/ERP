package com.example.erp.inventory;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    List<Warehouse> findAllByActiveTrueOrderByOwnerTypeAscNameAsc();
    List<Warehouse> findAllByOwnerTypeAndActiveTrueOrderByNameAsc(WarehouseOwnerType ownerType);
}
