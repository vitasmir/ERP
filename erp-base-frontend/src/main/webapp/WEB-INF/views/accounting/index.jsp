<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.accounting.AccountingOverviewView" %>
<%@ page import="com.example.erp.frontend.accounting.AccountingOverviewView.InvoiceView" %>
<%@ page import="com.example.erp.frontend.accounting.AccountingOverviewView.InvoiceLineView" %>
<%@ page import="com.example.erp.frontend.companies.CompaniesServlet.CompanyView" %>
<!doctype html>
<html lang="cs">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>ERP | Účetnictví</title>
										  <link rel="stylesheet" href="assets/base.css?v=20261001-1">
	<link rel="stylesheet" href="assets/accounting.css?v=20260929-011500">
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
						<td>
							<details class="invoice-items">
								<summary><strong><%= invoice.invoiceNumber() %></strong><span><%= invoice.lines().size() %> položek</span></summary>
								<div class="invoice-items-panel">
									<% for (InvoiceLineView line : invoice.lines()) { %>
										<div class="invoice-item">
											<% if (line.imageUrl() != null && !line.imageUrl().isBlank()) { %><img src="<%= line.imageUrl() %>" alt="<%= line.description() %>"><% } else { %><span class="invoice-item-placeholder">FM</span><% } %>
											<div><strong><%= line.description() %></strong><small><%= line.quantity().stripTrailingZeros().toPlainString() %> ks · <%= line.unitPrice() %> Kč / kus</small></div>
										</div>
									<% } %>
								</div>
							</details>
						</td>
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
						<% if ("DRAFT".equals(invoice.status())) { %>
							<details class="invoice-edit"><summary>Upravit</summary>
								<form method="post">
									<h3>Upravit fakturu</h3>
									<input type="hidden" name="action" value="update">
									<input type="hidden" name="id" value="<%= invoice.id() %>">
									<input type="hidden" name="version" value="<%= invoice.version() %>">
									<label>Číslo faktury<input name="invoiceNumber" value="<%= invoice.invoiceNumber() %>" required maxlength="40"></label>
									<label>Odběratel<input name="partnerName" value="<%= invoice.partnerName() %>" required maxlength="160"></label>
									<label>Vystaveno<input type="date" name="issueDate" value="<%= invoice.issueDate() %>" required></label>
									<label>Splatnost<input type="date" name="dueDate" value="<%= invoice.dueDate() %>" required></label>
									<label>Celkem<input type="number" name="totalAmount" value="<%= invoice.totalAmount() %>" min="0.01" step="0.01" required></label>
									<div class="invoice-edit-actions"><button class="dialog-cancel" type="button" onclick="this.closest('details').removeAttribute('open')">Zrušit</button><button class="secondary" type="submit">Uložit změny</button></div>
								</form>
							</details>
						<% } %>
						</td>
					</tr>
				<% } %>
					</tbody>
				</table>
			</div>
		</section>
	</main>
	<script src="assets/base.js?v=20260928-160511"></script>
</body>
</html>