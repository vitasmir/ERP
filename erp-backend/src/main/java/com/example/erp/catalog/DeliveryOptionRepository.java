package com.example.erp.catalog;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryOptionRepository extends JpaRepository<DeliveryOption, UUID> {
    Optional<DeliveryOption> findByCodeAndEnabledTrue(String code);
}