package com.example.erp.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ApiAccessTests {
    @Test
    void databaseRolesCannotCrossModuleBoundariesOrManageAccounts() {
        assertTrue(ApiAccess.allowed("Administrátor", "accounting", false));
        assertTrue(ApiAccess.allowed("Nákupčí", "purchase", false));
        assertTrue(ApiAccess.allowed("Logistika", "inventory", false));
        assertFalse(ApiAccess.allowed("Nákupčí", "accounting", true));
        assertFalse(ApiAccess.allowed("Logistika", "catalog", true));
        assertFalse(ApiAccess.allowed("Nákupčí", "users", false));
        assertTrue(ApiAccess.allowed("Administrátor", "users", false));
        assertFalse(ApiAccess.allowed("Admin", "users", false));
        assertFalse(ApiAccess.allowed("HR", "hr", false));
        assertFalse(ApiAccess.allowed("Zaměstnanec", "planning", true));
        assertFalse(ApiAccess.allowed("unknown", "accounting", true));
    }
}