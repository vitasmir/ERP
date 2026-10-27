package com.example.erp.marketing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/marketing")
public class MarketingController {
    private final MarketingCampaignRepository campaigns;

    public MarketingController(MarketingCampaignRepository campaigns) { this.campaigns = campaigns; }

    @GetMapping("/overview")
    public MarketingOverview overview() {
        List<CampaignResponse> items = campaigns.findAllByOrderByPlannedStartDateAsc().stream()
                .map(CampaignResponse::from).toList();
        return new MarketingOverview(items.stream().filter(item -> item.status() == CampaignStatus.RUNNING).count(),
                items.stream().filter(item -> item.status() == CampaignStatus.PLANNED).count(),
                items.stream().mapToInt(CampaignResponse::leadCount).sum(),
                items.stream().map(CampaignResponse::spent).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2), items);
    }

        @PostMapping("/campaigns")
        public CampaignResponse create(@Valid @RequestBody CampaignRequest request) {
                return CampaignResponse.from(campaigns.save(new MarketingCampaign(UUID.randomUUID(), request.name(),
                                request.audience(), request.channel(), request.ownerName(), request.budget(), request.plannedStartDate())));
        }

        @PutMapping("/campaigns/{id}")
        public CampaignResponse update(@PathVariable UUID id, @Valid @RequestBody CampaignRequest request) {
                MarketingCampaign campaign = find(id);
                campaign.update(request.name(), request.audience(), request.channel(), request.ownerName(), request.budget(),
                                request.plannedStartDate());
                return CampaignResponse.from(campaigns.save(campaign));
        }

    @PatchMapping("/campaigns/{id}/launch")
    public CampaignResponse launch(@PathVariable UUID id) {
        MarketingCampaign campaign = find(id);
        campaign.launch();
        return CampaignResponse.from(campaigns.save(campaign));
    }

    @PatchMapping("/campaigns/{id}/complete")
    public CampaignResponse complete(@PathVariable UUID id) {
        MarketingCampaign campaign = find(id);
        campaign.complete();
        return CampaignResponse.from(campaigns.save(campaign));
    }

    private MarketingCampaign find(UUID id) { return campaigns.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketing campaign was not found.")); }

    public record CampaignRequest(@NotBlank String name, @NotBlank String audience, @NotBlank String channel,
            @NotBlank String ownerName, @NotNull @DecimalMin("0.00") BigDecimal budget,
            @NotNull java.time.LocalDate plannedStartDate) { }

    public record MarketingOverview(long runningCampaignCount, long plannedCampaignCount, int totalLeadCount,
            BigDecimal totalSpent, List<CampaignResponse> campaigns) { }

    public record CampaignResponse(UUID id, String name, String audience, String channel, String ownerName,
            BigDecimal budget, BigDecimal spent, int leadCount, CampaignStatus status, java.time.LocalDate plannedStartDate) {
        static CampaignResponse from(MarketingCampaign campaign) {
            return new CampaignResponse(campaign.getId(), campaign.getName(), campaign.getAudience(),
                    campaign.getChannel(), campaign.getOwnerName(), campaign.getBudget(), campaign.getSpent(),
                    campaign.getLeadCount(), campaign.getStatus(), campaign.getPlannedStartDate());
        }
    }
}