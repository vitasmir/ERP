<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.PurchaseOrderView" %>
<%! String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Nákup</title>
  <link rel="stylesheet" href="assets/base.css">
  <link rel="stylesheet" href="assets/purchase.css">
</head>
<body>
  <% PurchaseOverviewView overview = (PurchaseOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="purchase-page">
    <header class="purchase-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / NÁKUP</span><h1>Nákup</h1><p>Řiďte nákupní požadavky, objednávky dodavatelům a očekávané příjmy zboží.</p></div>
      <a href="#orders" class="primary">Přehled nákupu</a>
    </header>
    <% if (error != null) { %><p class="purchase-message error"><%= error %></p><% } if (actionError != null) { %><p class="purchase-message error"><%= actionError %></p><% } if (message != null) { %><p class="purchase-message"><%= message %></p><% } %>
    <section class="purchase-metrics">
      <article><span>Požadavky k objednání</span><strong><%= overview == null ? "-" : amount(overview.requestedValue()) %> Kč</strong><small>čeká na odeslání dodavateli</small></article>
      <article><span>Objednáno u dodavatelů</span><strong><%= overview == null ? "-" : amount(overview.orderedValue()) %> Kč</strong><small>očekáváme na sklad</small></article>
      <article><span>Otevřené požadavky</span><strong><%= overview == null ? "-" : overview.requestedCount() %></strong><small>vyžaduje rozhodnutí nákupu</small></article>
    </section>
    <section id="orders" class="purchase-section">
      <div class="section-head"><div><span class="eyebrow">NÁKUPNÍ POŽADAVKY A OBJEDNÁVKY</span><h2>Plán zásobování</h2></div><span class="order-count"><%= overview == null ? 0 : overview.orders().size() %> dokumenty</span></div>
      <div class="order-list">
        <% if (overview != null) for (PurchaseOrderView order : overview.orders()) { %>
        <article class="order-card">
          <div class="order-main"><span class="status-chip status-<%= order.status().toLowerCase() %>"><%= order.status().equals("REQUESTED") ? "POŽADAVEK" : "OBJEDNÁNO" %></span><h3><%= order.orderNumber() %></h3><p><%= order.supplierName() %></p></div>
          <dl><div><dt>Požadováno</dt><dd><%= order.requestedOn() %></dd></div><div><dt>Očekávané dodání</dt><dd><%= order.expectedDeliveryDate() %></dd></div><div><dt>Celkem</dt><dd><%= amount(order.totalAmount()) %> Kč</dd></div></dl>
          <% if ("REQUESTED".equals(order.status())) { %><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><button class="secondary" type="submit">Vystavit objednávku</button></form><% } else { %><span class="ordered-label">Čeká na dodání</span><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
</body>
</html>