package com.example.erp.promo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/promo-campaigns")
public class PromoCampaignController {
    private final PromoCampaignRepository campaigns;

    public PromoCampaignController(PromoCampaignRepository campaigns) { this.campaigns = campaigns; }

    @GetMapping
    public List<PromoCampaignResponse> list() {
        return campaigns.findAllByOrderByStartsOnDesc().stream().map(PromoCampaignResponse::from).toList();
    }

    @PatchMapping("/{id}/status")
    public PromoCampaignResponse updateStatus(@PathVariable UUID id, @RequestBody CampaignStatusUpdate update) {
        PromoCampaign campaign = campaigns.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promo campaign was not found."));
        campaign.changeStatus(update.status());
        return PromoCampaignResponse.from(campaigns.save(campaign));
    }

    public record CampaignStatusUpdate(CampaignStatus status) { }

    public record PromoCampaignResponse(UUID id, String name, CampaignStatus status, LocalDate startsOn,
            LocalDate endsOn, BigDecimal regularPrice, BigDecimal promoPrice, int plannedQuantity,
            int actualQuantity, BigDecimal marketingContribution) {
        static PromoCampaignResponse from(PromoCampaign campaign) {
            return new PromoCampaignResponse(campaign.getId(), campaign.getName(), campaign.getStatus(),
                    campaign.getStartsOn(), campaign.getEndsOn(), campaign.getRegularPrice(), campaign.getPromoPrice(),
                    campaign.getPlannedQuantity(), campaign.getActualQuantity(), campaign.getMarketingContribution());
        }
    }
}