package com.example.erp.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.erp.accounting.AccountInvoice;
import com.example.erp.accounting.AccountInvoiceRepository;
import com.example.erp.accounting.InvoiceStatus;
import com.example.erp.crm.CrmLead;
import com.example.erp.crm.CrmLeadRepository;
import com.example.erp.crm.LeadStage;
import com.example.erp.promo.CampaignStatus;
import com.example.erp.promo.PromoCampaign;
import com.example.erp.promo.PromoCampaignRepository;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final AccountInvoiceRepository invoices;
    private final CrmLeadRepository leads;
    private final PromoCampaignRepository campaigns;

    public DashboardController(AccountInvoiceRepository invoices, CrmLeadRepository leads,
            PromoCampaignRepository campaigns) {
        this.invoices = invoices;
        this.leads = leads;
        this.campaigns = campaigns;
    }

    @GetMapping("/overview")
    public DashboardOverview overview() {
        List<AccountInvoice> invoiceItems = invoices.findAllByOrderByDueDateAsc();
        List<CrmLead> leadItems = leads.findAllByOrderByExpectedCloseDateAsc();
        List<PromoCampaign> campaignItems = campaigns.findAllByOrderByStartsOnDesc();

        BigDecimal receivables = invoiceItems.stream().filter(invoice -> invoice.getStatus() != InvoiceStatus.PAID)
                .map(invoice -> invoice.getTotalAmount().subtract(invoice.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = invoiceItems.stream().filter(invoice -> invoice.getStatus() == InvoiceStatus.OVERDUE)
                .map(invoice -> invoice.getTotalAmount().subtract(invoice.getPaidAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pipeline = leadItems.stream().map(CrmLead::getExpectedRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal forecast = leadItems.stream()
                .map(lead -> lead.getExpectedRevenue().multiply(BigDecimal.valueOf(lead.getProbability())).movePointLeft(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);

        return new DashboardOverview(receivables, overdue, pipeline, forecast,
                campaignItems.stream().filter(campaign -> campaign.getStatus() == CampaignStatus.ACTIVE).count(),
                invoiceItems.stream().filter(invoice -> invoice.getStatus() != InvoiceStatus.PAID).limit(3)
                        .map(InvoiceItem::from).toList(),
                leadItems.stream().filter(lead -> lead.getStage() != LeadStage.WON).limit(3).map(LeadItem::from).toList(),
                campaignItems.stream().filter(campaign -> campaign.getStatus() != CampaignStatus.COMPLETED)
                        .limit(3).map(CampaignItem::from).toList());
    }

    public record DashboardOverview(BigDecimal receivables, BigDecimal overdue, BigDecimal pipeline,
            BigDecimal forecast, long activeCampaignCount, List<InvoiceItem> invoices, List<LeadItem> leads,
            List<CampaignItem> campaigns) { }

    public record InvoiceItem(UUID id, String invoiceNumber, String partnerName, LocalDate dueDate,
            BigDecimal outstandingAmount, InvoiceStatus status) {
        static InvoiceItem from(AccountInvoice invoice) {
            return new InvoiceItem(invoice.getId(), invoice.getInvoiceNumber(), invoice.getPartnerName(),
                    invoice.getDueDate(), invoice.getTotalAmount().subtract(invoice.getPaidAmount()),
                    invoice.getStatus());
        }
    }

    public record LeadItem(UUID id, String name, String customerName, BigDecimal expectedRevenue,
            int probability, LocalDate expectedCloseDate, LeadStage stage) {
        static LeadItem from(CrmLead lead) {
            return new LeadItem(lead.getId(), lead.getName(), lead.getCustomerName(), lead.getExpectedRevenue(),
                    lead.getProbability(), lead.getExpectedCloseDate(), lead.getStage());
        }
    }

    public record CampaignItem(UUID id, String name, LocalDate startsOn, LocalDate endsOn, CampaignStatus status) {
        static CampaignItem from(PromoCampaign campaign) {
            return new CampaignItem(campaign.getId(), campaign.getName(), campaign.getStartsOn(), campaign.getEndsOn(),
                    campaign.getStatus());
        }
    }
}