import assert from "node:assert/strict";
import test from "node:test";
import { handleEcommerce } from "../src/controllers/ecommerce";
import { handleShop } from "../src/controllers/shop";
import { Backend, type Context, type Session } from "../src/lib/context";
import { newSession } from "../src/lib/session";
import { fixtureId, startFixtureBackend } from "./fixtures";

test("every eCommerce PHP action maps to the same backend endpoint and payload", async () => {
  const fixture = await startFixtureBackend();
  try {
    const cases: [string, string, string, Record<string, string>][] = [
      ["homepage", "PUT", "/catalog/homepage", { design: "CLASSIC", headline: "Title", subheadline: "Subtitle", textX: "20", textY: "30" }],
      ["category", "POST", "/catalog/categories", { name: "Food", slug: "food", parentId: "" }],
      ["updateCategory", "PUT", `/catalog/categories/${fixtureId}`, { name: "Food", slug: "food", categoryId: fixtureId, parentId: "" }],
      ["deleteCategory", "DELETE", `/catalog/categories/${fixtureId}`, { categoryId: fixtureId }],
      ["product", "POST", "/catalog/products", { sku: "SKU", name: "Product", unit: "ks", price: "12.3456789", active: "on", categoryId: fixtureId }],
      ["product", "PUT", `/catalog/products/${fixtureId}`, { productId: fixtureId, sku: "SKU", name: "Product", unit: "ks", active: "on" }],
      ["import", "POST", "/catalog/products/import", { products: '[{"sku":"import"}]' }],
      ["estimate", "POST", "/catalog/delivery-estimates", { productId: fixtureId, quantity: "2", postalCode: "12345", method: "HOME" }],
      ["toggleProduct", "PUT", `/catalog/products/${fixtureId}/active`, { productId: fixtureId, active: "on" }],
      ["addImage", "POST", `/catalog/products/${fixtureId}/images`, { productId: fixtureId, imageUrl: "/image.png", active: "on" }],
      ["activateImage", "PUT", `/catalog/products/${fixtureId}/images/${fixtureId}/active`, { productId: fixtureId, imageId: fixtureId }],
      ["deleteImage", "DELETE", `/catalog/products/${fixtureId}/images/${fixtureId}`, { productId: fixtureId, imageId: fixtureId }],
      ["removeFromCategory", "PUT", `/catalog/products/${fixtureId}/category`, { productId: fixtureId }],
      ["deleteProduct", "DELETE", `/catalog/products/${fixtureId}`, { productId: fixtureId }],
    ];
    for (const [action, method, path, values] of cases) {
      const session = { ...newSession(), token: "private-fixture-token" };
      const context = makeContext("/ecommerce", { action, ...values }, session, fixture.url);
      const previous = fixture.calls.length;
      const response = await handleEcommerce(context);
      assert.ok(response instanceof Response, action);
      assert.equal(response.status, 303, action);
      const call = fixture.calls.slice(previous).find((call) => call.method === method && call.path === `/api/v1${path}`);
      assert.ok(call, action);
      if (action === "product" && method === "POST") assert.match(JSON.stringify(call.body), /"price":"12.3456789"/);
      if (action === "product" && method === "PUT") assert.match(JSON.stringify(call.body), /"price":null/);
      if (action === "homepage") assert.match(JSON.stringify(call.body), /"textX":"20"/);
    }
  } finally { await new Promise<void>((resolve) => fixture.server.close(() => resolve())); }
});

test("all cart actions retain PHP stock limits and invalid payment preserves cart and delivery", async () => {
  const fixture = await startFixtureBackend();
  const session = newSession();
  try {
    for (const [action, quantity, expected] of [
      ["add", "2", 2], ["increase", "", 3], ["decrease", "", 2], ["set", "50", 8], ["remove", "", 0],
    ] as const) {
      const response = await handleShop(makeContext("/eshop", { action, productId: fixtureId, quantity }, session, fixture.url));
      assert.ok(response instanceof Response);
      assert.equal(response.status, 303);
      assert.equal(session.cart[fixtureId] || 0, expected, action);
    }
    session.cart[fixtureId] = 2;
    session.delivery = { firstName: "Jan", lastName: "Test", phone: "+420 123 456 789", city: "Praha", street: "Test 1", postalCode: "123 45" };
    const invalid = await handleShop(makeContext("/eshop", { action: "payment", paymentMethod: "card",
      cardNumber1: "bad", cardExpiry: "99/99", cardCvc: "bad" }, session, fixture.url));
    assert.ok(invalid instanceof Response);
    assert.match(decodeURIComponent(invalid.headers.get("location") || "").replaceAll("+", " "), /Zkontrolujte číslo karty/);
    assert.equal(session.cart[fixtureId], 2);
    assert.ok(session.delivery);
    assert.ok(!fixture.calls.some((call) => call.path === "/api/v1/sales/orders/checkout"));
    const paid = await handleShop(makeContext("/eshop", { action: "payment", paymentMethod: "card",
      cardNumber1: "1111", cardNumber2: "2222", cardNumber3: "3333", cardNumber4: "4444",
      cardExpiry: "12/30", cardCvc: "123" }, session, fixture.url));
    assert.ok(paid instanceof Response);
    assert.equal(paid.headers.get("location"), "/eshop?order=completed");
    const checkout = fixture.calls.find((call) => call.path === "/api/v1/sales/orders/checkout");
    assert.ok(checkout);
    assert.match(JSON.stringify(checkout.body), /"paymentMethod":"CARD"/);
    assert.ok(!JSON.stringify(checkout.body).includes("11112222"));
    assert.deepEqual(session.cart, {});
    assert.equal(session.delivery, undefined);
  } finally { await new Promise<void>((resolve) => fixture.server.close(() => resolve())); }
});

function makeContext(pathname: string, values: Record<string, string>, session: Session, backendUrl: string): Context {
  const form = new FormData();
  for (const [key, value] of Object.entries(values)) form.set(key, value);
  return { request: new Request(`http://localhost${pathname}`, { method: "POST" }), url: new URL(`http://localhost${pathname}`),
    form, session, backend: new Backend(session, backendUrl), render: (template, data = {}, status = 200) => ({ template, data, status }) };
}
