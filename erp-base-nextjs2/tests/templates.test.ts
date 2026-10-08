import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import test from "node:test";
import { Backend, type Context } from "../src/lib/context";
import { appCatalog } from "../src/lib/apps";
import { newSession } from "../src/lib/session";
import { renderPage } from "../src/lib/templates";

const id = "11111111-1111-1111-1111-111111111111";
const category = { id, name: "Potraviny", children: [], sortOrder: 1, active: true };
const product = { id, name: "Produkt", sku: "SKU-1", unit: "ks", price: 10.25, purchasePrice: 8,
  availableQuantity: 5, availability: [{ quantity: 5, warehouseName: "Sklad" }], categoryId: id, active: true, images: [] };
const role = { id, name: "Administrátor", color: "#D9ED62", initial: "A", description: "Správa" };
const employee = { id, fullName: "Jan Test", teamId: id, teamName: "Tým", jobTitle: role.name, userRoleName: role.name,
  employmentStartDate: "2026-01-01", status: "ACTIVE", hasUserAccount: false, deputyEmployeeId: null };
const warehouse = { id, name: "Sklad", ownerType: "COMPANY", locations: [] };
const data = {
  error: null, actionError: null, message: null, page: { title: "Veřejná stránka", content: "<script>unsafe</script>" },
  allowedModules: appCatalog.map((app) => app.id), moduleCatalog: Object.fromEntries(appCatalog.map((app) => [app.id, app])),
  companies: [], users: [], employees: [employee], roles: [role], roleOptions: [role], products: [product],
  warehouses: [warehouse], locations: [], workplaceOptions: [], workplaces: [], settings: {}, edit: true, admin: true,
  matrix: { modules: [{ key: "hr", name: "Lidé" }], permissions: [{ roleId: id, moduleKey: "hr" }] },
  overview: { employees: [employee], teams: [{ id, name: "Tým" }], orders: [], shifts: [], invoices: [], leads: [],
    documents: [], campaigns: [], projects: [], tickets: [], transactions: [], items: [] },
  availability: null, selectedEmployeeId: null, selectedCategoryId: null, categoryOptions: [{ ...category, depth: 0, productCount: 1 }],
  movements: null, selectedItemId: null, historyFilter: "", view: "stock", campaigns: [], stores: [],
  ecommerce: { homepage: { headline: "Obchod", design: "CLASSIC", textX: 20, textY: 30 }, categories: [category], products: [product] },
  shop: { categories: [category], categoryOptions: [{ ...category, depth: 0, productCount: 1 }], products: [product],
    cart: [{ product, quantity: 2, lineTotal: "20.50" }], cartCount: 2, cartTotal: "20.50", totalProductCount: 1,
    selectedCategoryId: null, checkoutOpen: true, paymentOpen: false, deliveryFee: 100 },
};

function context(pathname: string): Context {
  const session = { ...newSession(), userName: "Jan Test", roleName: "Administrátor" };
  return { request: new Request(`http://localhost${pathname}`), url: new URL(`http://localhost${pathname}`),
    form: new FormData(), session, backend: new Backend(session), render: (template, data = {}, status = 200) => ({ template, data, status }) };
}

test("every full PHP template renders as HTML using Node.js, including nonempty HR/category rows", async () => {
  const files = (await readdir("templates", { recursive: true }))
    .filter((name) => name.endsWith(".twig") && !path.basename(name).startsWith("_") && name !== "base.html.twig");
  for (const template of files) {
    const html = await renderPage({ template, data, status: 200 }, context("/apps"));
    assert.match(html, /<!doctype html>/i, template);
    assert.match(html, /<html lang="cs">/, template);
    assert.ok(!html.includes("TwigException"), template);
  }
});

test("launcher retains the original sidebar, tiles, drawer, assets and selected navigation", async () => {
  const html = await renderPage({ template: "home/index.html.twig", data, status: 200 }, context("/apps"));
  assert.match(html, /class="nav-item active"/);
  assert.match(html, /Vše, co vaše firma potřebuje\./);
  assert.match(html, /class="module-tile" data-module="base"/);
  assert.match(html, /id="module-drawer"/);
  assert.match(html, /id="module-search"/);
  assert.match(html, /<b>Jan Test<\/b>/);
  assert.match(html, /src="\/assets\/base\.js\?v=20261008-2"/);
  assert.match(html, /href="\/compatibility\.css\?v=4"/);
});

test("PHP assets remain byte-identical except for the shared calendar dialog fix", async () => {
  const source = path.resolve("../erp-base-php/public/assets");
  for (const filename of await readdir(source)) {
    if (filename === "base.js") continue;
    assert.deepEqual(await readFile(path.join("public/assets", filename)), await readFile(path.join(source, filename)), filename);
  }
});

test("the shared calendar uses the native modal layer with a viewport-sized transparent shell", async () => {
  const script = await readFile("public/assets/base.js", "utf8");
  const styles = await readFile("public/compatibility.css", "utf8");
  assert.match(script, /const datePickerModal = document\.createElement\("dialog"\)/);
  assert.match(script, /datePickerModal\.showModal\(\)/);
  assert.match(script, /datePickerModal\.close\(\)/);
  assert.match(script, /datePickerModal\.addEventListener\("cancel", \(event\) => \{\s*event\.preventDefault\(\);\s*closeDatePicker\(\);/);
  assert.match(script, /event\.key === "Escape" && datePickerModal\.open\) \{\s*event\.preventDefault\(\);\s*closeDatePicker\(\);/);
  assert.match(styles, /dialog\.date-picker-modal \{[^}]*width: 100%;[^}]*height: 100%;[^}]*margin: 0;[^}]*padding: 0;[^}]*border: 0;[^}]*background: transparent;/);
});

test("workspace styling fills the available width without double module padding or sidebar changes", async () => {
  const styles = await readFile("public/compatibility.css", "utf8");
  assert.match(styles, /\.app-shell > \.main \{[^}]*flex: 1;[^}]*min-width: 0;[^}]*width: auto;[^}]*padding-inline: 24px;/);
  assert.match(styles, /\.app-shell > \.main > :is\(main, div\)\[class\*="-page"\] \{\s*padding-inline: 0;/);
  assert.match(styles, /@media \(max-width: 620px\) \{\s*\.app-shell > \.main \{\s*padding-inline: 15px;/);
  assert.ok(!styles.includes(".sidebar"));
});

test("purchase table adapts to narrower workspaces without hiding data or action buttons", async () => {
  const styles = await readFile("public/compatibility.css", "utf8");
  assert.match(styles, /\.purchase-page \.order-table \{\s*min-width: 880px;/);
  assert.match(styles, /\.purchase-page \.order-table th,\s*\.purchase-page \.order-table td \{\s*padding-inline: 10px;/);
  assert.match(styles, /\.purchase-page \.order-table \.secondary \{\s*max-width: 100%;\s*white-space: normal;/);
  assert.match(styles, /\.purchase-page \.order-table \.order-detail-line \{\s*grid-template-columns: repeat\(auto-fit, minmax\(76px, 1fr\)\);/);
  for (const [column, width] of [[2, 16], [3, 18], [7, 16]]) {
    assert.match(styles, new RegExp(`\\.purchase-page \\.order-table th:nth-child\\(${column}\\) \\{\\s*width: ${width}%;`));
  }
  assert.match(styles, /\.purchase-page \.order-table th:nth-child\(4\),\s*\.purchase-page \.order-table th:nth-child\(5\) \{\s*width: 10%;/);
  assert.ok(!styles.includes("overflow-x: hidden"));
});

test("public content is escaped and script JSON cannot close its script element", async () => {
  const html = await renderPage({ template: "website/public.html.twig", data, status: 200 }, context("/public-test"));
  assert.ok(!html.includes("<script>unsafe</script>"));
  const users = await renderPage({
    template: "admin/users.html.twig", status: 200,
    data: { ...data, employees: [{ ...employee, jobTitle: "</script><script>unsafe</script>" }] },
  }, context("/users"));
  assert.ok(!users.includes("</script><script>unsafe</script>"));
  assert.match(users, /\\u003c\/script/);
});
