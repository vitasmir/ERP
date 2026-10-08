import type { Row } from "../../lib/context";
import { InlinePageScript, list, record, text, value, type ViewProps } from "../shared";

const messages = (data: Row, prefix: string) => (
  <>
    {value(data, "error") && <p className={`${prefix}-message error`} role="alert">{value(data, "error")}</p>}
    {value(data, "actionError") && <p className={`${prefix}-message error`} role="alert">{value(data, "actionError")}</p>}
    {value(data, "message") && <p className={`${prefix}-message`} role="status">{value(data, "message")}</p>}
  </>
);

function amount(input: unknown, decimals = 0): string {
  const number = Number(input);
  return input == null || input === "" || !Number.isFinite(number) ? (input == null || input === "" ? "-" : text(input))
    : number.toFixed(decimals);
}

function grouped(input: unknown): string {
  const number = Number(input);
  return input == null || input === "" || !Number.isFinite(number)
    ? (input == null || input === "" ? "-" : text(input))
    : new Intl.NumberFormat("cs-CZ", { maximumFractionDigits: 0, useGrouping: true }).format(number).replace(/\u00a0/g, " ");
}

function metric(label: string, valueText: string, detail: string) {
  return <article><span>{label}</span><strong>{valueText}</strong><small>{detail}</small></article>;
}

const CRM_SCRIPT = `
(() => {
  const root = document.querySelector('.crm-page');
  if (!root) return;
  const labels = {NEW:'Nové', QUALIFIED:'Kvalifikované', PROPOSAL:'Nabídka', WON:'Vyhráno'};
  let dragged = null;
  let saving = false;
  const status = root.querySelector('#pipeline-status');
  const updateCounts = () => root.querySelectorAll('.pipeline-column').forEach(column => {
    const count = column.querySelector('.column-count');
    if (count) count.textContent = String(column.querySelectorAll('.kanban-card').length);
  });
  const setStage = (card, stage) => {
    card.dataset.stage = stage;
    const badge = card.querySelector('.stage');
    if (badge) { badge.textContent = labels[stage] || stage; badge.className = 'stage stage-' + stage.toLowerCase(); }
  };
  root.addEventListener('dragstart', event => {
    const card = event.target.closest('.kanban-card');
    if (!card) return;
    if (saving) { event.preventDefault(); return; }
    dragged = card;
    card.classList.add('dragging');
  });
  root.addEventListener('dragend', event => {
    const card = event.target.closest('.kanban-card');
    if (card) card.classList.remove('dragging');
  });
  root.addEventListener('dragover', event => {
    const zone = event.target.closest('.drop-zone');
    if (zone) { event.preventDefault(); zone.classList.add('drag-target'); }
  });
  root.addEventListener('dragleave', event => {
    const zone = event.target.closest('.drop-zone');
    if (zone && !zone.contains(event.relatedTarget)) zone.classList.remove('drag-target');
  });
  root.addEventListener('drop', async event => {
    const zone = event.target.closest('.drop-zone');
    if (!zone) return;
    event.preventDefault();
    zone.classList.remove('drag-target');
    if (saving || !dragged || dragged.dataset.stage === zone.dataset.stage) return;
    const card = dragged;
    const previousZone = card.parentElement;
    const previousStage = card.dataset.stage;
    if (!previousZone || !status) return;
    saving = true;
    zone.appendChild(card);
    setStage(card, zone.dataset.stage);
    updateCounts();
    status.textContent = 'Ukládám změnu...';
    try {
      const response = await fetch('/crm?id=' + encodeURIComponent(card.dataset.leadId) + '&stage=' + encodeURIComponent(zone.dataset.stage), {method:'PUT'});
      if (!response.ok || response.redirected) throw new Error('save failed');
      status.textContent = 'Fáze byla uložena';
    } catch (_) {
      previousZone.appendChild(card);
      setStage(card, previousStage);
      updateCounts();
      status.textContent = 'Změnu se nepodařilo uložit';
    } finally { saving = false; dragged = null; }
  });
  updateCounts();
})();`;

const ACCOUNTING_SCRIPT = `
(() => {
  document.addEventListener('click', event => {
    const cell = event.target.closest('.invoice-items-cell');
    if (!cell || event.target.closest('summary')) return;
    const details = cell.querySelector('.invoice-items');
    if (details) details.open = !details.open;
  });
})();`;

export function AccountingPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const invoices = list(overview.invoices);
  return (
    <div className="accounting-page">
      <header className="accounting-header">
        <a href="/apps" className="back-link">← Aplikace</a>
        <div><span className="eyebrow">FINANCE / ÚČETNICTVÍ</span><h1>Účetnictví</h1><p>Faktury, pohledávky a peněžní tok na jednom místě.</p></div>
        <a href="#invoices" className="primary">Přehled faktur</a>
      </header>
      {messages(data, "accounting")}
      <section className="accounting-metrics">
        {metric("Pohledávky", `${value(overview, "receivables", "-")} Kč`, "neuhrazené faktury")}
        {metric("Po splatnosti", `${value(overview, "overdue", "-")} Kč`, "vyžaduje kontrolu")}
        {metric("Otevřené faktury", value(overview, "openInvoiceCount", "-"), "čekají na úhradu")}
      </section>
      <section id="invoices" className="invoice-section">
        <div className="section-head"><div><span className="eyebrow">VYSTAVENÉ FAKTURY</span><h2>Přehled pohledávek</h2></div><span className="invoice-count">{invoices.length} faktur</span></div>
        <div className="invoice-table-wrap"><table className="invoice-table">
          <thead><tr><th>Stav</th><th>Faktura</th><th>Položky</th><th>Odběratel</th><th>Vystaveno</th><th>Splatnost</th><th>Celkem</th><th>Úhrada</th><th>Akce</th></tr></thead>
          <tbody>{invoices.map((invoice, index) => {
            const lines = list(invoice.lines);
            const status = value(invoice, "status");
            return <tr className="invoice-row" key={value(invoice, "id", String(index))}>
              <td><span className={`status-chip status-${status.toLowerCase()}`}>{status}</span></td>
              <td><strong>{value(invoice, "invoiceNumber")}</strong></td>
              <td className="invoice-items-cell"><details className="invoice-items">
                <summary><span>{lines.length} položek</span></summary>
                <div className="invoice-items-panel">{lines.map((entry, lineIndex) => (
                  <div className="invoice-item" key={`${value(entry, "description")}-${lineIndex}`}>
                    {value(entry, "imageUrl") ? <img src={value(entry, "imageUrl")} alt={value(entry, "description")} /> : <span className="invoice-item-placeholder">FM</span>}
                    <div><strong>{value(entry, "description")}</strong><small>{value(entry, "quantity")} ks · {value(entry, "unitPrice")} Kč / kus</small></div>
                  </div>
                ))}</div>
              </details></td>
              <td><span className="invoice-partner">{value(invoice, "partnerName")}</span></td>
              <td>{value(invoice, "issueDate")}</td><td>{value(invoice, "dueDate")}</td>
              <td><strong>{value(invoice, "totalAmount")} Kč</strong></td>
              <td className="invoice-payment-cell">{status === "PAID"
                ? <span className="paid-label">Uhrazeno {value(invoice, "paidAmount")} Kč</span>
                : <span className="unpaid-label">Neuhrazeno</span>}</td>
              <td className="invoice-action-cell">
                {status !== "PAID" && <form method="post" action="/accounting"><input type="hidden" name="id" value={value(invoice, "id")} /><button className="secondary" type="submit">Označit jako uhrazenou</button></form>}
                <a className="invoice-pdf-link" href={`/accounting?pdf=${encodeURIComponent(value(invoice, "id"))}`}>Stáhnout PDF</a>
                {status === "DRAFT" && <details className="invoice-edit"><summary>Upravit</summary>
                  <form method="post" action="/accounting">
                    <h3>Upravit fakturu</h3>
                    <input type="hidden" name="action" value="update" /><input type="hidden" name="id" value={value(invoice, "id")} /><input type="hidden" name="version" value={value(invoice, "version")} />
                    <label>Číslo faktury<input name="invoiceNumber" defaultValue={value(invoice, "invoiceNumber")} required maxLength={40} /></label>
                    <label>Odběratel<input name="partnerName" defaultValue={value(invoice, "partnerName")} required maxLength={160} /></label>
                    <label>Vystaveno<input type="date" name="issueDate" defaultValue={value(invoice, "issueDate")} required /></label>
                    <label>Splatnost<input type="date" name="dueDate" defaultValue={value(invoice, "dueDate")} required /></label>
                    <label>Celkem<input type="number" name="totalAmount" defaultValue={value(invoice, "totalAmount")} min="0.01" step="0.01" required /></label>
                    <div className="invoice-edit-actions"><button className="dialog-cancel" type="button" data-close-details>Zrušit</button><button className="secondary" type="submit">Uložit změny</button></div>
                  </form>
                </details>}
              </td>
            </tr>;
          })}</tbody>
        </table></div>
      </section>
      <InlinePageScript source={ACCOUNTING_SCRIPT} />
      <InlinePageScript source={`document.addEventListener('click', event => { const button = event.target.closest('[data-close-details]'); if (button) button.closest('details').removeAttribute('open'); });`} />
    </div>
  );
}

const crmStages = [
  ["NEW", "Nové"], ["QUALIFIED", "Kvalifikované"], ["PROPOSAL", "Nabídka"], ["WON", "Vyhráno"],
] as const;

export function CrmPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const leads = list(overview.leads);
  return (
    <div className="crm-page">
      <header className="crm-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">OBCHOD / CRM</span><h1>CRM pipeline</h1><p>Posouvejte obchodní příležitosti od prvního kontaktu až po uzavřený obchod.</p></div><a href="#new-lead" className="primary">+ Nová příležitost</a></header>
      {messages(data, "crm")}
      <section className="crm-metrics">
        {metric("Hodnota pipeline", `${value(overview, "pipeline", "-")} Kč`, "všechny příležitosti")}
        {metric("Vážený forecast", `${value(overview, "forecast", "-")} Kč`, "podle pravděpodobnosti")}
        {metric("Otevřené příležitosti", value(overview, "openLeadCount", "-"), "v aktivní pipeline")}
      </section>
      <section id="new-lead" className="new-lead-panel">
        <div className="section-head"><div><span className="eyebrow">NOVÝ OBCHOD</span><h2>Nová příležitost</h2></div><span className="pipeline-hint">Začne ve fázi Nové</span></div>
        <form method="post" action="/crm" className="new-lead-form">
          <label>Název příležitosti<input name="name" required maxLength={200} placeholder="Např. Rozšíření licence" /></label>
          <label>Zákazník<input name="customerName" required maxLength={200} placeholder="Název zákazníka" /></label>
          <label>Hodnota (Kč)<input name="expectedRevenue" required type="number" min="0" step="0.01" placeholder="0.00" /></label>
          <label>Pravděpodobnost<input name="probability" required type="number" min="0" max="100" defaultValue={20} /></label>
          <label>Očekávané uzavření<input name="expectedCloseDate" required type="date" /></label>
          <button className="primary" type="submit">Vytvořit příležitost</button>
        </form>
      </section>
      <section id="pipeline" className="pipeline-section">
        <div className="section-head"><div><span className="eyebrow">OBCHODNÍ PIPELINE</span><h2>Kanban příležitostí</h2></div><span id="pipeline-status" className="pipeline-hint" role="status">Přetáhněte kartu do jiné fáze</span></div>
        <div className="pipeline-board">{crmStages.map(([stage, label]) => (
          <section className="pipeline-column" data-stage={stage} key={stage}>
            <header><div><span className={`stage-dot stage-${stage.toLowerCase()}`} /><h3>{label}</h3></div><span className="column-count">0</span></header>
            <div className="drop-zone" data-stage={stage}>{leads.filter((lead) => value(lead, "stage") === stage).map((lead, index) => (
              <article className="kanban-card" draggable="true" data-lead-id={value(lead, "id")} data-stage={stage} key={value(lead, "id", `${stage}-${index}`)}>
                <div className="card-top"><span className={`stage stage-${stage.toLowerCase()}`}>{label}</span><span className="probability">{value(lead, "probability")} %</span></div>
                <h4>{value(lead, "name")}</h4><p>{value(lead, "customerName")}</p>
                <footer><strong>{value(lead, "expectedRevenue")} Kč</strong><span>do {value(lead, "expectedCloseDate")}</span></footer>
              </article>
            ))}</div>
          </section>
        ))}</div>
      </section>
      <InlinePageScript source={CRM_SCRIPT} />
    </div>
  );
}

export function DocumentsPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const documents = list(overview.documents);
  return (
    <div className="documents-page">
      <header className="documents-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">BASE / DOKUMENTY</span><h1>Dokumenty</h1><p>Spravujte interní dokumenty, jejich vlastníky a schvalování v jednom přehledu.</p></div><a href="#documents" className="primary">Přehled dokumentů</a></header>
      {messages(data, "documents")}
      <section className="documents-metrics">
        {metric("Čeká na schválení", value(overview, "pendingApprovalCount", "-"), "vyžaduje rozhodnutí")}
        {metric("Schválené dokumenty", value(overview, "approvedCount", "-"), "v aktuálním přehledu")}
        {metric("Kategorie", value(overview, "categoryCount", "-"), "typy uložených dokumentů")}
      </section>
      <section id="documents" className="documents-section">
        <div className="section-head"><div><span className="eyebrow">EVIDENCE DOKUMENTŮ</span><h2>Soubory a schvalovací procesy</h2></div><span className="document-count">{documents.length} dokumenty</span></div>
        <div className="document-list">{documents.map((document, index) => {
          const status = value(document, "status");
          const statusText = status === "PENDING_APPROVAL" ? "KE SCHVÁLENÍ" : status === "APPROVED" ? "SCHVÁLENO" : "KONCEPT";
          return <article className="document-card" key={value(document, "id", String(index))}>
            <div className="document-main"><span className="document-mark">▣</span><div><span className={`status-chip status-${status.toLowerCase()}`}>{statusText}</span><h3>{value(document, "title")}</h3><p>{value(document, "category")} · {value(document, "referenceCode")}</p></div></div>
            <dl><div><dt>Vlastník</dt><dd>{value(document, "ownerName")}</dd></div><div><dt>Aktualizováno</dt><dd>{value(document, "updatedOn")}</dd></div></dl>
            {status === "PENDING_APPROVAL" ? <form method="post" action="/documents"><input type="hidden" name="id" value={value(document, "id")} /><button className="secondary" type="submit">Schválit dokument</button></form> : <span className="approved-label">Proces je uzavřen</span>}
          </article>;
        })}</div>
      </section>
    </div>
  );
}

function formatDateTime(input: string): string {
  const date = new Date(input);
  if (!Number.isFinite(date.getTime())) return input;
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${pad(date.getUTCDate())}.${pad(date.getUTCMonth() + 1)}.${date.getUTCFullYear()} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

export function HelpdeskPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const tickets = list(overview.tickets);
  return (
    <div className="helpdesk-page">
      <header className="helpdesk-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PROVOZ / HELPDESK</span><h1>Helpdesk</h1><p>Řiďte servisní požadavky, SLA termíny a práci podpůrných týmů.</p></div><a href="#tickets" className="primary">Otevřené požadavky</a></header>
      {messages(data, "helpdesk")}
      <section className="helpdesk-metrics">
        {metric("Otevřené požadavky", value(overview, "openTicketCount", "-"), "čekají na vyřešení")}
        {metric("Vysoká priorita", value(overview, "highPriorityCount", "-"), "vyžaduje okamžitou pozornost")}
        {metric("SLA do 8 hodin", value(overview, "dueSoonCount", "-"), "blíží se termín řešení")}
      </section>
      <section id="tickets" className="helpdesk-section">
        <div className="section-head"><div><span className="eyebrow">SERVISNÍ POŽADAVKY</span><h2>Fronta podpory</h2></div><span className="ticket-count">{tickets.length} požadavky</span></div>
        <div className="ticket-table-wrap"><table className="ticket-table">
          <thead><tr><th>POŽADAVEK</th><th>TÝM</th><th>SLA TERMÍN</th><th>STAV</th><th><span className="sr-only">AKCE</span></th></tr></thead>
          <tbody>{tickets.map((ticket, index) => {
            const status = value(ticket, "status");
            const priority = value(ticket, "priority");
            return <tr key={value(ticket, "id", String(index))}>
              <td data-label="Požadavek"><span className={`priority priority-${priority.toLowerCase()}`}>{priority === "HIGH" ? "VYSOKÁ" : priority === "MEDIUM" ? "STŘEDNÍ" : "NÍZKÁ"}</span><span className={`status-chip status-${status.toLowerCase()}`}>{status === "IN_PROGRESS" ? "ŘEŠÍ SE" : status === "RESOLVED" ? "VYŘEŠENO" : "OTEVŘENO"}</span><strong>{value(ticket, "subject")}</strong><span>{value(ticket, "ticketNumber")} · {value(ticket, "requesterName")}</span></td>
              <td data-label="Tým">{value(ticket, "assignedTeam")}</td><td data-label="SLA termín">{formatDateTime(value(ticket, "dueAt"))}</td>
              <td data-label="Stav">{status === "RESOLVED" ? "Požadavek uzavřen" : "Čeká na vyřešení"}</td>
              <td className="ticket-actions">{status !== "RESOLVED" && <form method="post" action="/helpdesk?v=20261001-2"><input type="hidden" name="id" value={value(ticket, "id")} /><button className="secondary" type="submit">Označit jako vyřešené</button></form>}</td>
            </tr>;
          })}</tbody>
        </table></div>
      </section>
    </div>
  );
}

export function ProjectsPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const projects = list(overview.projects);
  return (
    <div className="projects-page">
      <header className="projects-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PROVOZ / PROJEKTY</span><h1>Projekty</h1><p>Sledujte odpovědnosti, termíny a postup klíčových iniciativ napříč organizací.</p></div><a href="#portfolio" className="primary">Přehled projektů</a></header>
      {messages(data, "projects")}
      <section className="projects-metrics">
        {metric("Aktivní projekty", value(overview, "activeProjectCount", "-"), "plánované a rozpracované")}
        {metric("Probíhá", value(overview, "inProgressCount", "-"), "aktivně řešené iniciativy")}
        {metric("Termín do 14 dnů", value(overview, "dueSoonCount", "-"), "vyžaduje pozornost týmu")}
      </section>
      <section id="portfolio" className="projects-section">
        <div className="section-head"><div><span className="eyebrow">PROJEKTOVÉ PORTFOLIO</span><h2>Plán a postup práce</h2></div><span className="project-count">{projects.length} projekty</span></div>
        <div className="project-list">{projects.map((project, index) => {
          const status = value(project, "status");
          const progress = value(project, "progress");
          return <article className="project-card" key={value(project, "id", String(index))}>
            <div className="project-main"><span className={`status-chip status-${status.toLowerCase()}`}>{status === "IN_PROGRESS" ? "PROBÍHÁ" : status === "PLANNED" ? "PLÁNOVÁNO" : "DOKONČENO"}</span><h3>{value(project, "name")}</h3><p>{value(project, "department")} · vlastník: {value(project, "ownerName")}</p></div>
            <div className="progress-group"><div className="progress-meta"><span>Postup</span><b>{progress} %</b></div><div className="progress-track"><span style={{ width: `${progress}%` }} /></div><small>Termín dokončení: {value(project, "dueDate")}</small></div>
            {status !== "COMPLETED" ? <form method="post" action="/projects"><input type="hidden" name="id" value={value(project, "id")} /><button className="secondary" type="submit">Dokončit projekt</button></form> : <span className="completed-label">Projekt uzavřen</span>}
          </article>;
        })}</div>
      </section>
    </div>
  );
}

export function MarketingPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const campaigns = list(overview.campaigns);
  return (
    <div className="marketing-page">
      <header className="marketing-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">OBCHOD / MARKETING</span><h1>Marketing</h1><p>Plánujte komunikaci, sledujte čerpání rozpočtu a vyhodnocujte přínos kampaní.</p></div><a href="#campaigns" className="primary">Přehled kampaní</a></header>
      {messages(data, "marketing")}
      <section className="marketing-metrics">
        {metric("Aktivní kampaně", value(overview, "runningCampaignCount", "-"), "aktuálně doručované komunikace")}
        {metric("Plánované kampaně", value(overview, "plannedCampaignCount", "-"), "připravené ke spuštění")}
        {metric("Získané leady", value(overview, "totalLeadCount", "-"), `vyčerpáno ${value(overview, "totalSpent", "-")} Kč`)}
      </section>
      <section className="workflow-form"><h2>Nová kampaň</h2><form method="post" action="/marketing">
        <input type="hidden" name="action" value="create" /><input name="name" placeholder="Název kampaně" required />
        <input name="audience" placeholder="Segment" required /><select name="channel" defaultValue="EMAIL"><option value="EMAIL">E-mail</option><option value="SOCIAL">Sociální sítě</option><option value="EVENT">Událost</option></select>
        <input name="ownerName" placeholder="Vlastník" required /><input type="number" step="0.01" name="budget" placeholder="Rozpočet Kč" required />
        <input type="date" name="plannedStartDate" required /><button className="primary" type="submit">Založit kampaň</button>
      </form></section>
      <section id="campaigns" className="marketing-section">
        <div className="section-head"><div><span className="eyebrow">KAMPANĚ A SEGMENTY</span><h2>Marketingový plán</h2></div><span className="campaign-count">{campaigns.length} kampaně</span></div>
        <div className="campaign-list">{campaigns.map((campaign, index) => {
          const status = value(campaign, "status");
          const channel = value(campaign, "channel");
          return <article className="campaign-card" key={value(campaign, "id", String(index))}>
            <div className="campaign-main"><span className="channel">{channel === "EMAIL" ? "E-MAIL" : channel === "SOCIAL" ? "SOCIÁLNÍ SÍTĚ" : "UDÁLOST"}</span><span className={`status-chip status-${status.toLowerCase()}`}>{status === "RUNNING" ? "AKTIVNÍ" : status === "PLANNED" ? "PLÁNOVÁNO" : "DOKONČENO"}</span><h3>{value(campaign, "name")}</h3><p>{value(campaign, "audience")} · vlastník: {value(campaign, "ownerName")}</p></div>
            <dl><div><dt>Rozpočet</dt><dd>{value(campaign, "spent")} / {value(campaign, "budget")} Kč</dd></div><div><dt>Leady</dt><dd>{value(campaign, "leadCount")} · start: {value(campaign, "plannedStartDate")}</dd></div></dl>
            {status === "PLANNED" ? <form method="post" action="/marketing"><input type="hidden" name="id" value={value(campaign, "id")} /><button className="secondary" type="submit">Spustit kampaň</button></form> : status === "RUNNING" ? <span className="running-label">Kampaň běží</span> : <span className="completed-label">Kampaň uzavřena</span>}
          </article>;
        })}</div>
      </section>
    </div>
  );
}

const contentTypes = [
  ["CONTENT", "Obsah"], ["LANDING", "Úvod"], ["CATALOG", "Katalog"], ["CAMPAIGN", "Kampaň"],
] as const;

export function WebsitePage({ data }: ViewProps) {
  const overview = record(data.overview);
  const editingPage = data.editingPage == null ? null : record(data.editingPage);
  const pages = list(overview.pages);
  return (
    <div className="website-page">
      <header className="website-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">OBCHOD / WEB</span><h1>Web</h1><p>Spravujte obsah webu, produktový katalog a zákaznické formuláře propojené s ERP.</p></div><a href="#pages" className="primary">Obsah webu</a></header>
      {messages(data, "website")}
      <section className="website-metrics">
        {metric("Publikované stránky", value(overview, "publishedPageCount", "-"), "viditelné návštěvníkům")}
        {metric("Koncepty k publikaci", value(overview, "draftPageCount", "-"), "čekají na schválení")}
        {metric("Návštěvy za měsíc", overview.monthlyVisits == null ? "-" : grouped(overview.monthlyVisits), `${value(overview, "formPageCount", "-")} stránek s formulářem`)}
      </section>
      <section className="workflow-form" id="new-page">
        <h2>{editingPage ? "Upravit stránku" : "Nová stránka"}</h2>
        <form method="post" action="/website">
          <input type="hidden" name="action" value={editingPage ? "edit" : "create"} />
          {editingPage && <input type="hidden" name="id" value={value(editingPage, "id")} />}
          <input name="title" placeholder="Název stránky" defaultValue={value(editingPage ?? {}, "title")} required />
          <input name="slug" placeholder="/url-slug" defaultValue={value(editingPage ?? {}, "slug")} required />
          <select name="contentType" defaultValue={value(editingPage ?? {}, "contentType", "CONTENT")}>{contentTypes.map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
          <input name="ownerName" placeholder="Správce" defaultValue={value(editingPage ?? {}, "ownerName")} required />
          <textarea name="content" placeholder="Obsah stránky" defaultValue={value(editingPage ?? {}, "content")} />
          <button className="primary" type="submit">{editingPage ? "Uložit změny" : "Uložit koncept"}</button>
          {editingPage && <a className="secondary" href="/website#new-page">Zrušit úpravu</a>}
        </form>
      </section>
      <section id="pages" className="website-section">
        <div className="section-head"><div><span className="eyebrow">OBSAH A KATALOG</span><h2>Stránky webu</h2></div><span className="page-count">{pages.length} položky</span></div>
        <div className="page-list">{pages.map((item, index) => {
          const status = value(item, "status");
          const contentType = value(item, "contentType");
          const slug = value(item, "slug");
          const safePath = slug.startsWith("/") && !slug.startsWith("//");
          return <article className="web-card" key={value(item, "id", String(index))}>
            <div className="web-main"><span className="content-type">{contentType === "LANDING" ? "ÚVOD" : contentType === "CATALOG" ? "KATALOG" : contentType === "CAMPAIGN" ? "KAMPAŇ" : "OBSAH"}</span><span className={`status-chip status-${status.toLowerCase()}`}>{status === "PUBLISHED" ? "PUBLIKOVÁNO" : "KONCEPT"}</span><h3>{value(item, "title")}</h3><p>{status === "PUBLISHED" && safePath ? <a href={slug}>{slug}</a> : slug} · správce: {value(item, "ownerName")}</p></div>
            <dl><div><dt>Návštěvy / měsíc</dt><dd>{grouped(item.monthlyVisits)}</dd></div><div><dt>Formulář</dt><dd>{item.hasContactForm ? "Aktivní" : "Bez formuláře"}</dd></div></dl>
            <div className="web-actions">
              {status === "DRAFT" ? <form method="post" action="/website"><input type="hidden" name="action" value="publish" /><input type="hidden" name="id" value={value(item, "id")} /><button className="secondary" type="submit">Publikovat</button></form> : <span className="published-label">Stránka je online</span>}
              <a className="secondary" href={`/website?edit=${encodeURIComponent(value(item, "id"))}#new-page`}>Upravit</a>
              <form method="post" action="/website" data-confirm-delete data-confirm-message="Opravdu smazat stránku?"><input type="hidden" name="action" value="delete" /><input type="hidden" name="id" value={value(item, "id")} /><button className="danger-button" type="submit">Smazat</button></form>
            </div>
          </article>;
        })}</div>
      </section>
      <InlinePageScript source={`document.addEventListener('submit', event => { const form = event.target.closest('form[data-confirm-delete]'); if (form && !window.confirm(form.dataset.confirmMessage || 'Opravdu pokračovat?')) event.preventDefault(); });`} />
    </div>
  );
}

export function PublicWebsitePage({ data }: ViewProps) {
  const page = data.page == null ? null : record(data.page);
  return (
    <div className="website-page">
      {value(data, "error") && <p className="website-message error" role="alert">{value(data, "error")}</p>}
      {page && <>
        <header className="website-header"><div><span className="eyebrow">WEB</span><h1>{value(page, "title")}</h1></div></header>
        <section className="website-section" dangerouslySetInnerHTML={{ __html: value(page, "content") }} />
      </>}
    </div>
  );
}

export function SalesPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const orders = list(overview.orders);
  return (
    <main className="sales-page">
      <header className="sales-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">OBCHOD / PRODEJ</span><h1>Prodej</h1><p>Převádějte nabídky do objednávek a mějte pod kontrolou dodávky pro zákazníky.</p></div></header>
      {[value(data, "error"), value(data, "actionError")].filter(Boolean).map((error, index) => <p className="sales-message error" role="alert" key={index}>{error}</p>)}
      {value(data, "message") && <p className="sales-message" role="status">{value(data, "message")}</p>}
      <section className="sales-metrics">
        {metric("Hodnota nabídek", `${overview.quoteValue == null ? "-" : amount(overview.quoteValue, 2)} Kč`, "čeká na potvrzení")}
        {metric("Potvrzené objednávky", `${overview.confirmedValue == null ? "-" : amount(overview.confirmedValue, 2)} Kč`, "připraveno k dodání")}
        {metric("Otevřené nabídky", value(overview, "quoteCount", "-"), "ke schválení zákazníkem")}
      </section>
      <section id="orders" className="sales-section">
        <div className="section-head"><div><span className="eyebrow">NABÍDKY A OBJEDNÁVKY</span><h2>Obchodní dokumenty</h2></div><span className="order-count">{orders.length} dokumenty</span></div>
        <div className="sales-table-wrap"><table className="sales-table">
          <thead><tr><th>Dokument</th><th>Zákazník</th><th>Vystaveno</th><th>Dodání</th><th className="amount-column">Celkem</th><th>Stav / akce</th></tr></thead>
          <tbody>{orders.map((order, index) => {
            const quote = value(order, "status") === "QUOTE";
            return <tr key={value(order, "id", String(index))}>
              <td data-label="Dokument"><span className={`status-chip status-${value(order, "status").toLowerCase()}`}>{quote ? "NABÍDKA" : "OBJEDNÁVKA"}</span><strong>{value(order, "orderNumber")}</strong></td>
              <td data-label="Zákazník">{value(order, "customerName")}</td><td data-label="Vystaveno">{value(order, "orderDate")}</td><td data-label="Dodání">{value(order, "deliveryDate")}</td>
              <td data-label="Celkem" className="amount-column"><strong>{amount(order.totalAmount, 2)} Kč</strong></td>
              <td data-label="Stav / akce"><div className="sales-actions">
                {quote ? <form method="post"><input type="hidden" name="id" value={value(order, "id")} /><input type="hidden" name="action" value="confirm" /><button className="secondary" type="submit">Potvrdit objednávku</button></form> : <span className="confirmed-label">Potvrzeno k dodání</span>}
                <details className="sales-edit"><summary>Upravit</summary><form method="post">
                  <h3 className="sales-dialog-title">Upravit objednávku</h3><input type="hidden" name="action" value="update" /><input type="hidden" name="id" value={value(order, "id")} />
                  <label>Číslo dokumentu<input name="orderNumber" defaultValue={value(order, "orderNumber")} required maxLength={30} /></label>
                  <label>Zákazník<input name="customerName" defaultValue={value(order, "customerName")} required maxLength={200} /></label>
                  <label>Vystaveno<input type="date" name="orderDate" defaultValue={value(order, "orderDate")} required /></label>
                  <label>Dodání<input type="date" name="deliveryDate" defaultValue={value(order, "deliveryDate")} required /></label>
                  <label>Celkem<input type="number" name="totalAmount" defaultValue={value(order, "totalAmount")} min="0" step="0.01" required /></label>
                  <div className="sales-dialog-actions"><button className="dialog-cancel" type="button" data-close-details>Zrušit</button><button className="secondary" type="submit">Uložit změny</button></div>
                </form></details>
                <form method="post" data-confirm-delete data-confirm-message="Opravdu chcete tento dokument smazat?"><input type="hidden" name="action" value="delete" /><input type="hidden" name="id" value={value(order, "id")} /><button className="danger-button" type="submit">Smazat</button></form>
              </div></td>
            </tr>;
          })}</tbody>
        </table></div>
      </section>
      <InlinePageScript source={`document.addEventListener('click', event => { const button = event.target.closest('[data-close-details]'); if (button) button.closest('details').removeAttribute('open'); }); document.addEventListener('submit', event => { const form = event.target.closest('form[data-confirm-delete]'); if (form && !window.confirm(form.dataset.confirmMessage || 'Opravdu pokračovat?')) event.preventDefault(); });`} />
    </main>
  );
}

const MANUFACTURING_SCRIPT = `
(() => {
  document.addEventListener('click', event => {
    const button = event.target.closest('.edit-order');
    const dialog = document.getElementById('edit-order-dialog');
    if (button && dialog) {
      document.getElementById('edit-order-id').value = button.dataset.id || '';
      document.getElementById('edit-order-title').textContent = button.dataset.orderNumber || '';
      document.getElementById('edit-order-product').textContent = button.dataset.product || '';
      const quantity = document.getElementById('completed-quantity');
      quantity.value = button.dataset.completed || '';
      quantity.max = button.dataset.planned || '';
      document.getElementById('completed-quantity-help').textContent = 'Plánované množství: ' + (button.dataset.planned || '') + ' ks';
      dialog.showModal();
      return;
    }
    if (event.target.closest('.dialog-close, .dialog-cancel')) {
      const current = document.getElementById('edit-order-dialog');
      if (current) current.close();
    }
    if (event.target === dialog) dialog.close();
  });
})();`;

export function ManufacturingPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const orders = list(overview.orders);
  return (
    <main className="manufacturing-page">
      <header className="manufacturing-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PROVOZ / VÝROBA</span><h1>Výroba</h1><p>Plánujte výrobní příkazy, sledujte rozpracovanost a uzavírejte dokončenou produkci.</p></div><a href="#orders" className="primary">Plán výroby</a></header>
      {[value(data, "error"), value(data, "actionError")].filter(Boolean).map((error, index) => <p className="manufacturing-message error" role="alert" key={index}>{error}</p>)}
      {value(data, "message") && <p className="manufacturing-message" role="status">{value(data, "message")}</p>}
      <section className="manufacturing-metrics">
        {metric("Plánovaná výroba", `${value(overview, "plannedQuantity", "-")} ks`, "v neuzavřených příkazech")}
        {metric("Dokončená výroba", `${value(overview, "completedQuantity", "-")} ks`, "z aktuálního plánu")}
        {metric("Rozpracované příkazy", value(overview, "activeOrderCount", "-"), "na výrobních pracovištích")}
      </section>
      <section id="orders" className="manufacturing-section">
        <div className="section-head"><div><span className="eyebrow">VÝROBNÍ PŘÍKAZY</span><h2>Plán a průběh výroby</h2></div><span className="order-count">{orders.length} příkazy</span></div>
        <div className="manufacturing-table-wrap"><table className="manufacturing-table">
          <thead><tr><th>Výrobní příkaz</th><th>Průběh výroby</th><th>Stav / akce</th></tr></thead>
          <tbody>{orders.map((order, index) => {
            const planned = Number(value(order, "plannedQuantity", "0"));
            const completed = Number(value(order, "completedQuantity", "0"));
            const progress = planned === 0 ? 0 : Math.floor(completed * 100 / planned);
            const status = value(order, "status");
            return <tr key={value(order, "id", String(index))}>
              <td data-label="Výrobní příkaz"><span className={`status-chip status-${status.toLowerCase()}`}>{status === "IN_PROGRESS" ? "PROBÍHÁ" : status === "PLANNED" ? "PLÁNOVÁNO" : "DOKONČENO"}</span><strong>{value(order, "orderNumber")}</strong><span className="manufacturing-product">{value(order, "productName")} · {value(order, "workCenter")}</span></td>
              <td data-label="Průběh výroby"><div className="progress-group"><div className="progress-meta"><span>Hotovo <b>{value(order, "completedQuantity")} / {value(order, "plannedQuantity")} ks</b></span><span>{progress} %</span></div><div className="progress-track"><span style={{ width: `${progress}%` }} /></div><small>Plánované datum: {value(order, "plannedDate")}</small></div></td>
              <td data-label="Stav / akce"><button className="secondary edit-order" type="button" data-id={value(order, "id")} data-product={value(order, "productName")} data-order-number={value(order, "orderNumber")} data-completed={value(order, "completedQuantity")} data-planned={value(order, "plannedQuantity")}>Upravit</button>
                {status !== "COMPLETED" ? <form method="post"><input type="hidden" name="id" value={value(order, "id")} /><button className="secondary" type="submit">Dokončit příkaz</button></form> : <span className="completed-label">Výroba uzavřena</span>}
              </td>
            </tr>;
          })}</tbody>
        </table></div>
      </section>
      <dialog className="manufacturing-dialog" id="edit-order-dialog">
        <form method="post" id="edit-order-form">
          <input type="hidden" name="action" value="update" /><input type="hidden" name="id" id="edit-order-id" />
          <div className="dialog-head"><div><span className="eyebrow">ÚPRAVA VÝROBY</span><h2 id="edit-order-title">Výrobní příkaz</h2></div><button className="dialog-close" type="button" aria-label="Zavřít">×</button></div>
          <p className="dialog-product" id="edit-order-product" />
          <label htmlFor="completed-quantity">Vyrobeno kusů</label><input id="completed-quantity" name="completedQuantity" type="number" min="0" required />
          <small id="completed-quantity-help" />
          <div className="dialog-actions"><button className="secondary dialog-cancel" type="button">Zrušit</button><button className="primary" type="submit">Uložit změny</button></div>
        </form>
      </dialog>
      <InlinePageScript source={MANUFACTURING_SCRIPT} />
    </main>
  );
}

export function PosPage({ data }: ViewProps) {
  const overview = record(data.overview);
  const transactions = list(overview.transactions);
  return (
    <main className="pos-page">
      <header className="pos-header"><a href="/apps" className="back-link">← Aplikace</a><div><span className="eyebrow">PRODEJ / POKLADNA</span><h1>Pokladna</h1><p>Uzavírejte účtenky na prodejně a sledujte aktuální tržbu podle plateb.</p></div><a href="#receipts" className="primary">Otevřené účtenky</a></header>
      {[value(data, "error"), value(data, "actionError")].filter(Boolean).map((error, index) => <p className="pos-message error" role="alert" key={index}>{error}</p>)}
      {value(data, "message") && <p className="pos-message" role="status">{value(data, "message")}</p>}
      <section className="pos-metrics">
        {metric("Dnešní tržba", `${overview.paidToday == null ? "-" : amount(overview.paidToday, 2)} Kč`, "uhrazené účtenky")}
        {metric("Otevřené účtenky", value(overview, "openTransactionCount", "-"), "čekají na platbu")}
        {metric("Položky v obsluze", value(overview, "itemCount", "-"), "napříč zobrazenými účtenkami")}
      </section>
      <section id="receipts" className="pos-section">
        <div className="section-head"><div><span className="eyebrow">AKTUÁLNÍ ÚČTENKY</span><h2>Obsluha prodejen</h2></div><span className="receipt-count">{transactions.length} účtenky</span></div>
        <div className="receipt-table-wrap"><table className="receipt-table">
          <thead><tr><th>Účtenka</th><th>Prodejna a čas</th><th>Položky</th><th>Celkem</th><th>Platba</th><th>Obsluha</th></tr></thead>
          <tbody>{transactions.map((transaction, index) => {
            const paid = value(transaction, "status") !== "OPEN";
            return <tr key={value(transaction, "id", String(index))}>
              <td data-label="Účtenka"><strong>{value(transaction, "receiptNumber")}</strong><span className={`status-chip status-${value(transaction, "status").toLowerCase()}`}>{paid ? "UHRAZENO" : "OTEVŘENO"}</span></td>
              <td data-label="Prodejna a čas"><strong>{value(transaction, "storeName")}</strong><span>{value(transaction, "openedAt").replace(/T/g, " ")}</span></td>
              <td data-label="Položky">{value(transaction, "itemCount")}</td><td data-label="Celkem"><strong>{amount(transaction.totalAmount, 2)} Kč</strong></td><td data-label="Platba">{value(transaction, "paymentMethod", "-") || "-"}</td>
              <td data-label="Obsluha">{!paid ? <form method="post" className="payment-form"><input type="hidden" name="id" value={value(transaction, "id")} /><label>Platba<select name="method" defaultValue="CARD"><option value="CARD">Karta</option><option value="CASH">Hotovost</option><option value="VOUCHER">Poukázka</option></select></label><button className="secondary" type="submit">Přijmout platbu</button></form> : <span className="paid-label">Platba přijata</span>}</td>
            </tr>;
          })}</tbody>
        </table></div>
      </section>
    </main>
  );
}
