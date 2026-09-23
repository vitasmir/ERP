<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.planning.PlanningOverviewView" %>
<%@ page import="com.example.erp.frontend.planning.PlanningOverviewView.ShiftView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Plánování</title>
  <link rel="stylesheet" href="assets/base.css">
  <link rel="stylesheet" href="assets/planning.css">
</head>
<body>
  <% PlanningOverviewView overview = (PlanningOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="planning-page">
    <header class="planning-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / PLÁNOVÁNÍ</span><h1>Plánování</h1><p>Rozvrhujte směny, hlídejte neobsazené sloty a publikujte týdenní plán týmu.</p></div>
      <a href="#schedule" class="primary">Plán směn</a>
    </header>
    <% if (error != null) { %><p class="planning-message error"><%= error %></p><% } if (actionError != null) { %><p class="planning-message error"><%= actionError %></p><% } if (message != null) { %><p class="planning-message"><%= message %></p><% } %>
    <section class="planning-metrics">
      <article><span>Naplánované směny</span><strong><%= overview == null ? "-" : overview.shiftCount() %></strong><small>sloty v aktuálním plánu</small></article>
      <article><span>Neobsazené sloty</span><strong><%= overview == null ? "-" : overview.openShiftCount() %></strong><small>čekají na přiřazení</small></article>
      <article><span>Kapacita týmu</span><strong><%= overview == null ? "-" : overview.plannedHours() %> h</strong><small><%= overview == null ? "-" : overview.draftShiftCount() %> směn v návrhu</small></article>
    </section>
    <section id="schedule" class="planning-section">
      <div class="section-head"><div><span class="eyebrow">TÝDENNÍ PLÁN</span><h2>Směny a kapacity</h2></div><span class="shift-count"><%= overview == null ? 0 : overview.shifts().size() %> sloty</span></div>
      <div class="shift-list">
        <% if (overview != null) for (ShiftView shift : overview.shifts()) { %>
        <article class="shift-card">
          <div class="shift-main"><span class="status-chip status-<%= shift.status().toLowerCase() %>"><%= shift.status().equals("PUBLISHED") ? "PUBLIKOVÁNO" : "NÁVRH" %></span><h3><%= shift.roleName() %></h3><p><%= shift.department() %> · <%= shift.employeeName() == null ? "Neobsazeno" : shift.employeeName() %></p></div>
          <dl><div><dt>Začátek</dt><dd><%= shift.startAt().replace('T', ' ') %></dd></div><div><dt>Konec</dt><dd><%= shift.endAt().replace('T', ' ') %></dd></div></dl>
          <% if ("DRAFT".equals(shift.status())) { %><form method="post"><input type="hidden" name="id" value="<%= shift.id() %>"><button class="secondary" type="submit">Publikovat směnu</button></form><% } else { %><span class="published-label">Plán je viditelný</span><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
</body>
</html>