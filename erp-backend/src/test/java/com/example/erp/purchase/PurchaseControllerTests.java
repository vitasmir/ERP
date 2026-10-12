package com.example.erp.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.erp.catalog.Product;
import com.example.erp.catalog.ProductRepository;
import com.example.erp.inventory.InventoryStockService;
import com.example.erp.inventory.Warehouse;
import com.example.erp.inventory.WarehouseOwnerType;
import com.example.erp.inventory.WarehouseRepository;

class PurchaseControllerTests {
    @Test
    void createsPurchaseOrderUsingSupplierOwningSourceWarehouse() {
        UUID supplierId = UUID.randomUUID();
        UUID sourceWarehouseId = UUID.randomUUID();
        UUID destinationWarehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Supplier supplier = mock(Supplier.class);
        when(supplier.getId()).thenReturn(supplierId);
        when(supplier.getName()).thenReturn("Test supplier");

        Warehouse sourceWarehouse = mock(Warehouse.class);
        when(sourceWarehouse.getId()).thenReturn(sourceWarehouseId);
        when(sourceWarehouse.getOwnerType()).thenReturn(WarehouseOwnerType.SUPPLIER);
        when(sourceWarehouse.getSupplierId()).thenReturn(supplierId);
        when(sourceWarehouse.isActive()).thenReturn(true);

        Warehouse destinationWarehouse = mock(Warehouse.class);
        when(destinationWarehouse.getId()).thenReturn(destinationWarehouseId);
        when(destinationWarehouse.getOwnerType()).thenReturn(WarehouseOwnerType.COMPANY);
        when(destinationWarehouse.isActive()).thenReturn(true);

        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);
        when(product.getPurchasePrice()).thenReturn(BigDecimal.ONE);

        SupplierRepository suppliers = mock(SupplierRepository.class);
        when(suppliers.findById(supplierId)).thenReturn(Optional.of(supplier));
        WarehouseRepository warehouses = mock(WarehouseRepository.class);
        when(warehouses.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouses.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        ProductRepository products = mock(ProductRepository.class);
        when(products.findById(productId)).thenReturn(Optional.of(product));
        PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PurchaseController controller = new PurchaseController(orders, mock(PurchaseOrderLineRepository.class),
                warehouses, suppliers, products, mock(InventoryStockService.class));

        var request = new PurchaseController.CreatePurchaseOrderRequest(supplierId, LocalDate.now(),
                LocalDate.now().plusDays(1), sourceWarehouseId, destinationWarehouseId,
                List.of(new PurchaseController.PurchaseOrderLineRequest(productId, 1, BigDecimal.ONE)));

        PurchaseController.PurchaseOrderResponse response = controller.create(request);

        assertEquals(supplierId, response.supplierId());
        assertEquals("Test supplier", response.supplierName());
    }

    @Test
    void rejectsPurchaseOrderWhenSupplierDoesNotOwnSourceWarehouse() {
        UUID supplierId = UUID.randomUUID();
        UUID otherSupplierId = UUID.randomUUID();
        UUID sourceWarehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Supplier supplier = mock(Supplier.class);
        when(supplier.getId()).thenReturn(supplierId);
        when(supplier.getName()).thenReturn("Test supplier");

        Warehouse sourceWarehouse = mock(Warehouse.class);
        when(sourceWarehouse.getOwnerType()).thenReturn(WarehouseOwnerType.SUPPLIER);
        when(sourceWarehouse.getSupplierId()).thenReturn(otherSupplierId);
        when(sourceWarehouse.isActive()).thenReturn(true);

        SupplierRepository suppliers = mock(SupplierRepository.class);
        when(suppliers.findById(supplierId)).thenReturn(Optional.of(supplier));
        WarehouseRepository warehouses = mock(WarehouseRepository.class);
        when(warehouses.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
        PurchaseController controller = new PurchaseController(orders, mock(PurchaseOrderLineRepository.class),
                warehouses, suppliers, mock(ProductRepository.class), mock(InventoryStockService.class));

        var request = new PurchaseController.CreatePurchaseOrderRequest(supplierId, LocalDate.now(),
                LocalDate.now().plusDays(1), sourceWarehouseId, UUID.randomUUID(),
                List.of(new PurchaseController.PurchaseOrderLineRequest(productId, 1, BigDecimal.ONE)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orders, never()).save(any(PurchaseOrder.class));
    }
}
