<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="com.example.erp.frontend.crm.CrmOverviewView" %>
<%@ page import="com.example.erp.frontend.crm.CrmOverviewView.LeadView" %>
<!doctype html>
<html lang="cs">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ERP | CRM pipeline</title>
  <link rel="stylesheet" href="assets/base.css?v=20260928-154028">
  <link rel="stylesheet" href="assets/crm.css?v=20260928-154028">
</head>
<body>
  <% CrmOverviewView overview = (CrmOverviewView) request.getAttribute("overview"); String error = (String) request.getAttribute("error"); String message = request.getParameter("message"); %>
  <main class="crm-page">
    <header class="crm-header">
      <a href="apps" class="back-link">← Aplikace</a>
      <div><span class="eyebrow">OBCHOD / CRM</span><h1>CRM pipeline</h1><p>Posouvejte obchodní příležitosti od prvního kontaktu až po uzavřený obchod.</p></div>
      <a href="#new-lead" class="primary">+ Nová příležitost</a>
    </header>
    <% if (error != null) { %><p class="crm-message error"><%= error %></p><% } if (message != null) { %><p class="crm-message"><%= message %></p><% } %>
    <section class="crm-metrics">
      <article><span>Hodnota pipeline</span><strong><%= overview == null ? "-" : overview.pipeline() %> Kč</strong><small>všechny příležitosti</small></article>
      <article><span>Vážený forecast</span><strong><%= overview == null ? "-" : overview.forecast() %> Kč</strong><small>podle pravděpodobnosti</small></article>
      <article><span>Otevřené příležitosti</span><strong><%= overview == null ? "-" : overview.openLeadCount() %></strong><small>v aktivní pipeline</small></article>
    </section>
    <section id="new-lead" class="new-lead-panel">
      <div class="section-head"><div><span class="eyebrow">NOVÝ OBCHOD</span><h2>Nová příležitost</h2></div><span class="pipeline-hint">Začne ve fázi Nové</span></div>
      <form method="post" class="new-lead-form">
        <label>Název příležitosti<input name="name" required maxlength="200" placeholder="Např. Rozšíření licence"></label>
        <label>Zákazník<input name="customerName" required maxlength="200" placeholder="Název zákazníka"></label>
        <label>Hodnota (Kč)<input name="expectedRevenue" required type="number" min="0" step="0.01" placeholder="0.00"></label>
        <label>Pravděpodobnost<input name="probability" required type="number" min="0" max="100" value="20"></label>
        <label>Očekávané uzavření<input name="expectedCloseDate" required type="date"></label>
        <button class="primary" type="submit">Vytvořit příležitost</button>
      </form>
    </section>
    <section id="pipeline" class="pipeline-section">
      <div class="section-head"><div><span class="eyebrow">OBCHODNÍ PIPELINE</span><h2>Kanban příležitostí</h2></div><span id="pipeline-status" class="pipeline-hint">Přetáhněte kartu do jiné fáze</span></div>
      <div class="pipeline-board">
        <% String[] stages = {"NEW", "QUALIFIED", "PROPOSAL", "WON"}; String[] labels = {"Nové", "Kvalifikované", "Nabídka", "Vyhráno"}; for (int i = 0; i < stages.length; i++) { %>
        <section class="pipeline-column" data-stage="<%= stages[i] %>">
          <header><div><span class="stage-dot stage-<%= stages[i].toLowerCase() %>"></span><h3><%= labels[i] %></h3></div><span class="column-count">0</span></header>
          <div class="drop-zone" data-stage="<%= stages[i] %>">
            <% if (overview != null) for (LeadView lead : overview.leads()) if (stages[i].equals(lead.stage())) { %>
            <article class="kanban-card" draggable="true" data-lead-id="<%= lead.id() %>" data-stage="<%= lead.stage() %>">
              <div class="card-top"><span class="stage stage-<%= lead.stage().toLowerCase() %>"><%= labels[i] %></span><span class="probability"><%= lead.probability() %> %</span></div>
              <h4><%= lead.name() %></h4><p><%= lead.customerName() %></p>
              <footer><strong><%= lead.expectedRevenue() %> Kč</strong><span>do <%= lead.expectedCloseDate() %></span></footer>
            </article>
            <% } %>
          </div>
        </section>
        <% } %>
      </div>
    </section>
  </main>
  <script>
    const cards = document.querySelectorAll('.kanban-card');
    const zones = document.querySelectorAll('.drop-zone');
    const status = document.getElementById('pipeline-status');
    let dragged;
    const updateCounts = () => document.querySelectorAll('.pipeline-column').forEach(column => column.querySelector('.column-count').textContent = column.querySelectorAll('.kanban-card').length);
    cards.forEach(card => card.addEventListener('dragstart', () => { dragged = card; card.classList.add('dragging'); }));
    cards.forEach(card => card.addEventListener('dragend', () => card.classList.remove('dragging')));
    zones.forEach(zone => {
      zone.addEventListener('dragover', event => { event.preventDefault(); zone.classList.add('drag-target'); });
      zone.addEventListener('dragleave', () => zone.classList.remove('drag-target'));
      zone.addEventListener('drop', async event => {
        event.preventDefault(); zone.classList.remove('drag-target');
        if (!dragged || dragged.dataset.stage === zone.dataset.stage) return;
        const previousZone = dragged.parentElement; const previousStage = dragged.dataset.stage;
        zone.appendChild(dragged); dragged.dataset.stage = zone.dataset.stage;
        dragged.querySelector('.stage').textContent = zone.dataset.stage === 'NEW' ? 'Nové' : zone.dataset.stage === 'QUALIFIED' ? 'Kvalifikované' : zone.dataset.stage === 'PROPOSAL' ? 'Nabídka' : 'Vyhráno';
        updateCounts(); status.textContent = 'Ukládám změnu...';
        try {
          const response = await fetch('crm?id=' + encodeURIComponent(dragged.dataset.leadId) + '&stage=' + encodeURIComponent(zone.dataset.stage), { method: 'PUT' });
          if (!response.ok) throw new Error('update failed');
          status.textContent = 'Fáze byla uložena';
        } catch (error) { previousZone.appendChild(dragged); dragged.dataset.stage = previousStage; updateCounts(); status.textContent = 'Změnu se nepodařilo uložit'; }
      });
    });
    updateCounts();
  </script>
</body>
</html>
