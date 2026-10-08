import assert from "node:assert/strict";
import { afterEach, beforeEach, mock, test } from "node:test";
import { Backend, type Context, type Page, type Session } from "../src/lib/context";
import { handleAdmin } from "../src/controllers/admin";
import { handleIndependent } from "../src/controllers/independent";

const ID = "12345678-abcd-1234-abcd-123456789abc";
const UPPER_ID = ID.toUpperCase();
type Call = { method: string; path: string; body?: unknown; authenticated: boolean };
const session = (): Session => ({
  token: "test-token", expiresAt: 0, lastActivityAt: 0, cart: {},
});

class MockBackend extends Backend {
  calls: Call[] = [];
  values: Record<string, unknown> = {};
  response: () => Response = () => new Response("{}", { status: 200 });
  requestFailure?: Error;
  constructor() { super(session()); }
  override async request(method: string, path: string, body?: unknown, authenticated = true): Promise<Response> {
    this.calls.push({ method, path, body, authenticated });
    if (this.requestFailure) throw this.requestFailure;
    return this.response();
  }
  override async json(method: string, path: string): Promise<unknown> {
    this.calls.push({ method, path, authenticated: true });
    const value = this.values[path];
    if (value instanceof Error) throw value;
    return value ?? (path.endsWith("/overview") || path.endsWith("/matrix") || path.endsWith("/settings") ? {} : []);
  }
}

function context(path: string, method = "GET", values: Record<string, string> = {}, backend = new MockBackend()): Context & { backend: MockBackend } {
  const form = new FormData();
  for (const [name, value] of Object.entries(values)) form.append(name, value);
  const url = new URL(path, "http://localhost");
  return {
    url, request: new Request(url, { method }), form, session: session(), backend,
    render: (view, data = {}, status = 200) => ({ view, data, status }),
  };
}

function page(value: Page | Response | null): Page {
  assert.ok(value !== null && !(value instanceof Response));
  return value;
}

function location(value: Page | Response | null): URL {
  assert.ok(value instanceof Response);
  assert.ok(value.status >= 300 && value.status < 400);
  const target = value.headers.get("Location");
  assert.ok(target);
  return new URL(target, "http://localhost");
}

function call(c: Context & { backend: MockBackend }, method: string, path: string, body?: unknown, authenticated = true): void {
  assert.deepEqual(c.backend.calls, [{ method, path, body, authenticated }]);
}

beforeEach(() => { mock.method(console, "error", () => {}); });
afterEach(() => { mock.restoreAll(); });

const company = { name: "  Firma  ", type: "  CUSTOMER ", currency: " CZK ", status: " ACTIVE ", color: " #fff " };
const user = {
  employeeId: UPPER_ID, username: " jan ", fullName: " Jan Novák ", companyName: " Firma ",
  password: " 1234567890 ", roleName: "Ignored by PHP", status: " ACTIVE ", color: " #fff ",
};
const role = {
  name: " Role ", initial: " R ", description: " Desc ", color: " #fff ",
  canRead: "", canEdit: "false", canDelete: "0",
};
const settings = {
  companyName: " Firma ", companyEmail: " info@example.cz ", currencyCode: " CZK ", timezone: " Europe/Prague ",
  fiscalYearStartMonth: " 10 ", defaultPaymentTermsDays: "0", deliveryFee: " 1e2 ",
  eshopMarginPercent: "99.98", eshopRoundingUnit: "10", eshopDefaultVatRate: "21",
};
const invoice = {
  invoiceNumber: " INV-1 ", partnerName: " Firma ", issueDate: "2026-10-08",
  dueDate: "2026-10-20", totalAmount: "001.2300",
};
const invoiceBody = {
  invoiceNumber: "INV-1", partnerName: "Firma", issueDate: "2026-10-08", dueDate: "2026-10-20",
  lines: [{ description: "Faktura INV-1", quantity: 1, unitPrice: "001.2300", vatRate: 0 }],
};
const web = { title: " Title ", slug: " /news ", contentType: " CONTENT ", ownerName: " Jan ", content: "  <b>Original</b>\n " };
const webBody = { title: "Title", slug: "/news", contentType: "CONTENT", ownerName: "Jan", content: web.content };

for (const action of ["create", "update"]) {
  test(`companies ${action} maps all trimmed fields and preserves ID casing`, async () => {
    const c = context("/companies", "POST", { ...company, action, id: UPPER_ID });
    const target = location(await handleAdmin(c));
    call(c, action === "create" ? "POST" : "PUT", `/api/v1/companies${action === "update" ? `/${UPPER_ID}` : ""}`,
      { name: "Firma", type: "CUSTOMER", currency: "CZK", status: "ACTIVE", color: "#fff" });
    assert.equal(target.searchParams.get("message"), action === "create" ? "Společnost byla vytvořena." : "Společnost byla upravena.");
  });

  test(`users ${action} preserves password/status/color, omits roleName`, async () => {
    const c = context("/users", "POST", { ...user, action, id: UPPER_ID, password: action === "update" ? "" : user.password });
    location(await handleAdmin(c));
    call(c, action === "create" ? "POST" : "PUT", `/api/v1/users${action === "update" ? `/${UPPER_ID}` : ""}`, {
      employeeId: UPPER_ID, fullName: "Jan Novák", username: "jan", password: action === "update" ? "" : user.password,
      companyName: "Firma", status: " ACTIVE ", color: " #fff ",
    });
  });

  test(`roles ${action} maps checkbox presence and redirects to roles even from role-modules`, async () => {
    const c = context("/role-modules", "POST", { ...role, action, id: ID });
    assert.equal(location(await handleAdmin(c)).pathname, "/roles");
    call(c, action === "create" ? "POST" : "PUT", `/api/v1/roles${action === "update" ? `/${ID}` : ""}`, {
      name: "Role", initial: "R", description: "Desc", color: "#fff",
      canRead: true, canInsert: false, canEdit: true, canManage: false, canDelete: true,
    });
  });
}

test("users delete has no body or irrelevant required fields", async () => {
  const c = context("/users", "POST", { action: "delete", id: ID });
  assert.equal(location(await handleAdmin(c)).searchParams.get("message"), "Uživatel byl smazán.");
  call(c, "DELETE", `/api/v1/users/${ID}`);
});

test("module permissions collect distinct prefixed keys, preserving module underscores and empty matrix", async () => {
  const c = context("/roles", "POST", {
    action: "save-module-permissions", [`permission_${ID}_sales_reports`]: "off", ignored: "value",
  });
  c.form.append(`permission_${ID}_sales_reports`, "on");
  assert.equal(location(await handleAdmin(c)).pathname, "/role-modules");
  call(c, "PUT", "/api/v1/roles/matrix", { permissions: [{ roleId: ID, moduleKey: "sales_reports" }] });
  const empty = context("/role-modules", "POST", { action: "save-module-permissions" });
  await handleAdmin(empty);
  call(empty, "PUT", "/api/v1/roles/matrix", { permissions: [] });
});

test("settings PATCH uses numeric integer/decimal values, including scientific notation", async () => {
  const c = context("/settings", "POST", settings);
  assert.equal(location(await handleAdmin(c)).searchParams.get("message"), "Nastavení bylo uloženo.");
  call(c, "PATCH", "/api/v1/settings", {
    companyName: "Firma", companyEmail: "info@example.cz", currencyCode: "CZK", timezone: "Europe/Prague",
    fiscalYearStartMonth: 10, defaultPaymentTermsDays: 0, deliveryFee: 100,
    eshopMarginPercent: 99.98, eshopRoundingUnit: 10, eshopDefaultVatRate: 21,
  });
});

for (const email of ["a@b.c", "a@[127.0.0.1]", "a@[IPv6:::1]", '"abc"@example.com', "a@foo.xn--p1ai"]) {
  test(`settings accepts PHP email syntax ${email}`, async () => {
    const c = context("/settings", "POST", { ...settings, companyEmail: email });
    assert.equal(location(await handleAdmin(c)).searchParams.get("error"), null);
    assert.equal(c.backend.calls.length, 1);
  });
}

const invalidAdmin: [string, Record<string, string>, string][] = [
  ["/companies", { ...company, action: "delete" }, "Vyplňte platné údaje společnosti."],
  ["/companies", { ...company, action: "update", id: "bad" }, "Vyberte platnou společnost."],
  ["/users", { ...user, action: "create", password: "short" }, "Vyplňte platné údaje uživatele."],
  ["/users", { action: "delete", id: "" }, "Vyplňte platné údaje uživatele."],
  ["/users", { ...user, action: "create", employeeId: "bad" }, "Vyplňte platné údaje uživatele."],
  ["/roles", { ...role, action: "delete" }, "Vyplňte platné údaje role."],
  ["/role-modules", { action: "save-module-permissions", [`permission_${ID}_`]: "on" }, "Vyberte platná oprávnění modulů."],
  ["/role-modules", { action: "save-module-permissions", permission_bad_module: "on" }, "Vyberte platná oprávnění modulů."],
];
for (const [path, values, message] of invalidAdmin) {
  test(`admin validation ${path} ${JSON.stringify(values)}`, async () => {
    const c = context(path, "POST", values);
    assert.equal(location(await handleAdmin(c)).searchParams.get("error"), message);
    assert.equal(c.backend.calls.length, 0);
  });
}

for (const [name, value] of Object.entries({
  companyEmail: "bad@", fiscalYearStartMonth: "13", defaultPaymentTermsDays: "-1",
  deliveryFee: "-1", eshopMarginPercent: "99.99", eshopRoundingUnit: "5", eshopDefaultVatRate: "101",
})) {
  test(`settings rejects invalid ${name}`, async () => {
    const c = context("/settings", "POST", { ...settings, [name]: value });
    assert.equal(location(await handleAdmin(c)).searchParams.get("error"), "Zkontrolujte zadané hodnoty nastavení.");
    assert.equal(c.backend.calls.length, 0);
  });
}

for (const [path, template, endpoints] of [
  ["/companies", "companies", ["/api/v1/companies"]],
  ["/users", "users", ["/api/v1/users", "/api/v1/users/employee-options", "/api/v1/roles", "/api/v1/companies"]],
  ["/roles", "roles", ["/api/v1/roles", "/api/v1/roles/matrix"]],
  ["/role-modules", "role_modules", ["/api/v1/roles", "/api/v1/roles/matrix"]],
  ["/settings", "settings", ["/api/v1/settings"]],
] as const) {
  test(`admin GET ${path} reads expected resources and selects its React view`, async () => {
    const c = context(`${path}?message=ok&error=original`);
    const result = page(await handleAdmin(c));
    assert.equal(result.view, `admin/${template}`);
    assert.equal(result.data.message, "ok");
    assert.equal(result.data.error, "original");
    assert.deepEqual(c.backend.calls.map((request) => request.path), endpoints);
  });
}

test("users GET derives initials/status/date labels and employee selection after a partial failure", async () => {
  const c = context(`/users?employeeId=${ID}`);
  c.backend.values = {
    "/api/v1/users": [
      { fullName: "Žan Émile", status: "INVITED", lastAccessAt: "2026-10-08T12:03:00+02:00" },
      { fullName: "A", status: "SUSPENDED", lastAccessAt: "bad-date" },
      { fullName: "Jan", status: "ACTIVE", lastAccessAt: null },
      { fullName: "élise žáková", lastAccessAt: null },
    ],
    "/api/v1/users/employee-options": [{ id: ID, fullName: "Jan Novák" }],
    "/api/v1/roles": new Error("offline"),
  };
  const result = page(await handleAdmin(c));
  assert.deepEqual(result.data.users, [
    { fullName: "Žan Émile", status: "INVITED", lastAccessAt: "2026-10-08T12:03:00+02:00", initials: "ŽÉ", statusLabel: "Pozvánka čeká", lastAccessLabel: "8. 10. 2026 12:03" },
    { fullName: "A", status: "SUSPENDED", lastAccessAt: "bad-date", initials: "A", statusLabel: "Pozastavený", lastAccessLabel: "bad-date" },
    { fullName: "Jan", status: "ACTIVE", lastAccessAt: null, initials: "JA", statusLabel: "Aktivní", lastAccessLabel: "Nikdy" },
    { fullName: "élise žáková", lastAccessAt: null, initials: "éž", statusLabel: "Aktivní", lastAccessLabel: "Nikdy" },
  ]);
  assert.equal(result.data.requestedEmployeeName, "Jan Novák");
  assert.equal(result.data.error, "Backend pro uživatele není dostupný.");
  assert.deepEqual(result.data.companies, []);
  assert.equal(c.backend.calls.length, 3);
});

test("backend admin rejection and transport failure preserve PHP messages and log", async () => {
  const c = context("/companies", "POST", { ...company, action: "create" });
  c.backend.response = () => new Response("detail", { status: 409 });
  assert.equal(location(await handleAdmin(c)).searchParams.get("error"), "Společnost s tímto názvem již existuje.");
  c.backend.requestFailure = new Error("offline");
  assert.equal(location(await handleAdmin(c)).searchParams.get("error"), "Backend pro společnosti není dostupný.");
  assert.ok((console.error as typeof console.error & { mock: { callCount(): number } }).mock.callCount() >= 2);
});

const actions: [string, Record<string, string>, string, string, unknown, string][] = [
  ["/accounting", { ...invoice, action: "create" }, "POST", "/api/v1/accounting/invoices", invoiceBody, "Faktura byla uložena."],
  ["/accounting", { ...invoice, action: "update", id: ID, version: "3" }, "PUT", `/api/v1/accounting/invoices/${ID}`, { version: 3, invoice: invoiceBody }, "Faktura byla uložena."],
  ["/accounting", { action: "payment", id: ID, amount: "+012.3400" }, "PATCH", `/api/v1/accounting/invoices/${ID}/payment`, { amount: "+012.3400" }, "Úhrada byla uložena."],
  ["/accounting", { id: ID }, "PATCH", `/api/v1/accounting/invoices/${ID}/paid`, null, "Faktura byla označena jako uhrazená."],
  ["/crm", { name: " Lead ", customerName: " Customer ", expectedRevenue: "01.00", probability: "50", expectedCloseDate: "2026-10-08" }, "POST", "/api/v1/crm/leads",
    { name: "Lead", customerName: "Customer", expectedRevenue: "01.00", probability: 50, expectedCloseDate: "2026-10-08" }, "Příležitost byla vytvořena."],
  ["/crm", { id: ID }, "PATCH", `/api/v1/crm/leads/${ID}/won`, null, "Příležitost byla označena jako vyhraná."],
  ["/documents", { id: ID }, "PATCH", `/api/v1/documents/${ID}/approve`, null, "Dokument byl schválen."],
  ["/projects", { id: ID }, "PATCH", `/api/v1/projects/${ID}/complete`, null, "Projekt byl označen jako dokončený."],
  ["/helpdesk", { id: ID }, "PATCH", `/api/v1/helpdesk/tickets/${ID}/resolve`, null, "Požadavek byl označen jako vyřešený."],
  ["/marketing", { action: "create", name: " Name ", audience: " All ", channel: "EMAIL", ownerName: " Jan ", budget: "-001.2300", plannedStartDate: "2026-10-08" },
    "POST", "/api/v1/marketing/campaigns", { name: "Name", audience: "All", channel: "EMAIL", ownerName: "Jan", budget: "-001.2300", plannedStartDate: "2026-10-08" }, "Kampaň byla vytvořena."],
  ["/marketing", { action: "launch", id: ID }, "PATCH", `/api/v1/marketing/campaigns/${ID}/launch`, null, "Kampaň byla spuštěna."],
  ["/marketing", { action: "complete", id: ID }, "PATCH", `/api/v1/marketing/campaigns/${ID}/complete`, null, "Kampaň byla dokončena."],
  ["/website", { ...web, action: "create" }, "POST", "/api/v1/website/pages", webBody, "Stránka byla vytvořena."],
  ["/website", { ...web, action: "edit", id: ID }, "PUT", `/api/v1/website/pages/${ID}`, webBody, "Stránka byla upravena."],
  ["/website", { action: "publish", id: ID }, "PATCH", `/api/v1/website/pages/${ID}/publish`, null, "Stránka byla publikována."],
  ["/website", { action: "delete", id: ID }, "DELETE", `/api/v1/website/pages/${ID}`, null, "Stránka byla smazána."],
];
for (const [path, values, method, endpoint, body, message] of actions) {
  test(`independent ${path} ${values.action ?? "default"} action mapping`, async () => {
    const c = context(path, "POST", values);
    const target = location(await handleIndependent(c));
    call(c, method, endpoint, body);
    assert.equal(target.pathname, path);
    assert.equal(target.searchParams.get("message"), message);
    if (path === "/helpdesk") assert.equal(target.searchParams.get("v"), "20261001-2");
  });
}

for (const module of ["dashboard", "accounting", "crm", "documents", "projects", "helpdesk", "marketing", "website"]) {
  test(`independent GET ${module} overview`, async () => {
    const c = context(`/${module}?v=20261001-2&message=ok&error=action`);
    c.backend.values[`/api/v1/${module}/overview`] = { value: 1 };
    const result = page(await handleIndependent(c));
    assert.equal(result.view, `${module}/index`);
    assert.deepEqual(result.data.overview, { value: 1 });
    assert.equal(result.data.actionError, "action");
    assert.equal(result.data.message, "ok");
    assert.equal(result.data.breadcrumb, module.toUpperCase());
    assert.equal(c.backend.calls.length, module === "accounting" ? 2 : 1);
    if (module === "accounting") assert.equal(c.backend.calls[1].path, "/api/v1/companies");
  });
}

test("overview partial accounting load and website editing page derivation", async () => {
  const c = context("/accounting");
  c.backend.values = { "/api/v1/accounting/overview": { invoices: [] }, "/api/v1/companies": new Error("offline") };
  const result = page(await handleIndependent(c));
  assert.deepEqual(result.data.overview, { invoices: [] });
  assert.match(String(result.data.error), /Účetnictví/);
  const website = context(`/website?edit=${ID}`);
  website.backend.values["/api/v1/website/overview"] = { pages: [{ id: ID, title: "Page" }] };
  assert.deepEqual(page(await handleIndependent(website)).data.editingPage, { id: ID, title: "Page" });
  website.url.searchParams.set("edit", "missing");
  assert.equal(page(await handleIndependent(website)).data.error, "Stránka pro úpravu nebyla nalezena.");
});

test("helpdesk version redirect retains query and does not load overview", async () => {
  const c = context("/helpdesk?message=hello&v=old&extra=1");
  const result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.equal(result.status, 302);
  const target = location(result);
  assert.equal(target.searchParams.get("v"), "20261001-2");
  assert.equal(target.searchParams.get("message"), "hello");
  assert.equal(target.searchParams.get("extra"), "1");
  assert.equal(c.backend.calls.length, 0);
});

const invalidIndependent: [string, Record<string, string>, string][] = [
  ["/documents", { id: "bad" }, "Neplatný identifikátor záznamu."],
  ["/accounting", { ...invoice, action: "create", invoiceNumber: "😀".repeat(41) }, "Vyplňte platnou hodnotu pole invoiceNumber."],
  ["/accounting", { ...invoice, action: "create", issueDate: "2026-02-30" }, "Vyplňte platné datum v poli issueDate."],
  ["/accounting", { ...invoice, action: "create", totalAmount: "1e2" }, "Vyplňte platnou částku v poli totalAmount."],
  ["/accounting", { id: ID, action: "payment", amount: "0" }, "Částka v poli amount musí být alespoň 0.01."],
  ["/accounting", { ...invoice, action: "update", id: ID, version: "01" }, "Vyplňte platné celé číslo v poli version."],
  ["/crm", { id: "" }, "Neplatný identifikátor záznamu."],
  ["/crm", { name: "Lead", customerName: "Customer", expectedRevenue: "0", probability: "101", expectedCloseDate: "2026-10-08" }, "Vyplňte platné celé číslo v poli probability."],
  ["/marketing", { action: "create", channel: "WEB" }, "Neplatný kanál kampaně."],
  ["/website", { ...web, action: "create", contentType: "OTHER" }, "Neplatný typ stránky."],
];
for (const [path, values, message] of invalidIndependent) {
  test(`independent validation ${path} ${message}`, async () => {
    const c = context(path, "POST", values);
    assert.equal(location(await handleIndependent(c)).searchParams.get("error"), message);
    assert.equal(c.backend.calls.length, 0);
  });
}

test("independent backend failure appends stripped UTF-8 details truncated to 500 code points", async () => {
  const c = context("/projects", "POST", { id: ID });
  c.backend.response = () => new Response(`<b>${"😀".repeat(510)}</b>`, { status: 409 });
  assert.equal(location(await handleIndependent(c)).searchParams.get("error"),
    `Backend odmítl změnu (HTTP 409). Změna nebyla uložena. ${"😀".repeat(500)}`);
  c.backend.requestFailure = new Error("offline");
  assert.equal(location(await handleIndependent(c)).searchParams.get("error"), "Změnu se nepodařilo uložit. Backend není dostupný.");
});

test("CRM PUT stage uses query ID/stage and returns original JSON response", async () => {
  const c = context(`/crm?id=${UPPER_ID}&stage=WON`, "PUT");
  c.backend.response = () => new Response('{"stage":"WON"}', { status: 200 });
  const result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.equal(await result.text(), '{"stage":"WON"}');
  assert.equal(result.headers.get("Content-Type"), "application/json");
  call(c, "PATCH", `/api/v1/crm/leads/${UPPER_ID}/stage`, { stage: "WON" });
});

test("CRM stage returns original validation, backend HTTP status and unavailable JSON", async () => {
  const c = context(`/crm?id=${ID}&stage=INVALID`, "PUT");
  let result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.equal(result.status, 400);
  assert.deepEqual(await result.json(), { error: "Neplatná fáze příležitosti." });
  c.url.searchParams.set("stage", "NEW");
  c.backend.response = () => new Response("rejected", { status: 409 });
  result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.equal(result.status, 409);
  assert.deepEqual(await result.json(), { error: "Změnu fáze backend odmítl (HTTP 409)." });
  c.backend.requestFailure = new Error("offline");
  result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.equal(result.status, 503);
});

test("CRM and PDF retain successful no-content responses", async () => {
  for (const path of [`/crm?id=${ID}&stage=WON`, `/accounting?pdf=${ID}`]) {
    const c = context(path, path.startsWith("/crm") ? "PUT" : "GET");
    c.backend.response = () => new Response(null, { status: 204 });
    const result = await handleIndependent(c);
    assert.ok(result instanceof Response);
    assert.equal(result.status, 204);
    assert.equal(await result.text(), "");
  }
});

test("invoice PDF forwards binary bytes and disposition without other backend headers", async () => {
  const c = context(`/accounting?pdf=${UPPER_ID}`);
  const bytes = Uint8Array.from([37, 80, 68, 70, 0, 255]);
  c.backend.response = () => new Response(bytes, {
    status: 200, headers: { "Content-Disposition": 'attachment; filename="invoice.pdf"', "X-Backend": "private" },
  });
  const result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.deepEqual(new Uint8Array(await result.arrayBuffer()), bytes);
  assert.equal(result.headers.get("Content-Type"), "application/pdf");
  assert.equal(result.headers.get("Content-Disposition"), 'attachment; filename="invoice.pdf"');
  assert.equal(result.headers.get("X-Backend"), null);
  call(c, "GET", `/api/v1/accounting/invoices/${UPPER_ID}/pdf`);
});

test("invoice PDF invalid, missing and unavailable preserve text and status", async () => {
  const c = context("/accounting?pdf=bad");
  let result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.equal(result.status, 400);
  assert.equal(await result.text(), "Neplatné číslo faktury.");
  assert.equal(c.backend.calls.length, 0);
  c.url.searchParams.set("pdf", ID);
  c.backend.response = () => new Response("", { status: 404 });
  result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.equal(result.status, 404);
  assert.equal(await result.text(), "PDF faktury není dostupné (HTTP 404).");
  c.backend.requestFailure = new Error("offline");
  result = await handleIndependent(c);
  assert.ok(result instanceof Response);
  assert.equal(result.status, 503);
});

test("public page and visit requests are unauthenticated and preserve nested UTF-8 slug", async () => {
  const c = context("/news/%C4%8Dl%C3%A1nek");
  c.backend.response = () => new Response('{"title":"Článek","content":"Text"}');
  const result = page(await handleIndependent(c));
  assert.equal(result.view, "website/public");
  assert.equal(result.data.pageTitle, "Článek");
  assert.deepEqual(c.backend.calls, [
    { method: "GET", path: "/api/v1/website/pages/public?slug=%2Fnews%2F%C4%8Dl%C3%A1nek", body: null, authenticated: false },
    { method: "POST", path: "/api/v1/website/pages/visit?slug=%2Fnews%2F%C4%8Dl%C3%A1nek", body: null, authenticated: false },
  ]);
});

test("public page missing/offline and visit failure preserve status and original errors", async () => {
  const c = context("/news");
  c.backend.response = () => new Response("", { status: 404 });
  let result = page(await handleIndependent(c));
  assert.equal(result.status, 404);
  assert.equal(result.data.error, "Stránka nebyla nalezena.");
  assert.equal(c.backend.calls.length, 1);
  c.backend.response = () => new Response("", { status: 500 });
  result = page(await handleIndependent(c));
  assert.equal(result.status, 503);
  assert.equal(result.data.error, "Stránku nelze načíst. Backend není dostupný.");
  c.backend.response = () => c.backend.calls[c.backend.calls.length - 1].method === "GET"
    ? new Response('{"title":"News"}') : new Response("", { status: 500 });
  result = page(await handleIndependent(c));
  assert.equal(result.status, 200);
  assert.equal(result.data.pageTitle, "News");
  assert.equal(result.data.error, "Návštěvu stránky se nepodařilo zaznamenat.");
});

test("route ownership leaves other controllers and excluded public paths untouched", async () => {
  for (const path of ["/", "/users", "/apps", "/shop", "/inventory", "/assets/file.css", "/legacy.jsp"]) {
    assert.equal(await handleIndependent(context(path)), null, path);
  }
  assert.equal(await handleIndependent(context("/news", "POST")), null);
  assert.equal(await handleAdmin(context("/website")), null);
  const method = await handleIndependent(context("/dashboard", "POST"));
  assert.ok(method instanceof Response);
  assert.equal(method.status, 405);
});

test("real Backend with mock fetch preserves exact settings JSON and public auth omission", async () => {
  const fetchMock = mock.method(globalThis, "fetch", async () => new Response("{}"));
  const c: Context = context("/settings", "POST", settings);
  c.backend = new Backend(c.session, "http://backend.test");
  await handleAdmin(c);
  const [url, init] = fetchMock.mock.calls[0].arguments as unknown as [string, RequestInit];
  assert.equal(url, "http://backend.test/api/v1/settings");
  assert.equal(init.method, "PATCH");
  assert.equal(new Headers(init.headers).get("Authorization"), "Bearer test-token");
  assert.equal(JSON.parse(String(init.body)).deliveryFee, 100);
  const publicContext: Context = context("/news");
  publicContext.backend = new Backend(publicContext.session, "http://backend.test");
  await handleIndependent(publicContext);
  const [, publicInit] = fetchMock.mock.calls[1].arguments as unknown as [string, RequestInit];
  assert.equal(new Headers(publicInit.headers).has("Authorization"), false);
});
