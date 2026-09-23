<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.settings.SettingsView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Nastavení</title>
  <link rel="stylesheet" href="assets/base.css">
  <link rel="stylesheet" href="assets/settings.css">
</head>
<body>
  <% SettingsView settings = (SettingsView) request.getAttribute("settings"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="settings-page">
    <header class="settings-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">SYSTÉM / NASTAVENÍ</span><h1>Nastavení</h1><p>Konfigurace společnosti, lokality a pravidel používaných napříč ERP.</p></div>
      <span class="settings-state">CENTRÁLNÍ KONFIGURACE</span>
    </header>
    <% if (error != null) { %><p class="settings-message error"><%= error %></p><% } if (actionError != null) { %><p class="settings-message error"><%= actionError %></p><% } if (message != null) { %><p class="settings-message"><%= message %></p><% } %>
    <% if (settings != null) { %>
    <form method="post" class="settings-form">
      <section class="settings-section"><div class="section-copy"><span class="eyebrow">ORGANIZACE</span><h2>Identita společnosti</h2><p>Tyto údaje se používají na výstupech a v systémové komunikaci.</p></div><div class="form-grid"><label>Název společnosti<input name="companyName" required value="<%= settings.companyName() %>"></label><label>Kontaktní e-mail<input name="companyEmail" type="email" required value="<%= settings.companyEmail() %>"></label></div></section>
      <section class="settings-section"><div class="section-copy"><span class="eyebrow">LOKALIZACE A ÚČETNICTVÍ</span><h2>Provozní pravidla</h2><p>Nastavte měnu, pracovní čas a výchozí pravidla pro obchodní dokumenty.</p></div><div class="form-grid"><label>Měna<select name="currencyCode"><option value="CZK" <%= "CZK".equals(settings.currencyCode()) ? "selected" : "" %>>CZK</option><option value="EUR" <%= "EUR".equals(settings.currencyCode()) ? "selected" : "" %>>EUR</option></select></label><label>Časové pásmo<select name="timezone"><option value="Europe/Prague" <%= "Europe/Prague".equals(settings.timezone()) ? "selected" : "" %>>Europe/Prague</option><option value="Europe/Bratislava" <%= "Europe/Bratislava".equals(settings.timezone()) ? "selected" : "" %>>Europe/Bratislava</option></select></label><label>Začátek účetního roku<input name="fiscalYearStartMonth" type="number" min="1" max="12" required value="<%= settings.fiscalYearStartMonth() %>"></label><label>Výchozí splatnost (dny)<input name="defaultPaymentTermsDays" type="number" min="0" max="365" required value="<%= settings.defaultPaymentTermsDays() %>"></label></div></section>
      <footer class="settings-footer"><span>Poslední změna: <%= settings.updatedAt().replace('T', ' ') %></span><button class="primary" type="submit">Uložit nastavení</button></footer>
    </form>
    <% } %>
  </main>
</body>
</html>