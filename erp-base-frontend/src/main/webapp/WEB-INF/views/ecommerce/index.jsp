<%@ page contentType="text/html" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.ecommerce.EcommerceView" %>
<%@ page import="com.example.erp.frontend.ecommerce.EcommerceView.Category" %>
<%@ page import="com.example.erp.frontend.ecommerce.EcommerceView.Product" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | eCommerce</title><link rel="stylesheet" href="assets/base.css"><link rel="stylesheet" href="assets/ecommerce.css">
</head>
<body>
<% EcommerceView data = (EcommerceView) request.getAttribute("ecommerce"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
<main class="commerce-page">
  <header class="commerce-header"><a href="apps" class="back-link">← Aplikace</a><div><span class="eyebrow">OBCHOD / ECOMMERCE</span><h1>Obchod</h1><p>Navrhněte úvod, spravujte katalog a ověřte dostupnost zboží v každé lokalitě.</p></div><a href="#catalog" class="primary">Katalog produktů</a></header>
  <% if (error != null) { %><p class="commerce-message error"><%= error %></p><% } if (actionError != null) { %><p class="commerce-message error"><%= actionError %></p><% } if (message != null) { %><p class="commerce-message"><%= message %></p><% } %>
  <% if (data != null) { %>
  <section class="commerce-grid">
    <article class="panel homepage-panel">
      <div class="panel-heading"><div><span class="eyebrow">ÚVODNÍ STRÁNKA</span><h2>Vzhled a sdělení</h2></div><span class="panel-note">Klikněte do náhledu nebo použijte šipky</span></div>
      <form method="post" id="homepage-form">
        <input type="hidden" name="action" value="homepage"><input type="hidden" name="textX" id="text-x" value="<%= data.homepage().textX() %>"><input type="hidden" name="textY" id="text-y" value="<%= data.homepage().textY() %>">
        <div class="design-stage design-<%= data.homepage().design().toLowerCase() %>" id="design-stage" tabindex="0" aria-label="Náhled úvodní stránky">
          <div class="stage-copy" id="stage-copy" style="left:<%= data.homepage().textX() %>%;top:<%= data.homepage().textY() %>%"><span>FRESH / LOCAL / TODAY</span><h3><%= data.homepage().headline() %></h3><p><%= data.homepage().subheadline() %></p></div>
        </div>
        <div class="editor-fields"><label>Design<select name="design" id="design-select"><option value="BOTANICAL" <%= "BOTANICAL".equals(data.homepage().design()) ? "selected" : "" %>>Botanická sklizeň</option><option value="MARKET" <%= "MARKET".equals(data.homepage().design()) ? "selected" : "" %>>Městský trh</option><option value="MINIMAL" <%= "MINIMAL".equals(data.homepage().design()) ? "selected" : "" %>>Čistý minimalismus</option></select></label><label>Nadpis<input name="headline" value="<%= data.homepage().headline() %>" maxlength="200"></label><label>Podnadpis<input name="subheadline" value="<%= data.homepage().subheadline() %>" maxlength="500"></label></div>
        <button class="primary" type="submit">Uložit úvodní stránku</button>
      </form>
    </article>
    <article class="panel category-panel">
      <div class="panel-heading"><div><span class="eyebrow">KATALOG</span><h2>Strom kategorií</h2></div><span class="panel-note"><%= data.categories().size() %> kořenů</span></div>
      <ul class="category-tree"><% for (Category category : data.categories()) { %><li><strong><%= category.name() %></strong><small>/<%= category.slug() %></small><% if (!category.children().isEmpty()) { %><ul><% for (Category child : category.children()) { %><li><span><%= child.name() %></span><small>/<%= child.slug() %></small></li><% } %></ul><% } %></li><% } %></ul>
      <form method="post" class="compact-form"><input type="hidden" name="action" value="category"><label>Nová kategorie<input name="name" placeholder="Např. Nápoje" required></label><label>Slug<input name="slug" placeholder="napoje" required></label><label>Nadřazená<select name="parentId"><option value="">Kořen</option><% for (Category category : data.categories()) { %><option value="<%= category.id() %>"><%= category.name() %></option><% } %></select></label><button class="secondary" type="submit">Přidat kategorii</button></form>
    </article>
  </section>
  <section class="catalog-section" id="catalog">
    <div class="section-heading"><div><span class="eyebrow">PRODUKTY A DOSTUPNOST</span><h2>Katalog napojený na inventory</h2></div><span class="panel-note"><%= data.products().size() %> produktů</span></div>
    <div class="product-list"><% for (Product product : data.products()) { %><article class="product-card"><div class="product-top"><div><span class="sku"><%= product.sku() %></span><h3><%= product.name() %></h3><p><%= product.description() %></p></div><strong class="price"><%= product.price() %> Kč / <%= product.unit() %></strong></div><div class="availability"><% for (EcommerceView.Availability stock : product.availability()) { %><div class="stock-row"><span><%= stock.locationName() %></span><span class="stock-<%= stock.stockStatus().toLowerCase() %>"><%= stock.quantity() %> <%= product.unit() %> · <%= stock.stockStatus().equals("AVAILABLE") ? "Dostupné" : stock.stockStatus().equals("LOW") ? "Nízká zásoba" : "Vyprodáno" %></span></div><% } %></div><form method="post" class="delivery-form"><input type="hidden" name="action" value="estimate"><input type="hidden" name="productId" value="<%= product.id() %>"><label>Množství<input type="number" name="quantity" value="1" min="1"></label><label>PSČ<input name="postalCode" placeholder="60200" pattern="[0-9]{5}" required></label><label>Způsob<select name="method"><option value="HOME">Domů</option><option value="BOX">Box</option></select></label><button class="secondary" type="submit">Zjistit doručení</button></form></article><% } %></div>
  </section>
  <section class="import-section"><div><span class="eyebrow">IMPORT PRODUKTŮ</span><h2>Načíst dávku podle SKU</h2><p>JSON import provede upsert: existující SKU aktualizuje, nové založí.</p></div><form method="post"><input type="hidden" name="action" value="import"><textarea name="products" rows="6">[
  {"sku":"PORK-NECK-01","name":"Vepřová krkovice bez kosti","unit":"kg","description":"Čerstvé maso","price":189.90,"categoryId":"60000000-0000-0000-0000-000000000002","active":true}
]</textarea><button class="primary" type="submit">Importovat produkty</button></form></section>
  <% } %>
</main><script src="assets/ecommerce.js"></script>
</body></html>
