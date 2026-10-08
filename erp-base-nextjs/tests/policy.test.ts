import assert from "node:assert/strict";
import test from "node:test";
import { isSameOrigin, publicApi, validApiPath } from "../src/lib/policy";
import { uniqueRoles, roleKey } from "../src/lib/data";
import { changeQuantity, deliveryValues } from "../src/lib/cart-store";

test("API paths reject traversal, encoded delimiters and external URLs", () => {
  for (const path of [["..", "settings"], ["%2fetc"], ["http:", "example.com"], ["."]]) assert.equal(validApiPath(path), false);
  assert.equal(validApiPath(["accounting", "invoices.csv"]), true);
});
test("origin checks include protocol and published port", () => {
  assert.equal(isSameOrigin(new Headers({ host: "localhost:4201", origin: "http://localhost:4201" }), "http://localhost:3000/api/cart"), true);
  for (const origin of ["https://localhost:4201", "http://localhost:3000", "http://example.org"]) {
    assert.equal(isSameOrigin(new Headers({ host: "localhost:4201", origin }), "http://localhost:3000/api/cart"), false);
  }
  assert.equal(isSameOrigin(new Headers(), "http://localhost:3000/api/cart"), false);
});
test("only explicit read-only public resources and checkout are anonymous", () => {
  assert.equal(publicApi("GET", "settings/public"), true);
  assert.equal(publicApi("GET", "settings"), false);
  assert.equal(publicApi("PUT", "catalog/products"), false);
  assert.equal(publicApi("GET", "planning/roles"), false);
  assert.equal(publicApi("POST", "sales/orders/checkout"), true);
});
test("role names stay unique and role normalization handles Czech accents", () => {
  assert.deepEqual(uniqueRoles(["Driver", "Driver", " Planner ", "Planner", ""]), ["Driver", "Planner"]);
  assert.equal(roleKey(" Administrátor "), "administrator");
});
test("cart enforces integer quantities and stock capacity", () => {
  assert.equal(changeQuantity(2, "increase", 1, 2), 2);
  assert.equal(changeQuantity(1, "decrease", 1, 3), 0);
  assert.equal(changeQuantity(1, "remove", 1, 3), 0);
  assert.throws(() => changeQuantity(1, "add", 1.5, 3));
  assert.throws(() => changeQuantity(1, "unknown", 1, 3));
});
test("delivery validates all required customer fields", () => {
  assert.throws(() => deliveryValues({ firstName: "Jane" }));
  assert.equal(deliveryValues({ firstName: " Jane ", lastName: "Buyer", phone: "+420123456789",
    street: "Street 1", city: "Brno", postalCode: "602 00" }).firstName, "Jane");
});
