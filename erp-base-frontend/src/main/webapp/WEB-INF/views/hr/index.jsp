<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.hr.HrOverviewView" %>
<%@ page import="com.example.erp.frontend.hr.HrOverviewView.EmployeeView" %>
<%@ page import="com.example.erp.frontend.hr.HrServlet.RoleOption" %>
<%@ page import="com.fasterxml.jackson.databind.JsonNode" %>
<%@ page import="static com.example.erp.frontend.base.RolesServlet.escapeHtml" %>
<%@ page import="java.time.LocalDate" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Lidé</title>
  <link rel="stylesheet" href="assets/base.css?v=20261001-1">
  <link rel="stylesheet" href="assets/hr.css?v=20260928-154028">
  <link rel="stylesheet" href="assets/workforce.css?v=20261002-1">
</head>
<body>
  <% HrOverviewView overview = (HrOverviewView) request.getAttribute("overview"); RoleOption[] roleOptions = (RoleOption[]) request.getAttribute("roleOptions"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <%
    JsonNode availability = (JsonNode) request.getAttribute("availability");
    Object selectedEmployeeId = request.getAttribute("selectedEmployeeId");
    String role = java.text.Normalizer.normalize(String.valueOf(session.getAttribute("roleName")), java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").strip().toLowerCase(java.util.Locale.ROOT);
    boolean admin = java.util.Set.of("administrator", "admin").contains(role);
    boolean edit = admin || java.util.Set.of("hr", "personalista").contains(role);
  %>
  <main class="hr-page">
    <header class="hr-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PEOPLE / HR</span><h1>Lidé</h1></div>
      <a href="#employees" class="primary">Přehled týmu</a>
    </header>
    <% if (error != null) { %><p role="alert" class="hr-message error"><%= escapeHtml(error) %></p><% } if (actionError != null) { %><p role="alert" class="hr-message error"><%= escapeHtml(actionError) %></p><% } if (message != null) { %><p role="status" class="hr-message"><%= escapeHtml(message) %></p><% } %>
    <section class="hr-metrics">
      <article><span>Aktivní zaměstnanci</span><strong><%= overview == null ? "-" : overview.activeEmployeeCount() %></strong><small>v evidovaných týmech</small></article>
      <article><span>Nástupy</span><strong><%= overview == null ? "-" : overview.onboardingCount() %></strong><small>čekají na aktivaci</small></article>
      <article><span>Týmy</span><strong><%= overview == null ? "-" : overview.teamCount() %></strong><small>napříč organizací</small></article>
    </section>
    <% if (edit) { %>
    <section class="workforce-section">
      <h2>Týmy</h2>
      <form method="post" class="workforce-form">
        <input type="hidden" name="action" value="createTeam">
        <label>Nový tým<input name="name" maxlength="150" required></label>
        <button class="primary" type="submit">Založit tým</button>
      </form>
      <div class="team-list">
           <% if (overview != null && overview.teams() != null) for (HrOverviewView.TeamView team : overview.teams()) {
             long teamMemberCount = overview.employees().stream().filter(employee -> team.id().equals(employee.teamId())).count(); %>
        <div class="team-row">
          <form method="post">
          <input type="hidden" name="action" value="updateTeam">
          <input type="hidden" name="teamId" value="<%= team.id() %>">
          <input name="name" value="<%= escapeHtml(team.name()) %>" maxlength="150" required>
          <span class="team-member-count">(<%= teamMemberCount %>)</span>
          <button class="secondary" type="submit">Přejmenovat</button>
        </form>
          <form method="post">
            <input type="hidden" name="action" value="deleteTeam">
            <input type="hidden" name="teamId" value="<%= team.id() %>">
            <button class="secondary danger-action" type="submit">Smazat</button>
          </form>
          <button class="secondary team-filter-button" type="button" data-team-filter="<%= team.id() %>">Zobrazit členy týmu</button>
          <button class="secondary team-filter-button" type="button" data-show-all-employees>Zobrazit všechny zaměstnance</button>
        </div>
        <% } %>
      </div>
    </section>
    <section class="workforce-section">
      <h2>Nový zaměstnanec</h2>
      <form method="post" class="workforce-form">
        <input type="hidden" name="action" value="create">
        <label>Jméno a příjmení<input name="fullName" maxlength="200" required></label>
        <label>Tým<select name="teamId" id="new-team" required><option value="" disabled selected>Vyberte tým</option><% if (overview != null && overview.teams() != null) for (HrOverviewView.TeamView team : overview.teams()) { %><option value="<%= team.id() %>" data-team-name="<%= escapeHtml(team.name()) %>"><%= escapeHtml(team.name()) %></option><% } %></select></label>
        <label>Pracovní role<select name="jobTitle" id="new-job-title" required><option value="" disabled selected>Vyberte roli</option><% if (roleOptions != null) for (RoleOption option : roleOptions) { %><option value="<%= escapeHtml(option.name()) %>"><%= escapeHtml(option.name()) %></option><% } %></select></label>
        <label>Nástup<input type="date" name="employmentStartDate" required></label>
        <label>Zástupce<select name="deputyEmployeeId" data-deputy-role-for="new-job-title" data-deputy-team-for="new-team" required><option value="" disabled selected>Vyberte zástupce</option><% if (overview != null) for (EmployeeView deputy : overview.employees()) { %><option value="<%= deputy.id() %>" data-user-role="<%= escapeHtml(deputy.userRoleName() == null ? "" : deputy.userRoleName()) %>" data-team-id="<%= deputy.teamId() %>" data-team-name="<%= escapeHtml(deputy.teamName() == null ? "" : deputy.teamName()) %>"><%= escapeHtml(deputy.fullName()) %></option><% } %></select></label>
        <button class="primary" type="submit">Založit zaměstnance</button>
      </form>
    </section>
    <% } %>
    <section id="employees" class="hr-section">
      <div class="section-head"><div><span class="eyebrow">ZAMĚSTNANCI</span><h2>Organizace a nástupy</h2></div><span class="employee-count" id="employee-count"><%= overview == null ? 0 : overview.employees().size() %> zaměstnanci</span></div>
      <div class="employee-table-wrap">
        <table class="employee-table">
          <thead><tr><th>STAV</th><th>ZAMĚSTNANEC</th><th>PRACOVNÍ ROLE</th><th>TÝM</th><th>ZÁSTUPCE</th><th>NÁSTUP</th><th>AKTIVACE</th><th>DOSTUPNOST</th><th>ÚČET</th></tr></thead>
          <tbody>
        <% if (overview != null) for (EmployeeView employee : overview.employees()) {
             String initials = employee.fullName().chars().filter(character -> character == ' ').count() > 0
                 ? employee.fullName().substring(0, 1) + employee.fullName().substring(employee.fullName().lastIndexOf(' ') + 1, employee.fullName().lastIndexOf(' ') + 2)
             : employee.fullName().substring(0, Math.min(2, employee.fullName().length()));
             boolean employmentStarted = !LocalDate.parse(employee.employmentStartDate()).isAfter(LocalDate.now());
             boolean jobTitleIsRole = false;
             String roleInitial = employee.jobTitle() == null || employee.jobTitle().isBlank() ? "?" : employee.jobTitle().substring(0, 1).toUpperCase();
             String roleColor = "#D9ED62";
             if (roleOptions != null) for (RoleOption option : roleOptions) {
               if (option.name().equals(employee.jobTitle())) { jobTitleIsRole = true; roleInitial = option.initial(); roleColor = option.color(); break; }
             }
             String selectedRole = jobTitleIsRole ? employee.jobTitle() : employee.userRoleName(); %>
        <tr class="employee-record" data-employee-team-id="<%= employee.teamId() %>">
          <td><span class="status-chip status-<%= escapeHtml(employee.status().toLowerCase()) %>"><%= employee.status().equals("ONBOARDING") ? "NÁSTUP" : employee.status().equals("ACTIVE") ? "AKTIVNÍ" : "NEAKTIVNÍ" %></span></td>
          <td><div class="employee-main"><span class="employee-avatar"><%= escapeHtml(initials.toUpperCase()) %></span><strong><%= escapeHtml(employee.fullName()) %></strong></div></td>
          <td><span class="employee-role-mark" style="--role-color:<%= escapeHtml(roleColor) %>"><%= escapeHtml(roleInitial) %></span><%= escapeHtml(employee.jobTitle()) %></td>
          <td><%= escapeHtml(employee.teamName()) %></td>
          <td><%= escapeHtml(employee.deputyName()) %></td>
          <td><%= escapeHtml(employee.employmentStartDate()) %></td>
          <td class="employee-action-cell"><% if (edit) { %><form method="post"><input type="hidden" name="id" value="<%= employee.id() %>"><input type="hidden" name="action" value="<%= "ACTIVE".equals(employee.status()) ? "deactivate" : "activate" %>"><% if ("ACTIVE".equals(employee.status()) || employmentStarted) { %><button class="secondary" type="submit"><%= "ACTIVE".equals(employee.status()) ? "Ukončit pracovní poměr" : "Aktivovat nástup" %></button><% } else { %><button class="secondary" type="button" disabled title="Aktivace bude možná po datu nástupu">Aktivovat po nástupu</button><% } %></form><% } %></td>
          <td class="employee-action-cell"><a href="hr?employeeId=<%= employee.id() %>#availability">Dostupnost a kvalifikace</a></td>
          <td class="employee-action-cell"><% if (admin) { if (employee.hasUserAccount()) { %><a href="users">Účet v Uživatelích</a><% } else { %><a href="users?employeeId=<%= employee.id() %>">Vytvořit účet</a><% } } %></td>
        </tr>
          <% if (edit) { %>
        <tr class="employee-edit-row" data-employee-team-id="<%= employee.teamId() %>"><td colspan="9"><details class="workforce-edit"><summary>Upravit zaměstnance</summary>
            <form method="post" class="workforce-form">
              <input type="hidden" name="action" value="update"><input type="hidden" name="id" value="<%= employee.id() %>">
              <label>Jméno a příjmení<input name="fullName" maxlength="200" value="<%= escapeHtml(employee.fullName()) %>" required></label>
              <label>Tým<select name="teamId" id="team-<%= employee.id() %>" required><% if (overview.teams() != null) for (HrOverviewView.TeamView team : overview.teams()) { %><option value="<%= team.id() %>" data-team-name="<%= escapeHtml(team.name()) %>" <%= team.id().equals(employee.teamId()) ? "selected" : "" %>><%= escapeHtml(team.name()) %></option><% } %></select></label>
              <label>Pracovní role<select name="jobTitle" id="job-title-<%= employee.id() %>" required><% if (roleOptions != null) for (RoleOption option : roleOptions) { %><option value="<%= escapeHtml(option.name()) %>" <%= option.name().equals(selectedRole) ? "selected" : "" %>><%= escapeHtml(option.name()) %></option><% } %></select></label>
              <label>Nástup<input type="date" name="employmentStartDate" value="<%= escapeHtml(employee.employmentStartDate()) %>" required></label>
              <label>Zástupce<select name="deputyEmployeeId" data-deputy-role-for="job-title-<%= employee.id() %>" data-deputy-team-for="team-<%= employee.id() %>" required><% if (overview.employees() != null) for (EmployeeView deputy : overview.employees()) { if (!deputy.id().equals(employee.id())) { %><option value="<%= deputy.id() %>" data-user-role="<%= escapeHtml(deputy.userRoleName() == null ? "" : deputy.userRoleName()) %>" data-team-id="<%= deputy.teamId() %>" data-team-name="<%= escapeHtml(deputy.teamName() == null ? "" : deputy.teamName()) %>" <%= deputy.id().equals(employee.deputyEmployeeId()) ? "selected" : "" %>><%= escapeHtml(deputy.fullName()) %></option><% } } %></select></label>
              <button type="submit" class="secondary">Uložit profil</button>
            </form>
          </details></td></tr>
          <% } %>
        <% } %>
          </tbody>
        </table>
      </div>
    </section>
    <% if (availability != null) { %>
    <section id="availability" class="workforce-section">
      <h2>Dostupnost a kvalifikace</h2>
      <% if (overview != null) for (EmployeeView employee : overview.employees()) { if (employee.id().equals(selectedEmployeeId)) { %><h3><%= escapeHtml(employee.fullName()) %></h3><% } } %>
      <h3>Kvalifikace</h3>
      <ul><% for (JsonNode qualification : availability.path("qualifications")) { %><li><%= escapeHtml(qualification.asText()) %></li><% } %></ul>
      <% if (edit) { %><form method="post" class="workforce-form">
        <input type="hidden" name="action" value="qualification"><input type="hidden" name="id" value="<%= selectedEmployeeId %>">
        <label>Pracovní role<input name="roleName" maxlength="150" required></label><button type="submit" class="secondary">Přidat kvalifikaci</button>
      </form><% } %>
      <h3>Absence</h3>
      <% if (availability.path("absences").isEmpty()) { %><p>Žádné absence.</p><% } %>
      <% for (JsonNode absence : availability.path("absences")) { %>
      <div class="workforce-event"><p><%= escapeHtml(absence.path("startAt").asText()) %> – <%= escapeHtml(absence.path("endAt").asText()) %> / <%= escapeHtml(absence.path("reason").asText()) %></p>
        <% if (edit) { %><form method="post"><input type="hidden" name="action" value="removeAbsence"><input type="hidden" name="id" value="<%= selectedEmployeeId %>"><input type="hidden" name="absenceId" value="<%= escapeHtml(absence.path("id").asText()) %>"><button type="submit" class="secondary">Zrušit absenci</button></form><% } %>
      </div><% } %>
      <% if (edit) { %><form method="post" class="workforce-form">
        <input type="hidden" name="action" value="absence"><input type="hidden" name="id" value="<%= selectedEmployeeId %>">
        <label>Od<input type="datetime-local" name="startAt" required></label><label>Do<input type="datetime-local" name="endAt" required></label>
        <label>Důvod<input name="reason" maxlength="240" required></label><button type="submit" class="secondary">Zapsat absenci</button>
      </form><% } %>
    </section>
    <% } %>
  </main>
  <script>
    var employeeRows = document.querySelectorAll('[data-employee-team-id]');
    var employeeCount = document.getElementById('employee-count');
    function filterEmployees(teamId) {
      var visibleEmployees = 0;
      employeeRows.forEach(function (row) {
        var visible = !teamId || row.dataset.employeeTeamId === teamId;
        row.hidden = !visible;
        if (visible && row.classList.contains('employee-record')) visibleEmployees += 1;
      });
      if (employeeCount) employeeCount.textContent = visibleEmployees + ' zaměstnanci';
    }
    document.querySelectorAll('[data-team-filter]').forEach(function (button) {
      button.addEventListener('click', function () {
        filterEmployees(button.dataset.teamFilter);
        document.getElementById('employees').scrollIntoView({ behavior: 'smooth' });
      });
    });
    document.querySelectorAll('[data-show-all-employees]').forEach(function (button) {
      button.addEventListener('click', function () {
        filterEmployees('');
        document.getElementById('employees').scrollIntoView({ behavior: 'smooth' });
      });
    });
    document.querySelectorAll('[data-deputy-role-for]').forEach(function (deputySelect) {
      var roleSelect = document.getElementById(deputySelect.dataset.deputyRoleFor);
      var teamSelect = document.getElementById(deputySelect.dataset.deputyTeamFor);
      if (!roleSelect || !teamSelect) return;
      var selectedDeputy = deputySelect.value;
      function normalizedRole(value) {
        return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase();
      }
      function normalizedTeam(value) {
        return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase();
      }
      function filterDeputies() {
        var role = normalizedRole(roleSelect.value);
        var team = teamSelect.value;
        var teamName = normalizedTeam(teamSelect.options[teamSelect.selectedIndex]?.dataset.teamName || '');
        var availableDeputies = 0;
        Array.from(deputySelect.options).forEach(function (option) {
          var sameTeam = option.dataset.teamId === team || normalizedTeam(option.dataset.teamName || '') === teamName;
          var matches = !option.value || normalizedRole(option.dataset.userRole) === role && sameTeam;
          option.hidden = !matches;
          option.disabled = !matches;
          if (matches && option.value) availableDeputies += 1;
        });
        deputySelect.required = availableDeputies > 0;
        if (selectedDeputy && !deputySelect.querySelector('option[value="' + selectedDeputy + '"]:not([disabled])')) {
          deputySelect.value = '';
        }
        selectedDeputy = deputySelect.value;
      }
      roleSelect.addEventListener('change', filterDeputies);
      filterDeputies();
    });
  </script>
  <script src="assets/base.js?v=20261005-1"></script>
</body>
</html>