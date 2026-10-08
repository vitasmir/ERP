import { isIP } from "node:net";
import {
  type Context, type Page, type Row, field, feedback, integer, decimal,
  row, rows, str, uuid, reportError,
} from "../lib/context";

const routes: Record<string, [string, string, string]> = {
  "/companies": ["companies", "Společnosti", "BASE / ENTITIES"],
  "/users": ["users", "Uživatelé", "BASE / USERS"],
  "/roles": ["roles", "Role a oprávnění", "BASE / ACCESS"],
  "/role-modules": ["role_modules", "Role pro moduly", "BASE / MODULE ACCESS"],
  "/settings": ["settings", "Nastavení", "BASE / CONFIGURATION"],
};

function validUuid(value: string): boolean {
  try { uuid(value); return true; } catch { return false; }
}

function initials(name: string): string {
  const words = name.trim().split(/\s+/u).filter(Boolean);
  if (!words.length) return "";
  const first = Array.from(words[0]);
  return (first[0] + (words.length > 1 ? Array.from(words[words.length - 1])[0] : first[1] ?? ""))
    .replace(/[a-z]/g, (letter) => letter.toUpperCase());
}

function lastAccess(value: unknown): string {
  if (typeof value !== "string" || !value) return "Nikdy";
  const parsed = new Date(value);
  if (!Number.isFinite(parsed.getTime())) return value;
  // PHP preserves an explicitly supplied offset and otherwise uses its default timezone.
  const offset = value.match(/([+-])(\d{2}):?(\d{2})$/);
  const milliseconds = offset
    ? (offset[1] === "+" ? 1 : -1) * (Number(offset[2]) * 60 + Number(offset[3])) * 60_000 : 0;
  if (offset || /Z$/i.test(value)) {
    const local = new Date(parsed.getTime() + milliseconds);
    return `${local.getUTCDate()}. ${local.getUTCMonth() + 1}. ${local.getUTCFullYear()} ${String(local.getUTCHours()).padStart(2, "0")}:${String(local.getUTCMinutes()).padStart(2, "0")}`;
  }
  return `${parsed.getDate()}. ${parsed.getMonth() + 1}. ${parsed.getFullYear()} ${String(parsed.getHours()).padStart(2, "0")}:${String(parsed.getMinutes()).padStart(2, "0")}`;
}

async function companiesPost(c: Context): Promise<Response> {
  const action = field(c, "action");
  const name = field(c, "name").trim();
  const type = field(c, "type").trim();
  if (!["create", "update"].includes(action) || !name || !type) {
    return feedback("/companies", "Vyplňte platné údaje společnosti.", true);
  }
  let path = "/api/v1/companies";
  if (action === "update") {
    const id = field(c, "id");
    if (!validUuid(id)) return feedback("/companies", "Vyberte platnou společnost.", true);
    path += `/${id}`;
  }
  try {
    const response = await c.backend.request(action === "update" ? "PUT" : "POST", path, {
      name, type, currency: field(c, "currency").trim(),
      status: field(c, "status").trim(), color: field(c, "color").trim(),
    });
    if (response.ok) return feedback("/companies", action === "update" ? "Společnost byla upravena." : "Společnost byla vytvořena.");
    reportError(response.status, "Could not save ERP company.");
    return feedback("/companies", response.status === 409 ? "Společnost s tímto názvem již existuje."
      : `Backend změnu společnosti odmítl (HTTP ${response.status}).`, true);
  } catch (error) {
    reportError(error, "Could not save ERP company.");
    return feedback("/companies", "Backend pro společnosti není dostupný.", true);
  }
}

async function usersPost(c: Context): Promise<Response> {
  const action = field(c, "action");
  const id = field(c, "id");
  const invalid = () => feedback("/users", "Vyplňte platné údaje uživatele.", true);
  if (!["create", "update", "delete"].includes(action)
    || action !== "create" && !validUuid(id)) return invalid();
  try {
    let response: Response;
    if (action === "delete") {
      response = await c.backend.request("DELETE", `/api/v1/users/${id}`);
    } else {
      const employeeId = field(c, "employeeId");
      const username = field(c, "username").trim();
      const fullName = field(c, "fullName").trim();
      const companyName = field(c, "companyName").trim();
      const password = field(c, "password");
      if (!validUuid(employeeId) || !username || !fullName || !companyName
        || action === "create" && Buffer.byteLength(password, "utf8") < 10) return invalid();
      response = await c.backend.request(action === "update" ? "PUT" : "POST",
        `/api/v1/users${action === "update" ? `/${id}` : ""}`, {
          employeeId, fullName, username, password, companyName,
          status: field(c, "status"), color: field(c, "color"),
        });
    }
    if (response.ok) return feedback("/users", action === "update" ? "Uživatel byl aktualizován."
      : action === "delete" ? "Uživatel byl smazán." : "Uživatel byl přidán.");
    reportError(response.status, "Could not save ERP user.");
    return feedback("/users", `Backend změnu uživatele odmítl (HTTP ${response.status}).`, true);
  } catch (error) {
    reportError(error, "Could not save ERP user.");
    return feedback("/users", "Backend pro uživatele není dostupný.", true);
  }
}

async function rolesPost(c: Context): Promise<Response> {
  const action = field(c, "action");
  try {
    if (action === "save-module-permissions") {
      const permissions: Row[] = [];
      for (const name of new Set(c.form.keys())) {
        if (!name.startsWith("permission_")) continue;
        const suffix = name.slice("permission_".length);
        const separator = suffix.indexOf("_");
        const roleId = suffix.slice(0, separator);
        const moduleKey = suffix.slice(separator + 1);
        if (separator < 0 || !validUuid(roleId) || !moduleKey) {
          return feedback("/role-modules", "Vyberte platná oprávnění modulů.", true);
        }
        permissions.push({ roleId, moduleKey });
      }
      const response = await c.backend.request("PUT", "/api/v1/roles/matrix", { permissions });
      if (response.ok) return feedback("/role-modules", "Oprávnění modulů byla uložena.");
      reportError(response.status, "Could not save ERP roles.");
      return feedback("/role-modules", "Oprávnění se nepodařilo uložit.", true);
    }
    const name = field(c, "name").trim();
    const initial = field(c, "initial").trim();
    const id = field(c, "id");
    if (!["create", "update"].includes(action) || !name || !initial || action === "update" && !validUuid(id)) {
      return feedback("/roles", "Vyplňte platné údaje role.", true);
    }
    const response = await c.backend.request(action === "update" ? "PUT" : "POST",
      `/api/v1/roles${action === "update" ? `/${id}` : ""}`, {
        name, initial, description: field(c, "description").trim(),
        canRead: c.form.has("canRead"), canInsert: c.form.has("canInsert"),
        canEdit: c.form.has("canEdit"), canManage: c.form.has("canManage"), canDelete: c.form.has("canDelete"),
        color: field(c, "color").trim(),
      });
    if (response.ok) return feedback("/roles", action === "update" ? "Role byla aktualizována." : "Role byla vytvořena.");
    reportError(response.status, "Could not save ERP roles.");
    return feedback("/roles", `Backend změnu role odmítl (HTTP ${response.status}).`, true);
  } catch (error) {
    reportError(error, "Could not save ERP roles.");
    return feedback(action === "save-module-permissions" ? "/role-modules" : "/roles",
      "Backend změnu odmítl nebo není dostupný.", true);
  }
}

function settingInteger(value: string): number {
  const trimmed = value.trim();
  if (!/^[+-]?(?:0|[1-9]\d*)$/.test(trimmed)) throw new Error("Invalid integer");
  return integer(trimmed);
}

function validEmail(value: string): boolean {
  const separator = value.lastIndexOf("@");
  if (separator < 1 || value.length > 254) return false;
  const local = value.slice(0, separator);
  const domain = value.slice(separator + 1);
  if (local.length > 64 || !/^[\x00-\x7f]+$/.test(value)) return false;
  const unquoted = /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$/;
  const quoted = /^"(?:[\x21\x23-\x5b\x5d-\x7e]|\\[\x20-\x7e])*"$/;
  if (!unquoted.test(local) && !quoted.test(local)) return false;
  if (domain.startsWith("[") && domain.endsWith("]")) {
    const literal = domain.slice(1, -1);
    return literal.startsWith("IPv6:") ? isIP(literal.slice(5)) === 6 : isIP(literal) === 4;
  }
  const labels = domain.split(".");
  return labels.length > 1 && labels.every((label) =>
    label.length <= 63 && /^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$/.test(label))
    && /^[A-Za-z]/.test(labels[labels.length - 1]);
}

async function settingsPost(c: Context): Promise<Response> {
  const body: Row = {};
  try {
    for (const name of ["companyName", "companyEmail", "currencyCode", "timezone"]) {
      body[name] = field(c, name).trim();
      if (!body[name]) throw new Error("Missing setting");
    }
    if (!validEmail(str(body.companyEmail))) {
      throw new Error("Invalid email");
    }
    body.fiscalYearStartMonth = settingInteger(field(c, "fiscalYearStartMonth"));
    body.defaultPaymentTermsDays = settingInteger(field(c, "defaultPaymentTermsDays"));
    for (const name of ["deliveryFee", "eshopMarginPercent", "eshopRoundingUnit", "eshopDefaultVatRate"]) {
      body[name] = Number(decimal(field(c, name).trim()));
    }
    if (Number(body.fiscalYearStartMonth) < 1 || Number(body.fiscalYearStartMonth) > 12
      || Number(body.defaultPaymentTermsDays) < 0 || Number(body.deliveryFee) < 0
      || Number(body.eshopMarginPercent) < 0 || Number(body.eshopMarginPercent) > 99.98
      || ![1, 10, 100].includes(Number(body.eshopRoundingUnit))
      || Number(body.eshopDefaultVatRate) < 0 || Number(body.eshopDefaultVatRate) > 100) {
      throw new Error("Invalid setting");
    }
  } catch {
    return feedback("/settings", "Zkontrolujte zadané hodnoty nastavení.", true);
  }
  try {
    const response = await c.backend.request("PATCH", "/api/v1/settings", body);
    if (response.ok) return feedback("/settings", "Nastavení bylo uloženo.");
    reportError(response.status, "Could not save ERP settings.");
    return feedback("/settings", `Uložení nastavení backend odmítl (HTTP ${response.status}).`, true);
  } catch (error) {
    reportError(error, "Could not save ERP settings.");
    return feedback("/settings", "Backend pro nastavení není dostupný.", true);
  }
}

export async function handleAdmin(c: Context): Promise<Page | Response | null> {
  const path = c.url.pathname;
  const route = routes[path];
  if (!route) return null;
  if (!["GET", "POST"].includes(c.request.method)) {
    return new Response(null, { status: 405, headers: { Allow: "GET, POST" } });
  }
  if (c.request.method === "POST") {
    if (path === "/companies") return companiesPost(c);
    if (path === "/users") return usersPost(c);
    if (path === "/settings") return settingsPost(c);
    return rolesPost(c);
  }
  let error = c.url.searchParams.get("error");
  const data: Row = {};
  let unavailable: string;
  let log: string;
  if (path === "/companies") {
    data.companies = [];
    unavailable = "Backend pro společnosti není dostupný.";
    log = "Could not load ERP companies.";
  } else if (path === "/users") {
    Object.assign(data, { users: [], employees: [], roles: [], companies: [] });
    unavailable = "Backend pro uživatele není dostupný.";
    log = "Could not load ERP user administration data.";
  } else if (path === "/settings") {
    data.settings = null;
    unavailable = "Backend pro nastavení není dostupný.";
    log = "Could not load ERP settings.";
  } else {
    Object.assign(data, { roles: [], matrix: [] });
    unavailable = "Backend pro role není dostupný.";
    log = "Could not load ERP roles.";
  }
  try {
    if (path === "/companies") data.companies = rows(await c.backend.json("GET", "/api/v1/companies"));
    else if (path === "/users") {
      data.users = rows(await c.backend.json("GET", "/api/v1/users"));
      data.employees = rows(await c.backend.json("GET", "/api/v1/users/employee-options"));
      data.roles = rows(await c.backend.json("GET", "/api/v1/roles"));
      data.companies = rows(await c.backend.json("GET", "/api/v1/companies"));
    } else if (path === "/settings") data.settings = row(await c.backend.json("GET", "/api/v1/settings"));
    else {
      data.roles = rows(await c.backend.json("GET", "/api/v1/roles"));
      data.matrix = row(await c.backend.json("GET", "/api/v1/roles/matrix"));
    }
  } catch (failure) {
    reportError(failure, log);
    error ??= unavailable;
  }
  if (path === "/users") {
    data.users = rows(data.users).map((user) => ({
      ...user, initials: initials(str(user.fullName)),
      statusLabel: user.status === "INVITED" ? "Pozvánka čeká" : user.status === "SUSPENDED" ? "Pozastavený" : "Aktivní",
      lastAccessLabel: lastAccess(user.lastAccessAt),
    }));
    data.requestedEmployeeId = c.url.searchParams.get("employeeId") ?? "";
    data.requestedEmployeeName = str(rows(data.employees).find((employee) =>
      employee.id === data.requestedEmployeeId)?.fullName);
  }
  return c.render(`admin/${route[0]}.html.twig`, {
    ...data, message: c.url.searchParams.get("message"), error,
    pageTitle: route[1], breadcrumb: route[2],
  });
}
