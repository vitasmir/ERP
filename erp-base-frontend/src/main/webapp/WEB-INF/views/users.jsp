<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="com.example.erp.frontend.users.UsersServlet.UserView" %>
<%@ page import="com.example.erp.frontend.users.UsersServlet.EmployeeOption" %>
<%@ page import="com.example.erp.frontend.users.UsersServlet.RoleOption" %>
<%@ include file="fragments/base-header.jspf" %>
<link rel="stylesheet" href="assets/users.css">
<style>.topbar .breadcrumb{display:none !important}</style>
<style>.table-avatar{background:var(--card-color,#dce9d7)}</style>
<%
	UserView[] loadedUsers = (UserView[]) request.getAttribute("users");
	List<UserView> users = loadedUsers == null ? List.of() : List.of(loadedUsers);
	EmployeeOption[] loadedEmployees = (EmployeeOption[]) request.getAttribute("employeeOptions");
	List<EmployeeOption> employees = loadedEmployees == null ? List.of() : List.of(loadedEmployees);
	RoleOption[] loadedRoles = (RoleOption[]) request.getAttribute("roleOptions");
	List<RoleOption> roles = loadedRoles == null ? List.of() : List.of(loadedRoles);
	String message = (String) request.getAttribute("message");
	String error = (String) request.getAttribute("error");
	String requestedEmployeeId = request.getParameter("employeeId");
	String requestedEmployeeName = "";
	for (EmployeeOption employee : employees) {
		if (employee.id().toString().equals(requestedEmployeeId)) {
			requestedEmployeeName = employee.fullName();
			break;
		}
	}
%>
<section class="section-head"><div><span class="eyebrow">BASE / USERS</span><h2>Uživatelé</h2><p>Účty a přístupy do ERP systému.</p></div><button class="primary" id="add-user" type="button">+ Nový uživatel</button></section>
<% if (message != null) { %><div class="page-alert success-alert"><%= escapeHtml(message) %></div><% } %>
<% if (error != null) { %><div class="page-alert error-alert"><%= escapeHtml(error) %></div><% } %>
<section class="panel table-panel"><div class="table-tools"><input id="user-search" placeholder="Hledat uživatele..." aria-label="Hledat uživatele"><span id="user-count"><%= users.size() %> uživatelů</span></div><div class="table-scroll"><table><thead><tr><th>UŽIVATEL</th><th>ROLE</th><th>SPOLEČNOST</th><th>STAV</th><th>POSLEDNÍ PŘÍSTUP</th><th><span class="sr-only">AKCE</span></th></tr></thead><tbody id="user-table">
<% for (UserView user : users) { %>
<tr data-user-id="<%= user.id() %>" data-employee-id="<%= user.employeeId() == null ? "" : user.employeeId() %>" data-full-name="<%= escapeHtml(user.fullName()) %>" data-role-name="<%= escapeHtml(user.roleName()) %>" data-company-name="<%= escapeHtml(user.companyName()) %>" data-username="<%= escapeHtml(user.username()) %>" data-status="<%= escapeHtml(user.status()) %>"><td><span class="table-avatar"><%= escapeHtml(user.initials()) %></span><%= escapeHtml(user.fullName()) %></td><td><%= escapeHtml(user.roleName()) %></td><td><%= escapeHtml(user.companyName()) %></td><td><span class="status status-<%= escapeHtml(user.statusClass()) %>"><%= escapeHtml(user.statusLabel()) %></span></td><td><%= escapeHtml(user.lastAccessLabel()) %></td><td class="table-actions"><button class="table-action edit-user" type="button" title="Upravit uživatele">Upravit</button><form method="post" action="users" class="delete-user-form"><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<%= user.id() %>"><button class="table-action danger-action" type="submit" title="Smazat uživatele">Smazat</button></form></td></tr>
<% } %>
</tbody></table></div></section>
<div class="user-modal<%= requestedEmployeeId == null || requestedEmployeeId.isBlank() ? "" : " open" %>" id="user-modal" data-requested-employee-id="<%= escapeHtml(requestedEmployeeId) %>" aria-hidden="<%= requestedEmployeeId == null || requestedEmployeeId.isBlank() %>"><div class="user-modal-backdrop"></div><section class="user-dialog" role="dialog" aria-modal="true" aria-labelledby="user-dialog-title"><button class="dialog-close" id="close-user-dialog" type="button" aria-label="Zavřít">×</button><span class="eyebrow">BASE / USERS</span><h2 id="user-dialog-title">Nový uživatel</h2><p>Účet bude propojený se zaměstnancem v modulu Lidé.</p><form method="post" action="users" id="user-form"><input type="hidden" name="action" id="user-action" value="create"><input type="hidden" name="id" id="user-id"><label>Zaměstnanec<select name="employeeId" id="user-employee-id" required><option value="" disabled>Vyberte zaměstnance</option><% for (EmployeeOption employee : employees) { %><option value="<%= employee.id() %>" data-has-account="<%= employee.hasAccount() %>"<%= employee.id().toString().equals(requestedEmployeeId) ? " selected" : "" %>><%= escapeHtml(employee.fullName()) %> · <%= escapeHtml(employee.teamName()) %><%= employee.hasAccount() ? " · účet již existuje" : "" %></option><% } %></select></label><label>Jméno a příjmení<input name="fullName" id="user-full-name" value="<%= escapeHtml(requestedEmployeeName) %>" required maxlength="200"></label><label>Uživatelské jméno<input name="username" id="user-username" required maxlength="64" pattern="[A-Za-z0-9._-]{3,64}"></label><label>Heslo<input name="password" id="user-password" type="password" minlength="10" required autocomplete="new-password"></label><label>Role<select name="roleName" id="user-role-name" required><option value="" disabled>Vyberte roli</option><% for (RoleOption role : roles) { %><option value="<%= escapeHtml(role.name()) %>"><%= escapeHtml(role.name()) %></option><% } %></select></label><label>Společnost<input name="companyName" id="user-company-name" required maxlength="200"></label><label>Stav<select name="status" id="user-status"><option value="ACTIVE">Aktivní</option><option value="INVITED">Pozvánka čeká</option><option value="SUSPENDED">Pozastavený</option></select></label><div class="dialog-actions"><button class="secondary-button" id="cancel-user-dialog" type="button">Zrušit</button><button class="primary" type="submit" id="save-user">Přidat uživatele</button></div></form></section></div>
<%@ include file="fragments/base-footer.jspf" %>
<%!
	private String escapeHtml(String value) {
		if (value == null) return "";
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#39;");
	}
%>