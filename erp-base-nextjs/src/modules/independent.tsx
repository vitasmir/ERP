"use client";

import { useEffect, useState } from "react";
import { Action, Alert, ApiForm, Editor, Metrics, Module, Table, type Column, type Field } from "@/components/ui";
import { useApi } from "@/lib/client-api";
import { money, number, record, rows, text, type Json, type Row } from "@/lib/data";

const titles: Record<string, string> = {
  dashboard: "Dashboard",
  accounting: "Účetnictví",
  crm: "CRM pipeline",
  documents: "Dokumenty",
  projects: "Projekty",
  helpdesk: "Helpdesk",
  marketing: "Marketing",
  website: "Web",
};

const labels: Record<string, string> = {
  NEW: "Nové", QUALIFIED: "Kvalifikované", PROPOSAL: "Nabídka", WON: "Vyhráno",
  DRAFT: "Koncept", ISSUED: "Vystaveno", PARTIALLY_PAID: "Částečně uhrazeno",
  PAID: "Uhrazeno", OVERDUE: "Po splatnosti", CANCELLED: "Zrušeno",
  PENDING_APPROVAL: "Ke schválení", APPROVED: "Schváleno",
  PLANNED: "Plánováno", IN_PROGRESS: "Probíhá", COMPLETED: "Dokončeno",
  OPEN: "Otevřeno", RESOLVED: "Vyřešeno",
  HIGH: "Vysoká", MEDIUM: "Střední", LOW: "Nízká",
  EMAIL: "E-mail", SOCIAL: "Sociální sítě", EVENT: "Událost",
  RUNNING: "Běží", ACTIVE: "Aktivní", PUBLISHED: "Publikováno",
  CONTENT: "Obsah", LANDING: "Úvod", CATALOG: "Katalog", CAMPAIGN: "Kampaň",
};

function translated(value: Json | undefined) {
  return labels[text(value)] ?? text(value);
}

function statusColumn(key = "status", label = "Stav"): Column {
  return { key, label, render: (row) => translated(row[key]) };
}

function options(values: string[]) {
  return values.map((value) => ({ value, label: labels[value] ?? value }));
}

function pathId(row: Row) {
  return encodeURIComponent(text(row.id));
}

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function dateTime(value: Json | undefined) {
  const raw = text(value);
  const date = new Date(raw);
  return raw && !Number.isNaN(date.getTime()) ? date.toLocaleString("cs-CZ") : raw;
}

function trimmed(values: Row, names: string[]): Row {
  return Object.fromEntries(names.map((name) => {
    const value = text(values[name]).trim();
    if (!value) throw new Error("Vyplňte všechna povinná textová pole.");
    return [name, value];
  }));
}

function decimal(value: Json | undefined, minimum: number, label: string): string {
  const raw = text(value).trim();
  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(raw) || !Number.isFinite(Number(raw)) || Number(raw) < minimum) {
    throw new Error(`${label}: vyplňte platnou částku alespoň ${minimum}.`);
  }
  return raw;
}

export function createInvoicePayload(values: Row): Row {
  const invoice = trimmed(values, ["invoiceNumber", "partnerName"]);
  return {
    ...invoice,
    issueDate: text(values.issueDate),
    dueDate: text(values.dueDate),
    lines: [{
      description: `Faktura ${text(invoice.invoiceNumber)}`,
      quantity: 1,
      unitPrice: decimal(values.totalAmount, 0.01, "Celkem"),
      vatRate: 0,
    }],
  };
}

export function updateInvoicePayload(values: Row, lines: Row[]): Row {
  const version = Number(values.version);
  if (!Number.isSafeInteger(version) || version < 0 || text(values.version) === "") {
    throw new Error("Chybí platná verze faktury. Načtěte fakturu znovu.");
  }
  if (!lines.length) throw new Error("Faktura musí obsahovat alespoň jednu položku.");
  return {
    version,
    invoice: {
      ...trimmed(values, ["invoiceNumber", "partnerName"]),
      issueDate: text(values.issueDate),
      dueDate: text(values.dueDate),
      lines: lines.map((line, index) => ({
        productId: line.productId ?? null,
        imageUrl: line.imageUrl ?? null,
        ...trimmed({ description: values[`line${index}Description`] }, ["description"]),
        quantity: decimal(values[`line${index}Quantity`], 0.001, "Množství"),
        unitPrice: decimal(values[`line${index}Price`], 0, "Cena"),
        vatRate: decimal(values[`line${index}Vat`], 0, "DPH"),
      })),
    },
  };
}

export function paymentPayload(values: Row): Row {
  const paidOn = text(values.paidOn);
  const date = new Date(`${paidOn}T00:00:00Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(paidOn) || Number.isNaN(date.getTime())
    || date.toISOString().slice(0, 10) !== paidOn || paidOn > today()) {
    throw new Error("Datum úhrady musí být platné datum v minulosti nebo dnes.");
  }
  return {
    amount: decimal(values.amount, 0.01, "Úhrada"),
    paidOn,
    ...trimmed(values, ["reference"]),
  };
}

export function websitePayload(values: Row): Row {
  const page = trimmed(values, ["title", "slug", "contentType", "ownerName"]);
  if (!["CONTENT", "LANDING", "CATALOG", "CAMPAIGN"].includes(text(page.contentType))) {
    throw new Error("Neplatný typ stránky.");
  }
  return { ...page, content: text(values.content) };
}

function Summary({ data, labels: metricLabels, amounts = [] }: {
  data: Row; labels: Record<string, string>; amounts?: string[];
}) {
  return <Metrics data={{
    ...data,
    ...Object.fromEntries(amounts.map((key) => [key, money(data[key])])),
  }} labels={metricLabels} />;
}

const invoiceFields: Field[] = [
  { name: "invoiceNumber", label: "Číslo faktury", required: true, maxLength: 40 },
  { name: "partnerName", label: "Odběratel", required: true, maxLength: 160 },
  { name: "issueDate", label: "Vystaveno", type: "date", required: true },
  { name: "dueDate", label: "Splatnost", type: "date", required: true },
];

function InvoiceEditor({ invoice, onSaved }: { invoice: Row; onSaved: () => void }) {
  const [opened, setOpened] = useState(false);
  const detail = useApi(opened ? `accounting/invoices/${pathId(invoice)}` : null);
  const data = record(detail.data);
  const current = record(data.invoice);
  const lines = rows(data.lines);
  const fields: Field[] = [
    ...invoiceFields,
    { name: "version", label: "Verze", type: "hidden" },
    ...lines.flatMap((line, index): Field[] => [
      { name: `line${index}Description`, label: `Položka ${index + 1}: popis`, required: true, maxLength: 240, value: text(line.description) },
      { name: `line${index}Quantity`, label: "Množství", type: "number", required: true, min: 0.001, step: "0.001", value: text(line.quantity) },
      { name: `line${index}Price`, label: "Cena bez DPH (Kč)", type: "number", required: true, min: 0, step: "0.01", value: text(line.unitPrice) },
      { name: `line${index}Vat`, label: "DPH (%)", type: "number", required: true, min: 0, max: 100, step: "0.01", value: text(line.vatRate) },
    ]),
  ];
  return <div className="editor">
    <button type="button" onClick={() => setOpened(!opened)}>{opened ? "Zrušit úpravu" : "Upravit fakturu"}</button>
    {opened && <>
      <Alert>{detail.error}</Alert>
      {detail.error && <button type="button" onClick={detail.reload}>Zkusit znovu</button>}
      {detail.loading ? <p role="status">Načítání faktury…</p> : detail.data && !detail.error &&
        <ApiForm key={`${text(invoice.id)}-${text(data.version)}`} path={`accounting/invoices/${pathId(invoice)}`}
          method="PUT" fields={fields} initial={{ ...current, version: data.version ?? null }}
          transform={(values) => updateInvoicePayload(values, lines)}
          onSaved={() => { setOpened(false); onSaved(); }} label="Uložit změny" />}
    </>}
  </div>;
}

function InvoiceLines({ invoice }: { invoice: Row }) {
  const lines = rows(invoice.lines);
  return <Editor title={`${lines.length} položek`}>
    <Table data={lines} columns={[
      { key: "description", label: "Popis" },
      { key: "imageUrl", label: "Obrázek", render: (line) => text(line.imageUrl) &&
        <img src={text(line.imageUrl)} alt={text(line.description)} width={64} height={64} /> },
      { key: "quantity", label: "Množství" },
      { key: "unitPrice", label: "Cena za kus", money: true },
    ]} />
  </Editor>;
}

function Accounting({ data, reload }: { data: Row; reload: () => void }) {
  return <>
    <Summary data={data} labels={{ receivables: "Pohledávky", overdue: "Po splatnosti", openInvoiceCount: "Otevřené faktury" }}
      amounts={["receivables", "overdue"]} />
    <Editor title="Nová faktura">
      <ApiForm path="accounting/invoices" fields={[...invoiceFields,
        { name: "totalAmount", label: "Celkem (Kč, bez DPH)", type: "number", min: 0.01, step: "0.01", required: true },
      ]} initial={{ issueDate: today() }} transform={createInvoicePayload} onSaved={reload} label="Vytvořit koncept" />
    </Editor>
    <h2>Přehled faktur</h2>
    <Table data={rows(data.invoices)} columns={[
      statusColumn(), { key: "invoiceNumber", label: "Faktura" },
      { key: "lines", label: "Položky", render: (invoice) => <InvoiceLines invoice={invoice} /> },
      { key: "partnerName", label: "Odběratel" },
      { key: "issueDate", label: "Vystaveno" }, { key: "dueDate", label: "Splatnost" },
      { key: "totalAmount", label: "Celkem", money: true }, { key: "paidAmount", label: "Uhrazeno", money: true },
      { key: "outstanding", label: "Zbývá uhradit", render: (invoice) => money(
        ["DRAFT", "CANCELLED"].includes(text(invoice.status))
          ? 0 : Math.max(0, number(invoice.totalAmount) - number(invoice.paidAmount)),
      ) },
    ]} actions={(invoice) => {
      const path = `accounting/invoices/${pathId(invoice)}`;
      const payable = !["DRAFT", "PAID", "CANCELLED"].includes(text(invoice.status));
      return <>
        <a href={`/api/backend/${path}/pdf`} download>Stáhnout PDF</a>
        {invoice.status === "DRAFT" && <>
          <InvoiceEditor invoice={invoice} onSaved={reload} />
          <Action path={`${path}/issue`} label="Vystavit fakturu" onSaved={reload} />
        </>}
        {payable && <>
          <Action path={`${path}/paid`} label="Označit jako uhrazenou" onSaved={reload} />
          <Editor title="Zapsat úhradu">
            <ApiForm path={`${path}/payment`} method="PATCH" fields={[
              { name: "amount", label: "Částka (Kč)", type: "number", min: 0.01, max: Math.max(0, number(invoice.totalAmount) - number(invoice.paidAmount)), step: "0.01", required: true },
              { name: "paidOn", label: "Datum úhrady", type: "date", required: true },
              { name: "reference", label: "Reference úhrady", required: true, maxLength: 160 },
            ]} initial={{ paidOn: today() }} transform={paymentPayload} onSaved={reload} label="Uložit úhradu" />
          </Editor>
        </>}
      </>;
    }} />
  </>;
}

const leadColumns: Column[] = [
  { key: "name", label: "Příležitost" }, { key: "customerName", label: "Zákazník" },
  { key: "expectedRevenue", label: "Hodnota", money: true },
  { key: "probability", label: "Pravděpodobnost", render: (lead) => `${text(lead.probability)} %` },
  { key: "expectedCloseDate", label: "Očekávané uzavření" }, statusColumn("stage", "Fáze"),
];

function Crm({ data, reload }: { data: Row; reload: () => void }) {
  return <>
    <Summary data={data} labels={{ pipeline: "Hodnota pipeline", forecast: "Vážený forecast", openLeadCount: "Otevřené příležitosti" }}
      amounts={["pipeline", "forecast"]} />
    <h2>Nová příležitost</h2>
    <ApiForm path="crm/leads" fields={[
      { name: "name", label: "Název příležitosti", required: true, maxLength: 200 },
      { name: "customerName", label: "Zákazník", required: true, maxLength: 200 },
      { name: "expectedRevenue", label: "Hodnota (Kč)", type: "number", required: true, min: 0, step: "0.01" },
      { name: "probability", label: "Pravděpodobnost (%)", type: "number", required: true, min: 0, max: 100, step: "1", value: 20 },
      { name: "expectedCloseDate", label: "Očekávané uzavření", type: "date", required: true },
    ]} transform={(values, form) => ({
      ...trimmed(values, ["name", "customerName"]),
      expectedRevenue: decimal(text(form.get("expectedRevenue")?.toString()), 0, "Hodnota"),
      probability: values.probability ?? 20,
      expectedCloseDate: text(values.expectedCloseDate),
    })} onSaved={reload} label="Vytvořit příležitost" />
    <h2>Obchodní pipeline</h2>
    <p>Fázi příležitosti změňte výběrem v jejím řádku.</p>
    <Table data={rows(data.leads)} columns={leadColumns} actions={(lead) => <>
      <ApiForm key={`${text(lead.id)}-${text(lead.stage)}`} path={`crm/leads/${pathId(lead)}/stage`} method="PATCH"
        fields={[{ name: "stage", label: "Nová fáze", type: "select", options: options(["NEW", "QUALIFIED", "PROPOSAL", "WON"]) }]}
        initial={lead} onSaved={reload} label="Změnit fázi" />
      {lead.stage !== "WON" && <Action path={`crm/leads/${pathId(lead)}/won`} label="Označit jako vyhranou" onSaved={reload} />}
    </>} />
  </>;
}

function Documents({ data, reload }: { data: Row; reload: () => void }) {
  return <>
    <Summary data={data} labels={{ pendingApprovalCount: "Čeká na schválení", approvedCount: "Schválené dokumenty", categoryCount: "Kategorie" }} />
    <h2>Soubory a schvalovací procesy</h2>
    <Table data={rows(data.documents)} columns={[
      { key: "title", label: "Dokument" }, { key: "category", label: "Kategorie" },
      { key: "referenceCode", label: "Reference" }, { key: "ownerName", label: "Vlastník" },
      { key: "updatedOn", label: "Aktualizováno" }, statusColumn(),
    ]} actions={(document) => document.status === "PENDING_APPROVAL"
      ? <Action path={`documents/${pathId(document)}/approve`} label="Schválit dokument" onSaved={reload} />
      : "Proces je uzavřen"} />
  </>;
}

function Projects({ data, reload }: { data: Row; reload: () => void }) {
  return <>
    <Summary data={data} labels={{ activeProjectCount: "Aktivní projekty", inProgressCount: "Probíhá", dueSoonCount: "Termín do 14 dnů" }} />
    <h2>Projektové portfolio</h2>
    <Table data={rows(data.projects)} columns={[
      { key: "name", label: "Projekt" }, { key: "department", label: "Oddělení" },
      { key: "ownerName", label: "Vlastník" }, { key: "dueDate", label: "Termín dokončení" },
      { key: "progress", label: "Postup", render: (project) => <>
        <progress aria-label={`Postup projektu ${text(project.name)}`} max={100} value={number(project.progress)} /> {text(project.progress)} %
      </> }, statusColumn(),
    ]} actions={(project) => project.status !== "COMPLETED"
      ? <Action path={`projects/${pathId(project)}/complete`} label="Dokončit projekt" onSaved={reload} />
      : "Projekt uzavřen"} />
  </>;
}

function Helpdesk({ data, reload }: { data: Row; reload: () => void }) {
  return <>
    <Summary data={data} labels={{ openTicketCount: "Otevřené požadavky", highPriorityCount: "Vysoká priorita", dueSoonCount: "SLA do 8 hodin" }} />
    <h2>Fronta podpory</h2>
    <Table data={rows(data.tickets)} columns={[
      { key: "ticketNumber", label: "Číslo požadavku" }, { key: "subject", label: "Požadavek" },
      { key: "requesterName", label: "Žadatel" }, { key: "assignedTeam", label: "Tým" },
      statusColumn("priority", "Priorita"), { key: "dueAt", label: "SLA termín", render: (ticket) => dateTime(ticket.dueAt) },
      statusColumn(),
    ]} actions={(ticket) => ticket.status !== "RESOLVED"
      ? <Action path={`helpdesk/tickets/${pathId(ticket)}/resolve`} label="Označit jako vyřešené" onSaved={reload} />
      : "Požadavek uzavřen"} />
  </>;
}

function Marketing({ data, reload }: { data: Row; reload: () => void }) {
  return <>
    <Summary data={data} labels={{ runningCampaignCount: "Aktivní kampaně", plannedCampaignCount: "Plánované kampaně", totalLeadCount: "Získané leady", totalSpent: "Vyčerpáno" }}
      amounts={["totalSpent"]} />
    <h2>Nová kampaň</h2>
    <ApiForm path="marketing/campaigns" fields={[
      { name: "name", label: "Název kampaně", required: true },
      { name: "audience", label: "Segment", required: true },
      { name: "channel", label: "Kanál", type: "select", options: options(["EMAIL", "SOCIAL", "EVENT"]) },
      { name: "ownerName", label: "Vlastník", required: true },
      { name: "budget", label: "Rozpočet (Kč)", type: "number", required: true, min: 0, step: "0.01" },
      { name: "plannedStartDate", label: "Plánovaný začátek", type: "date", required: true },
    ]} transform={(values, form) => ({
      ...trimmed(values, ["name", "audience", "channel", "ownerName"]),
      budget: decimal(form.get("budget")?.toString(), 0, "Rozpočet"),
      plannedStartDate: text(values.plannedStartDate),
    })} onSaved={reload} label="Založit kampaň" />
    <h2>Marketingový plán</h2>
    <Table data={rows(data.campaigns)} columns={[
      { key: "name", label: "Kampaň" }, { key: "audience", label: "Segment" },
      statusColumn("channel", "Kanál"), { key: "ownerName", label: "Vlastník" },
      { key: "budget", label: "Rozpočet", money: true }, { key: "spent", label: "Vyčerpáno", money: true },
      { key: "leadCount", label: "Leady" }, { key: "plannedStartDate", label: "Začátek" }, statusColumn(),
    ]} actions={(campaign) => campaign.status === "PLANNED"
      ? <Action path={`marketing/campaigns/${pathId(campaign)}/launch`} label="Spustit kampaň" onSaved={reload} />
      : campaign.status === "RUNNING"
        ? <Action path={`marketing/campaigns/${pathId(campaign)}/complete`} label="Dokončit kampaň" onSaved={reload} />
        : "Kampaň uzavřena"} />
  </>;
}

const pageFields: Field[] = [
  { name: "title", label: "Název stránky", required: true },
  { name: "slug", label: "URL stránky (např. /o-nas)", required: true },
  { name: "contentType", label: "Typ stránky", type: "select", options: options(["CONTENT", "LANDING", "CATALOG", "CAMPAIGN"]) },
  { name: "ownerName", label: "Správce", required: true },
  { name: "content", label: "Obsah stránky", type: "textarea" },
];

function Website({ data, reload }: { data: Row; reload: () => void }) {
  const [editId, setEditId] = useState("");
  useEffect(() => { setEditId(new URLSearchParams(window.location.search).get("edit") ?? ""); }, []);
  const pages = rows(data.pages);
  const editing = pages.find((page) => text(page.id) === editId);
  return <>
    <Summary data={data} labels={{ publishedPageCount: "Publikované stránky", draftPageCount: "Koncepty k publikaci", monthlyVisits: "Návštěvy za měsíc", formPageCount: "Stránky s formulářem" }} />
    <section id="new-page">
      <h2>{editing ? "Upravit stránku" : "Nová stránka"}</h2>
      <Alert>{editId && !editing ? "Stránka pro úpravu nebyla nalezena." : ""}</Alert>
      <ApiForm key={editId || "new"} path={editing ? `website/pages/${pathId(editing)}` : "website/pages"}
        method={editing ? "PUT" : "POST"} fields={pageFields} initial={editing}
        transform={websitePayload} onSaved={() => { setEditId(""); reload(); }}
        label={editing ? "Uložit změny" : "Uložit koncept"} />
      {editId && <button type="button" onClick={() => setEditId("")}>Zrušit úpravu</button>}
      {editing?.status === "PUBLISHED" && <p>Uložením změn se stránka vrátí do konceptu. Poté ji znovu publikujte.</p>}
    </section>
    <h2>Stránky webu</h2>
    <Table data={pages} columns={[
      { key: "title", label: "Stránka" }, statusColumn("contentType", "Typ"),
      { key: "slug", label: "URL", render: (page) => {
        const slug = text(page.slug);
        return page.status === "PUBLISHED" && /^\/(?!\/)/.test(slug) && !slug.includes("\\")
          ? <a href={slug}>{slug}</a> : slug;
      } },
      { key: "ownerName", label: "Správce" }, { key: "monthlyVisits", label: "Návštěvy / měsíc" },
      { key: "hasContactForm", label: "Formulář", render: (page) => page.hasContactForm === true ? "Aktivní" : "Bez formuláře" },
      statusColumn(),
    ]} actions={(page) => <>
      {page.status === "DRAFT" && <Action path={`website/pages/${pathId(page)}/publish`} label="Publikovat" onSaved={reload} />}
      <button type="button" onClick={() => { setEditId(text(page.id)); document.getElementById("new-page")?.scrollIntoView({ behavior: "smooth" }); }}>Upravit</button>
      <Action path={`website/pages/${pathId(page)}`} method="DELETE" label="Smazat" confirm="Opravdu smazat stránku?"
        onSaved={() => { if (text(page.id) === editId) setEditId(""); reload(); }} />
    </>} />
  </>;
}

function Dashboard({ data }: { data: Row }) {
  return <>
    <p>Pracovní přehled financí, obchodních příležitostí a promo aktivit.</p>
    <Summary data={data} labels={{ receivables: "Pohledávky", overdue: "Po splatnosti", pipeline: "Hodnota pipeline", forecast: "Vážený forecast", activeCampaignCount: "Aktivní promo kampaně" }}
      amounts={["receivables", "overdue", "pipeline", "forecast"]} />
    <h2>Neuhrazené faktury</h2><a href="/accounting">Všechny faktury →</a>
    <Table data={rows(data.invoices)} columns={[
      { key: "invoiceNumber", label: "Faktura" }, { key: "partnerName", label: "Odběratel" },
      { key: "dueDate", label: "Splatnost" }, { key: "outstandingAmount", label: "Zbývá uhradit", money: true }, statusColumn(),
    ]} />
    <h2>Nejbližší příležitosti</h2><a href="/crm">Otevřít CRM →</a>
    <Table data={rows(data.leads)} columns={leadColumns} />
    <h2>Stav promo kampaní</h2><a href="/promo">Správa promo kampaní →</a>
    <Table data={rows(data.campaigns)} columns={[
      { key: "name", label: "Kampaň" }, { key: "startsOn", label: "Začátek" },
      { key: "endsOn", label: "Konec" }, statusColumn(),
    ]} />
  </>;
}

export function IndependentModule({ module }: { module: string }) {
  const supported = Object.hasOwn(titles, module);
  const overview = useApi(supported ? `${module}/overview` : null);
  const data = record(overview.data);
  let content;
  switch (module) {
    case "dashboard": content = <Dashboard data={data} />; break;
    case "accounting": content = <Accounting data={data} reload={overview.reload} />; break;
    case "crm": content = <Crm data={data} reload={overview.reload} />; break;
    case "documents": content = <Documents data={data} reload={overview.reload} />; break;
    case "projects": content = <Projects data={data} reload={overview.reload} />; break;
    case "helpdesk": content = <Helpdesk data={data} reload={overview.reload} />; break;
    case "marketing": content = <Marketing data={data} reload={overview.reload} />; break;
    case "website": content = <Website data={data} reload={overview.reload} />; break;
  }
  return <Module title={titles[module] ?? "Neznámý modul"} loading={overview.loading}
    error={supported ? overview.error : "Tento modul není podporován."}>
    {overview.error
      ? <button type="button" onClick={overview.reload}>Zkusit načíst znovu</button>
      : <div key={module}>{content}</div>}
  </Module>;
}
