package com.example.erp.marketing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketing_campaigns")
public class MarketingCampaign {
    @Id
    private UUID id;

    private String name;
    private String audience;
    private String channel;

    @Column(name = "owner_name")
    private String ownerName;

    private BigDecimal budget;
    private BigDecimal spent;

    @Column(name = "lead_count")
    private int leadCount;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status;

    @Column(name = "planned_start_date")
    private LocalDate plannedStartDate;

    protected MarketingCampaign() { }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getAudience() { return audience; }
    public String getChannel() { return channel; }
    public String getOwnerName() { return ownerName; }
    public BigDecimal getBudget() { return budget; }
    public BigDecimal getSpent() { return spent; }
    public int getLeadCount() { return leadCount; }
    public CampaignStatus getStatus() { return status; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }

    public void launch() { status = CampaignStatus.RUNNING; }
}