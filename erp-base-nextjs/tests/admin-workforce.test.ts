import assert from "node:assert/strict";
import test from "node:test";
import { isAdministrator, modulePermissions } from "../src/modules/admin";
import { validateInterval, workforceRoles } from "../src/modules/workforce";

test("role matrix accepts seeded UUIDs and leaves administrator permissions to the backend", () => {
  const admin = { id: "00000000-0000-0000-0000-000000000001", name: "Administrátor" };
  const planner = { id: "00000000-0000-0000-0000-000000000002", name: "Plánovač" };
  const form = new FormData();
  form.set(`permission_${admin.id}_planning`, "on");
  form.set(`permission_${planner.id}_planning`, "on");
  form.set(`permission_${planner.id}_unknown`, "on");
  assert.equal(isAdministrator(admin), true);
  assert.deepEqual(modulePermissions([admin, planner], [{ key: "planning", name: "Plánování" }, { key: "hr", name: "Lidé" }], form), {
    permissions: [{ roleId: planner.id, moduleKey: "planning" }],
  });
});

test("unchecked permissions are removed and malformed role identifiers are rejected", () => {
  assert.deepEqual(modulePermissions([{ id: "00000000-0000-0000-0000-000000000002", name: "HR" }], [{ key: "hr" }], new FormData()), { permissions: [] });
  assert.throws(() => modulePermissions([{ id: "not-a-uuid", name: "HR" }], [{ key: "hr" }], new FormData()), /identifikátor/);
});

test("planning uses the complete role collection without normalized duplicates", () => {
  assert.deepEqual(workforceRoles([" Administrátor ", "Plánovač", "HR", "Logistika", "Nákupčí", "hr", "planovac", ""], ["Personalista", "HR"]), [
    "Administrátor", "Plánovač", "HR", "Logistika", "Nákupčí", "Personalista",
  ]);
});

test("HR role objects and existing custom employee roles are preserved", () => {
  assert.deepEqual(workforceRoles([{ id: "role-1", name: "HR" }, { id: "role-2", name: "Vedoucí týmu" }], ["Řidič", "Vedoucí týmu"]), [
    "HR", "Vedoucí týmu", "Řidič",
  ]);
});

test("shift and absence intervals reject equal, reversed or invalid dates", () => {
  for (const endAt of ["2026-10-08T08:00", "2026-10-08T08:00:00", "2026-10-07T18:00", "invalid"]) {
    assert.throws(() => validateInterval({ startAt: "2026-10-08T08:00", endAt }), /Konec/);
  }
  const valid = { startAt: "2026-10-08T08:00", endAt: "2026-10-09T01:00", employeeId: null };
  assert.deepEqual(validateInterval(valid), valid);
});
