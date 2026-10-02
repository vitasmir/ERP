package com.example.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WarehouseControllerTests {
    @Test
        void exposesCatalogPricesAndAvailability() {
                WarehouseController controller = new WarehouseController();
                WarehouseController.Product product = controller.product("PORK-NECK-01");
                var availability = controller.productAvailability("PORK-NECK-01");

                assertEquals("08590000000015", product.gtin());
                assertEquals("129.90", product.purchasePriceNet().toPlainString());
                assertEquals("202.07", product.salePriceGross().toPlainString());
                assertEquals(70, availability.get(0).availableQuantity());
                assertEquals("WH-PRAHA", availability.get(0).warehouseCode());
    }

    @Test
        void filtersProductsByCategoryAndSearchText() {
                WarehouseController controller = new WarehouseController();

                assertEquals("PORK-NECK-01", controller.products("meat", "krkovice").get(0).sku());
                assertTrue(controller.products("unknown", null).isEmpty());
    }
}