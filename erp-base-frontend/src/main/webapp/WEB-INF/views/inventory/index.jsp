<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="com.example.erp.frontend.inventory.InventoryOverviewView" %>
<%@ page import="com.example.erp.frontend.inventory.InventoryOverviewView.InventoryItemView" %>
<%@ page import="com.example.erp.frontend.inventory.InventoryOverviewView.InventoryProductView" %>
<%@ page import="com.example.erp.frontend.inventory.InventoryOverviewView.WarehouseStockView" %>
<%@ page import="com.example.erp.frontend.inventory.InventoryMovementsView" %>
<%@ page import="com.example.erp.frontend.inventory.InventoryMovementsView.MovementView" %>
<%@ page import="static com.example.erp.frontend.base.RolesServlet.escapeHtml" %>
<%! String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Sklad</title>
  <link rel="stylesheet" href="assets/base.css?v=20261001-1">
  <link rel="stylesheet" href="assets/inventory.css?v=20260928-154028">
  <link rel="stylesheet" href="assets/inventory-views.css?v=20261004-3">
  <style>.inventory-image-modal{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;padding:24px;background:rgba(15,29,24,.82);cursor:zoom-out}.inventory-image-modal-content{position:relative;display:grid;place-items:center;max-width:calc(100vw - 48px);max-height:calc(100vh - 48px)}.inventory-image-modal img{display:block;max-width:calc(100vw - 48px);max-height:calc(100vh - 48px);width:auto;height:auto;object-fit:contain;box-shadow:0 20px 60px rgba(0,0,0,.35);cursor:default}.inventory-image-modal-close{position:absolute;z-index:1;top:-18px;right:-18px;width:36px;height:36px;border:0;border-radius:50%;background:#fffdf8;color:#17362b;font-size:26px;line-height:1;cursor:pointer;box-shadow:0 4px 14px rgba(0,0,0,.25)}@media(max-width:520px){.inventory-image-modal{padding:12px}.inventory-image-modal img{max-width:calc(100vw - 24px);max-height:calc(100vh - 24px)}} </style>
  <style>.product-image-popup{position:fixed;z-index:1000;width:min(420px,calc(100vw - 32px));height:min(420px,calc(100vh - 32px));display:grid;place-items:center;padding:10px;background:#fffdf9;border:1px solid #d8d5ce;box-shadow:0 18px 50px rgba(20,29,38,.3);pointer-events:none}.product-image-popup img{display:block;max-width:100%;max-height:100%;width:auto;height:auto;object-fit:contain}</style>
</head>
<body>
  <%
    InventoryOverviewView overview = (InventoryOverviewView) request.getAttribute("overview");
    String error = (String) request.getAttribute("error");
    String message = request.getParameter("message");
    String actionError = request.getParameter("error");
    String selectedWarehouse = request.getParameter("warehouseName");
    String selectedView = java.util.Set.of("products", "history").contains(
        String.valueOf(request.getParameter("view"))) ? request.getParameter("view") : "stock";
    boolean canEdit = overview != null && overview.canEdit();
  %>
  <main class="inventory-page">
    <header class="inventory-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / SKLAD</span><h1>Sklad</h1><p>Zásoby podle lokace, příjem, výdej a dohledatelná historie pohybů.</p></div>
      <div class="view-switch" role="tablist" aria-label="Pohled skladu">
        <% for (String tab : java.util.List.of("stock", "products", "history")) { %>
          <button id="tab-<%= tab %>" class="view-tab <%= tab.equals(selectedView) ? "active" : "" %>"
              type="button" role="tab" aria-selected="<%= tab.equals(selectedView) %>" aria-controls="<%= tab %>"
              tabindex="<%= tab.equals(selectedView) ? "0" : "-1" %>" data-inventory-view="<%= tab %>">
            <%= "stock".equals(tab) ? "Sklad" : "products".equals(tab) ? "Produkty" : "Historie" %>
          </button>
        <% } %>
      </div>
    </header>
    <% if (error != null) { %><p class="inventory-message error" role="alert"><%= escapeHtml(error) %></p><% } %>
    <% if (actionError != null) { %><p class="inventory-message error" role="alert"><%= escapeHtml(actionError) %></p><% } %>
    <% if (message != null) { %><p class="inventory-message" role="status"><%= escapeHtml(message) %></p><% } %>
    <section class="inventory-metrics">
      <article><span>Zásoba celkem</span><strong><%= overview == null ? "-" : overview.totalQuantity() %> jednotek</strong><small>součet evidovaných kusů a měrných jednotek</small></article>
      <article><span>Hodnota zásob</span><strong><%= overview == null ? "-" : amount(overview.stockValue()) %> Kč</strong><small>v pořizovacích cenách</small></article>
      <article><span>Pod minimem</span><strong><%= overview == null ? "-" : overview.lowStockCount() %></strong><small>lokace vyžadují doplnění</small></article>
    </section>
    <div class="inventory-filters" <%= "history".equals(selectedView) ? "hidden" : "" %>>
      <label for="inventory-search">Hledat produkt, SKU nebo lokaci
        <input id="inventory-search" type="search" placeholder="Název nebo SKU">
      </label>
      <label class="inventory-filter-check"><input id="inventory-low-stock" type="checkbox"> Jen pod minimem</label>
      <span id="inventory-filter-count" role="status" aria-live="polite"></span>
    </div>
    <section id="stock" class="inventory-section inventory-view-panel" role="tabpanel" aria-labelledby="tab-stock"
        <%= "stock".equals(selectedView) ? "" : "hidden" %>>
      <div class="section-head"><div><span class="eyebrow">SKLADOVÉ POLOŽKY</span><h2>Stav zásob podle lokace</h2></div><span class="item-count"><%= overview == null ? 0 : overview.items().size() %> lokace</span></div>
      <div class="category-tree">
        <% String categoryPath = null; if (overview != null) for (InventoryItemView item : overview.items()) { if (!item.categoryPath().equals(categoryPath)) { if (categoryPath != null) { %></tbody></table></div></details><% } categoryPath = item.categoryPath(); %><details class="category-branch" open><summary><span>Kategorie</span> <strong><%= escapeHtml(categoryPath) %></strong></summary><div class="stock-table-wrap"><table class="stock-table"><thead><tr><th>Lokace</th><th>Položka</th><th>Obrázek</th><th>Skladem</th><th>Minimum</th><th>Jednotková cena</th><th>Pohyb zásob</th></tr></thead><tbody><% } boolean lowStock = item.quantity() < item.reorderLevel(); %>
        <tr class="<%= lowStock ? "low-stock" : "" %>" data-stock-row data-low-stock="<%= lowStock %>"
            data-search="<%= escapeHtml(item.productName() + " " + item.sku() + " " + item.locationName()) %>">
          <td data-label="Lokace"><span class="status-chip <%= lowStock ? "status-low" : "status-ok" %>"><%= lowStock ? "DOPLNIT" : "V POŘÁDKU" %></span><strong><%= escapeHtml(item.locationName()) %></strong></td>
          <td data-label="Položka"><strong><%= escapeHtml(item.productName()) %></strong><span><%= escapeHtml(item.sku()) %></span></td>
          <td data-label="Obrázek"><div class="stock-item-image"><% if (item.imageUrl() != null && !item.imageUrl().isBlank()) { %><img src="<%= escapeHtml(item.imageUrl()) %>" alt="<%= escapeHtml(item.productName()) %>"><% } else { %><span>FM</span><% } %></div></td>
          <td data-label="Skladem"><strong><%= item.quantity() %> <%= escapeHtml(item.unit()) %></strong></td>
          <td data-label="Minimum">
            <% if (canEdit) { %>
              <input form="receive-form-<%= item.id() %>" type="number" name="reorderLevel" min="0" max="2147483647"
                  step="1" value="<%= item.reorderLevel() %>" aria-label="Minimum <%= escapeHtml(item.productName()) %>" required>
            <% } else { %><%= item.reorderLevel() %><% } %> <%= escapeHtml(item.unit()) %>
          </td>
          <td data-label="Jednotková cena">
            <% if (canEdit) { %>
              <input form="receive-form-<%= item.id() %>" type="number" name="unitCost" min="0" max="9999999999.99"
                  step="0.01" value="<%= amount(item.unitCost()) %>" aria-label="Jednotková cena <%= escapeHtml(item.productName()) %>" required>
            <% } else { %><%= amount(item.unitCost()) %><% } %> Kč
          </td>
          <td data-label="Pohyb zásob">
            <% if (canEdit) { %>
              <form id="receive-form-<%= item.id() %>" method="post" class="stock-movement-form">
                <input type="hidden" name="id" value="<%= item.id() %>">
                <label>Typ<select name="action" data-stock-action><option value="receive">Příjem</option><option value="dispatch">Výdej</option></select></label>
                <label>Množství (<%= escapeHtml(item.unit()) %>)
                  <input type="number" name="quantity" min="1" max="2147483647" step="1" value="1"
                      data-available="<%= item.quantity() %>" required>
                </label>
                <label>Doklad / reference<input name="reference" maxlength="120" placeholder="Číslo dokladu" required></label>
                <details><summary>Poznámka</summary><textarea name="note" rows="2" maxlength="500" aria-label="Poznámka k pohybu"></textarea></details>
                <button class="secondary" type="submit">Zaúčtovat příjem</button>
              </form>
            <% } else { %><span>Pouze pro čtení</span><% } %>
            <a class="inventory-history-link" href="inventory?view=history&amp;itemId=<%= item.id() %>">Historie položky</a>
          </td>
        </tr>
        <% } if (categoryPath != null) { %></tbody></table></div></details><% } %>
      </div>
    </section>
    <section id="products" class="inventory-section inventory-view-panel" role="tabpanel" aria-labelledby="tab-products"
        <%= "products".equals(selectedView) ? "" : "hidden" %>>
      <div class="section-head"><div><span class="eyebrow">PRODUKTY VE SKLADU</span><h2>Produkty a dostupnost</h2></div><div class="warehouse-picker"><label for="warehouse-select">Vybraný sklad</label><select id="warehouse-select"><% if (overview != null && !overview.products().isEmpty()) { for (WarehouseStockView warehouse : overview.products().get(0).warehouses()) { %><option value="<%= warehouse.locationName() %>" <%= warehouse.locationName().equals(selectedWarehouse) ? "selected" : "" %>><%= warehouse.locationName() %></option><% } } %></select></div><span class="item-count"><%= overview == null ? 0 : overview.products().size() %> produktů</span></div>
      <div class="warehouse-product-grid">
        <% if (overview != null) for (InventoryProductView product : overview.products()) { %>
        <%
          boolean productLowStock = overview.items().stream()
              .anyMatch(item -> item.sku().equals(product.sku()) && item.quantity() < item.reorderLevel());
        %>
        <article class="warehouse-product-card" data-product-card data-low-stock="<%= productLowStock %>"
            data-search="<%= escapeHtml(product.productName() + " " + product.sku()) %>">
          <div class="warehouse-product-media"><% if (product.imageUrl() != null && !product.imageUrl().isBlank()) { %><img src="<%= product.imageUrl() %>" alt="<%= product.productName() %>"><% } else { %><span>Bez obrázku</span><% } %></div>
          <div class="warehouse-product-body"><span class="product-sku"><%= product.sku() %></span><h3><%= product.productName() %></h3><p><%= product.description() == null || product.description().isBlank() ? "Bez popisu" : product.description() %></p><small><%= product.categoryPath() %> · <%= product.locationCount() %> lokace</small></div>
          <div class="warehouse-product-stock"><div><dt>Celkem na skladech</dt><dd><%= product.warehouseQuantity() %> <%= product.unit() %></dd></div><div><dt>Z hlavního skladu k dispozici</dt><dd><%= product.centralQuantity() %> <%= product.unit() %></dd></div></div>
          <div class="warehouse-location-list">
            <% for (WarehouseStockView warehouse : product.warehouses()) { %>
              <div class="warehouse-location-row" data-warehouse="<%= escapeHtml(warehouse.locationName()) %>">
                <dl>
                  <div><dt>Na vybraném skladu</dt><dd><%= warehouse.quantity() %> <%= escapeHtml(product.unit()) %></dd></div>
                  <div>
                    <dt>Objednat z hlavního skladu</dt>
                    <dd>
                      <% if (canEdit) { %>
                        <form method="post" class="order-form">
                          <input type="hidden" name="action" value="order">
                          <input type="hidden" name="productId" value="<%= product.productId() %>">
                          <input type="hidden" name="locationName" value="<%= escapeHtml(warehouse.locationName()) %>">
                          <input type="number" name="quantity" min="0" max="2147483647" step="1" value="<%= warehouse.orderedFromCentral() %>"
                              aria-label="Objednat <%= escapeHtml(product.productName()) %>" required>
                          <span><%= escapeHtml(product.unit()) %></span><button class="secondary" type="submit">Uložit</button>
                        </form>
                      <% } else { %><%= warehouse.orderedFromCentral() %> <%= escapeHtml(product.unit()) %><% } %>
                    </dd>
                  </div>
                </dl>
              </div>
            <% } %>
          </div>
        </article>
        <% } %>
      </div>
    </section>
    <%@ include file="history.jspf" %>
  </main>
  <script src="assets/inventory.js?v=20261004-1"></script>
</body>
</html>