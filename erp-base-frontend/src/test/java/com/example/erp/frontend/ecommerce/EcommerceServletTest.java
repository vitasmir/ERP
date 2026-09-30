package com.example.erp.frontend.ecommerce;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class EcommerceServletTest {
    @Test
    void parseBigDecimalOrDefaultUsesFallbackForBlankOrInvalidValues() {
        assertEquals(new BigDecimal("50.00"), EcommerceServlet.parseBigDecimalOrDefault("", new BigDecimal("50.00")));
        assertEquals(new BigDecimal("50.00"), EcommerceServlet.parseBigDecimalOrDefault("   ", new BigDecimal("50.00")));
        assertEquals(new BigDecimal("50.00"), EcommerceServlet.parseBigDecimalOrDefault("NaN", new BigDecimal("50.00")));
        assertEquals(new BigDecimal("18.34"), EcommerceServlet.parseBigDecimalOrDefault("18.34", new BigDecimal("50.00")));
    }
}
