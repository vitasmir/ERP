<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.hr.HrOverviewView" %>
<%@ page import="com.example.erp.frontend.hr.HrOverviewView.EmployeeView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Lidé</title>
  <link rel="stylesheet" href="assets/base.css">
  <link rel="stylesheet" href="assets/hr.css">
</head>
<body>
  <% HrOverviewView overview = (HrOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="hr-page">
    <header class="hr-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PEOPLE / HR</span><h1>Lidé</h1><p>Udržujte přehled o týmech, pracovních rolích a nástupech zaměstnanců.</p></div>
      <a href="#employees" class="primary">Přehled týmu</a>
    </header>
    <% if (error != null) { %><p class="hr-message error"><%= error %></p><% } if (actionError != null) { %><p class="hr-message error"><%= actionError %></p><% } if (message != null) { %><p class="hr-message"><%= message %></p><% } %>
    <section class="hr-metrics">
      <article><span>Aktivní zaměstnanci</span><strong><%= overview == null ? "-" : overview.activeEmployeeCount() %></strong><small>v evidovaných týmech</small></article>
      <article><span>Nástupy</span><strong><%= overview == null ? "-" : overview.onboardingCount() %></strong><small>čekají na aktivaci</small></article>
      <article><span>Týmy</span><strong><%= overview == null ? "-" : overview.teamCount() %></strong><small>napříč organizací</small></article>
    </section>
    <section id="employees" class="hr-section">
      <div class="section-head"><div><span class="eyebrow">ZAMĚSTNANCI</span><h2>Organizace a nástupy</h2></div><span class="employee-count"><%= overview == null ? 0 : overview.employees().size() %> zaměstnanci</span></div>
      <div class="employee-list">
        <% if (overview != null) for (EmployeeView employee : overview.employees()) { String initials = employee.fullName().chars().filter(character -> character == ' ').count() > 0 ? employee.fullName().substring(0, 1) + employee.fullName().substring(employee.fullName().lastIndexOf(' ') + 1, employee.fullName().lastIndexOf(' ') + 2) : employee.fullName().substring(0, Math.min(2, employee.fullName().length())); %>
        <article class="employee-card">
          <div class="employee-main"><span class="employee-avatar"><%= initials.toUpperCase() %></span><div><span class="status-chip status-<%= employee.status().toLowerCase() %>"><%= employee.status().equals("ONBOARDING") ? "NÁSTUP" : employee.status().equals("ACTIVE") ? "AKTIVNÍ" : "NEAKTIVNÍ" %></span><h3><%= employee.fullName() %></h3><p><%= employee.jobTitle() %></p></div></div>
          <dl><div><dt>Tým</dt><dd><%= employee.teamName() %></dd></div><div><dt>Nástup</dt><dd><%= employee.employmentStartDate() %></dd></div></dl>
          <% if ("ONBOARDING".equals(employee.status())) { %><form method="post"><input type="hidden" name="id" value="<%= employee.id() %>"><button class="secondary" type="submit">Aktivovat nástup</button></form><% } else { %><span class="active-label">Profil je aktivní</span><% } %>
          <% if (employee.hasUserAccount()) { %><a href="users">Účet v Uživatelích</a><% } else { %><a href="users?employeeId=<%= employee.id() %>">Vytvořit účet</a><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
</body>
</html>