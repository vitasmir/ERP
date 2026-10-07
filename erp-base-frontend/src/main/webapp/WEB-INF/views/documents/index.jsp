<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.documents.DocumentsOverviewView" %>
<%@ page import="com.example.erp.frontend.documents.DocumentsOverviewView.DocumentView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Dokumenty</title>
  <link rel="stylesheet" href="assets/base.css?v=20261001-1">
  <link rel="stylesheet" href="assets/documents.css?v=20261007-1">
</head>
<body>
  <% DocumentsOverviewView overview = (DocumentsOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="documents-page">
    <header class="documents-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">BASE / DOKUMENTY</span><h1>Dokumenty</h1><p>Spravujte interní dokumenty, jejich vlastníky a schvalování v jednom přehledu.</p></div>
      <a href="#documents" class="primary">Přehled dokumentů</a>
    </header>
    <% if (error != null) { %><p class="documents-message error"><%= error %></p><% } if (actionError != null) { %><p class="documents-message error"><%= actionError %></p><% } if (message != null) { %><p class="documents-message"><%= message %></p><% } %>
    <section class="documents-metrics">
      <article><span>Čeká na schválení</span><strong><%= overview == null ? "-" : overview.pendingApprovalCount() %></strong><small>vyžaduje rozhodnutí</small></article>
      <article><span>Schválené dokumenty</span><strong><%= overview == null ? "-" : overview.approvedCount() %></strong><small>v aktuálním přehledu</small></article>
      <article><span>Kategorie</span><strong><%= overview == null ? "-" : overview.categoryCount() %></strong><small>typy uložených dokumentů</small></article>
    </section>
    <section id="documents" class="documents-section">
      <div class="section-head"><div><span class="eyebrow">EVIDENCE DOKUMENTŮ</span><h2>Soubory a schvalovací procesy</h2></div><span class="document-count"><%= overview == null ? 0 : overview.documents().size() %> dokumenty</span></div>
      <div class="document-list">
        <% if (overview != null) for (DocumentView document : overview.documents()) { %>
        <article class="document-card">
          <div class="document-main"><span class="document-mark">▣</span><div><span class="status-chip status-<%= document.status().toLowerCase() %>"><%= document.status().equals("PENDING_APPROVAL") ? "KE SCHVÁLENÍ" : document.status().equals("APPROVED") ? "SCHVÁLENO" : "KONCEPT" %></span><h3><%= document.title() %></h3><p><%= document.category() %> · <%= document.referenceCode() %></p></div></div>
          <dl><div><dt>Vlastník</dt><dd><%= document.ownerName() %></dd></div><div><dt>Aktualizováno</dt><dd><%= document.updatedOn() %></dd></div></dl>
          <% if ("PENDING_APPROVAL".equals(document.status())) { %><form method="post"><input type="hidden" name="id" value="<%= document.id() %>"><button class="secondary" type="submit">Schválit dokument</button></form><% } else { %><span class="approved-label">Proces je uzavřen</span><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
</body>
</html>