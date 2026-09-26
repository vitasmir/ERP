package com.example.erp.users;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ApiAccessTests {
    @Test
    void rolesCannotCrossModuleBoundariesOrManageAccounts() {
        assertTrue(ApiAccess.allowed("Účetní", "accounting", false));
        assertFalse(ApiAccess.allowed("Zaměstnanec", "accounting", true));
        assertFalse(ApiAccess.allowed("Účetní", "users", false));
        assertTrue(ApiAccess.allowed("HR", "hr", false));
        assertTrue(ApiAccess.allowed("Vedoucí týmu", "hr", true));
        assertFalse(ApiAccess.allowed("Vedoucí týmu", "hr", false));
        assertTrue(ApiAccess.allowed("Zaměstnanec", "planning", true));
        assertFalse(ApiAccess.allowed("Zaměstnanec", "planning", false));
        assertTrue(ApiAccess.allowed("Administrátor", "users", false));
        assertFalse(ApiAccess.allowed("unknown", "accounting", true));
    }
}