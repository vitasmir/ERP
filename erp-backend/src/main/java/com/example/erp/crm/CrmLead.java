package com.example.erp.crm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "crm_leads")
public class CrmLead {
    @Id private UUID id;
    private String name;
    private String customerName;
    private BigDecimal expectedRevenue;
    private int probability;
    @Enumerated(EnumType.STRING) private LeadStage stage;
    private LocalDate expectedCloseDate;

    protected CrmLead() { }
    public static CrmLead create(String name, String customerName, BigDecimal expectedRevenue, int probability,
            LocalDate expectedCloseDate) {
        CrmLead lead = new CrmLead();
        lead.id = UUID.randomUUID();
        lead.name = name;
        lead.customerName = customerName;
        lead.expectedRevenue = expectedRevenue;
        lead.probability = probability;
        lead.stage = LeadStage.NEW;
        lead.expectedCloseDate = expectedCloseDate;
        return lead;
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getExpectedRevenue() { return expectedRevenue; }
    public int getProbability() { return probability; }
    public LeadStage getStage() { return stage; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public void markWon() { stage = LeadStage.WON; probability = 100; }
    public void moveTo(LeadStage nextStage) { stage = nextStage; }
}