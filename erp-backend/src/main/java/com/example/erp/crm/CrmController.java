package com.example.erp.crm;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/leads")
    public LeadResponse create(@RequestBody CreateLeadRequest request) {
        if (request.name() == null || request.name().isBlank() || request.customerName() == null || request.customerName().isBlank()
                || request.expectedRevenue() == null || request.expectedRevenue().signum() < 0 || request.probability() < 0
                || request.probability() > 100 || request.expectedCloseDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead contains invalid values.");
        }
        CrmLead lead = CrmLead.create(request.name().trim(), request.customerName().trim(), request.expectedRevenue(),
                request.probability(), request.expectedCloseDate());
        return LeadResponse.from(leads.save(lead));
    }

    @PatchMapping("/leads/{id}/won")
    public LeadResponse markWon(@PathVariable UUID id) {
        CrmLead lead = leads.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead was not found."));
        lead.markWon();
        return LeadResponse.from(leads.save(lead));
    }

    @PatchMapping("/leads/{id}/stage")
    public LeadResponse moveStage(@PathVariable UUID id, @RequestBody StageChangeRequest request) {
        CrmLead lead = leads.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead was not found."));
        try {
            LeadStage nextStage = LeadStage.valueOf(request.stage());
            if (nextStage == LeadStage.WON) lead.markWon(); else lead.moveTo(nextStage);
            return LeadResponse.from(leads.save(lead));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown CRM stage.");
        }
    }

    public record CrmOverview(BigDecimal pipeline, BigDecimal forecast, long openLeadCount, List<LeadResponse> leads) { }
    public record StageChangeRequest(String stage) { }
        public record CreateLeadRequest(String name, String customerName, BigDecimal expectedRevenue, int probability,
            LocalDate expectedCloseDate) { }
    public record LeadResponse(UUID id, String name, String customerName, BigDecimal expectedRevenue, int probability, LeadStage stage, LocalDate expectedCloseDate) {
        static LeadResponse from(CrmLead lead) { return new LeadResponse(lead.getId(), lead.getName(), lead.getCustomerName(), lead.getExpectedRevenue(), lead.getProbability(), lead.getStage(), lead.getExpectedCloseDate()); }
    }
}