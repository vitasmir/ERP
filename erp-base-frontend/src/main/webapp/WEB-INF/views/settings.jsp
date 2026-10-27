<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.settings.SettingsView" %>
<%@ include file="fragments/base-header.jspf" %>
<% SettingsView settings = (SettingsView) request.getAttribute("settings"); String error = (String) request.getAttribute("error"); %>
<section class="section-head"><div><span class="eyebrow">BASE / CONFIGURATION</span><h2>Nastavení</h2><p>Základní konfigurace instance ERP.</p></div></section>
<% if (error != null) { %><p class="settings-message error"><%= error %></p><% } %>
<% if (settings != null) { %>
<form method="post" class="panel settings-panel">
	<label>Název společnosti<input name="companyName" value="<%= settings.companyName() %>" required></label>
	<label>E-mail společnosti<input type="email" name="companyEmail" value="<%= settings.companyEmail() %>" required></label>
	<label>Výchozí měna<input name="currencyCode" value="<%= settings.currencyCode() %>" required></label>
	<label>Časové pásmo<input name="timezone" value="<%= settings.timezone() %>" required></label>
	<label>Začátek fiskálního roku<input type="number" name="fiscalYearStartMonth" min="1" max="12" value="<%= settings.fiscalYearStartMonth() %>" required></label>
	<label>Splatnost faktur ve dnech<input type="number" name="defaultPaymentTermsDays" min="0" value="<%= settings.defaultPaymentTermsDays() %>" required></label>
	<label>Poplatek za doručení (Kč)<input type="number" name="deliveryFee" min="0" step="0.01" value="<%= settings.deliveryFee() %>" required></label>
	<label>Globální marže (%)<input type="number" name="eshopMarginPercent" min="0" max="99.98" step="0.01" value="<%= settings.eshopMarginPercent() %>" required></label>
	<label>Zaokrouhlení ceny (Kč)<select name="eshopRoundingUnit"><option value="1" <%= settings.eshopRoundingUnit().compareTo(java.math.BigDecimal.ONE) == 0 ? "selected" : "" %>>1 Kč</option><option value="10" <%= settings.eshopRoundingUnit().compareTo(java.math.BigDecimal.TEN) == 0 ? "selected" : "" %>>10 Kč</option><option value="100" <%= settings.eshopRoundingUnit().compareTo(new java.math.BigDecimal("100")) == 0 ? "selected" : "" %>>100 Kč</option></select></label>
	<label>Výchozí DPH (%)<input type="number" name="eshopDefaultVatRate" min="0" max="100" step="0.01" value="<%= settings.eshopDefaultVatRate() %>" required></label>
	<button class="primary" type="submit">Uložit nastavení</button>
</form>
<% } %>
<%@ include file="fragments/base-footer.jspf" %>