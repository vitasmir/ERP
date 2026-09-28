<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="com.example.erp.frontend.pos.PosOverviewView" %>
<%@ page import="com.example.erp.frontend.pos.PosOverviewView.PosTransactionView" %>
<%! String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Pokladna</title>
  <link rel="stylesheet" href="assets/base.css?v=20260928-160510">
  <link rel="stylesheet" href="assets/pos.css?v=20260928-154028">
  <link rel="stylesheet" href="assets/pos-table.css?v=20260928-154028">
</head>
<body>
  <% PosOverviewView overview = (PosOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="pos-page">
    <header class="pos-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PRODEJ / POKLADNA</span><h1>Pokladna</h1><p>Uzavírejte účtenky na prodejně a sledujte aktuální tržbu podle plateb.</p></div>
      <a href="#receipts" class="primary">Otevřené účtenky</a>
    </header>
    <% if (error != null) { %><p class="pos-message error"><%= error %></p><% } if (actionError != null) { %><p class="pos-message error"><%= actionError %></p><% } if (message != null) { %><p class="pos-message"><%= message %></p><% } %>
    <section class="pos-metrics">
      <article><span>Dnešní tržba</span><strong><%= overview == null ? "-" : amount(overview.paidToday()) %> Kč</strong><small>uhrazené účtenky</small></article>
      <article><span>Otevřené účtenky</span><strong><%= overview == null ? "-" : overview.openTransactionCount() %></strong><small>čekají na platbu</small></article>
      <article><span>Položky v obsluze</span><strong><%= overview == null ? "-" : overview.itemCount() %></strong><small>napříč zobrazenými účtenkami</small></article>
    </section>
    <section id="receipts" class="pos-section">
      <div class="section-head"><div><span class="eyebrow">AKTUÁLNÍ ÚČTENKY</span><h2>Obsluha prodejen</h2></div><span class="receipt-count"><%= overview == null ? 0 : overview.transactions().size() %> účtenky</span></div>
      <div class="receipt-table-wrap">
        <table class="receipt-table">
          <thead><tr><th>Účtenka</th><th>Prodejna a čas</th><th>Položky</th><th>Celkem</th><th>Platba</th><th>Obsluha</th></tr></thead>
          <tbody>
            <% if (overview != null) for (PosTransactionView transaction : overview.transactions()) { %>
            <tr>
              <td data-label="Účtenka"><strong><%= transaction.receiptNumber() %></strong><span class="status-chip status-<%= transaction.status().toLowerCase() %>"><%= transaction.status().equals("OPEN") ? "OTEVŘENO" : "UHRAZENO" %></span></td>
              <td data-label="Prodejna a čas"><strong><%= transaction.storeName() %></strong><span><%= transaction.openedAt().replace('T', ' ') %></span></td>
              <td data-label="Položky"><%= transaction.itemCount() %></td>
              <td data-label="Celkem"><strong><%= amount(transaction.totalAmount()) %> Kč</strong></td>
              <td data-label="Platba"><%= transaction.paymentMethod() == null ? "-" : transaction.paymentMethod() %></td>
              <td data-label="Obsluha"><% if ("OPEN".equals(transaction.status())) { %><form method="post" class="payment-form"><input type="hidden" name="id" value="<%= transaction.id() %>"><label>Platba<select name="method"><option value="CARD">Karta</option><option value="CASH">Hotovost</option><option value="VOUCHER">Poukázka</option></select></label><button class="secondary" type="submit">Přijmout platbu</button></form><% } else { %><span class="paid-label">Platba přijata</span><% } %></td>
            </tr>
            <% } %>
          </tbody>
        </table>
      </div>
    </section>
  </main>
</body>
</html>