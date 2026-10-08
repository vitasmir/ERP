import { once } from "node:events";
import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { cp, mkdtemp, rm } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";

export const fixtureId = "11111111-1111-1111-1111-111111111111";
export const fixtureProduct = {
  id: fixtureId, sku: "TEST-001", name: "Testovací produkt", description: "Produkt pro integrační test",
  unit: "ks", price: "10.25", purchasePrice: "8.00", vatRate: "21", eshopMarginPercent: "25",
  categoryId: fixtureId, active: true, imageUrl: null, images: [],
};
const role = { id: fixtureId, name: "Administrátor", color: "#D9ED62", initial: "A", description: "Správa ERP" };
const employee = {
  id: fixtureId, fullName: "Fixture User", teamId: fixtureId, teamName: "Tým",
  jobTitle: role.name, userRoleName: role.name, status: "ACTIVE", employmentStartDate: "2026-01-01",
  hasUserAccount: true, deputyEmployeeId: null, deputyName: null,
};

export type BackendCall = { method: string; path: string; body: unknown; authorization?: string };

export async function startFixtureBackend() {
  const calls: BackendCall[] = [];
  const state = { forbidden: "", failLogin: false };
  const server = createServer(async (request, response) => {
    let input = "";
    for await (const chunk of request) input += chunk;
    const body: unknown = input ? JSON.parse(input) : undefined;
    const url = new URL(request.url || "/", "http://fixture-backend");
    calls.push({ method: request.method || "GET", path: `${url.pathname}${url.search}`, body, authorization: request.headers.authorization });
    response.setHeader("Content-Type", "application/json");
    const json = (data: unknown, status = 200) => { response.statusCode = status; response.end(JSON.stringify(data)); };
    const pathname = url.pathname.replace(/^\/api\/v1\//, "");
    if (pathname === "auth/login") {
      if (state.failLogin) { json({ error: "Unavailable" }, 503); return; }
      if (input !== JSON.stringify({ username: "fixture", password: "fixture-password" })) { json({ error: "Invalid login" }, 401); return; }
      json({ token: "private-fixture-token", fullName: "Fixture User", roleName: role.name }); return;
    }
    const publicPath = /^catalog\/(categories\/tree|products(?:\/[^/]+\/availability)?)$/.test(pathname)
      || pathname === "settings/public" || pathname.startsWith("website/pages/public")
      || pathname.startsWith("website/pages/visit") || pathname === "sales/orders/checkout";
    if (!publicPath && request.headers.authorization !== "Bearer private-fixture-token") {
      json({ error: "Unauthorized" }, 401); return;
    }
    if (state.forbidden && pathname === state.forbidden) { json({ error: "Forbidden" }, 403); return; }
    if (pathname === "auth/me") {
      json({ administrator: true, modules: [], fullName: "Fixture User" }); return;
    }
    if (pathname === "auth/logout" || pathname === "website/pages/visit") { json({}); return; }
    if (pathname === "website/pages/public") {
      if (url.searchParams.get("slug") !== "/public-test") { json({}, 404); return; }
      json({ title: "Public fixture", content: "<script>unsafe</script>" }); return;
    }
    if (pathname === `accounting/invoices/${fixtureId}/pdf`) {
      response.setHeader("Content-Type", "application/pdf");
      response.setHeader("Content-Disposition", "attachment; filename=fixture.pdf");
      response.end("%PDF-fixture"); return;
    }
    if (request.method === "POST" && pathname === "sales/orders/checkout") { json({ id: fixtureId }, 201); return; }
    if (request.method !== "GET") { json({ id: fixtureId, categoryId: fixtureId }, request.method === "POST" ? 201 : 200); return; }
    switch (pathname) {
      case "roles": json([role]); return;
      case "roles/matrix":
        json({ modules: [{ key: "hr", name: "Lidé" }, { key: "planning", name: "Plánování" }], permissions: [{ roleId: fixtureId, moduleKey: "hr" }] }); return;
      case "users":
        json([{ id: fixtureId, employeeId: fixtureId, fullName: "Fixture User", username: "fixture", roleName: role.name,
          status: "ACTIVE", companyName: "Test Company", lastAccessAt: null, color: "#D9ED62" }]); return;
      case "users/employee-options": json([employee]); return;
      case "companies": json([{ id: fixtureId, name: "Test Company", type: "RETAIL", currency: "CZK", status: "ACTIVE", color: "#D9ED62" }]); return;
      case "settings":
        json({ deliveryFee: 100, eshopMarginPercent: 25, eshopDefaultVatRate: 21 }); return;
      case "settings/public": json({ deliveryFee: 100 }); return;
      case "catalog/categories/tree":
        json([{ id: fixtureId, name: "Potraviny", slug: "potraviny", active: true, sortOrder: 10, children: [] }]); return;
      case "catalog/products": json([fixtureProduct]); return;
      case `catalog/products/${fixtureId}/availability`: json([{ warehouseId: fixtureId, warehouseName: "Sklad", quantity: 8 }]); return;
      case "catalog/homepage": json({ design: "CLASSIC", headline: "All Market", subheadline: "Lokální obchod", textX: 20, textY: 30 }); return;
      case "purchase/warehouses": json([{ id: fixtureId, name: "Sklad", ownerType: "COMPANY" }]); return;
      case "purchase/overview":
        json({
          requestedValue: 216400, orderedValue: 48700, requestedCount: 1,
          orders: ["REQUESTED", "ORDERED", "RECEIVED"].map((status, index) => ({
            id: fixtureId, orderNumber: `PO-2026-${index + 1}`, status,
            supplierName: "Testovací dodavatel s delším názvem",
            sourceWarehouseId: fixtureId, destinationWarehouseId: fixtureId,
            requestedOn: "2026-10-08", expectedDeliveryDate: "2026-10-28",
            quantity: 10, receivedQuantity: status === "RECEIVED" ? 10 : 0,
            totalAmount: index === 0 ? 216400 : 48700,
            lines: [{ productId: fixtureId, quantity: 10, unitPrice: 4870 }],
          })),
        }); return;
      case "planning/employees": json([employee]); return;
      case "planning/roles": json(["Administrátor", "Skladník"]); return;
      case "planning/workplaces": json([{ id: fixtureId, name: "Prodejna" }]); return;
      case "planning/notifications": json([]); return;
      case `planning/shifts/${fixtureId}/events`: json([]); return;
      case `hr/employees/${fixtureId}/availability`: json({ qualifications: ["Skladník"], absences: [] }); return;
      case "inventory/movements": json({ items: [], page: Number(url.searchParams.get("page") || 0), size: 20, totalElements: 0 }); return;
      case "promo-campaigns": json([]); return;
      case "promo-campaigns/options": json({ products: [fixtureProduct], suppliers: [] }); return;
      default:
        if (pathname.endsWith("/overview")) {
          json({
            employees: pathname === "hr/overview" ? [employee] : [], teams: [{ id: fixtureId, name: "Tým" }],
            orders: [], shifts: [], notifications: [], transactions: [], invoices: [], leads: [], documents: [],
            projects: [], tickets: [], campaigns: [], pages: [], recentInvoices: [], topLeads: [], recentCampaigns: [],
            products: [fixtureProduct], items: [], canEdit: true,
            receivables: 0, payables: 0, revenueThisMonth: 0, stockValue: 0,
          }); return;
        }
        json({ error: `Missing fixture: ${pathname}` }, 404);
    }
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("Backend fixture did not bind.");
  return { server, calls, state, url: `http://127.0.0.1:${address.port}` };
}

export async function startFixtureApplication(port?: number, standalone = false) {
  const backend = await startFixtureBackend();
  if (port === undefined) {
    const probe = createServer();
    probe.listen(0, "127.0.0.1");
    await once(probe, "listening");
    const address = probe.address();
    if (!address || typeof address === "string") throw new Error("Port probe did not bind.");
    port = address.port;
    await new Promise<void>((resolve) => probe.close(() => resolve()));
  }
  const sessionDirectory = await mkdtemp(path.join(tmpdir(), "erp-nextjs2-test-"));
  const runtimeDirectory = standalone ? await mkdtemp(path.join(tmpdir(), "erp-nextjs2-standalone-")) : undefined;
  const closeResources = async () => {
    await new Promise<void>((resolve) => backend.server.close(() => resolve()));
    await rm(sessionDirectory, { recursive: true });
    if (runtimeDirectory) await rm(runtimeDirectory, { recursive: true });
  };
  try {
    if (runtimeDirectory) {
      await cp(".next/standalone", runtimeDirectory, { recursive: true });
      await cp(".next/static", path.join(runtimeDirectory, ".next/static"), { recursive: true });
      await cp("public", path.join(runtimeDirectory, "public"), { recursive: true });
      await cp("templates", path.join(runtimeDirectory, "templates"), { recursive: true });
    }
  } catch (error) {
    await closeResources();
    throw error;
  }
  const args = runtimeDirectory ? ["server.js"] :
    ["node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", String(port)];
  const server = spawn(process.execPath, args, {
    cwd: runtimeDirectory,
    env: { ...process.env, BACKEND_URL: backend.url, SESSION_DIR: sessionDirectory, NEXT_TELEMETRY_DISABLED: "1",
      HOSTNAME: "127.0.0.1", PORT: String(port) },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let output = "";
  server.stdout.on("data", (chunk: Buffer) => { output += chunk.toString(); });
  server.stderr.on("data", (chunk: Buffer) => { output += chunk.toString(); });
  const url = `http://127.0.0.1:${port}`;
  const close = async () => {
    if (server.exitCode === null) { server.kill("SIGTERM"); await once(server, "exit"); }
    await closeResources();
  };
  try {
    let ready = false;
    for (let attempt = 0; attempt < 100; attempt++) {
      try { ready = (await fetch(`${url}/login`)).status === 200; } catch { /* Wait for this child server to bind. */ }
      if (ready) break;
      if (server.exitCode !== null) throw new Error(output);
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    if (!ready) throw new Error(`Frontend fixture did not start: ${output}`);
    return { ...backend, url, sessionDirectory, output: () => output, close };
  } catch (error) { await close(); throw error; }
}
