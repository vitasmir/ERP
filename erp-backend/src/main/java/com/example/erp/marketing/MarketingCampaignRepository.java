package com.example.erp.marketing;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingCampaignRepository extends JpaRepository<MarketingCampaign, UUID> {
    List<MarketingCampaign> findAllByOrderByPlannedStartDateAsc();
}