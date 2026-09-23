<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.manufacturing.ManufacturingOverviewView" %>
<%@ page import="com.example.erp.frontend.manufacturing.ManufacturingOverviewView.ManufacturingOrderView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Výroba</title>
  <link rel="stylesheet" href="assets/base.css">
  <link rel="stylesheet" href="assets/manufacturing.css">
</head>
<body>
  <% ManufacturingOverviewView overview = (ManufacturingOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="manufacturing-page">
    <header class="manufacturing-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / VÝROBA</span><h1>Výroba</h1><p>Plánujte výrobní příkazy, sledujte rozpracovanost a uzavírejte dokončenou produkci.</p></div>
      <a href="#orders" class="primary">Plán výroby</a>
    </header>
    <% if (error != null) { %><p class="manufacturing-message error"><%= error %></p><% } if (actionError != null) { %><p class="manufacturing-message error"><%= actionError %></p><% } if (message != null) { %><p class="manufacturing-message"><%= message %></p><% } %>
    <section class="manufacturing-metrics">
      <article><span>Plánovaná výroba</span><strong><%= overview == null ? "-" : overview.plannedQuantity() %> ks</strong><small>v neuzavřených příkazech</small></article>
      <article><span>Dokončená výroba</span><strong><%= overview == null ? "-" : overview.completedQuantity() %> ks</strong><small>z aktuálního plánu</small></article>
      <article><span>Rozpracované příkazy</span><strong><%= overview == null ? "-" : overview.activeOrderCount() %></strong><small>na výrobních pracovištích</small></article>
    </section>
    <section id="orders" class="manufacturing-section">
      <div class="section-head"><div><span class="eyebrow">VÝROBNÍ PŘÍKAZY</span><h2>Plán a průběh výroby</h2></div><span class="order-count"><%= overview == null ? 0 : overview.orders().size() %> příkazy</span></div>
      <div class="manufacturing-list">
        <% if (overview != null) for (ManufacturingOrderView order : overview.orders()) { int progress = order.plannedQuantity() == 0 ? 0 : order.completedQuantity() * 100 / order.plannedQuantity(); %>
        <article class="manufacturing-card">
          <div class="manufacturing-main"><span class="status-chip status-<%= order.status().toLowerCase() %>"><%= order.status().equals("IN_PROGRESS") ? "PROBÍHÁ" : order.status().equals("PLANNED") ? "PLÁNOVÁNO" : "DOKONČENO" %></span><h3><%= order.orderNumber() %></h3><p><%= order.productName() %> · <%= order.workCenter() %></p></div>
          <div class="progress-group"><div class="progress-meta"><span>Hotovo <b><%= order.completedQuantity() %> / <%= order.plannedQuantity() %> ks</b></span><span><%= progress %> %</span></div><div class="progress-track"><span style="width:<%= progress %>%"></span></div><small>Plánované datum: <%= order.plannedDate() %></small></div>
          <% if (!"COMPLETED".equals(order.status())) { %><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><button class="secondary" type="submit">Dokončit příkaz</button></form><% } else { %><span class="completed-label">Výroba uzavřena</span><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
</body>
</html>