<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="com.example.erp.frontend.sales.SalesOverviewView" %>
<%@ page import="com.example.erp.frontend.sales.SalesOverviewView.SalesOrderView" %>
<%! String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Prodej</title>
  <link rel="stylesheet" href="assets/base.css">
  <link rel="stylesheet" href="assets/sales.css">
</head>
<body>
  <% SalesOverviewView overview = (SalesOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="sales-page">
    <header class="sales-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">OBCHOD / PRODEJ</span><h1>Prodej</h1><p>Převádějte nabídky do objednávek a mějte pod kontrolou dodávky pro zákazníky.</p></div>
      <a href="#orders" class="primary">Přehled objednávek</a>
    </header>
    <% if (error != null) { %><p class="sales-message error"><%= error %></p><% } if (actionError != null) { %><p class="sales-message error"><%= actionError %></p><% } if (message != null) { %><p class="sales-message"><%= message %></p><% } %>
    <section class="sales-metrics">
      <article><span>Hodnota nabídek</span><strong><%= overview == null ? "-" : amount(overview.quoteValue()) %> Kč</strong><small>čeká na potvrzení</small></article>
      <article><span>Potvrzené objednávky</span><strong><%= overview == null ? "-" : amount(overview.confirmedValue()) %> Kč</strong><small>připraveno k dodání</small></article>
      <article><span>Otevřené nabídky</span><strong><%= overview == null ? "-" : overview.quoteCount() %></strong><small>ke schválení zákazníkem</small></article>
    </section>
    <section id="orders" class="sales-section">
      <div class="section-head"><div><span class="eyebrow">NABÍDKY A OBJEDNÁVKY</span><h2>Obchodní dokumenty</h2></div><span class="order-count"><%= overview == null ? 0 : overview.orders().size() %> dokumenty</span></div>
      <div class="order-list">
        <% if (overview != null) for (SalesOrderView order : overview.orders()) { %>
        <article class="order-card">
          <div class="order-main"><span class="status-chip status-<%= order.status().toLowerCase() %>"><%= order.status().equals("QUOTE") ? "NABÍDKA" : "OBJEDNÁVKA" %></span><h3><%= order.orderNumber() %></h3><p><%= order.customerName() %></p></div>
          <dl><div><dt>Vystaveno</dt><dd><%= order.orderDate() %></dd></div><div><dt>Dodání</dt><dd><%= order.deliveryDate() %></dd></div><div><dt>Celkem</dt><dd><%= amount(order.totalAmount()) %> Kč</dd></div></dl>
          <% if ("QUOTE".equals(order.status())) { %><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><button class="secondary" type="submit">Potvrdit objednávku</button></form><% } else { %><span class="confirmed-label">Potvrzeno k dodání</span><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
</body>
</html>