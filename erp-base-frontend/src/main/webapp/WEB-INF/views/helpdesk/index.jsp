<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.helpdesk.HelpdeskOverviewView" %>
<%@ page import="com.example.erp.frontend.helpdesk.HelpdeskOverviewView.TicketView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | Helpdesk</title>
  <link rel="stylesheet" href="assets/base.css?v=20260928-160510">
  <link rel="stylesheet" href="assets/helpdesk.css?v=20261001-2">
</head>
<body>
  <% HelpdeskOverviewView overview = (HelpdeskOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); String actionError = request.getParameter("error"); %>
  <main class="helpdesk-page">
    <header class="helpdesk-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">PROVOZ / HELPDESK</span><h1>Helpdesk</h1><p>Řiďte servisní požadavky, SLA termíny a práci podpůrných týmů.</p></div>
      <a href="#tickets" class="primary">Otevřené požadavky</a>
    </header>
    <% if (error != null) { %><p class="helpdesk-message error"><%= error %></p><% } if (actionError != null) { %><p class="helpdesk-message error"><%= actionError %></p><% } if (message != null) { %><p class="helpdesk-message"><%= message %></p><% } %>
    <section class="helpdesk-metrics">
      <article><span>Otevřené požadavky</span><strong><%= overview == null ? "-" : overview.openTicketCount() %></strong><small>čekají na vyřešení</small></article>
      <article><span>Vysoká priorita</span><strong><%= overview == null ? "-" : overview.highPriorityCount() %></strong><small>vyžaduje okamžitou pozornost</small></article>
      <article><span>SLA do 8 hodin</span><strong><%= overview == null ? "-" : overview.dueSoonCount() %></strong><small>blíží se termín řešení</small></article>
    </section>
    <section id="tickets" class="helpdesk-section">
      <div class="section-head"><div><span class="eyebrow">SERVISNÍ POŽADAVKY</span><h2>Fronta podpory</h2></div><span class="ticket-count"><%= overview == null ? 0 : overview.tickets().size() %> požadavky</span></div>
      <div class="ticket-table-wrap">
        <table class="ticket-table">
          <thead><tr><th>POŽADAVEK</th><th>TÝM</th><th>SLA TERMÍN</th><th>STAV</th><th><span class="sr-only">AKCE</span></th></tr></thead>
          <tbody>
            <% if (overview != null) for (TicketView ticket : overview.tickets()) { %>
            <tr>
              <td data-label="Požadavek"><span class="priority priority-<%= ticket.priority().toLowerCase() %>"><%= ticket.priority().equals("HIGH") ? "VYSOKÁ" : ticket.priority().equals("MEDIUM") ? "STŘEDNÍ" : "NÍZKÁ" %></span><span class="status-chip status-<%= ticket.status().toLowerCase() %>"><%= ticket.status().equals("IN_PROGRESS") ? "ŘEŠÍ SE" : ticket.status().equals("RESOLVED") ? "VYŘEŠENO" : "OTEVŘENO" %></span><strong><%= ticket.subject() %></strong><span><%= ticket.ticketNumber() %> · <%= ticket.requesterName() %></span></td>
              <td data-label="Tým"><%= ticket.assignedTeam() %></td>
              <td data-label="SLA termín"><%= ticket.dueAt().replace('T', ' ') %></td>
              <td data-label="Stav"><%= ticket.status().equals("RESOLVED") ? "Požadavek uzavřen" : "Čeká na vyřešení" %></td>
              <td class="ticket-actions"><% if (!"RESOLVED".equals(ticket.status())) { %><form method="post"><input type="hidden" name="id" value="<%= ticket.id() %>"><button class="secondary" type="submit">Označit jako vyřešené</button></form><% } %></td>
            </tr>
            <% } %>
          </tbody>
        </table>
      </div>
    </section>
  </main>
</body>
</html>