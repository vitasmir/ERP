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

import com.example.erp.catalog.ProductRepository;
import com.example.erp.inventory.InventoryStockService;
import com.example.erp.inventory.Warehouse;
import com.example.erp.inventory.WarehouseOwnerType;
import com.example.erp.inventory.WarehouseRepository;
import com.example.erp.users.ErpUser;

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

        SupplierRepository suppliers = mock(SupplierRepository.class);
        when(suppliers.findById(supplierId)).thenReturn(Optional.of(supplier));
        WarehouseRepository warehouses = mock(WarehouseRepository.class);
        when(warehouses.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouses.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        ProductRepository products = mock(ProductRepository.class);
        when(products.existsById(productId)).thenReturn(true);
        PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PurchaseController controller = new PurchaseController(orders, mock(PurchaseOrderLineRepository.class),
                warehouses, suppliers, products, mock(InventoryStockService.class), mock(PurchaseReceipts.class));

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
                warehouses, suppliers, mock(ProductRepository.class), mock(InventoryStockService.class),
                mock(PurchaseReceipts.class));

        var request = new PurchaseController.CreatePurchaseOrderRequest(supplierId, LocalDate.now(),
                LocalDate.now().plusDays(1), sourceWarehouseId, UUID.randomUUID(),
                List.of(new PurchaseController.PurchaseOrderLineRequest(productId, 1, BigDecimal.ONE)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(orders, never()).save(any(PurchaseOrder.class));
    }

    @Test
    void partialReceiptCombinesProductValueAndKeepsOrderOpen() {
        ReceiptFixture fixture = new ReceiptFixture();
        UUID receiptId = UUID.randomUUID();
        var request = new PurchaseController.ReceivePurchaseRequest(null, receiptId, List.of(
                new PurchaseController.ReceivePurchaseLine(fixture.first.getId(), 1),
                new PurchaseController.ReceivePurchaseLine(fixture.second.getId(), 1)));

        var response = fixture.controller.receive(fixture.order.getId(), request, fixture.actor);

        assertEquals(PurchaseOrderStatus.ORDERED, response.status());
        assertEquals(2, response.receivedQuantity());
        assertEquals(1, fixture.first.remainingQuantity());
        assertEquals(1, fixture.second.remainingQuantity());
        verify(fixture.inventory).receivePurchase(fixture.productId, fixture.destinationId, "Test warehouse",
                2, new BigDecimal("30.00"), fixture.order.getOrderNumber(), fixture.actor);
        verify(fixture.receipts).record(receiptId, fixture.order.getId(), fixture.signature(), List.of(
                new PurchaseReceipts.ReceiptLine(fixture.first.getId(), fixture.productId, 1, new BigDecimal("10.00")),
                new PurchaseReceipts.ReceiptLine(fixture.second.getId(), fixture.productId, 1, new BigDecimal("20.00"))),
                fixture.actor);
    }

    @Test
    void retryAfterFullReceiptDoesNotReceiveStockAgain() {
        ReceiptFixture fixture = new ReceiptFixture();
        UUID receiptId = UUID.randomUUID();
        fixture.first.receive(2);
        fixture.second.receive(2);
        fixture.order.receive(4);
        when(fixture.receipts.alreadyReceived(receiptId, fixture.order.getId(), fixture.signature())).thenReturn(true);
        var request = new PurchaseController.ReceivePurchaseRequest(null, receiptId, List.of(
                new PurchaseController.ReceivePurchaseLine(fixture.second.getId(), 1),
                new PurchaseController.ReceivePurchaseLine(fixture.first.getId(), 1)));

        var response = fixture.controller.receive(fixture.order.getId(), request, fixture.actor);

        assertEquals(PurchaseOrderStatus.RECEIVED, response.status());
        assertEquals(4, response.receivedQuantity());
        org.mockito.Mockito.verifyNoInteractions(fixture.inventory);
        verify(fixture.receipts, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void invalidReceiptDoesNotRecordOrChangeStock() {
        ReceiptFixture fixture = new ReceiptFixture();
        var first = new PurchaseController.ReceivePurchaseLine(fixture.first.getId(), 1);
        List<PurchaseController.ReceivePurchaseRequest> requests = List.of(
                new PurchaseController.ReceivePurchaseRequest(null, null, List.of(first)),
                new PurchaseController.ReceivePurchaseRequest(1, UUID.randomUUID(), List.of(first)),
                new PurchaseController.ReceivePurchaseRequest(null, UUID.randomUUID(), List.of()),
                new PurchaseController.ReceivePurchaseRequest(null, UUID.randomUUID(), List.of(first, first)),
                new PurchaseController.ReceivePurchaseRequest(null, UUID.randomUUID(), List.of(
                        new PurchaseController.ReceivePurchaseLine(UUID.randomUUID(), 1))),
                new PurchaseController.ReceivePurchaseRequest(null, UUID.randomUUID(), List.of(
                        new PurchaseController.ReceivePurchaseLine(fixture.first.getId(), 0))));
        for (var request : requests) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> fixture.controller.receive(fixture.order.getId(), request, fixture.actor));
            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
        var tooMuch = new PurchaseController.ReceivePurchaseRequest(null, UUID.randomUUID(), List.of(
                new PurchaseController.ReceivePurchaseLine(fixture.first.getId(), 3)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> fixture.controller.receive(fixture.order.getId(), tooMuch, fixture.actor)).getStatusCode());
        assertEquals(0, fixture.order.getReceivedQuantity());
        assertEquals(0, fixture.first.getReceivedQuantity());
        org.mockito.Mockito.verifyNoInteractions(fixture.inventory);
        verify(fixture.receipts, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void legacyEmptyRequestReceivesAllRemainingLines() {
        ReceiptFixture fixture = new ReceiptFixture();
        var response = fixture.controller.receive(fixture.order.getId(),
                new PurchaseController.ReceivePurchaseRequest(null, null, null), fixture.actor);

        assertEquals(PurchaseOrderStatus.RECEIVED, response.status());
        assertEquals(4, response.receivedQuantity());
        verify(fixture.inventory).receivePurchase(fixture.productId, fixture.destinationId, "Test warehouse",
                4, new BigDecimal("60.00"), fixture.order.getOrderNumber(), fixture.actor);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> fixture.controller.order(fixture.order.getId())).getStatusCode());
    }

    private static class ReceiptFixture {
        final UUID productId = UUID.randomUUID();
        final UUID destinationId = UUID.randomUUID();
        final PurchaseOrder order = PurchaseOrder.create("TEST-RECEIPT", "Test supplier", LocalDate.now(),
                LocalDate.now(), new BigDecimal("60.00"), null, destinationId, null, productId, 4);
        final PurchaseOrderLine first = PurchaseOrderLine.create(order.getId(), productId, 2, new BigDecimal("10.00"));
        final PurchaseOrderLine second = PurchaseOrderLine.create(order.getId(), productId, 2, new BigDecimal("20.00"));
        final InventoryStockService inventory = mock(InventoryStockService.class);
        final PurchaseReceipts receipts = mock(PurchaseReceipts.class);
        final ErpUser actor = mock(ErpUser.class);
        final PurchaseController controller;

        ReceiptFixture() {
            order.order();
            PurchaseOrderRepository orders = mock(PurchaseOrderRepository.class);
            when(orders.findForUpdate(order.getId())).thenReturn(Optional.of(order));
            when(orders.save(order)).thenReturn(order);
            PurchaseOrderLineRepository lines = mock(PurchaseOrderLineRepository.class);
            when(lines.findAllByPurchaseOrderIdOrderById(order.getId())).thenReturn(List.of(first, second));
            Warehouse destination = mock(Warehouse.class);
            when(destination.getId()).thenReturn(destinationId);
            when(destination.getName()).thenReturn("Test warehouse");
            when(destination.getOwnerType()).thenReturn(WarehouseOwnerType.COMPANY);
            when(destination.isActive()).thenReturn(true);
            WarehouseRepository warehouses = mock(WarehouseRepository.class);
            when(warehouses.findById(destinationId)).thenReturn(Optional.of(destination));
            controller = new PurchaseController(orders, lines, warehouses, mock(SupplierRepository.class),
                    mock(ProductRepository.class), inventory, receipts);
        }

        String signature() {
            return List.of(first.getId(), second.getId()).stream().sorted()
                    .map(id -> id + ":1").collect(java.util.stream.Collectors.joining(","));
        }
    }
}
