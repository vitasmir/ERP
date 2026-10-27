package com.example.erp.promo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PromoCampaignRepository extends JpaRepository<PromoCampaign, UUID> {
    List<PromoCampaign> findAllByOrderByStartsOnDesc();
}