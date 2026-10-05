<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.PurchaseOrderView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.WarehouseView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.ProductView" %>
<%! String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } String warehouseName(java.util.UUID id, java.util.List<WarehouseView> warehouses) { if (id == null || warehouses == null) return "-"; for (WarehouseView warehouse : warehouses) if (id.equals(warehouse.id())) return warehouse.name(); return id.toString(); } %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Nákup</title>
  <link rel="stylesheet" href="assets/base.css?v=20261001-1">
  <link rel="stylesheet" href="assets/purchase.css?v=20261004-1">
</head>
<body>
  <% PurchaseOverviewView overview = (PurchaseOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); java.util.List<WarehouseView> warehouses = (java.util.List<WarehouseView>) request.getAttribute("warehouses"); java.util.List<ProductView> products = (java.util.List<ProductView>) request.getAttribute("products"); %>
  <main class="purchase-page">
    <header class="purchase-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / NÁKUP</span><h1>Nákup</h1><p>Řiďte nákupní požadavky, objednávky dodavatelům a očekávané příjmy zboží.</p></div>
      <div class="purchase-actions"><a href="#orders" class="primary">Přehled nákupu</a><button type="button" class="primary add-order-button" data-open-order-dialog>Přidej objednávku</button></div>
    </header>
    <% if (error != null) { %><p class="purchase-message error"><%= error %></p><% } if (actionError != null) { %><p class="purchase-message error"><%= actionError %></p><% } if (message != null) { %><p class="purchase-message"><%= message %></p><% } %>
    <section class="purchase-metrics">
      <article><span>Požadavky k objednání</span><strong><%= overview == null ? "-" : amount(overview.requestedValue()) %> Kč</strong><small>čeká na odeslání dodavateli</small></article>
      <article><span>Objednáno u dodavatelů</span><strong><%= overview == null ? "-" : amount(overview.orderedValue()) %> Kč</strong><small>očekáváme na sklad</small></article>
      <article><span>Otevřené požadavky</span><strong><%= overview == null ? "-" : overview.requestedCount() %></strong><small>vyžaduje rozhodnutí nákupu</small></article>
    </section>
    <section id="orders" class="purchase-section">
      <div class="section-head"><div><span class="eyebrow">NÁKUPNÍ POŽADAVKY A OBJEDNÁVKY</span><h2>Plán zásobování</h2></div><span class="order-count"><%= overview == null ? 0 : overview.orders().size() %> dokumenty</span></div>
      <div class="order-table-wrap">
        <table class="order-table">
          <thead><tr><th>Dokument</th><th>Dodavatel</th><th>Tok skladu</th><th>Požadováno</th><th>Dodání</th><th class="amount-column">Celkem</th><th>Stav / akce</th></tr></thead>
          <tbody>
            <% if (overview != null) for (PurchaseOrderView order : overview.orders()) { %>
            <tr>
              <td data-label="Dokument"><span class="status-chip status-<%= order.status().toLowerCase() %>"><%= order.status().equals("REQUESTED") ? "POŽADAVEK" : "OBJEDNÁNO" %></span><strong><%= order.orderNumber() %></strong></td>
              <td data-label="Dodavatel"><%= order.supplierName() %></td>
              <td data-label="Tok skladu"><%= warehouseName(order.sourceWarehouseId(), warehouses) %> → <%= warehouseName(order.destinationWarehouseId(), warehouses) %><br><small><%= order.quantity() == null ? "-" : order.receivedQuantity() + " / " + order.quantity() %> ks</small></td>
              <td data-label="Požadováno"><%= order.requestedOn() %></td>
              <td data-label="Dodání"><%= order.expectedDeliveryDate() %></td>
              <td data-label="Celkem" class="amount-column"><strong><%= amount(order.totalAmount()) %> Kč</strong></td>
              <td data-label="Stav / akce"><% if ("REQUESTED".equals(order.status())) { %><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><button class="secondary" type="submit">Vystavit objednávku</button></form><% } else if ("ORDERED".equals(order.status()) && order.quantity() != null) { %><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><input type="hidden" name="action" value="receive"><input type="hidden" name="quantity" value="<%= order.quantity() - order.receivedQuantity() %>"><button class="secondary" type="submit">Přijmout na sklad</button></form><% } else { %><span class="ordered-label"><%= "RECEIVED".equals(order.status()) ? "Přijato na sklad" : "Čeká na dodání" %></span><% } %></td>
            </tr>
            <% } %>
          </tbody>
        </table>
      </div>
    </section>
  </main>
  <dialog class="order-dialog" data-order-dialog aria-labelledby="new-order-title">
    <div class="order-dialog-head"><div><span class="eyebrow">NOVÝ DOKUMENT</span><h2 id="new-order-title">Přidej objednávku</h2></div><button type="button" class="dialog-close" data-close-order-dialog aria-label="Zavřít">×</button></div>
      <form method="post" class="new-order-form">
        <input type="hidden" name="action" value="create">
        <label>Dodavatel<input name="supplierName" required maxlength="200" placeholder="Název dodavatele"></label>
        <label>Požadováno<input type="date" name="requestedOn" required></label>
        <label>Očekávané dodání<input type="date" name="expectedDeliveryDate" required></label>
        <label>Celkem Kč<input type="number" name="totalAmount" min="0" step="0.01" required placeholder="0.00"></label>
        <label>Dodavatelský sklad<select name="sourceWarehouseId" required><option value="">Vyberte sklad</option><% if (warehouses != null) for (WarehouseView warehouse : warehouses) if ("SUPPLIER".equals(warehouse.ownerType())) { %><option value="<%= warehouse.id() %>"><%= warehouse.name() %></option><% } %></select></label>
        <label>Firemní cílový sklad<select name="destinationWarehouseId" required><option value="">Vyberte sklad</option><% if (warehouses != null) for (WarehouseView warehouse : warehouses) if ("COMPANY".equals(warehouse.ownerType())) { %><option value="<%= warehouse.id() %>"><%= warehouse.name() %></option><% } %></select></label>
        <label>Produkt<select name="productId" required><option value="">Vyberte produkt</option><% if (products != null) for (ProductView product : products) { %><option value="<%= product.id() %>"><%= product.name() %> (<%= product.sku() %>)</option><% } %></select></label>
        <label>Množství<input type="number" name="quantity" min="1" step="1" required placeholder="1"></label>
        <button class="secondary" type="submit">Vytvořit objednávku</button>
      </form>
  </dialog>
  <script>
    const orderDialog = document.querySelector('[data-order-dialog]');
    document.querySelector('[data-open-order-dialog]').addEventListener('click', () => orderDialog.showModal());
    document.querySelector('[data-close-order-dialog]').addEventListener('click', () => orderDialog.close());
    orderDialog.addEventListener('click', event => { if (event.target === orderDialog) orderDialog.close(); });
  </script>
  <script src="assets/base.js?v=20260928-160511"></script>
</body>
</html>