<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.manufacturing.ManufacturingOverviewView" %>
<%@ page import="com.example.erp.frontend.manufacturing.ManufacturingOverviewView.ManufacturingOrderView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Výroba</title>
  <link rel="stylesheet" href="assets/base.css?v=20261001-1">
  <link rel="stylesheet" href="assets/manufacturing.css?v=20260928-154028">
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
      <div class="manufacturing-table-wrap">
        <table class="manufacturing-table">
          <thead><tr><th>Výrobní příkaz</th><th>Průběh výroby</th><th>Stav / akce</th></tr></thead>
          <tbody>
            <% if (overview != null) for (ManufacturingOrderView order : overview.orders()) { int progress = order.plannedQuantity() == 0 ? 0 : order.completedQuantity() * 100 / order.plannedQuantity(); %>
            <tr>
              <td data-label="Výrobní příkaz"><span class="status-chip status-<%= order.status().toLowerCase() %>"><%= order.status().equals("IN_PROGRESS") ? "PROBÍHÁ" : order.status().equals("PLANNED") ? "PLÁNOVÁNO" : "DOKONČENO" %></span><strong><%= order.orderNumber() %></strong><span class="manufacturing-product"><%= order.productName() %> · <%= order.workCenter() %></span></td>
              <td data-label="Průběh výroby"><div class="progress-group"><div class="progress-meta"><span>Hotovo <b><%= order.completedQuantity() %> / <%= order.plannedQuantity() %> ks</b></span><span><%= progress %> %</span></div><div class="progress-track"><span style="width:<%= progress %>%"></span></div><small>Plánované datum: <%= order.plannedDate() %></small></div></td>
              <td data-label="Stav / akce"><button class="secondary edit-order" type="button" data-id="<%= order.id() %>" data-product="<%= order.productName() %>" data-order-number="<%= order.orderNumber() %>" data-completed="<%= order.completedQuantity() %>" data-planned="<%= order.plannedQuantity() %>">Upravit</button><% if (!"COMPLETED".equals(order.status())) { %><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><button class="secondary" type="submit">Dokončit příkaz</button></form><% } else { %><span class="completed-label">Výroba uzavřena</span><% } %></td>
            </tr>
            <% } %>
          </tbody>
        </table>
      </div>
    </section>
  </main>
  <dialog class="manufacturing-dialog" id="edit-order-dialog">
    <form method="post" id="edit-order-form">
      <input type="hidden" name="action" value="update">
      <input type="hidden" name="id" id="edit-order-id">
      <div class="dialog-head"><div><span class="eyebrow">ÚPRAVA VÝROBY</span><h2 id="edit-order-title">Výrobní příkaz</h2></div><button class="dialog-close" type="button" aria-label="Zavřít">×</button></div>
      <p class="dialog-product" id="edit-order-product"></p>
      <label for="completed-quantity">Vyrobeno kusů</label>
      <input id="completed-quantity" name="completedQuantity" type="number" min="0" required>
      <small id="completed-quantity-help"></small>
      <div class="dialog-actions"><button class="secondary dialog-cancel" type="button">Zrušit</button><button class="primary" type="submit">Uložit změny</button></div>
    </form>
  </dialog>
  <script>
    const editDialog = document.getElementById('edit-order-dialog');
    const editForm = document.getElementById('edit-order-form');
    const completedQuantity = document.getElementById('completed-quantity');
    document.querySelectorAll('.edit-order').forEach(button => button.addEventListener('click', () => {
      document.getElementById('edit-order-id').value = button.dataset.id;
      document.getElementById('edit-order-title').textContent = button.dataset.orderNumber;
      document.getElementById('edit-order-product').textContent = button.dataset.product;
      completedQuantity.value = button.dataset.completed;
      completedQuantity.max = button.dataset.planned;
      document.getElementById('completed-quantity-help').textContent = 'Plánované množství: ' + button.dataset.planned + ' ks';
      editDialog.showModal();
    }));
    document.querySelector('.dialog-close').addEventListener('click', () => editDialog.close());
    document.querySelector('.dialog-cancel').addEventListener('click', () => editDialog.close());
    editDialog.addEventListener('click', event => { if (event.target === editDialog) editDialog.close(); });
  </script>
</body>
</html>