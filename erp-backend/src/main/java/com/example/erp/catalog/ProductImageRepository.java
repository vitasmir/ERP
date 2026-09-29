package com.example.erp.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    List<ProductImage> findAllByProductIdOrderBySortOrderAscIdAsc(UUID productId);
    Optional<ProductImage> findByProductIdAndActiveTrue(UUID productId);
    void deleteAllByProductId(UUID productId);
}
