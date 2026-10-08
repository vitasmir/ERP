import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { existsSync } from "node:fs";
import test from "node:test";
import { fixtureId, startFixtureApplication } from "./fixtures";

test("Next.js 16.3 standalone server preserves all PHP routes, assets, login, forms, cart, PDF and logout", {
  skip: !existsSync(".next/BUILD_ID"), timeout: 60_000,
}, async () => {
  const app = await startFixtureApplication(undefined, true);
  try {
    const post = (pathname: string, values: Record<string, string>, cookie = "") => fetch(`${app.url}${pathname}`, {
      method: "POST", headers: { Origin: app.url, Cookie: cookie }, body: new URLSearchParams(values), redirect: "manual",
    });
    const protectedPage = await fetch(`${app.url}/apps`, { redirect: "manual" });
    assert.equal(protectedPage.status, 302);
    assert.equal(protectedPage.headers.get("location"), "/login");
    const crossOrigin = await fetch(`${app.url}/login`, { method: "POST",
      headers: { Origin: "http://evil.invalid" }, body: new URLSearchParams({ username: "fixture", password: "fixture-password" }) });
    assert.equal(crossOrigin.status, 403);
    const crossOriginStage = await fetch(`${app.url}/crm?id=${fixtureId}&stage=QUALIFIED`, {
      method: "PUT", headers: { Origin: "http://evil.invalid" },
    });
    assert.equal(crossOriginStage.status, 403);
    const empty = await post("/login", {});
    assert.equal(empty.status, 400);
    assert.match(await empty.text(), /Zadejte uživatelské jméno a heslo/);
    assert.equal((await post("/login", { username: "fixture", password: "bad" })).status, 401);
    const login = await post("/login", { username: "fixture", password: "fixture-password" });
    assert.equal(login.status, 303);
    const cookie = login.headers.get("set-cookie")?.split(";")[0];
    assert.ok(cookie);
    assert.match(login.headers.get("set-cookie") || "", /HttpOnly; SameSite=Lax/);
    assert.ok(!cookie.includes("private-fixture-token"));
    assert.equal(login.headers.get("location"), "/apps");
    for (const module of [
      "apps", "companies", "users", "roles", "role-modules", "settings", "dashboard", "accounting", "crm",
      "documents", "projects", "helpdesk", "marketing", "website", "hr", "planning", "inventory", "promo",
      "sales", "purchase", "manufacturing", "pos", "ecommerce",
    ]) {
      const response: Response = await fetch(`${app.url}/${module}`, { headers: { Cookie: cookie } });
      const html = await response.text();
      assert.equal(response.status, 200, `${module}: ${html}`);
      assert.match(html, /<div class="app-shell">/, module);
      assert.ok(!html.includes("private-fixture-token"), module);
      assert.ok(!html.includes('role="alert"'), `${module}: unexpected visible error: ${html}`);
      assert.match(response.headers.get("cache-control") || "", /no-store/);
      const assets = [...html.matchAll(/(?:href|src)="(\/assets\/[^"]+)"/g)];
      for (const [, asset] of assets) {
        const assetResponse = await fetch(`${app.url}${asset}`);
        assert.equal(assetResponse.status, 200, `${module}: ${asset}`);
        assert.ok((await assetResponse.text()).length > 100, asset);
      }
    }
    for (const pathname of [`/hr?employeeId=${fixtureId}`, `/planning?audit=${fixtureId}`, "/inventory?view=history"]) {
      const response: Response = await fetch(`${app.url}${pathname}`, { headers: { Cookie: cookie } });
      assert.equal(response.status, 200);
      assert.ok(!(await response.text()).includes('role="alert"'), pathname);
    }
    app.state.forbidden = "crm/overview";
    const denied = await fetch(`${app.url}/crm`, { headers: { Cookie: cookie }, redirect: "manual" });
    assert.equal(denied.status, 303);
    assert.match(decodeURIComponent(denied.headers.get("location") || "").replaceAll("+", " "), /nemá oprávnění k modulu CRM/);
    app.state.forbidden = "";
    const movedLead = await fetch(`${app.url}/crm?id=${fixtureId}&stage=QUALIFIED`, {
      method: "PUT", headers: { Origin: app.url, Cookie: cookie },
    });
    assert.equal(movedLead.status, 200);
    assert.ok(app.calls.some((call) => call.method === "PATCH" && call.path === `/api/v1/crm/leads/${fixtureId}/stage`
      && JSON.stringify(call.body) === JSON.stringify({ stage: "QUALIFIED" })));
    const form = await post("/companies", { action: "create", name: "New company", type: "RETAIL",
      currency: "CZK", status: "ACTIVE", color: "#D9ED62" }, cookie);
    assert.equal(form.status, 303);
    assert.ok(app.calls.some((call) => call.path === "/api/v1/companies" && call.method === "POST"
      && JSON.stringify(call.body).includes("New company")));
    const pdf = await fetch(`${app.url}/accounting?pdf=${fixtureId}`, { headers: { Cookie: cookie } });
    assert.equal(pdf.headers.get("content-type"), "application/pdf");
    assert.equal(await pdf.text(), "%PDF-fixture");
    const head = await fetch(`${app.url}/companies`, { method: "HEAD", headers: { Cookie: cookie } });
    assert.equal(head.status, 200);
    assert.equal(head.headers.get("content-type"), "text/html; charset=utf-8");
    assert.equal(await head.text(), "");
    const publicPage = await fetch(`${app.url}/public-test`);
    assert.equal(publicPage.status, 200);
    const publicHtml = await publicPage.text();
    assert.match(publicHtml, /Public fixture/);
    assert.match(publicHtml, /<style>\.dnd-drop \{ color: red; \}<\/style>/);
    assert.match(publicHtml, /<div class="dnd-drop">Dropped content<\/div>/);
    assert.match(publicHtml, /<script>window\.dndReady = true;<\/script>/);
    assert.equal((await fetch(`${app.url}/missing-page`)).status, 404);
    const shop = await fetch(`${app.url}/eshop`);
    const guestCookie = shop.headers.get("set-cookie")?.split(";")[0];
    assert.ok(guestCookie);
    assert.match(await shop.text(), /Testovací produkt/);
    const added = await post("/eshop", { action: "add", productId: fixtureId, quantity: "2" }, guestCookie);
    assert.equal(added.status, 303);
    assert.match(await (await fetch(`${app.url}/eshop`, { headers: { Cookie: guestCookie } })).text(), /20\.50 Kč/);
    const delivery = await post("/eshop", { action: "delivery", firstName: "Jan", lastName: "Test",
      phone: "+420 123 456 789", street: "Test 1", city: "Praha", postalCode: "123 45" }, guestCookie);
    assert.equal(delivery.headers.get("location"), "/eshop?checkout=payment#payment-step");
    const payment = await post("/eshop", { action: "payment", paymentMethod: "cod" }, guestCookie);
    assert.equal(payment.headers.get("location"), "/eshop?order=completed");
    const checkout = app.calls.find((call) => call.path === "/api/v1/sales/orders/checkout");
    assert.ok(checkout);
    assert.equal(checkout.authorization, undefined);
    assert.match(JSON.stringify(checkout.body), /"paymentMethod":"CASH"/);
    assert.match(JSON.stringify(checkout.body), /"quantity":2/);
    const cartCleared = await fetch(`${app.url}/eshop`, { headers: { Cookie: guestCookie } });
    assert.match(await cartCleared.text(), /Košík čeká na svůj první nákup/);
    const relogin = await post("/login", { username: "fixture", password: "fixture-password" }, cookie);
    const rotated = relogin.headers.get("set-cookie")?.split(";")[0];
    assert.ok(rotated && rotated !== cookie);
    assert.equal((await fetch(`${app.url}/apps`, { headers: { Cookie: cookie }, redirect: "manual" })).status, 302);
    const logout = await post("/logout", {}, rotated);
    assert.equal(logout.headers.get("location"), "/login");
    assert.equal((await fetch(`${app.url}/apps`, { headers: { Cookie: rotated }, redirect: "manual" })).status, 302);
    for (const filename of await readdir(app.sessionDirectory)) {
      const stored = await readFile(`${app.sessionDirectory}/${filename}`, "utf8");
      assert.ok(!stored.includes("fixture-password"));
    }
    assert.ok(!app.output().includes("Frontend request failed"), app.output());
  } finally { await app.close(); }
});
