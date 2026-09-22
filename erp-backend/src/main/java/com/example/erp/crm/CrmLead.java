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
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getExpectedRevenue() { return expectedRevenue; }
    public int getProbability() { return probability; }
    public LeadStage getStage() { return stage; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public void markWon() { stage = LeadStage.WON; probability = 100; }
}