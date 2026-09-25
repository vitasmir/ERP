package com.example.erp.purchase;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    List<Supplier> findAllByOrderByNameAsc();
}