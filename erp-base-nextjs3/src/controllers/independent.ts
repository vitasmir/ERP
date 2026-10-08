import {
  type Context, type Page, type Row, ValidationError, field, row, rows,
  uuid, date, integer, feedback, redirect, reportError,
} from "../lib/context";

const HELP_VERSION = "20261001-2";
const titles: Record<string, string> = {
  dashboard: "Dashboard", accounting: "Účetnictví", crm: "CRM pipeline",
  documents: "Dokumenty", projects: "Projekty", helpdesk: "Helpdesk",
  marketing: "Marketing", website: "Web",
};
const reserved = new Set([
  "/apps", "/dashboard", "/roles", "/role-modules", "/companies", "/settings", "/users", "/accounting",
  "/crm", "/documents", "/ecommerce", "/helpdesk", "/hr", "/inventory", "/manufacturing", "/marketing",
  "/planning", "/pos", "/projects", "/promo", "/purchase", "/sales", "/website", "/login", "/logout", "/shop", "/eshop",
]);

function id(c: Context, query = false): string {
  const value = query ? c.url.searchParams.get("id") ?? "" : field(c, "id");
  try { uuid(value); } catch { throw new ValidationError("Neplatný identifikátor záznamu."); }
  return value;
}

function text(c: Context, name: string, maximum?: number): string {
  const value = field(c, name).trim();
  if (!value || maximum !== undefined && Array.from(value).length > maximum) {
    throw new ValidationError(`Vyplňte platnou hodnotu pole ${name}.`);
  }
  return value;
}

function decimalText(c: Context, name: string, minimum?: number): string {
  const value = text(c, name);
  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value) || !Number.isFinite(Number(value))) {
    throw new ValidationError(`Vyplňte platnou částku v poli ${name}.`);
  }
  if (minimum !== undefined && Number(value) < minimum) {
    throw new ValidationError(`Částka v poli ${name} musí být alespoň ${minimum}.`);
  }
  return value;
}

function integerField(c: Context, name: string, minimum: number, maximum = Number.MAX_SAFE_INTEGER): number {
  try {
    const value = field(c, name).trim();
    if (!/^[+-]?(?:0|[1-9]\d*)$/.test(value)) throw new Error("Invalid integer");
    const result = integer(value, minimum);
    if (result > maximum) throw new Error("Invalid integer");
    return result;
  } catch { throw new ValidationError(`Vyplňte platné celé číslo v poli ${name}.`); }
}

function dateField(c: Context, name: string): string {
  const value = text(c, name);
  try { return date(value); } catch { throw new ValidationError(`Vyplňte platné datum v poli ${name}.`); }
}

type Operation = [method: string, path: string, body: Row | null, success: string];

function operation(c: Context, module: string): Operation {
  const action = field(c, "action");
  if (module === "accounting") {
    const path = "/api/v1/accounting/invoices";
    if (action === "create" || action === "update") {
      const number = text(c, "invoiceNumber", 40);
      const invoice = {
        invoiceNumber: number, partnerName: text(c, "partnerName", 160),
        issueDate: dateField(c, "issueDate"), dueDate: dateField(c, "dueDate"),
        lines: [{
          description: `Faktura ${number}`, quantity: 1,
          unitPrice: decimalText(c, "totalAmount", 0.01), vatRate: 0,
        }],
      };
      return action === "create" ? ["POST", path, invoice, "Faktura byla uložena."]
        : ["PUT", `${path}/${id(c)}`, { version: integerField(c, "version", 0), invoice }, "Faktura byla uložena."];
    }
    const recordPath = `${path}/${id(c)}`;
    return action === "payment"
      ? ["PATCH", `${recordPath}/payment`, { amount: decimalText(c, "amount", 0.01) }, "Úhrada byla uložena."]
      : ["PATCH", `${recordPath}/paid`, null, "Faktura byla označena jako uhrazená."];
  }
  if (module === "crm") {
    if (c.form.has("id")) return ["PATCH", `/api/v1/crm/leads/${id(c)}/won`, null, "Příležitost byla označena jako vyhraná."];
    return ["POST", "/api/v1/crm/leads", {
      name: text(c, "name", 200), customerName: text(c, "customerName", 200),
      expectedRevenue: decimalText(c, "expectedRevenue", 0),
      probability: integerField(c, "probability", 0, 100),
      expectedCloseDate: dateField(c, "expectedCloseDate"),
    }, "Příležitost byla vytvořena."];
  }
  if (module === "documents") return ["PATCH", `/api/v1/documents/${id(c)}/approve`, null, "Dokument byl schválen."];
  if (module === "projects") return ["PATCH", `/api/v1/projects/${id(c)}/complete`, null, "Projekt byl označen jako dokončený."];
  if (module === "helpdesk") return ["PATCH", `/api/v1/helpdesk/tickets/${id(c)}/resolve`, null, "Požadavek byl označen jako vyřešený."];
  if (module === "marketing") {
    if (action === "create") {
      const channel = text(c, "channel");
      if (!["EMAIL", "SOCIAL", "EVENT"].includes(channel)) throw new ValidationError("Neplatný kanál kampaně.");
      return ["POST", "/api/v1/marketing/campaigns", {
        name: text(c, "name"), audience: text(c, "audience"), channel,
        ownerName: text(c, "ownerName"), budget: decimalText(c, "budget"),
        plannedStartDate: dateField(c, "plannedStartDate"),
      }, "Kampaň byla vytvořena."];
    }
    const complete = action === "complete";
    return ["PATCH", `/api/v1/marketing/campaigns/${id(c)}/${complete ? "complete" : "launch"}`,
      null, complete ? "Kampaň byla dokončena." : "Kampaň byla spuštěna."];
  }
  const path = "/api/v1/website/pages";
  if (action === "create" || action === "edit") {
    const contentType = text(c, "contentType");
    if (!["CONTENT", "LANDING", "CATALOG", "CAMPAIGN"].includes(contentType)) {
      throw new ValidationError("Neplatný typ stránky.");
    }
    const body = {
      title: text(c, "title"), slug: text(c, "slug"), contentType,
      ownerName: text(c, "ownerName"), content: field(c, "content"),
    };
    return action === "create" ? ["POST", path, body, "Stránka byla vytvořena."]
      : ["PUT", `${path}/${id(c)}`, body, "Stránka byla upravena."];
  }
  const recordPath = `${path}/${id(c)}`;
  return action === "delete" ? ["DELETE", recordPath, null, "Stránka byla smazána."]
    : ["PATCH", `${recordPath}/publish`, null, "Stránka byla publikována."];
}

async function save(c: Context, module: string): Promise<Response> {
  let error = true;
  let message: string;
  try {
    const [method, path, body, success] = operation(c, module);
    const response = await c.backend.request(method, path, body);
    if (response.ok) {
      error = false;
      message = success;
    } else {
      reportError(response.status, `Could not save ERP ${module}.`);
      message = `Backend odmítl změnu (HTTP ${response.status}). Změna nebyla uložena.`;
      const details = await response.text();
      if (details !== "") {
        const plain = details.replace(/<!--[\s\S]*?-->|<[^>]*>/g, "");
        message += ` ${Array.from(plain).slice(0, 500).join("")}`;
      }
    }
  } catch (failure) {
    if (failure instanceof ValidationError) message = failure.message;
    else {
      reportError(failure, `Could not save ERP ${module}.`);
      message = "Změnu se nepodařilo uložit. Backend není dostupný.";
    }
  }
  return feedback(`/${module}`, message, error, module === "helpdesk" ? { v: HELP_VERSION } : {});
}

async function overview(c: Context, module: string): Promise<Page> {
  let data: Row | null = null;
  let companies: Row[] = [];
  let error = "";
  try {
    data = row(await c.backend.json("GET", `/api/v1/${module}/overview`));
    if (module === "accounting") companies = rows(await c.backend.json("GET", "/api/v1/companies"));
  } catch (failure) {
    reportError(failure, `Could not load ERP ${module} overview.`);
    error = `Přehled modulu ${titles[module]} nelze načíst. Backend není dostupný nebo požadavek odmítl.`;
  }
  let editingPage: Row | null = null;
  if (module === "website" && c.url.searchParams.has("edit")) {
    editingPage = rows(data?.pages ?? []).find((page) => page.id === c.url.searchParams.get("edit")) ?? null;
    if (editingPage === null) error = `${error} Stránka pro úpravu nebyla nalezena.`.trim();
  }
  return c.render(`${module}/index`, {
    overview: data, companies, editingPage, error,
    actionError: c.url.searchParams.get("error") ?? "", message: c.url.searchParams.get("message") ?? "",
    pageTitle: titles[module], breadcrumb: module.toUpperCase(),
  });
}

async function invoicePdf(c: Context, value: string): Promise<Response> {
  try { uuid(value); } catch { return new Response("Neplatné číslo faktury.", { status: 400 }); }
  try {
    const response = await c.backend.request("GET", `/api/v1/accounting/invoices/${value}/pdf`);
    if (!response.ok) {
      reportError(response.status, "Could not download ERP invoice PDF.");
      return new Response(`PDF faktury není dostupné (HTTP ${response.status}).`, { status: response.status });
    }
    const headers = new Headers({ "Content-Type": "application/pdf" });
    const disposition = response.headers.get("content-disposition");
    if (disposition !== null) headers.set("Content-Disposition", disposition);
    const bytes = await response.arrayBuffer();
    return new Response([204, 205].includes(response.status) ? null : bytes, { status: response.status, headers });
  } catch (failure) {
    reportError(failure, "Could not download ERP invoice PDF.");
    return new Response("Stahování PDF se nezdařilo. Backend není dostupný.", { status: 503 });
  }
}

async function crmStage(c: Context): Promise<Response> {
  try {
    const recordId = id(c, true);
    const stage = c.url.searchParams.get("stage");
    if (!stage || !["NEW", "QUALIFIED", "PROPOSAL", "WON"].includes(stage)) {
      throw new ValidationError("Neplatná fáze příležitosti.");
    }
    const response = await c.backend.request("PATCH", `/api/v1/crm/leads/${recordId}/stage`, { stage });
    const body = await response.text();
    if (!response.ok) {
      reportError(response.status, "Could not save ERP CRM stage.");
      return Response.json({ error: `Změnu fáze backend odmítl (HTTP ${response.status}).` }, { status: response.status });
    }
    return new Response([204, 205].includes(response.status) ? null : body, {
      status: response.status, headers: { "Content-Type": "application/json" },
    });
  } catch (failure) {
    if (failure instanceof ValidationError) return Response.json({ error: failure.message }, { status: 400 });
    reportError(failure, "Could not save ERP CRM stage.");
    return Response.json({ error: "Změnu fáze se nepodařilo uložit. Backend není dostupný." }, { status: 503 });
  }
}

async function publicPage(c: Context): Promise<Page> {
  let query: URLSearchParams;
  let page: Row;
  try {
    query = new URLSearchParams({ slug: decodeURIComponent(c.url.pathname) });
    const response = await c.backend.request("GET", `/api/v1/website/pages/public?${query}`, null, false);
    if (response.status === 404) {
      return c.render("website/public", {
        page: null, error: "Stránka nebyla nalezena.", pageTitle: "Stránka nenalezena",
      }, 404);
    }
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    page = row(await response.json());
  } catch (failure) {
    reportError(failure, "Could not load ERP public website page.");
    return c.render("website/public", {
      page: null, error: "Stránku nelze načíst. Backend není dostupný.", pageTitle: "Web",
    }, 503);
  }
  let error: string | null = null;
  try {
    const visit = await c.backend.request("POST", `/api/v1/website/pages/visit?${query}`, null, false);
    if (!visit.ok) throw new Error(`HTTP ${visit.status}`);
  } catch (failure) {
    reportError(failure, "Could not record ERP public website visit.");
    error = "Návštěvu stránky se nepodařilo zaznamenat.";
  }
  return c.render("website/public", { page, pageTitle: page.title ?? "Web", error });
}

// Dispatch this handler after the other controllers: the public website route is a catchall.
export async function handleIndependent(c: Context): Promise<Page | Response | null> {
  const path = c.url.pathname;
  const module = path.slice(1);
  if (Object.hasOwn(titles, module)) {
    const allowed = module === "dashboard" ? ["GET"] : module === "crm" ? ["GET", "POST", "PUT"] : ["GET", "POST"];
    if (!allowed.includes(c.request.method)) {
      return new Response(null, { status: 405, headers: { Allow: allowed.join(", ") } });
    }
    if (c.request.method === "PUT") return crmStage(c);
    if (c.request.method === "POST") return save(c, module);
    if (module === "accounting" && c.url.searchParams.get("pdf")) return invoicePdf(c, c.url.searchParams.get("pdf")!);
    if (module === "helpdesk" && c.url.searchParams.get("v") !== HELP_VERSION) {
      const query = new URLSearchParams(c.url.searchParams);
      query.set("v", HELP_VERSION);
      return redirect(`/helpdesk?${query}`, 302);
    }
    return overview(c, module);
  }
  if (c.request.method !== "GET" || path === "/" || reserved.has(path)
    || path.startsWith("/assets/") || path.endsWith(".jsp")) return null;
  return publicPage(c);
}
