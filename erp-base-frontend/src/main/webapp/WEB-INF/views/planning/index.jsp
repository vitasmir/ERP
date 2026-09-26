<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.planning.PlanningOverviewView" %>
<%@ page import="com.example.erp.frontend.planning.PlanningOverviewView.ShiftView" %>
<%@ page import="com.fasterxml.jackson.databind.JsonNode" %>
<%@ page import="static com.example.erp.frontend.base.RolesServlet.escapeHtml" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Plánování</title>
  <link rel="stylesheet" href="assets/base.css">
  <link rel="stylesheet" href="assets/planning.css">
  <link rel="stylesheet" href="assets/workforce.css">
</head>
<body>
  <%
    PlanningOverviewView overview = (PlanningOverviewView) request.getAttribute("overview");
    String error = (String) request.getAttribute("error");
    String message = request.getParameter("message");
    String actionError = request.getParameter("error");
    JsonNode employees = (JsonNode) request.getAttribute("employees");
    JsonNode workplaces = (JsonNode) request.getAttribute("workplaces");
    JsonNode notifications = (JsonNode) request.getAttribute("notifications");
    JsonNode events = (JsonNode) request.getAttribute("events");
    String role = java.text.Normalizer.normalize(String.valueOf(session.getAttribute("roleName")), java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").strip().toLowerCase(java.util.Locale.ROOT);
    boolean manageAll = java.util.Set.of("administrator", "admin", "hr", "personalista", "planovac", "planner").contains(role);
    boolean edit = manageAll || java.util.Set.of("vedouci tymu", "team lead").contains(role);
  %>
  <main class="planning-page">
    <header class="planning-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / PLÁNOVÁNÍ</span><h1>Plánování</h1></div>
      <a href="#schedule" class="primary">Plán směn</a>
    </header>
    <% if (error != null) { %><p role="alert" class="planning-message error"><%= escapeHtml(error) %></p><% } if (actionError != null) { %><p role="alert" class="planning-message error"><%= escapeHtml(actionError) %></p><% } if (message != null) { %><p role="status" class="planning-message"><%= escapeHtml(message) %></p><% } %>
    <section class="planning-metrics">
      <article><span>Naplánované směny</span><strong><%= overview == null ? "-" : overview.shiftCount() %></strong><small>sloty v aktuálním plánu</small></article>
      <article><span>Neobsazené sloty</span><strong><%= overview == null ? "-" : overview.openShiftCount() %></strong><small>čekají na přiřazení</small></article>
      <article><span>Kapacita týmu</span><strong><%= overview == null ? "-" : overview.plannedHours() %> h</strong><small><%= overview == null ? "-" : overview.draftShiftCount() %> směn v návrhu</small></article>
    </section>
    <% if (edit) { %>
    <section class="workforce-section">
      <h2>Nová směna</h2>
      <form method="post" class="workforce-form">
        <input type="hidden" name="action" value="create">
        <label>Zaměstnanec<select name="employeeId"><% if (manageAll) { %><option value="">Neobsazeno</option><% } %>
          <% if (employees != null) for (JsonNode employee : employees) { %><option value="<%= escapeHtml(employee.path("id").asText()) %>"><%= escapeHtml(employee.path("fullName").asText() + " / " + employee.path("teamName").asText()) %></option><% } %>
        </select></label>
        <label>Pracovní role<input name="roleName" maxlength="150" list="role-options" required></label>
        <label>Pracoviště<select name="department" required><% if (workplaces != null) for (JsonNode workplace : workplaces) { %><option value="<%= escapeHtml(workplace.path("name").asText()) %>"><%= escapeHtml(workplace.path("name").asText()) %></option><% } %></select></label>
        <label>Začátek<input type="datetime-local" name="startAt" required></label>
        <label>Konec<input type="datetime-local" name="endAt" required></label>
        <button class="primary" type="submit">Vytvořit směnu</button>
      </form>
      <datalist id="role-options"><% if (employees != null) for (JsonNode employee : employees) { %><option value="<%= escapeHtml(employee.path("jobTitle").asText()) %>"><% } %></datalist>
    </section>
    <% } %>
    <% if (manageAll) { %>
    <section class="workforce-section">
      <details><summary>Pracoviště a kapacity</summary>
        <% if (workplaces != null) for (JsonNode workplace : workplaces) { %><p><%= escapeHtml(workplace.path("name").asText()) %>: <%= workplace.path("capacity").asInt() %></p><% } %>
        <form method="post" class="workforce-form">
          <input type="hidden" name="action" value="workplace">
          <label>Pracoviště<input name="name" maxlength="150" list="workplace-options" required></label>
          <label>Souběžné sloty<input name="capacity" type="number" min="1" max="10000" value="1" required></label>
          <button type="submit" class="secondary">Uložit kapacitu</button>
        </form>
        <datalist id="workplace-options"><% if (workplaces != null) for (JsonNode workplace : workplaces) { %><option value="<%= escapeHtml(workplace.path("name").asText()) %>"><% } %></datalist>
      </details>
    </section>
    <% } %>
    <section id="schedule" class="planning-section">
      <% if (edit) { %><form method="post" id="publish-plan" class="workforce-actions"><input type="hidden" name="action" value="publishPlan"><button class="secondary" type="submit">Publikovat vybrané směny</button></form><% } %>
      <div class="section-head"><div><span class="eyebrow">TÝDENNÍ PLÁN</span><h2>Směny a kapacity</h2></div><span class="shift-count"><%= overview == null ? 0 : overview.shifts().size() %> sloty</span></div>
      <div class="shift-list">
        <% if (overview != null) for (ShiftView shift : overview.shifts()) { %>
        <article class="shift-card">
          <div class="shift-main"><span class="status-chip status-<%= escapeHtml(shift.status().toLowerCase()) %>"><%= shift.status().equals("PUBLISHED") ? "PUBLIKOVÁNO" : "NÁVRH" %></span><h3><%= escapeHtml(shift.roleName()) %></h3><p><%= escapeHtml(shift.department()) %> · <%= shift.employeeName() == null ? "Neobsazeno" : escapeHtml(shift.employeeName()) %></p></div>
          <dl><div><dt>Začátek</dt><dd><%= shift.startAt().replace('T', ' ') %></dd></div><div><dt>Konec</dt><dd><%= shift.endAt().replace('T', ' ') %></dd></div></dl>
          <div class="workforce-actions">
            <% if (edit && "DRAFT".equals(shift.status())) { %>
            <label><input type="checkbox" form="publish-plan" name="shiftIds" value="<%= shift.id() %>"> Vybrat</label>
            <form method="post"><input type="hidden" name="action" value="publish"><input type="hidden" name="id" value="<%= shift.id() %>"><button class="secondary" type="submit">Publikovat směnu</button></form>
            <form method="post"><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<%= shift.id() %>"><button class="secondary" type="submit">Smazat koncept</button></form>
            <% } %>
            <a href="planning?audit=<%= shift.id() %>#audit">Historie</a>
          </div>
          <% if (edit) { %>
          <details class="workforce-edit"><summary>Upravit směnu</summary>
            <form method="post" class="workforce-form">
              <input type="hidden" name="action" value="update"><input type="hidden" name="id" value="<%= shift.id() %>"><input type="hidden" name="version" value="<%= shift.version() %>">
              <label>Zaměstnanec<select name="employeeId"><% if (manageAll) { %><option value="">Neobsazeno</option><% } %>
                <% if (employees != null) for (JsonNode employee : employees) { %><option value="<%= escapeHtml(employee.path("id").asText()) %>" <%= employee.path("id").asText().equals(String.valueOf(shift.employeeId())) ? "selected" : "" %>><%= escapeHtml(employee.path("fullName").asText() + " / " + employee.path("teamName").asText()) %></option><% } %>
              </select></label>
              <label>Pracovní role<input name="roleName" value="<%= escapeHtml(shift.roleName()) %>" maxlength="150" list="role-options" required></label>
              <label>Pracoviště<select name="department" required><% if (workplaces != null) for (JsonNode workplace : workplaces) { %><option value="<%= escapeHtml(workplace.path("name").asText()) %>" <%= workplace.path("name").asText().equals(shift.department()) ? "selected" : "" %>><%= escapeHtml(workplace.path("name").asText()) %></option><% } %></select></label>
              <label>Začátek<input type="datetime-local" name="startAt" value="<%= escapeHtml(shift.startAt()) %>" required></label>
              <label>Konec<input type="datetime-local" name="endAt" value="<%= escapeHtml(shift.endAt()) %>" required></label>
              <button type="submit" class="secondary">Uložit směnu</button>
            </form>
          </details>
          <% } %>
        </article>
        <% } %>
      </div>
      <% if (overview != null && overview.shifts().isEmpty()) { %><p>Žádné směny.</p><% } %>
    </section>
    <% if (events != null) { %>
    <section id="audit" class="workforce-section"><h2>Historie směny</h2>
      <% for (JsonNode event : events) { %><p class="workforce-event"><time><%= escapeHtml(event.path("occurredAt").asText()) %></time> <strong><%= escapeHtml(event.path("action").asText()) %></strong> / <%= escapeHtml(event.path("actor").asText()) %><br><%= escapeHtml(event.path("details").asText()) %></p><% } %>
      <% if (events.isEmpty()) { %><p>Žádné záznamy.</p><% } %>
    </section>
    <% } %>
    <section class="workforce-section"><h2>Moje oznámení</h2>
      <% if (notifications == null || notifications.isEmpty()) { %><p>Žádná oznámení.</p><% } else for (JsonNode notice : notifications) { %>
      <div class="workforce-event"><p><%= escapeHtml(notice.path("message").asText()) %></p><time><%= escapeHtml(notice.path("createdAt").asText()) %></time>
        <% if (notice.path("readAt").isNull()) { %><form method="post"><input type="hidden" name="action" value="read"><input type="hidden" name="id" value="<%= escapeHtml(notice.path("id").asText()) %>"><button type="submit" class="secondary">Označit jako přečtené</button></form><% } %>
      </div><% } %>
    </section>
  </main>
</body>
</html>