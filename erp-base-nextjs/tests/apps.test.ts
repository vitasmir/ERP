import assert from "node:assert/strict";
import test from "node:test";
import { filterApps, getAllowedApps } from "../src/lib/apps";

test("launcher shows only modules granted to the user and maps backend keys to routes", () => {
  const apps = getAllowedApps(false, ["roles", "promo-campaigns", "catalog"]);

  assert.deepEqual(apps.map(({ id }) => id), ["base", "promotions", "ecommerce"]);
  assert.equal(apps[1].path, "promo");
  assert.equal(apps[2].path, "ecommerce");
});

test("administrator sees the full launcher catalog", () => {
  assert.equal(getAllowedApps(true, []).length, 18);
});

test("launcher filters modules by category and Czech text", () => {
  const apps = getAllowedApps(true, []);

  assert.deepEqual(filterApps(apps, "", "operations").map(({ id }) => id), [
    "purchase", "inventory", "manufacturing", "project", "helpdesk", "planning",
  ]);
  assert.deepEqual(filterApps(apps, "DODAVATELÉ", "all").map(({ id }) => id), ["purchase"]);
  assert.deepEqual(filterApps(apps, "neexistující", "all"), []);
});
