package com.example.erp.crm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/crm")
public class CrmController {
    private final CrmLeadRepository leads;
    public CrmController(CrmLeadRepository leads) { this.leads = leads; }

    @GetMapping("/overview")
    public CrmOverview overview() {
        List<LeadResponse> items = leads.findAllByOrderByExpectedCloseDateAsc().stream().map(LeadResponse::from).toList();
        BigDecimal pipeline = items.stream().map(LeadResponse::expectedRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal forecast = items.stream().map(item -> item.expectedRevenue().multiply(BigDecimal.valueOf(item.probability())).movePointLeft(2)).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        return new CrmOverview(pipeline, forecast, items.stream().filter(item -> item.stage() != LeadStage.WON).count(), items);
    }

    @PatchMapping("/leads/{id}/won")
    public LeadResponse markWon(@PathVariable UUID id) {
        CrmLead lead = leads.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead was not found."));
        lead.markWon();
        return LeadResponse.from(leads.save(lead));
    }

    public record CrmOverview(BigDecimal pipeline, BigDecimal forecast, long openLeadCount, List<LeadResponse> leads) { }
    public record LeadResponse(UUID id, String name, String customerName, BigDecimal expectedRevenue, int probability, LeadStage stage, LocalDate expectedCloseDate) {
        static LeadResponse from(CrmLead lead) { return new LeadResponse(lead.getId(), lead.getName(), lead.getCustomerName(), lead.getExpectedRevenue(), lead.getProbability(), lead.getStage(), lead.getExpectedCloseDate()); }
    }
}