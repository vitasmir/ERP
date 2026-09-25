<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ page import="java.util.List" %>
        <%@ page import="com.example.erp.frontend.promo.PromoCampaignView" %>
            <%@ page import="com.example.erp.frontend.promo.PromoOptionsView" %>
                <!doctype html>
                <html lang="cs">

                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>ERP | Promo kampaně</title>
                    <link rel="stylesheet" href="assets/base.css">
                    <link rel="stylesheet" href="assets/promo.css">
                    <link rel="stylesheet" href="assets/promo-form.css">
                    <link rel="stylesheet" href="assets/promo-table.css">
                </head>

                <body>
                    <main class="promo-page">
                        <header class="promo-header"><a href="./" class="back-link">← Aplikace</a>
                            <div><span class="eyebrow">PRODEJ / PROMO</span>
                                <h1>Promo kampaně</h1>
                                <p>Plánujte letákové akce, kontrolujte marži a upravujte jejich průběh.</p>
                            </div><button type="button" class="primary" id="add-campaign">+ Nová kampaň</button>
                        </header>
                        <% String error=(String) request.getAttribute("error"); String
                            message=request.getParameter("message"); if (error !=null) { %>
                            <p class="promo-message error">
                                <%= error %>
                            </p>
                            <% } if (message !=null) { %>
                                <p class="promo-message">
                                    <%= message %>
                                </p>
                                <% } %>
                                    <section class="promo-features">
                                        <article><b>Plán akce</b><span>Datum, sortiment a cenový rozdíl</span></article>
                                        <article><b>Alokace prodejen</b><span>Distribuce plánovaného množství</span>
                                        </article>
                                        <article><b>Výkon kampaně</b><span>Plán versus skutečný prodej</span></article>
                                    </section>
                                    <section id="campaigns" class="campaign-section">
                                        <div class="section-head">
                                            <div><span class="eyebrow">AKTUÁLNÍ KAMPANĚ</span>
                                                <h2>Řízení průběhu</h2>
                                            </div><span class="campaign-count">
                                                <%= request.getAttribute("campaigns")==null ? 0 : ((List<?>)
                                                    request.getAttribute("campaigns")).size() %> kampaní
                                            </span>
                                        </div>
                                        <div class="campaign-table-wrap">
                                            <table class="campaign-table">
                                                <thead>
                                                    <tr><th>Produkt</th><th>Kampaň</th><th>Období</th><th>Běžná cena</th><th>Akční cena</th><th>Plán / skutečnost</th><th>Stav</th><th>Akce</th></tr>
                                                </thead>
                                                <tbody>
                                                    <% List<PromoCampaignView> campaigns = (List<PromoCampaignView>) request.getAttribute("campaigns"); if (campaigns != null) for (PromoCampaignView campaign : campaigns) { %>
                                                    <tr>
                                                        <td data-label="Produkt"><div class="campaign-product-photo"><% if (campaign.imageUrl() != null && !campaign.imageUrl().isBlank()) { %><img src="<%= campaign.imageUrl() %>" data-image="<%= campaign.imageUrl() %>" alt="<%= campaign.name() %>"><% } else { %><span aria-hidden="true">Bez fotografie</span><% } %></div></td>
                                                        <td data-label="Kampaň"><strong><%= campaign.name() %></strong><span class="status-chip status-<%= campaign.status().toLowerCase() %>"><%= campaign.status() %></span></td>
                                                        <td data-label="Období"><%= campaign.startsOn() %><br> až <%= campaign.endsOn() %></td>
                                                        <td data-label="Běžná cena"><%= campaign.regularPrice() %> Kč</td>
                                                        <td data-label="Akční cena"><strong><%= campaign.promoPrice() %> Kč</strong></td>
                                                        <td data-label="Plán / skutečnost"><%= campaign.plannedQuantity() %> / <%= campaign.actualQuantity() %></td>
                                                        <td data-label="Stav"><form id="campaign-status-<%= campaign.id() %>" method="post" class="campaign-form"><input type="hidden" name="id" value="<%= campaign.id() %>"><select name="status" aria-label="Stav kampaně" onchange="this.form.submit()"><% for (String status : List.of("PLANNED", "ACTIVE", "COMPLETED", "CANCELLED")) { %><option value="<%= status %>" <%= status.equals(campaign.status()) ? "selected" : "" %>><%= status %></option><% } %></select></form></td>
                                                        <td data-label="Akce"><button type="button" class="secondary campaign-edit" data-id="<%= campaign.id() %>" data-name="<%= campaign.name() %>" data-product-id="<%= campaign.productId() %>" data-supplier-id="<%= campaign.supplierId() %>" data-starts-on="<%= campaign.startsOn() %>" data-ends-on="<%= campaign.endsOn() %>" data-regular-price="<%= campaign.regularPrice() %>" data-promo-price="<%= campaign.promoPrice() %>" data-supplier-purchase-price="<%= campaign.supplierPurchasePrice() %>" data-planned-quantity="<%= campaign.plannedQuantity() %>" data-marketing-contribution="<%= campaign.marketingContribution() %>">Upravit</button></td>
                                                    </tr>
                                                    <% } %>
                                                </tbody>
                                            </table>
                                        </div>
                                    </section>
                                    <% PromoOptionsView options=(PromoOptionsView) request.getAttribute("options"); %>
                                        <div class="campaign-modal" id="campaign-modal" aria-hidden="true">
                                            <div class="campaign-modal-backdrop"></div>
                                            <section class="campaign-dialog" role="dialog" aria-modal="true"
                                                aria-labelledby="campaign-dialog-title"><button type="button"
                                                    class="dialog-close" id="close-campaign"
                                                    aria-label="Zavřít">×</button><span class="eyebrow">PRODEJ /
                                                    PROMO</span>
                                                <h2 id="campaign-dialog-title">Nová promo kampaň</h2>
                                                <p>Zadejte plán akce, ceny a plánované množství.</p>
                                                <form method="post" action="promo" class="campaign-create-form"><input
                                                    type="hidden" name="action" value="create"><input type="hidden" name="id" value=""><label>Název
                                                        kampaně<input name="name" required
                                                            maxlength="200"></label><label>Produkt<select
                                                            name="productId" required>
                                                            <option value="">Vyberte produkt</option>
                                                            <% if (options !=null) for (PromoOptionsView.ProductOption
                                                                product : options.products()) { %>
                                                                <option value="<%= product.id() %>" data-image="<%= product.imageUrl() == null ? "" : product.imageUrl() %>">
                                                                    <%= product.name() %> (<%= product.unit() %>)
                                                                </option>
                                                                <% } %>
                                                        </select></label><div class="product-preview" id="product-preview" aria-live="polite"><span>Vyberte produkt pro náhled fotografie</span><img id="product-preview-image" class="product-preview-image" alt="" hidden></div><label>Dodavatel<select name="supplierId"
                                                            required>
                                                            <option value="">Vyberte dodavatele</option>
                                                            <% if (options !=null) for (PromoOptionsView.SupplierOption
                                                                supplier : options.suppliers()) { %>
                                                                <option value="<%= supplier.id() %>">
                                                                    <%= supplier.name() %>
                                                                </option>
                                                                <% } %>
                                                        </select></label>
                                                    <div class="form-grid"><label>Začátek<input type="date"
                                                                name="startsOn" required></label><label>Konec<input
                                                                type="date" name="endsOn" required></label><label>Běžná
                                                            cena<input type="number" name="regularPrice" min="0"
                                                                step="0.01" required></label><label>Akční cena<input
                                                                type="number" name="promoPrice" min="0" step="0.01"
                                                                required></label><label>Nákupní cena<input type="number"
                                                                name="supplierPurchasePrice" min="0" step="0.01"
                                                                required></label><label>Plánované množství<input
                                                                type="number" name="plannedQuantity" min="0" step="1"
                                                                required></label><label>Příspěvek dodavatele<input
                                                                type="number" name="marketingContribution" min="0"
                                                                step="0.01" value="0" required></label></div>
                                                        <div class="dialog-actions"><button type="button" class="secondary"
                                                            id="cancel-campaign">Zrušit</button><button type="submit"
                                                            class="primary" id="campaign-submit">Přidat kampaň</button></div>
                                                </form>
                                            </section>
                                        </div>
                                        <script src="assets/promo.js"></script>
                    </main>
                </body>

                </html>