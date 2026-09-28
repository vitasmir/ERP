<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.projects.ProjectsOverviewView" %>
<%@ page import="com.example.erp.frontend.projects.ProjectsOverviewView.ProjectView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Projekty</title>
  <link rel="stylesheet" href="assets/base.css?v=20260928-160510">
  <link rel="stylesheet" href="assets/projects.css?v=20260928-154028">
</head>
<body>
  <% ProjectsOverviewView overview = (ProjectsOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="projects-page">
    <header class="projects-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / PROJEKTY</span><h1>Projekty</h1><p>Sledujte odpovědnosti, termíny a postup klíčových iniciativ napříč organizací.</p></div>
      <a href="#portfolio" class="primary">Přehled projektů</a>
    </header>
    <% if (error != null) { %><p class="projects-message error"><%= error %></p><% } if (actionError != null) { %><p class="projects-message error"><%= actionError %></p><% } if (message != null) { %><p class="projects-message"><%= message %></p><% } %>
    <section class="projects-metrics">
      <article><span>Aktivní projekty</span><strong><%= overview == null ? "-" : overview.activeProjectCount() %></strong><small>plánované a rozpracované</small></article>
      <article><span>Probíhá</span><strong><%= overview == null ? "-" : overview.inProgressCount() %></strong><small>aktivně řešené iniciativy</small></article>
      <article><span>Termín do 14 dnů</span><strong><%= overview == null ? "-" : overview.dueSoonCount() %></strong><small>vyžaduje pozornost týmu</small></article>
    </section>
    <section id="portfolio" class="projects-section">
      <div class="section-head"><div><span class="eyebrow">PROJEKTOVÉ PORTFOLIO</span><h2>Plán a postup práce</h2></div><span class="project-count"><%= overview == null ? 0 : overview.projects().size() %> projekty</span></div>
      <div class="project-list">
        <% if (overview != null) for (ProjectView project : overview.projects()) { %>
        <article class="project-card">
          <div class="project-main"><span class="status-chip status-<%= project.status().toLowerCase() %>"><%= project.status().equals("IN_PROGRESS") ? "PROBÍHÁ" : project.status().equals("PLANNED") ? "PLÁNOVÁNO" : "DOKONČENO" %></span><h3><%= project.name() %></h3><p><%= project.department() %> · vlastník: <%= project.ownerName() %></p></div>
          <div class="progress-group"><div class="progress-meta"><span>Postup</span><b><%= project.progress() %> %</b></div><div class="progress-track"><span style="width:<%= project.progress() %>%"></span></div><small>Termín dokončení: <%= project.dueDate() %></small></div>
          <% if (!"COMPLETED".equals(project.status())) { %><form method="post"><input type="hidden" name="id" value="<%= project.id() %>"><button class="secondary" type="submit">Dokončit projekt</button></form><% } else { %><span class="completed-label">Projekt uzavřen</span><% } %>
        </article>
        <% } %>
      </div>
    </section>
  </main>
</body>
</html>