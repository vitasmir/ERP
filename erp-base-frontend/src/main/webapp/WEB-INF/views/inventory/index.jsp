<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="com.example.erp.frontend.inventory.InventoryOverviewView" %>
<%@ page import="com.example.erp.frontend.inventory.InventoryOverviewView.InventoryItemView" %>
<%! String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Sklad</title>
  <link rel="stylesheet" href="assets/base.css">
  <link rel="stylesheet" href="assets/inventory.css">
</head>
<body>
  <% InventoryOverviewView overview = (InventoryOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="inventory-page">
    <header class="inventory-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / SKLAD</span><h1>Sklad</h1><p>Sledujte zásoby podle lokace, minimální množství a příjem zboží na sklad.</p></div>
      <a href="#stock" class="primary">Přehled zásob</a>
    </header>
    <% if (error != null) { %><p class="inventory-message error"><%= error %></p><% } if (actionError != null) { %><p class="inventory-message error"><%= actionError %></p><% } if (message != null) { %><p class="inventory-message"><%= message %></p><% } %>
    <section class="inventory-metrics">
      <article><span>Zásoba celkem</span><strong><%= overview == null ? "-" : overview.totalQuantity() %> jednotek</strong><small>součet evidovaných kusů a měrných jednotek</small></article>
      <article><span>Hodnota zásob</span><strong><%= overview == null ? "-" : amount(overview.stockValue()) %> Kč</strong><small>v pořizovacích cenách</small></article>
      <article><span>Pod minimem</span><strong><%= overview == null ? "-" : overview.lowStockCount() %></strong><small>lokace vyžadují doplnění</small></article>
    </section>
    <section id="stock" class="inventory-section">
      <div class="section-head"><div><span class="eyebrow">SKLADOVÉ POLOŽKY</span><h2>Stav zásob podle lokace</h2></div><span class="item-count"><%= overview == null ? 0 : overview.items().size() %> lokace</span></div>
      <div class="category-tree">
        <% String categoryPath = null; if (overview != null) for (InventoryItemView item : overview.items()) { if (!item.categoryPath().equals(categoryPath)) { if (categoryPath != null) { %></div></details><% } categoryPath = item.categoryPath(); %><details class="category-branch" open><summary><span>Kategorie</span> <strong><%= categoryPath %></strong></summary><div class="stock-list"><% } boolean lowStock = item.quantity() < item.reorderLevel(); %>
        <article class="stock-card <%= lowStock ? "low-stock" : "" %>">
          <div class="stock-main"><span class="status-chip <%= lowStock ? "status-low" : "status-ok" %>"><%= lowStock ? "DOPLNIT" : "V POŘÁDKU" %></span><h3><%= item.locationName() %></h3><p><%= item.productName() %> · <%= item.sku() %></p></div>
          <dl><div><dt>Skladem</dt><dd><%= item.quantity() %> <%= item.unit() %></dd></div><div><dt>Minimum</dt><dd><%= item.reorderLevel() %> <%= item.unit() %></dd></div><div><dt>Jednotková cena</dt><dd><%= amount(item.unitCost()) %> Kč</dd></div></dl>
          <form method="post" class="receive-form"><input type="hidden" name="id" value="<%= item.id() %>"><label>Příjem <input type="number" name="quantity" min="1" value="1" required> <%= item.unit() %></label><button class="secondary" type="submit">Zaevidovat příjem</button></form>
        </article>
        <% } if (categoryPath != null) { %></div></details><% } %>
      </div>
    </section>
  </main>
</body>
</html>