package com.example.erp;

import org.junit.jupiter.api.Test;
import com.example.erp.promo.CampaignStatus;
import com.example.erp.promo.PromoCampaign;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

class ErpBackendApplicationTests {
    @Test
    void newCampaignStartsInPlannedState() {
        PromoCampaign campaign = new PromoCampaign(UUID.randomUUID(), "Test", UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), LocalDate.now().plusDays(7), BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, 100, BigDecimal.ZERO);
        org.junit.jupiter.api.Assertions.assertEquals(CampaignStatus.PLANNED, campaign.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(0, campaign.getActualQuantity());
    }
}
