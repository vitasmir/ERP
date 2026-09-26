<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="com.example.erp.frontend.companies.CompaniesServlet.CompanyView" %>
<%@ include file="fragments/base-header.jspf" %>
<link rel="stylesheet" href="assets/companies.css">
<style>.topbar .breadcrumb{display:none !important}</style>
<style>.company-logo{background:var(--card-color,#d9ed62)}</style>
<%
	CompanyView[] loadedCompanies = (CompanyView[]) request.getAttribute("companies");
	List<CompanyView> companies = loadedCompanies == null ? List.of() : List.of(loadedCompanies);
	String message = (String) request.getAttribute("message");
	String error = (String) request.getAttribute("error");
%>
<section class="section-head"><div><span class="eyebrow">BASE / ENTITIES</span><h2>Společnosti</h2><p>Organizační jednotky a jejich konfigurace.</p></div><button class="primary" id="add-company" type="button">+ Nová společnost</button></section>
<% if (message != null) { %><div class="company-alert success-alert"><%= escapeHtml(message) %></div><% } %>
<% if (error != null) { %><div class="company-alert error-alert"><%= escapeHtml(error) %></div><% } %>
<section class="company-grid"><% for (CompanyView company : companies) { %><article><span class="company-logo"><%= escapeHtml(company.initial()) %></span><h3><%= escapeHtml(company.name()) %></h3><small><%= escapeHtml(company.type()) %> · <%= escapeHtml(company.currency()) %> · <%= escapeHtml(company.statusLabel()) %></small></article><% } %></section>
<div class="company-modal" id="company-modal" aria-hidden="true"><div class="company-modal-backdrop"></div><section class="company-dialog" role="dialog" aria-modal="true" aria-labelledby="company-dialog-title"><button class="dialog-close" id="close-company-dialog" type="button" aria-label="Zavřít">×</button><span class="eyebrow">BASE / ENTITIES</span><h2 id="company-dialog-title">Nová společnost</h2><p>Přidejte organizační jednotku do ERP.</p><form method="post" action="companies" id="company-form"><label>Název společnosti<input name="name" required maxlength="200"></label><label>Typ společnosti<input name="type" required maxlength="100" placeholder="Např. Centrála"></label><label>Měna<select name="currency"><option value="CZK">CZK</option><option value="EUR">EUR</option><option value="USD">USD</option></select></label><label>Stav<select name="status"><option value="ACTIVE">Aktivní</option><option value="INACTIVE">Neaktivní</option></select></label><div class="dialog-actions"><button class="secondary-button" id="cancel-company-dialog" type="button">Zrušit</button><button class="primary" type="submit">Vytvořit společnost</button></div></form></section></div>
<%@ include file="fragments/base-footer.jspf" %>
<%!
	private String escapeHtml(String value) {
		if (value == null) return "";
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#39;");
	}
%>