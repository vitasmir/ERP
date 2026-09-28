<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="com.example.erp.frontend.dashboard.DashboardOverviewView" %>
<%@ page import="com.example.erp.frontend.dashboard.DashboardOverviewView.InvoiceItem" %>
<%@ page import="com.example.erp.frontend.dashboard.DashboardOverviewView.LeadItem" %>
<%@ page import="com.example.erp.frontend.dashboard.DashboardOverviewView.CampaignItem" %>
<%! String amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); } %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Dashboard</title>
  <link rel="stylesheet" href="assets/base.css?v=20260928-154028">
  <link rel="stylesheet" href="assets/dashboard.css?v=20260928-154028">
</head>
<body>
  <% DashboardOverviewView overview = (DashboardOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); %>
  <main class="dashboard-page">
    <header class="dashboard-header">
      <div class="dashboard-title">
        <a href="apps" class="back-link">← Aplikace</a>
        <span class="eyebrow">OVERVIEW / MANAGEMENT</span>
        <h1>Dashboard</h1>
        <p>Pracovní přehled financí, obchodních příležitostí a promo aktivit.</p>
      </div>
      <div class="dashboard-date"><span>DNEŠNÍ PŘEHLED</span><strong>23. 09. 2026</strong></div>
    </header>
    <% if (error != null) { %><p class="dashboard-message"><%= error %></p><% } %>

    <section class="dashboard-metrics" aria-label="Klíčové ukazatele">
      <article class="metric-card receivables"><span>Pohledávky</span><strong><%= overview == null ? "-" : amount(overview.receivables()) %> Kč</strong><small>čeká na úhradu</small><a href="accounting">Účetnictví →</a></article>
      <article class="metric-card overdue"><span>Po splatnosti</span><strong><%= overview == null ? "-" : amount(overview.overdue()) %> Kč</strong><small>vyžaduje pozornost</small><a href="accounting">Zobrazit faktury →</a></article>
      <article class="metric-card pipeline"><span>Hodnota pipeline</span><strong><%= overview == null ? "-" : amount(overview.pipeline()) %> Kč</strong><small>v otevřených příležitostech</small><a href="crm">CRM pipeline →</a></article>
      <article class="metric-card forecast"><span>Vážený forecast</span><strong><%= overview == null ? "-" : amount(overview.forecast()) %> Kč</strong><small>podle pravděpodobnosti</small><a href="crm">Detail forecastu →</a></article>
      <article class="metric-card campaigns"><span>Aktivní kampaně</span><strong><%= overview == null ? "-" : overview.activeCampaignCount() %></strong><small>právě v běhu</small><a href="promo">Promo kampaně →</a></article>
    </section>

    <section class="dashboard-grid">
      <section class="dashboard-panel invoice-panel">
        <div class="panel-head"><div><span class="eyebrow">FINANCE</span><h2>Neuhrazené faktury</h2></div><a href="accounting">Všechny faktury →</a></div>
        <div class="panel-list">
          <% if (overview != null) for (InvoiceItem invoice : overview.invoices()) { %>
          <article class="list-row"><div><span class="status status-<%= invoice.status().toLowerCase() %>"><%= invoice.status() %></span><strong><%= invoice.invoiceNumber() %></strong><small><%= invoice.partnerName() %> · splatnost <%= invoice.dueDate() %></small></div><b><%= amount(invoice.outstandingAmount()) %> Kč</b></article>
          <% } %>
        </div>
      </section>

      <section class="dashboard-panel lead-panel">
        <div class="panel-head"><div><span class="eyebrow">OBCHOD</span><h2>Nejbližší příležitosti</h2></div><a href="crm">Otevřít CRM →</a></div>
        <div class="panel-list">
          <% if (overview != null) for (LeadItem lead : overview.leads()) { %>
          <article class="list-row"><div><span class="status stage-<%= lead.stage().toLowerCase() %>"><%= lead.stage() %></span><strong><%= lead.name() %></strong><small><%= lead.customerName() %> · uzavření <%= lead.expectedCloseDate() %></small></div><b><%= amount(lead.expectedRevenue()) %> Kč <i><%= lead.probability() %> %</i></b></article>
          <% } %>
        </div>
      </section>

      <section class="dashboard-panel campaign-panel">
        <div class="panel-head"><div><span class="eyebrow">PROMO</span><h2>Stav kampaní</h2></div><a href="promo">Správa kampaní →</a></div>
        <div class="campaign-list">
          <% if (overview != null) for (CampaignItem campaign : overview.campaigns()) { %>
          <article class="campaign-row"><span class="status campaign-<%= campaign.status().toLowerCase() %>"><%= campaign.status() %></span><div><strong><%= campaign.name() %></strong><small><%= campaign.startsOn() %> až <%= campaign.endsOn() %></small></div></article>
          <% } %>
        </div>
      </section>
    </section>
  </main>
</body>
</html>