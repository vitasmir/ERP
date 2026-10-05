<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.Locale" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.PurchaseOrderView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.PurchaseLineView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.WarehouseView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.ProductView" %>
<%@ page import="com.example.erp.frontend.purchase.PurchaseOverviewView.ProductImageView" %>
<%! private static final DateTimeFormatter LOCAL_DATE_FORMAT = DateTimeFormatter.ofPattern("d. M. uuuu", Locale.forLanguageTag("cs-CZ")); String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } String date(LocalDate value) { return value == null ? "-" : LOCAL_DATE_FORMAT.format(value); } String warehouseName(java.util.UUID id, java.util.List<WarehouseView> warehouses) { if (id == null || warehouses == null) return "-"; for (WarehouseView warehouse : warehouses) if (id.equals(warehouse.id())) return warehouse.name(); return id.toString(); } ProductView product(java.util.UUID id, java.util.List<ProductView> products) { if (id == null || products == null) return null; for (ProductView product : products) if (id.equals(product.id())) return product; return null; } String productName(java.util.UUID id, java.util.List<ProductView> products) { ProductView product = product(id, products); return product == null ? (id == null ? "-" : id.toString()) : product.name(); } String productImage(java.util.UUID id, java.util.List<ProductView> products) { ProductView product = product(id, products); if (product == null) return null; if (product.imageUrl() != null && !product.imageUrl().isBlank()) return product.imageUrl(); if (product.images() != null) for (ProductImageView image : product.images()) if (image.active() && image.imageUrl() != null && !image.imageUrl().isBlank()) return image.imageUrl(); return null; } %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Nákup</title>
  <link rel="stylesheet" href="assets/base.css?v=20261001-1">
  <link rel="stylesheet" href="assets/purchase.css?v=20261005-7">
</head>
<body>
  <% PurchaseOverviewView overview = (PurchaseOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); java.util.List<WarehouseView> warehouses = (java.util.List<WarehouseView>) request.getAttribute("warehouses"); java.util.List<ProductView> products = (java.util.List<ProductView>) request.getAttribute("products"); %>
  <main class="purchase-page">
    <header class="purchase-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / NÁKUP</span><h1>Nákup</h1><p>Řiďte nákupní požadavky, objednávky dodavatelům a očekávané příjmy zboží.</p></div>
      <div class="purchase-actions"><a href="#orders" class="primary">Přehled nákupu</a><button type="button" class="primary add-order-button" data-open-order-dialog onclick="document.querySelector('[data-order-dialog]').showModal()">Přidej objednávku</button></div>
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
              <td data-label="Dokument"><button type="button" class="order-details-button" data-order-details><span class="status-chip status-<%= order.status().toLowerCase() %>"><%= order.status().equals("REQUESTED") ? "POŽADAVEK" : "OBJEDNÁNO" %></span><strong><%= order.orderNumber() %></strong><div class="line-summary"><% for (PurchaseLineView line : order.lines()) { %><span><%= productName(line.productId(), products) %> · <%= line.quantity() %> ks · <%= amount(line.unitPrice()) %> Kč/ks</span><% } %></div></button><template data-order-details-template><div class="order-details-content"><span class="eyebrow">OBSAH OBJEDNÁVKY</span><h2><%= order.orderNumber() %></h2><p class="order-details-meta"><%= order.supplierName() %> · <%= date(order.expectedDeliveryDate()) %></p><div class="order-detail-lines"><% for (PurchaseLineView line : order.lines()) { ProductView orderedProduct = product(line.productId(), products); String imageUrl = productImage(line.productId(), products); %><article class="order-detail-line"><% if (imageUrl != null) { %><img src="<%= imageUrl %>" alt="<%= productName(line.productId(), products) %>" loading="lazy"><% } else { %><div class="order-detail-image-placeholder" aria-hidden="true">Bez obrázku</div><% } %><div><h3><%= productName(line.productId(), products) %></h3><% if (orderedProduct != null) { %><p><%= orderedProduct.sku() %><% if (orderedProduct.unit() != null && !orderedProduct.unit().isBlank()) { %> · <%= orderedProduct.unit() %><% } %></p><% } %><strong><%= line.quantity() %> ks · <%= amount(line.unitPrice()) %> Kč/ks</strong></div></article><% } %></div><p class="order-details-total">Celkem: <strong><%= amount(order.totalAmount()) %> Kč</strong></p></div></template><% if ("REQUESTED".equals(order.status())) { %><div class="order-edit-data" data-requested="<%= order.requestedOn() %>" data-expected="<%= order.expectedDeliveryDate() %>" data-source="<%= order.sourceWarehouseId() %>" data-destination="<%= order.destinationWarehouseId() %>"><% for (PurchaseLineView line : order.lines()) { %><span data-edit-line data-product-id="<%= line.productId() %>" data-quantity="<%= line.quantity() %>" data-unit-price="<%= line.unitPrice() %>"></span><% } %></div><% } %></td>
              <td data-label="Dodavatel"><%= order.supplierName() %></td>
              <td data-label="Tok skladu"><span class="warehouse-flow"><span><%= warehouseName(order.sourceWarehouseId(), warehouses) %></span><span class="warehouse-arrow" aria-hidden="true">→</span><span><%= warehouseName(order.destinationWarehouseId(), warehouses) %></span></span></td>
              <td data-label="Požadováno"><span class="order-date"><%= date(order.requestedOn()) %></span><small class="order-quantity"><%= order.quantity() == null ? "-" : order.receivedQuantity() + " / " + order.quantity() %> ks</small></td>
              <td data-label="Dodání"><%= date(order.expectedDeliveryDate()) %></td>
              <td data-label="Celkem" class="amount-column"><strong><%= amount(order.totalAmount()) %> Kč</strong></td>
              <td data-label="Stav / akce"><% if ("REQUESTED".equals(order.status())) { %><button class="secondary edit-order-button" type="button" data-edit-order="<%= order.id() %>">Upravit</button><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><button class="secondary" type="submit">Vystavit objednávku</button></form><% } else if ("ORDERED".equals(order.status()) && order.quantity() != null) { %><form method="post"><input type="hidden" name="id" value="<%= order.id() %>"><input type="hidden" name="action" value="receive"><input type="hidden" name="quantity" value="<%= order.quantity() - order.receivedQuantity() %>"><button class="secondary" type="submit">Přijmout na sklad</button></form><% } else { %><span class="ordered-label"><%= "RECEIVED".equals(order.status()) ? "Přijato na sklad" : "Čeká na dodání" %></span><% } %></td>
            </tr>
            <% } %>
          </tbody>
        </table>
      </div>
    </section>
  </main>
  <dialog class="order-dialog" data-order-dialog aria-labelledby="new-order-title">
    <div class="order-dialog-head"><div><span class="eyebrow">NOVÝ DOKUMENT</span><h2 id="new-order-title">Přidej objednávku</h2></div><button type="button" class="dialog-close" data-close-order-dialog aria-label="Zavřít" onclick="document.querySelector('[data-order-dialog]').close()">×</button></div>
      <form method="post" class="new-order-form">
        <input type="hidden" name="action" value="create" data-order-action>
        <input type="hidden" name="id" value="" data-order-id>
        <label>Dodavatel<input name="supplierName" required maxlength="200" placeholder="Název dodavatele"></label>
        <label>Požadováno<span class="date-input-wrap"><input class="date-input" data-native-date-picker type="date" name="requestedOn" required><button class="date-picker-button" type="button" data-date-picker aria-label="Otevřít kalendář" onclick="window.openPurchaseCalendar(this)">Kalendář</button><span class="date-calendar" data-calendar hidden></span></span></label>
        <label>Očekávané dodání<span class="date-input-wrap"><input class="date-input" data-native-date-picker type="date" name="expectedDeliveryDate" required><button class="date-picker-button" type="button" data-date-picker aria-label="Otevřít kalendář" onclick="window.openPurchaseCalendar(this)">Kalendář</button><span class="date-calendar" data-calendar hidden></span></span></label>
        <label>Dodavatelský sklad<select name="sourceWarehouseId" required><option value="">Vyberte sklad</option><% if (warehouses != null) for (WarehouseView warehouse : warehouses) if ("SUPPLIER".equals(warehouse.ownerType())) { %><option value="<%= warehouse.id() %>"><%= warehouse.name() %></option><% } %></select></label>
        <label>Firemní cílový sklad<select name="destinationWarehouseId" required><option value="">Vyberte sklad</option><% if (warehouses != null) for (WarehouseView warehouse : warehouses) if ("COMPANY".equals(warehouse.ownerType())) { %><option value="<%= warehouse.id() %>"><%= warehouse.name() %></option><% } %></select></label>
        <fieldset class="purchase-lines" data-purchase-lines>
          <legend>Položky objednávky</legend>
          <div class="purchase-line" data-purchase-line>
            <label>Produkt<select name="productId" required data-product-select><option value="">Vyberte produkt</option><% if (products != null) for (ProductView product : products) { %><option value="<%= product.id() %>" data-price="<%= product.purchasePrice() == null ? "0.00" : product.purchasePrice() %>"><%= product.name() %> (<%= product.sku() %>)</option><% } %></select></label>
            <label>Množství<input type="number" name="quantity" min="1" step="1" required placeholder="1"></label>
            <label>Cena / ks<input type="number" name="unitPrice" min="0" step="0.01" required placeholder="0.00" data-unit-price></label>
            <button class="remove-line" type="button" data-remove-line aria-label="Odebrat položku">×</button>
          </div>
        </fieldset>
        <button class="add-line" type="button" data-add-line>+ Přidat další produkt</button>
        <output class="order-total" data-order-total>Celkem: 0,00 Kč</output>
        <button class="secondary" type="submit">Vytvořit objednávku</button>
      </form>
  </dialog>
  <dialog class="order-details-dialog" data-order-details-dialog aria-label="Obsah objednávky">
    <button type="button" class="dialog-close" data-close-order-details aria-label="Zavřít">×</button>
    <div data-order-details-content></div>
  </dialog>
  <script>
    const orderDialog = document.querySelector('[data-order-dialog]');
    document.querySelector('[data-open-order-dialog]').addEventListener('click', event => {
      if (!orderDialog.open) {
        event.preventDefault();
        orderDialog.showModal();
      }
    });
    document.querySelector('[data-close-order-dialog]').addEventListener('click', event => {
      if (orderDialog.open) {
        event.preventDefault();
        orderDialog.close();
      }
    });
    const orderForm = document.querySelector('.new-order-form');
    const orderAction = orderForm.querySelector('[data-order-action]');
    const orderId = orderForm.querySelector('[data-order-id]');
    const orderTitle = document.querySelector('#new-order-title');
    const orderSubmit = orderForm.querySelector('button[type="submit"]');
    const resetOrderForm = () => {
      orderForm.reset();
      orderAction.value = 'create';
      orderId.value = '';
      orderTitle.textContent = 'Přidej objednávku';
      orderSubmit.textContent = 'Vytvořit objednávku';
      const rows = [...lines.querySelectorAll('[data-purchase-line]')];
      rows.slice(1).forEach(row => row.remove());
      updateTotal();
    };
    document.querySelector('[data-open-order-dialog]').addEventListener('click', resetOrderForm);
    const localMonths = ['leden', 'únor', 'březen', 'duben', 'květen', 'červen', 'červenec', 'srpen', 'září', 'říjen', 'listopad', 'prosinec'];
    const localDays = ['Po', 'Út', 'St', 'Čt', 'Pá', 'So', 'Ne'];
    const dateValue = date => date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
    const openLocalCalendar = button => {
      const wrap = button.closest('.date-input-wrap');
      const input = wrap.querySelector('.date-input');
      const calendar = wrap.querySelector('[data-calendar]');
      const selected = input.value ? new Date(input.value + 'T00:00:00') : new Date();
      let month = new Date(selected.getFullYear(), selected.getMonth(), 1);
      const render = () => {
        const firstDay = (month.getDay() + 6) % 7;
        const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
        const headings = localDays.map(day => '<span class="date-calendar-day-name">' + day + '</span>').join('');
        const emptyDays = '<span></span>'.repeat(firstDay);
        const dayButtons = Array.from({ length: days }, (_, index) => '<button type="button" data-calendar-day="' + (index + 1) + '">' + (index + 1) + '</button>').join('');
        calendar.innerHTML = '<div class="date-calendar-header"><button type="button" data-calendar-prev>‹</button><strong>' + localMonths[month.getMonth()] + ' ' + month.getFullYear() + '</strong><button type="button" data-calendar-next>›</button></div><div class="date-calendar-grid">' + headings + emptyDays + dayButtons + '</div>';
        calendar.querySelector('[data-calendar-prev]').addEventListener('click', () => { month.setMonth(month.getMonth() - 1); render(); });
        calendar.querySelector('[data-calendar-next]').addEventListener('click', () => { month.setMonth(month.getMonth() + 1); render(); });
        calendar.querySelectorAll('[data-calendar-day]').forEach(day => day.addEventListener('click', () => {
          input.value = dateValue(new Date(month.getFullYear(), month.getMonth(), Number(day.dataset.calendarDay)));
          calendar.hidden = true;
        }));
      };
      document.querySelectorAll('[data-calendar]').forEach(item => { item.hidden = item !== calendar; });
      render();
      calendar.hidden = false;
    };
    window.openPurchaseCalendar = openLocalCalendar;
    document.querySelectorAll('[data-edit-order]').forEach(button => {
      button.addEventListener('click', () => {
        const row = button.closest('tr');
        const data = row.querySelector('[data-edit-line]')?.parentElement;
        orderForm.reset();
        orderAction.value = 'update';
        orderId.value = button.dataset.editOrder;
        orderTitle.textContent = 'Upravit požadavek';
        orderSubmit.textContent = 'Uložit změny';
        orderForm.querySelector('[name="supplierName"]').value = row.children[1].textContent.trim();
        orderForm.querySelector('[name="requestedOn"]').value = data.dataset.requested;
        orderForm.querySelector('[name="expectedDeliveryDate"]').value = data.dataset.expected;
        orderForm.querySelector('[name="sourceWarehouseId"]').value = data.dataset.source;
        orderForm.querySelector('[name="destinationWarehouseId"]').value = data.dataset.destination;
        const lineRows = [...lines.querySelectorAll('[data-purchase-line]')];
        lineRows.slice(1).forEach(line => line.remove());
        const editLines = [...data.querySelectorAll('[data-edit-line]')];
        editLines.slice(1).forEach(() => document.querySelector('[data-add-line]').click());
        [...lines.querySelectorAll('[data-purchase-line]')].forEach((line, index) => {
          const source = editLines[index];
          line.querySelector('[name="productId"]').value = source.dataset.productId;
          line.querySelector('[name="quantity"]').value = source.dataset.quantity;
          line.querySelector('[name="unitPrice"]').value = source.dataset.unitPrice;
        });
        updateTotal();
        orderDialog.showModal();
      });
    });
    const orderDetailsDialog = document.querySelector('[data-order-details-dialog]');
    const orderDetailsContent = orderDetailsDialog.querySelector('[data-order-details-content]');
    document.querySelectorAll('[data-order-details]').forEach(button => {
      button.addEventListener('click', () => {
        orderDetailsContent.replaceChildren(button.closest('td').querySelector('[data-order-details-template]').content.cloneNode(true));
        orderDetailsDialog.showModal();
      });
    });
    document.querySelector('[data-close-order-details]').addEventListener('click', () => orderDetailsDialog.close());
    orderDetailsDialog.addEventListener('click', event => { if (event.target === orderDetailsDialog) orderDetailsDialog.close(); });
    orderDialog.addEventListener('click', event => { if (event.target === orderDialog) orderDialog.close(); });
    const lines = document.querySelector('[data-purchase-lines]');
    const total = document.querySelector('[data-order-total]');
    const updateTotal = () => {
      const value = [...lines.querySelectorAll('[data-purchase-line]')].reduce((sum, row) => {
        const quantity = Number(row.querySelector('[name="quantity"]').value) || 0;
        const unitPrice = Number(row.querySelector('[name="unitPrice"]').value) || 0;
        return sum + quantity * unitPrice;
      }, 0);
      total.value = value.toLocaleString('cs-CZ', { style: 'currency', currency: 'CZK' });
      total.textContent = 'Celkem: ' + total.value;
    };
    const bindLine = row => {
      row.querySelector('[data-product-select]').addEventListener('change', event => {
        const price = event.target.selectedOptions[0]?.dataset.price;
        if (price != null) row.querySelector('[data-unit-price]').value = price;
        updateTotal();
      });
      row.querySelectorAll('input').forEach(input => input.addEventListener('input', updateTotal));
      row.querySelector('[data-remove-line]').addEventListener('click', () => {
        if (lines.children.length > 2) row.remove();
        updateTotal();
      });
    };
    bindLine(lines.querySelector('[data-purchase-line]'));
    document.querySelector('[data-add-line]').addEventListener('click', () => {
      const row = lines.querySelector('[data-purchase-line]').cloneNode(true);
      row.querySelectorAll('input').forEach(input => input.value = '');
      row.querySelector('select').value = '';
      lines.append(row);
      bindLine(row);
    });
  </script>
  <script src="assets/base.js?v=20261005-1"></script>
</body>
</html>