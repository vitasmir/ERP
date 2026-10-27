package com.example.erp.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    List<ProductCategory> findAllByOrderBySortOrderAscNameAsc();
    Optional<ProductCategory> findBySlug(String slug);
    boolean existsByParentId(UUID parentId);
}