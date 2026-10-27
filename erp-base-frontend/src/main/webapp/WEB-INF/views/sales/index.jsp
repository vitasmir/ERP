<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="com.example.erp.frontend.sales.SalesOverviewView" %>
<%@ page import="com.example.erp.frontend.sales.SalesOverviewView.SalesOrderView" %>
<%! String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } %>
<!doctype html>
<html lang="cs-CZ">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Prodej</title>
  <link rel="stylesheet" href="assets/base.css?v=20261001-1">
  <link rel="stylesheet" href="assets/sales.css?v=20260928-160507">
</head>
<body>
  <% SalesOverviewView overview = (SalesOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="sales-page">
    <header class="sales-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">OBCHOD / PRODEJ</span><h1>Prodej</h1><p>Převádějte nabídky do objednávek a mějte pod kontrolou dodávky pro zákazníky.</p></div>
    </header>
    <% if (error != null) { %><p class="sales-message error"><%= error %></p><% } if (actionError != null) { %><p class="sales-message error"><%= actionError %></p><% } if (message != null) { %><p class="sales-message"><%= message %></p><% } %>
    <section class="sales-metrics">
      <article><span>Hodnota nabídek</span><strong><%= overview == null ? "-" : amount(overview.quoteValue()) %> Kč</strong><small>čeká na potvrzení</small></article>
      <article><span>Potvrzené objednávky</span><strong><%= overview == null ? "-" : amount(overview.confirmedValue()) %> Kč</strong><small>připraveno k dodání</small></article>
      <article><span>Otevřené nabídky</span><strong><%= overview == null ? "-" : overview.quoteCount() %></strong><small>ke schválení zákazníkem</small></article>
    </section>
    <section id="orders" class="sales-section">
      <div class="section-head"><div><span class="eyebrow">NABÍDKY A OBJEDNÁVKY</span><h2>Obchodní dokumenty</h2></div><span class="order-count"><%= overview == null ? 0 : overview.orders().size() %> dokumenty</span></div>
      <div class="sales-table-wrap">
        <table class="sales-table">
          <thead><tr><th>Dokument</th><th>Zákazník</th><th>Vystaveno</th><th>Dodání</th><th class="amount-column">Celkem</th><th>Stav / akce</th></tr></thead>
          <tbody>
            <% if (overview != null) for (SalesOrderView order : overview.orders()) { %>
            <tr>
              <td data-label="Dokument"><span class="status-chip status-<%= order.status().toLowerCase() %>"><%= order.status().equals("QUOTE") ? "NABÍDKA" : "OBJEDNÁVKA" %></span><strong><%= order.orderNumber() %></strong></td>
              <td data-label="Zákazník"><%= order.customerName() %></td>
              <td data-label="Vystaveno"><%= order.orderDate() %></td>
              <td data-label="Dodání"><%= order.deliveryDate() %></td>
              <td data-label="Celkem" class="amount-column"><strong><%= amount(order.totalAmount()) %> Kč</strong></td>
              <td data-label="Stav / akce">
                <div class="sales-actions">
                  <% if ("QUOTE".equals(order.status())) { %><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><input type="hidden" name="action" value="confirm"><button class="secondary" type="submit">Potvrdit objednávku</button></form><% } else { %><span class="confirmed-label">Potvrzeno k dodání</span><% } %>
                  <details class="sales-edit"><summary>Upravit</summary><form method="post"><h3 class="sales-dialog-title">Upravit objednávku</h3><input type="hidden" name="action" value="update"><input type="hidden" name="id" value="<%= order.id() %>"><label>Číslo dokumentu<input name="orderNumber" value="<%= order.orderNumber() %>" required maxlength="30"></label><label>Zákazník<input name="customerName" value="<%= order.customerName() %>" required maxlength="200"></label><label>Vystaveno<input type="date" name="orderDate" value="<%= order.orderDate() %>" required></label><label>Dodání<input type="date" name="deliveryDate" value="<%= order.deliveryDate() %>" required></label><label>Celkem<input type="number" name="totalAmount" value="<%= order.totalAmount() %>" min="0" step="0.01" required></label><div class="sales-dialog-actions"><button class="dialog-cancel" type="button" onclick="this.closest('details').removeAttribute('open')">Zrušit</button><button class="secondary" type="submit">Uložit změny</button></div></form></details>
                  <form method="post" data-confirm-delete data-confirm-message="Opravdu chcete tento dokument smazat?"><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<%= order.id() %>"><button class="danger-button" type="submit">Smazat</button></form>
                </div>
              </td>
            </tr>
            <% } %>
          </tbody>
        </table>
      </div>
    </section>
  </main>
  <script src="assets/base.js?v=20261005-1"></script>
</body>
</html>