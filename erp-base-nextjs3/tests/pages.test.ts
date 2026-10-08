import assert from "node:assert/strict";
import test from "node:test";
import type { Page, Session } from "../src/lib/context";
import { renderPage } from "../src/components/render";

const session: Session = {
  userName: "Jan Test",
  roleName: "Administrátor",
  expiresAt: Date.now() + 60_000,
  lastActivityAt: Date.now(),
  cart: {},
};

const views: [string, string][] = [
  ["auth/login", "/login"], ["home/index", "/apps"], ["dashboard/index", "/dashboard"],
  ["admin/companies", "/companies"], ["admin/users", "/users"], ["admin/roles", "/roles"],
  ["admin/role_modules", "/role-modules"], ["admin/settings", "/settings"],
  ["accounting/index", "/accounting"], ["crm/index", "/crm"], ["documents/index", "/documents"],
  ["helpdesk/index", "/helpdesk"], ["projects/index", "/projects"], ["marketing/index", "/marketing"],
  ["website/index", "/website"], ["website/public", "/public-test"], ["sales/index", "/sales"],
  ["manufacturing/index", "/manufacturing"], ["pos/index", "/pos"], ["hr/index", "/hr"],
  ["planning/index", "/planning"], ["inventory/index", "/inventory"], ["promo/index", "/promo"],
  ["ecommerce/index", "/ecommerce"], ["shop/index", "/eshop"], ["purchase/index", "/purchase"],
];

for (const [view, pathname] of views) {
  test(`React view ${view} renders a complete HTML document`, () => {
    const page: Page = { view, data: {}, status: 200 };
    const html = renderPage(page, pathname, session);
    assert.match(html, /^<!doctype html><html lang="cs">/i);
    assert.match(html, /<\/body><\/html>$/);
    assert.ok(!html.includes(".twig"), view);
    assert.ok(!html.includes("TwigException"), view);
    if (!["auth/login", "shop/index"].includes(view)) assert.match(html, /class="app-shell"/, view);
  });
}

test("authentication and ERP pages preserve their original forms and sidebar links", () => {
  const login = renderPage({ view: "auth/login", data: {}, status: 200 }, "/login", session);
  assert.match(login, /<form[^>]+action="\/login"/);
  assert.match(login, /name="username"/);
  assert.match(login, /name="password"/);
  const users = renderPage({ view: "admin/users", data: {}, status: 200 }, "/users", session);
  assert.match(users, /href="\/users"/);
  assert.match(users, /class="nav-item active"/);
  assert.match(users, /<b>Jan Test<\/b>/);
});

test("public website content renders authored HTML, CSS, and JavaScript as markup", () => {
  const content = '<style>.dnd-drop { color: red; }</style><div class="dnd-drop">Dropped content</div><script>window.dndReady = true;</script>';
  const html = renderPage({ view: "website/public", data: { page: { title: "DND", content } }, status: 200 }, "/dnd", session);
  assert.match(html, /<style>\.dnd-drop \{ color: red; \}<\/style>/);
  assert.match(html, /<div class="dnd-drop">Dropped content<\/div>/);
  assert.match(html, /<script>window\.dndReady = true;<\/script>/);
  assert.ok(!html.includes("&lt;div"));
});

test("role cards pass the saved color through to their color mark", () => {
  const html = renderPage({
    view: "admin/roles",
    data: { roles: [{ id: "role-id", name: "Logistika", initial: "L", color: "#AB12CD" }] },
    status: 200,
  }, "/roles", session);
  assert.match(html, /class="role-card" style="--card-color:#AB12CD"/);
});

test("user avatars pass the saved color through to their background", () => {
  const html = renderPage({
    view: "admin/users",
    data: { users: [{ id: "user-id", employeeId: "employee-id", fullName: "Eva Nováková",
      initials: "EN", roleName: "Logistika", companyName: "Firma", status: "ACTIVE",
      statusLabel: "Aktivní", lastAccessLabel: "Nikdy", color: "#12ABCD" }] },
    status: 200,
  }, "/users", session);
  assert.match(html, /class="table-avatar" style="--card-color:#12ABCD"/);
});

test("company cards pass the saved color through to their logo", () => {
  const html = renderPage({
    view: "admin/companies",
    data: { companies: [{ id: "company-id", name: "Firma", type: "RETAIL", currency: "CZK",
      status: "ACTIVE", color: "#12ABCD" }] },
    status: 200,
  }, "/companies", session);
  assert.match(html, /class="company-logo" style="--card-color:#12ABCD"/);
});
