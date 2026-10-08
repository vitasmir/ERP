import assert from "node:assert/strict";
import { test } from "node:test";
import { Backend, type Context, type Page, type Row } from "../src/lib/context";
import { handleOperations } from "../src/controllers/operations";
import { handleInventory } from "../src/controllers/inventory";
import { handlePromo } from "../src/controllers/promo";

const id = "abcdef12-3456-7890-abcd-ef1234567890";
const upperId = id.toUpperCase();
type Call = [string, string, unknown];

function fixture(path: string, input?: Record<string, string | string[]>, replies: Record<string, unknown> = {}) {
  const calls: Call[] = [];
  const form = new FormData();
  for (const [key, value] of Object.entries(input ?? {})) {
    if (Array.isArray(value)) for (const entry of value) form.append(`${key}[]`, entry);
    else form.append(key, value);
  }
  const backend = new Backend({ expiresAt: 0, lastActivityAt: 0, cart: {} });
  const response = (value: unknown): Response => value instanceof Response ? value : Response.json(value ?? {});
  const reply = (method: string, path: string, body?: unknown) => {
    calls.push([method, path, body]);
    const value = replies[path];
    if (value instanceof Error) throw value;
    return value;
  };
  backend.mutate = async (method, path, body) => response(reply(method, path, body));
  backend.request = async (method, path, body) => response(reply(method, path, body));
  backend.json = async (method, path, body) => reply(method, path, body) ?? {};
  const context: Context = {
    request: new Request(`http://localhost${path}`, { method: input ? "POST" : "GET" }),
    url: new URL(`http://localhost${path}`), form,
    session: { expiresAt: 0, lastActivityAt: 0, cart: {} }, backend,
    render: (template, data = {}, status = 200) => ({ template, data, status }),
  };
  return { context, calls };
}

function page(value: Page | Response | null): Page {
  assert.ok(value && !(value instanceof Response));
  return value;
}

function location(value: Page | Response | null): URL {
  assert.ok(value instanceof Response);
  return new URL(value.headers.get("Location")!, "http://localhost");
}

const salesBody = {
  orderNumber: "O-1", customerName: "Buyer", orderDate: "not-validated-in-PHP",
  deliveryDate: "2026-11-01", totalAmount: "123456789.123456789",
};
const purchaseInput = {
  supplierName: "Supplier", requestedOn: "2026-10-08", expectedDeliveryDate: "2026-10-09",
  sourceWarehouseId: upperId, destinationWarehouseId: id,
  productId: [upperId, id], quantity: ["+0002", "3"], unitPrice: ["1.234567890", "+.2e2"],
};
const purchaseBody = {
  supplierName: "Supplier", requestedOn: "2026-10-08", expectedDeliveryDate: "2026-10-09",
  sourceWarehouseId: id, destinationWarehouseId: id,
  lines: [{ productId: id, quantity: 2, unitPrice: "1.234567890" }, { productId: id, quantity: 3, unitPrice: "+.2e2" }],
};
const shiftInput = { roleName: "Worker", department: "A", startAt: "start", endAt: "end", employeeId: "" };
const shiftBody = { ...shiftInput, employeeId: null };
const employeeInput = { fullName: "Test", jobTitle: "Worker", employmentStartDate: "2026-10-08", teamId: upperId };
const employeeBody = { ...employeeInput, teamId: id, teamName: "" };

const mappings: [string, Record<string, string | string[]>, string, string, unknown][] = [
  ["sales", { action: "create", ...salesBody }, "POST", "/sales/orders", salesBody],
  ["sales", { action: "update", id: upperId, ...salesBody }, "PUT", `/sales/orders/${id}`, salesBody],
  ["sales", { action: "delete", id: upperId }, "DELETE", `/sales/orders/${id}`, null],
  ["sales", { id: upperId }, "PATCH", `/sales/orders/${id}/confirm`, null],
  ["purchase", { action: "create", ...purchaseInput }, "POST", "/purchase/orders", purchaseBody],
  ["purchase", { action: "update", id: upperId, ...purchaseInput }, "PUT", `/purchase/orders/${id}`, purchaseBody],
  ["purchase", { id: upperId }, "PATCH", `/purchase/orders/${id}/order`, null],
  ["purchase", { action: "receive", id, quantity: "2" }, "PATCH", `/purchase/orders/${id}/receive`, { quantity: 2 }],
  ["purchase", { action: "receive", id }, "PATCH", `/purchase/orders/${id}/receive`, { quantity: null }],
  ["manufacturing", { id: upperId }, "PATCH", `/manufacturing/orders/${id}/complete`, null],
  ["manufacturing", { action: "update", id, completedQuantity: "0" }, "PATCH", `/manufacturing/orders/${id}/progress`, { completedQuantity: 0 }],
  ...["CARD", "CASH", "VOUCHER"].map((method): typeof mappings[number] => [
    "pos", { id: upperId, method }, "PATCH", `/pos/transactions/${id}/pay`, { method },
  ]),
  ["planning", { action: "create", ...shiftInput }, "POST", "/planning/shifts", shiftBody],
  ["planning", { action: "update", id, version: "0", ...shiftInput, employeeId: upperId }, "PUT", `/planning/shifts/${id}`, { ...shiftBody, employeeId: id, version: 0 }],
  ["planning", { action: "publishPlan", shiftIds: [upperId, id] }, "POST", "/planning/publish", { shiftIds: [id, id] }],
  ["planning", { action: "workplace", name: "Desk", capacity: "2" }, "POST", "/planning/workplaces", { name: "Desk", capacity: 2 }],
  ["planning", { action: "read", id }, "PATCH", `/planning/notifications/${id}/read`, null],
  ["planning", { action: "delete", id }, "DELETE", `/planning/shifts/${id}`, null],
  ["planning", { action: "publish", id }, "PATCH", `/planning/shifts/${id}/publish`, null],
  ["hr", { action: "create", ...employeeInput }, "POST", "/hr/employees", employeeBody],
  ["hr", { action: "update", id: upperId, ...employeeInput, deputyEmployeeId: upperId }, "PUT", `/hr/employees/${id}`, { ...employeeBody, deputyEmployeeId: id }],
  ["hr", { action: "createTeam", name: "Team" }, "POST", "/hr/teams", { name: "Team" }],
  ["hr", { action: "updateTeam", teamId: upperId, name: "Team" }, "PUT", `/hr/teams/${id}`, { name: "Team" }],
  ["hr", { action: "deleteTeam", teamId: upperId }, "DELETE", `/hr/teams/${id}`, null],
  ["hr", { action: "absence", id, startAt: "start", endAt: "end", reason: "Leave" }, "POST", `/hr/employees/${id}/absences`, { startAt: "start", endAt: "end", reason: "Leave" }],
  ["hr", { action: "qualification", id, roleName: "Worker" }, "POST", `/hr/employees/${id}/qualifications`, { roleName: "Worker" }],
  ["hr", { action: "removeAbsence", id, absenceId: upperId }, "DELETE", `/hr/employees/${id}/absences/${id}`, null],
  ["hr", { action: "activate", id }, "PATCH", `/hr/employees/${id}/activate`, null],
  ["hr", { action: "deactivate", id }, "PATCH", `/hr/employees/${id}/deactivate`, null],
];

for (const [module, input, method, path, body] of mappings) {
  test(`${module}: ${input.action ?? input.method ?? "default"} request mapping`, async () => {
    const { context, calls } = fixture(`/${module}`, input);
    const target = location(await handleOperations(context));
    assert.deepEqual(calls, [[method, `/api/v1${path}`, body]]);
    assert.equal(target.pathname, `/${module}`);
    assert.ok(target.searchParams.has("message"));
    assert.equal(target.searchParams.get("employeeId"), module === "hr" && "id" in input ? id : null);
  });
}

test("operations guards reject bad identifiers, dates, quantities, arrays and actions before mutation", async () => {
  for (const [module, input] of [
    ["sales", { id: "invalid" }],
    ["purchase", { action: "create", ...purchaseInput, requestedOn: "2026-02-30" }],
    ["purchase", { action: "create", ...purchaseInput, quantity: ["1"] }],
    ["purchase", { action: "create", ...purchaseInput, productId: id }],
    ["purchase", { action: "receive", id, quantity: "" }],
    ["manufacturing", { action: "update", id, completedQuantity: "-1" }],
    ["pos", { id, method: "TRANSFER" }],
    ["planning", { action: "publishPlan" }],
    ["planning", { action: "update", id, ...shiftInput, version: "-1" }],
    ["hr", { action: "create", ...employeeInput, teamId: "" }],
    ["hr", { action: "update", id, ...employeeInput, deputyEmployeeId: "bad" }],
    ["planning", { action: "not-an-action", id }],
  ] as [string, Record<string, string | string[]>][]) {
    const { context, calls } = fixture(`/${module}`, input);
    assert.ok(location(await handleOperations(context)).searchParams.has("error"));
    assert.deepEqual(calls, []);
  }
});

test("operations preserve PHP 64-bit integer JSON tokens and reject PHP integer overflow", async () => {
  const { context, calls } = fixture("/manufacturing", { action: "update", id, completedQuantity: "+009223372036854775807" });
  assert.ok(location(await handleOperations(context)).searchParams.has("message"));
  assert.equal(JSON.stringify(calls[0][2]), '{"completedQuantity":9223372036854775807}');
  const invalid = fixture("/manufacturing", { action: "update", id, completedQuantity: "9223372036854775808" });
  assert.ok(location(await handleOperations(invalid.context)).searchParams.has("error"));
  assert.deepEqual(invalid.calls, []);
});

test("planning supports PHP indexed arrays, overwrites repeated keys and rejects nested arrays", async () => {
  const { context, calls } = fixture("/planning", { action: "publishPlan" });
  context.form.append("shiftIds[4]", "bad-overwritten-value");
  context.form.append("shiftIds[4]", upperId);
  assert.ok(location(await handleOperations(context)).searchParams.has("message"));
  assert.deepEqual(calls, [["POST", "/api/v1/planning/publish", { shiftIds: [id] }]]);
  const nested = fixture("/planning", { action: "publishPlan" });
  nested.context.form.append("shiftIds[0][nested]", id);
  assert.ok(location(await handleOperations(nested.context)).searchParams.has("error"));
  assert.deepEqual(nested.calls, []);
});

test("module GET loads preserve backend data and planning audit collections", async () => {
  for (const module of ["sales", "purchase", "manufacturing", "pos", "planning", "hr"]) {
    const overview = { employees: [], marker: module };
    const { context, calls } = fixture(`/${module}${module === "planning" ? `?audit=${upperId}` : ""}`, undefined, {
      [`/api/v1/${module}/overview`]: overview,
    });
    context.session.roleName = "Vedoucí týmu";
    const result = page(await handleOperations(context));
    assert.equal(result.template, `${module}/index.html.twig`);
    assert.equal(result.data.overview, overview);
    assert.equal(result.data.edit, module !== "hr");
    assert.equal(result.data.manageAll, false);
    assert.equal(result.data.error, null);
    const paths = calls.map((call) => call[1]);
    if (module === "purchase") assert.deepEqual(paths.slice(1), ["/api/v1/purchase/warehouses", "/api/v1/catalog/products"]);
    if (module === "planning") assert.deepEqual(paths.slice(1), [
      "/api/v1/planning/employees", "/api/v1/planning/workplaces", "/api/v1/planning/notifications",
      "/api/v1/planning/roles", `/api/v1/planning/shifts/${id}/events`,
    ]);
  }
});

test("HR role fallback is deduplicated and employee availability selection is normalized", async () => {
  const { context, calls } = fixture(`/hr?employeeId=${upperId}`, undefined, {
    "/api/v1/hr/overview": { employees: [{ userRoleName: "Planner" }, { userRoleName: "Planner" }, { userRoleName: "" }] },
    "/api/v1/roles": new Error("Unavailable"), [`/api/v1/hr/employees/${id}/availability`]: { available: true },
  });
  context.session.roleName = "Personalista";
  const data = page(await handleOperations(context)).data;
  assert.deepEqual(data.roleOptions, [{ id: null, name: "Planner", initial: "P", color: "#D9ED62" }]);
  assert.equal(data.selectedEmployeeId, id);
  assert.equal(data.edit, true);
  assert.equal(data.manageAll, true);
  assert.equal(calls.at(-1)?.[1], `/api/v1/hr/employees/${id}/availability`);
});

test("GET bad audit/employee identifiers retain original visible validation feedback", async () => {
  for (const path of ["/planning?audit=bad", "/hr?employeeId=bad"]) {
    const { context } = fixture(path);
    assert.equal(page(await handleOperations(context)).data.error, "Neplatný identifikátor.");
  }
});

test("operations backend failures retain original action feedback", async () => {
  const { context } = fixture("/purchase", { action: "create", ...purchaseInput });
  context.backend.mutate = async () => { throw new Error("Unavailable"); };
  assert.equal(location(await handleOperations(context)).searchParams.get("error"), "Objednávku se nepodařilo uložit.");
  context.request = new Request("http://localhost/purchase");
  context.backend.json = async () => { throw new Error("Unavailable"); };
  assert.equal(page(await handleOperations(context)).data.error, "Backend pro tento modul není dostupný. Data se nepodařilo načíst.");
});

test("inventory receive, default receive, dispatch and order mappings preserve string costs and warehouse feedback", async () => {
  for (const action of ["receive", "dispatch", "order", "default"]) {
    const input = { id: upperId, quantity: "+0002", reference: "  DOC  ", note: "note", reorderLevel: "0", unitCost: "+0012,30",
      productId: upperId, locationName: " Sklad A ", ...(action === "default" ? {} : { action }) };
    const { context, calls } = fixture("/inventory", input);
    const target = location(await handleInventory(context));
    if (action === "order") {
      assert.deepEqual(calls, [["PATCH", "/api/v1/inventory/orders", { productId: id, locationName: " Sklad A ", quantity: 2 }]]);
      assert.equal(target.searchParams.get("warehouseName"), " Sklad A ");
      assert.equal(target.searchParams.get("view"), "products");
    } else {
      assert.deepEqual(calls, [["PATCH", `/api/v1/inventory/items/${id}/${action === "dispatch" ? "dispatch" : "receive"}`,
        action === "dispatch" ? { quantity: 2, reference: "DOC", note: "note" }
          : { quantity: 2, reorderLevel: 0, unitCost: "+0012.30", reference: "DOC", note: "note" }]]);
    }
    assert.ok(target.searchParams.has("message"));
  }
});

test("inventory int32, document length, minimum and cost validation", async () => {
  const base = { id, quantity: "1", reference: "DOC", reorderLevel: "0", unitCost: "1.00" };
  const changes: Record<string, string>[] = [
    { quantity: "2147483648" }, { quantity: "0" }, { quantity: "1.0" }, { id: "bad" },
    { reference: " " }, { reference: "ž".repeat(121) }, { note: "ž".repeat(501) },
    { reorderLevel: "-1" }, { unitCost: "-1" }, { unitCost: "1.001" }, { unitCost: "1e2" },
    { unitCost: "10000000000" }, { action: "unknown" },
  ];
  for (const change of changes) {
    const { context, calls } = fixture("/inventory", { ...base, ...change });
    assert.ok(location(await handleInventory(context)).searchParams.has("error"));
    assert.deepEqual(calls, []);
  }
  const { context, calls } = fixture("/inventory", { action: "order", productId: id, locationName: "Main", quantity: "0" });
  assert.ok(location(await handleInventory(context)).searchParams.has("message"));
  assert.equal((calls[0][2] as Row).quantity, 0);
});

test("inventory GET keeps consecutive groups, product low-stock mapping and Prague history dates", async () => {
  const items = [
    { id, sku: "A", categoryPath: "one", quantity: 1, reorderLevel: 2 },
    { id, sku: "B", categoryPath: "two", quantity: 3, reorderLevel: 2 },
    { id, sku: "C", categoryPath: "one", quantity: 1, reorderLevel: 2 },
  ];
  const products = [{ sku: "A", warehouses: [{ locationName: "Main" }] }, { sku: "B" }];
  const { context, calls } = fixture(`/inventory?view=history&itemId=${upperId}&page=+0001`, undefined, {
    "/api/v1/inventory/overview": { items, products, canEdit: "true" },
    [`/api/v1/inventory/movements?page=1&itemId=${id}`]: {
      items: [{ createdAt: "2026-01-02T12:34:56Z" }, { createdAt: "bad" }], size: 2, page: 1, totalElements: 5,
    },
  });
  // '+' in a URL means whitespace; the signed integer is URL-encoded like an actual GET form.
  context.url.searchParams.set("page", "+0001");
  const data = page(await handleInventory(context)).data;
  assert.equal(data.canEdit, false);
  assert.equal(data.selectedView, "history");
  assert.equal(data.selectedItemId, id);
  assert.deepEqual((data.groups as Row[]).map((group) => group.path), ["one", "two", "one"]);
  assert.deepEqual((data.products as Row[]).map((product) => product.lowStock), [true, false]);
  assert.deepEqual(data.allItems, items);
  assert.deepEqual(data.warehouseOptions, [{ locationName: "Main" }]);
  assert.deepEqual(data.movements, {
    items: [{ createdAt: "2026-01-02T12:34:56Z", createdAtFormatted: "02.01.2026 13:34:56" },
      { createdAt: "bad", createdAtFormatted: "bad" }], page: 1, totalElements: 5, totalPages: 3,
  });
  assert.equal(calls.at(-1)?.[1], `/api/v1/inventory/movements?page=1&itemId=${id}`);
});

test("inventory invalid history skips history request without losing overview", async () => {
  for (const query of ["page=-1", "page=2147483648", "page=", "itemId=bad"]) {
    const { context, calls } = fixture(`/inventory?${query}`, undefined, { "/api/v1/inventory/overview": { canEdit: true } });
    const data = page(await handleInventory(context)).data;
    assert.equal(data.canEdit, true);
    assert.equal(data.historyError, "Neplatný filtr nebo stránka historie.");
    assert.equal(data.movements, null);
    assert.equal(calls.length, 1);
  }
});

test("inventory role restrictions remain backend authoritative and 403 is visible", async () => {
  const { context } = fixture("/inventory", { id, action: "receive", quantity: "1", reference: "DOC", reorderLevel: "0", unitCost: "1" }, {
    [`/api/v1/inventory/items/${id}/receive`]: Response.json({ detail: "hidden" }, { status: 403 }),
  });
  context.session.roleName = "Worker";
  assert.equal(location(await handleInventory(context)).searchParams.get("error"), "Nemáte oprávnění k této skladové operaci.");
});

const promoInput = {
  name: "Promo", productId: upperId, supplierId: upperId, startsOn: "2026-10-08", endsOn: "2026-10-09",
  regularPrice: "+0001.23456789", promoPrice: ".5", supplierPurchasePrice: "1.", plannedQuantity: "+00002", marketingContribution: "-.2",
};

test("promo create/edit/status mappings and exact expected success statuses", async () => {
  for (const action of ["create", "edit", "status"]) {
    const path = `/api/v1/promo-campaigns${action === "create" ? "" : `/${id}${action === "status" ? "/status" : ""}`}`;
    const { context, calls } = fixture("/promo", action === "status" ? { id: upperId, status: "ACTIVE" }
      : { ...promoInput, id: upperId, action }, { [path]: Response.json({}, { status: action === "create" ? 201 : 200 }) });
    assert.ok(location(await handlePromo(context)).searchParams.has("message"));
    assert.deepEqual(calls, [[action === "create" ? "POST" : action === "edit" ? "PUT" : "PATCH", path,
      action === "status" ? { status: "ACTIVE" } : { ...promoInput, productId: id, supplierId: id, plannedQuantity: 2 }]]);
  }
  const { context } = fixture("/promo", { ...promoInput, action: "create" });
  assert.equal(location(await handlePromo(context)).searchParams.get("error"), "Backend odmítl vytvoření kampaně. (HTTP 200)");
});

test("promo int32 and non-exponent decimal guards preserve PHP date forwarding", async () => {
  const changes: Record<string, string>[] = [{ plannedQuantity: "2147483648" }, { plannedQuantity: "1.0" },
    { regularPrice: "1e2" }, { productId: "bad" }, { action: "edit", id: "bad" }];
  for (const change of changes) {
    const { context, calls } = fixture("/promo", { ...promoInput, action: "create", ...change });
    assert.equal(location(await handlePromo(context)).searchParams.get("error"), "Vyplňte platné údaje kampaně.");
    assert.deepEqual(calls, []);
  }
  const { context, calls } = fixture("/promo", { ...promoInput, action: "create", startsOn: "backend-validates-date", plannedQuantity: "-2147483648" },
    { "/api/v1/promo-campaigns": Response.json({}, { status: 201 }) });
  assert.ok(location(await handlePromo(context)).searchParams.has("message"));
  assert.equal((calls[0][2] as Row).startsOn, "backend-validates-date");
  assert.equal((calls[0][2] as Row).plannedQuantity, -2147483648);
});

test("promo GET preserves campaign/options fields used by original JavaScript", async () => {
  const campaigns = [{ id, imageUrl: "/photo", productId: id, supplierId: id, name: "Promo" }];
  const products = [{ id, name: "Product", imageUrl: "/photo", unit: "ks" }];
  const { context } = fixture("/promo?message=saved", undefined, {
    "/api/v1/promo-campaigns": campaigns, "/api/v1/promo-campaigns/options": { products, suppliers: "invalid" },
  });
  const data = page(await handlePromo(context)).data;
  assert.deepEqual(data.campaigns, campaigns);
  assert.deepEqual(data.options, { products, suppliers: [] });
  assert.deepEqual(data.statuses, ["PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"]);
  assert.equal(data.message, "saved");
});

test("promo backend details and expired login feedback remain visible", async () => {
  for (const [status, body, expected] of [
    [400, { detail: "Original backend detail", message: "secondary" }, "Original backend detail"],
    [401, { detail: "ignored" }, "Přihlášení vypršelo. Přihlaste se znovu."],
  ] as const) {
    const { context } = fixture("/promo", { id, status: "ACTIVE" }, {
      [`/api/v1/promo-campaigns/${id}/status`]: Response.json(body, { status }),
    });
    assert.equal(location(await handlePromo(context)).searchParams.get("error"), expected);
  }
});

test("inventory/promo feedback retains PHP RFC3986 encoding", async () => {
  const inventory = fixture("/inventory", { action: "order", productId: id, locationName: "Sklad A!*", quantity: "0" });
  const result = await handleInventory(inventory.context);
  assert.ok(result instanceof Response);
  const target = result.headers.get("Location")!;
  assert.ok(target.includes("warehouseName=Sklad%20A%21%2A"));
  assert.ok(!target.includes("+"));
  const promo = fixture("/promo", { id, status: "ACTIVE" });
  const promoResult = await handlePromo(promo.context);
  assert.ok(promoResult instanceof Response);
  assert.ok(promoResult.headers.get("Location")!.includes("Stav%20kampan"));
});

test("inventory/promo transport failures retain visible feedback and independent history loading", async () => {
  const inventory = fixture("/inventory", undefined, {
    "/api/v1/inventory/overview": new Error("offline"),
    "/api/v1/inventory/movements?page=0": new Error("history offline"),
  });
  const data = page(await handleInventory(inventory.context)).data;
  assert.equal(data.loadError, "Backend pro sklad není dostupný: offline");
  assert.equal(data.historyError, "Historii pohybů se nepodařilo načíst: history offline");
  const promo = fixture("/promo", undefined, { "/api/v1/promo-campaigns": new Error("offline") });
  assert.equal(page(await handlePromo(promo.context)).data.loadError, "Backend pro promo kampaně není dostupný: offline");
  for (const [handler, path, input, message] of [
    [handleInventory, "/inventory", { id, action: "dispatch", quantity: "1", reference: "DOC" },
      "Spojení se skladem selhalo. Před opakováním ověřte historii pohybů."],
    [handleInventory, "/inventory", { action: "order", productId: id, locationName: "Main", quantity: "1" },
      "Objednávku se nepodařilo odeslat. Ověřte stav před opakováním."],
    [handlePromo, "/promo", { ...promoInput, action: "create" },
      "Kampaň se nepodařilo uložit. Ověřte stav kampaní před opakováním."],
    [handlePromo, "/promo", { id, status: "ACTIVE" },
      "Změna stavu se nezdařila. Ověřte stav kampaně před opakováním."],
  ] as [typeof handleInventory, string, Record<string, string>, string][]) {
    const { context } = fixture(path, input);
    context.backend.request = async () => { throw new Error("offline"); };
    assert.equal(location(await handler(context)).searchParams.get("error"), message);
  }
});

test("all three handlers decline nonowned routes and methods", async () => {
  for (const handler of [handleOperations, handleInventory, handlePromo]) {
    const { context } = fixture("/other");
    assert.equal(await handler(context), null);
  }
  for (const [handler, path] of [[handleOperations, "/sales"], [handleInventory, "/inventory"], [handlePromo, "/promo"]] as const) {
    const { context } = fixture(path);
    context.request = new Request(context.url, { method: "DELETE" });
    assert.equal(await handler(context), null);
  }
});
