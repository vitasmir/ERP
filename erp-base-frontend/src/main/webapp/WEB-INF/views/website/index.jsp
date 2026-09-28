<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.website.WebsiteOverviewView" %>
<%@ page import="com.example.erp.frontend.website.WebsiteOverviewView.PageView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Web</title>
  <link rel="stylesheet" href="assets/base.css?v=20260928-160510">
  <link rel="stylesheet" href="assets/website.css?v=20260928-154028">
</head>
<body>
  <% WebsiteOverviewView overview = (WebsiteOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="website-page">
    <header class="website-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">OBCHOD / WEB</span><h1>Web</h1><p>Spravujte obsah webu, produktový katalog a zákaznické formuláře propojené s ERP.</p></div>
      <a href="#pages" class="primary">Obsah webu</a>
    </header>
    <% if (error != null) { %><p class="website-message error"><%= error %></p><% } if (actionError != null) { %><p class="website-message error"><%= actionError %></p><% } if (message != null) { %><p class="website-message"><%= message %></p><% } %>
    <section class="website-metrics">
      <article><span>Publikované stránky</span><strong><%= overview == null ? "-" : overview.publishedPageCount() %></strong><small>viditelné návštěvníkům</small></article>
      <article><span>Koncepty k publikaci</span><strong><%= overview == null ? "-" : overview.draftPageCount() %></strong><small>čekají na schválení</small></article>
      <article><span>Návštěvy za měsíc</span><strong><%= overview == null ? "-" : String.format("%,d", overview.monthlyVisits()).replace(',', ' ') %></strong><small><%= overview == null ? "-" : overview.formPageCount() %> stránek s formulářem</small></article>
    </section>
    <section class="workflow-form">
      <h2>Nová stránka</h2>
      <form method="post">
        <input type="hidden" name="action" value="create">
        <input name="title" placeholder="Název stránky" required>
        <input name="slug" placeholder="/url-slug" required>
        <select name="contentType">
          <option value="CONTENT">Obsah</option>
          <option value="LANDING">Úvod</option>
          <option value="CATALOG">Katalog</option>
          <option value="CAMPAIGN">Kampaň</option>
        </select>
        <input name="ownerName" placeholder="Správce" required>
        <textarea name="content" placeholder="Obsah stránky"></textarea>
        <button class="primary" type="submit">Uložit koncept</button>
      </form>
    </section>
    <section id="pages" class="website-section">
      <div class="section-head"><div><span class="eyebrow">OBSAH A KATALOG</span><h2>Stránky webu</h2></div><span class="page-count"><%= overview == null ? 0 : overview.pages().size() %> položky</span></div>
      <div class="page-list">
        <% if (overview != null) for (PageView item : overview.pages()) { %>
        <article class="web-card">
          <div class="web-main"><span class="content-type"><%= item.contentType().equals("LANDING") ? "ÚVOD" : item.contentType().equals("CATALOG") ? "KATALOG" : item.contentType().equals("CAMPAIGN") ? "KAMPAŇ" : "OBSAH" %></span><span class="status-chip status-<%= item.status().toLowerCase() %>"><%= item.status().equals("PUBLISHED") ? "PUBLIKOVÁNO" : "KONCEPT" %></span><h3><%= item.title() %></h3><p><%= item.slug() %> · správce: <%= item.ownerName() %></p></div>
          <dl><div><dt>Návštěvy / měsíc</dt><dd><%= String.format("%,d", item.monthlyVisits()).replace(',', ' ') %></dd></div><div><dt>Formulář</dt><dd><%= item.hasContactForm() ? "Aktivní" : "Bez formuláře" %></dd></div></dl>
          <% if ("DRAFT".equals(item.status())) { %><form method="post"><input type="hidden" name="id" value="<%= item.id() %>"><button class="secondary" type="submit">Publikovat</button></form><% } else { %><span class="published-label">Stránka je online</span><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
</body>
</html>