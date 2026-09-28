<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.marketing.MarketingOverviewView" %>
<%@ page import="com.example.erp.frontend.marketing.MarketingOverviewView.CampaignView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Marketing</title>
  <link rel="stylesheet" href="assets/base.css?v=20260928-160510">
  <link rel="stylesheet" href="assets/marketing.css?v=20260928-154028">
</head>
<body>
  <% MarketingOverviewView overview = (MarketingOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="marketing-page">
    <header class="marketing-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">OBCHOD / MARKETING</span><h1>Marketing</h1><p>Plánujte komunikaci, sledujte čerpání rozpočtu a vyhodnocujte přínos kampaní.</p></div>
      <a href="#campaigns" class="primary">Přehled kampaní</a>
    </header>
    <% if (error != null) { %><p class="marketing-message error"><%= error %></p><% } if (actionError != null) { %><p class="marketing-message error"><%= actionError %></p><% } if (message != null) { %><p class="marketing-message"><%= message %></p><% } %>
    <section class="marketing-metrics">
      <article><span>Aktivní kampaně</span><strong><%= overview == null ? "-" : overview.runningCampaignCount() %></strong><small>aktuálně doručované komunikace</small></article>
      <article><span>Plánované kampaně</span><strong><%= overview == null ? "-" : overview.plannedCampaignCount() %></strong><small>připravené ke spuštění</small></article>
      <article><span>Získané leady</span><strong><%= overview == null ? "-" : overview.totalLeadCount() %></strong><small>vyčerpáno <%= overview == null ? "-" : overview.totalSpent().toPlainString() %> Kč</small></article>
    </section>
    <section class="workflow-form">
      <h2>Nová kampaň</h2>
      <form method="post">
        <input type="hidden" name="action" value="create">
        <input name="name" placeholder="Název kampaně" required>
        <input name="audience" placeholder="Segment" required>
        <select name="channel">
          <option value="EMAIL">E-mail</option>
          <option value="SOCIAL">Sociální sítě</option>
          <option value="EVENT">Událost</option>
        </select>
        <input name="ownerName" placeholder="Vlastník" required>
        <input type="number" step="0.01" name="budget" placeholder="Rozpočet Kč" required>
        <input type="date" name="plannedStartDate" required>
        <button class="primary" type="submit">Založit kampaň</button>
      </form>
    </section>
    <section id="campaigns" class="marketing-section">
      <div class="section-head"><div><span class="eyebrow">KAMPANĚ A SEGMENTY</span><h2>Marketingový plán</h2></div><span class="campaign-count"><%= overview == null ? 0 : overview.campaigns().size() %> kampaně</span></div>
      <div class="campaign-list">
        <% if (overview != null) for (CampaignView campaign : overview.campaigns()) { %>
        <article class="campaign-card">
          <div class="campaign-main"><span class="channel"><%= campaign.channel().equals("EMAIL") ? "E-MAIL" : campaign.channel().equals("SOCIAL") ? "SOCIÁLNÍ SÍTĚ" : "UDÁLOST" %></span><span class="status-chip status-<%= campaign.status().toLowerCase() %>"><%= campaign.status().equals("RUNNING") ? "AKTIVNÍ" : campaign.status().equals("PLANNED") ? "PLÁNOVÁNO" : "DOKONČENO" %></span><h3><%= campaign.name() %></h3><p><%= campaign.audience() %> · vlastník: <%= campaign.ownerName() %></p></div>
          <dl><div><dt>Rozpočet</dt><dd><%= campaign.spent().toPlainString() %> / <%= campaign.budget().toPlainString() %> Kč</dd></div><div><dt>Leady</dt><dd><%= campaign.leadCount() %> · start: <%= campaign.plannedStartDate() %></dd></div></dl>
          <% if ("PLANNED".equals(campaign.status())) { %><form method="post"><input type="hidden" name="id" value="<%= campaign.id() %>"><button class="secondary" type="submit">Spustit kampaň</button></form><% } else if ("RUNNING".equals(campaign.status())) { %><span class="running-label">Kampaň běží</span><% } else { %><span class="completed-label">Kampaň uzavřena</span><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
  <script src="assets/base.js?v=20260928-160511"></script>
</body>
</html>