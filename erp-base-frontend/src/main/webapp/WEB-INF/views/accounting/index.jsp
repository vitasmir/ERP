<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.accounting.AccountingOverviewView" %>
<%@ page import="com.example.erp.frontend.accounting.AccountingOverviewView.InvoiceView" %>
<%@ page import="com.example.erp.frontend.companies.CompaniesServlet.CompanyView" %>
<!doctype html>
<html lang="cs">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>ERP | Účetnictví</title>
						<link rel="stylesheet" href="assets/base.css?v=20260928-160400">
	<link rel="stylesheet" href="assets/accounting.css?v=20260928-154028">
</head>
<body>
	<%
		AccountingOverviewView overview = (AccountingOverviewView) request.getAttribute("overview");
		CompanyView[] companies = (CompanyView[]) request.getAttribute("companies");
		String error = (String) request.getAttribute("error");
		String message = request.getParameter("message");
	%>
	<main class="accounting-page">
		<header class="accounting-header">
			<a href="apps" class="back-link">← Aplikace</a>
			<div>
				<span class="eyebrow">FINANCE / ÚČETNICTVÍ</span>
				<h1>Účetnictví</h1>
				<p>Faktury, pohledávky a peněžní tok na jednom místě.</p>
			</div>
			<a href="#invoices" class="primary">Přehled faktur</a>
		</header>

		<% if (error != null) { %>
			<p class="accounting-message error"><%= error %></p>
		<% } if (message != null) { %>
			<p class="accounting-message"><%= message %></p>
		<% } %>

		<section class="accounting-metrics">
			<article><span>Pohledávky</span><strong><%= overview == null ? "-" : overview.receivables() %> Kč</strong><small>neuhrazené faktury</small></article>
			<article><span>Po splatnosti</span><strong><%= overview == null ? "-" : overview.overdue() %> Kč</strong><small>vyžaduje kontrolu</small></article>
			<article><span>Otevřené faktury</span><strong><%= overview == null ? "-" : overview.openInvoiceCount() %></strong><small>čekají na úhradu</small></article>
		</section>

		<section class="workflow-form">
			<h2>Nová faktura</h2>
			<form class="invoice-form" method="post">
				<input type="hidden" name="action" value="create">
				<label>Číslo faktury<input name="invoiceNumber" placeholder="FV-2026-0019" required></label>
				<label>Odběratel<input name="partnerName" list="accounting-companies" placeholder="Vyberte společnost" required></label>
				<datalist id="accounting-companies"><% if (companies != null) for (CompanyView company : companies) { %><option value="<%= company.name() %>"><% } %></datalist>
				<label>Vystaveno<input type="date" name="issueDate" required></label>
				<label>Splatnost<input type="date" name="dueDate" required></label>
				<label>Celkem<input type="number" step="0.01" name="totalAmount" placeholder="0,00 Kč" required></label>
				<button class="primary" type="submit">Vystavit fakturu</button>
			</form>
		</section>

		<section id="invoices" class="invoice-section">
			<div class="section-head">
				<div><span class="eyebrow">VYSTAVENÉ FAKTURY</span><h2>Přehled pohledávek</h2></div>
				<span class="invoice-count"><%= overview == null ? 0 : overview.invoices().size() %> faktur</span>
			</div>
			<div class="invoice-table-wrap">
				<table class="invoice-table">
					<thead><tr><th>Stav</th><th>Faktura</th><th>Odběratel</th><th>Vystaveno</th><th>Splatnost</th><th>Celkem</th><th>Akce</th></tr></thead>
					<tbody>
				<% if (overview != null) for (InvoiceView invoice : overview.invoices()) { %>
					<tr class="invoice-row">
						<td><span class="status-chip status-<%= invoice.status().toLowerCase() %>"><%= invoice.status() %></span></td>
						<td><strong><%= invoice.invoiceNumber() %></strong></td>
						<td><span class="invoice-partner"><%= invoice.partnerName() %></span></td>
						<td><%= invoice.issueDate() %></td>
						<td><%= invoice.dueDate() %></td>
						<td><strong><%= invoice.totalAmount() %> Kč</strong></td>
						<td class="invoice-action-cell">
						<% if (!"PAID".equals(invoice.status())) { %>
							<form method="post">
								<input type="hidden" name="id" value="<%= invoice.id() %>">
								<button class="secondary" type="submit">Označit jako uhrazenou</button>
							</form>
						<% } else { %>
							<span class="paid-label">Uhrazeno <%= invoice.paidAmount() %> Kč</span>
						<% } %>
						</td>
					</tr>
				<% } %>
					</tbody>
				</table>
			</div>
		</section>
	</main>
	<script src="assets/base.js?v=20260928-160300"></script>
</body>
</html>