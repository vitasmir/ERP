import {
  type Context, type Page, type Row, ValidationError, date, decimal, feedback,
  fields, integer as safeInteger, normalizedRole, optionalUuid, reportError, row, rows, uuid,
} from "../lib/context";

const modules = ["sales", "purchase", "manufacturing", "pos", "planning", "hr"] as const;
type Module = typeof modules[number];
type Mutation = [method: string, path: string, body: Row | null, message: string];

function integer(value: unknown, minimum: number): number | object {
  if (typeof value !== "string" || !/^[+-]?\d+$/.test(value)) throw new ValidationError("Neplatné množství.");
  const number = BigInt(value);
  if (number < BigInt(minimum) || number > 9223372036854775807n || number < -9223372036854775808n) {
    throw new ValidationError("Neplatné množství.");
  }
  if (number <= BigInt(Number.MAX_SAFE_INTEGER) && number >= BigInt(Number.MIN_SAFE_INTEGER)) {
    return safeInteger(value, minimum);
  }
  // Preserve PHP's 64-bit integer JSON tokens without rounding them through a JS number.
  const json = JSON as typeof JSON & { rawJSON?: (source: string) => object };
  if (!json.rawJSON) throw new Error("Přesná 64bitová množství vyžadují JSON.rawJSON (Node.js 22+).");
  return json.rawJSON(number.toString());
}

function arrayInput(context: Context, name: string): FormDataEntryValue[] {
  // PHP's request bag treats bracketed form names as arrays, not scalar fields.
  if (context.form.has(name)) throw new ValidationError("Očekáván seznam.");
  const values = new Map<string, FormDataEntryValue>();
  let nextIndex = 0;
  for (const [key, value] of context.form) {
    if (!key.startsWith(`${name}[`)) continue;
    const index = key.slice(name.length + 1, -1);
    if (!key.endsWith("]") || /[\[\]]/.test(index)) throw new ValidationError("Neplatný seznam.");
    const arrayKey = index || String(nextIndex++);
    if (/^(?:0|[1-9]\d*)$/.test(arrayKey)) nextIndex = Math.max(nextIndex, Number(arrayKey) + 1);
    values.set(arrayKey, value);
  }
  if (!values.size) throw new ValidationError("Očekáván neprázdný seznam.");
  return [...values.values()];
}

function mutation(context: Context, module: Module): Mutation {
  const get = (name: string) => context.form.get(name);
  const action = get("action") ?? ({ sales: "confirm", purchase: "order", manufacturing: "complete" } as Partial<Record<Module, string>>)[module];
  const saved = "Změna byla uložena.";
  if (module === "sales") {
    const base = "/api/v1/sales/orders";
    if (action === "create" || action === "update") {
      return [action === "create" ? "POST" : "PUT", base + (action === "update" ? `/${uuid(get("id"))}` : ""),
        fields(context, ["orderNumber", "customerName", "orderDate", "deliveryDate", "totalAmount"]),
        action === "create" ? "Objednávka byla vytvořena." : "Dokument byl upraven."];
    }
    const path = `${base}/${uuid(get("id"))}`;
    if (action === "delete") return ["DELETE", path, null, "Dokument byl smazán."];
    if (action === "confirm") return ["PATCH", `${path}/confirm`, null, "Nabídka byla potvrzena jako objednávka."];
  } else if (module === "purchase") {
    const base = "/api/v1/purchase/orders";
    if (action === "create" || action === "update") {
      const ids = arrayInput(context, "productId");
      const quantities = arrayInput(context, "quantity");
      const prices = arrayInput(context, "unitPrice");
      if (ids.length !== quantities.length || ids.length !== prices.length) throw new ValidationError("Neplatné položky.");
      const lines = ids.map((id, index) => ({
        productId: uuid(id), quantity: integer(quantities[index], 1), unitPrice: decimal(prices[index]),
      }));
      return [action === "create" ? "POST" : "PUT", base + (action === "update" ? `/${uuid(get("id"))}` : ""), {
        supplierName: get("supplierName"), requestedOn: date(get("requestedOn")),
        expectedDeliveryDate: date(get("expectedDeliveryDate")),
        sourceWarehouseId: uuid(get("sourceWarehouseId")), destinationWarehouseId: uuid(get("destinationWarehouseId")), lines,
      }, action === "create" ? "Nákupní objednávka byla vytvořena." : "Nákupní objednávka byla upravena."];
    }
    if (action === "order" || action === "receive") {
      return ["PATCH", `${base}/${uuid(get("id"))}/${action}`,
        action === "receive" ? { quantity: context.form.has("quantity") ? integer(get("quantity"), 1) : null } : null,
        action === "receive" ? "Zboží bylo přijato na sklad." : "Nákupní objednávka byla vystavena."];
    }
  } else if (module === "manufacturing") {
    const path = `/api/v1/manufacturing/orders/${uuid(get("id"))}`;
    if (action === "update") return ["PATCH", `${path}/progress`, { completedQuantity: integer(get("completedQuantity"), 0) }, "Počet vyrobených kusů byl upraven."];
    if (action === "complete") return ["PATCH", `${path}/complete`, null, "Výrobní příkaz byl dokončen."];
  } else if (module === "pos") {
    const method = get("method");
    if (typeof method === "string" && ["CARD", "CASH", "VOUCHER"].includes(method)) {
      return ["PATCH", `/api/v1/pos/transactions/${uuid(get("id"))}/pay`, { method }, "Platba byla přijata a účtenka uzavřena."];
    }
  } else if (module === "planning") {
    const base = "/api/v1/planning";
    if (action === "create" || action === "update") {
      const body = fields(context, ["roleName", "department", "startAt", "endAt"]);
      body.employeeId = optionalUuid(get("employeeId"));
      if (action === "update") body.version = integer(get("version"), 0);
      return [action === "create" ? "POST" : "PUT", `${base}/shifts` + (action === "update" ? `/${uuid(get("id"))}` : ""), body, saved];
    }
    if (action === "publishPlan") return ["POST", `${base}/publish`, { shiftIds: arrayInput(context, "shiftIds").map(uuid) }, saved];
    if (action === "workplace") return ["POST", `${base}/workplaces`, { name: get("name"), capacity: integer(get("capacity"), 1) }, saved];
    if (action === "read" || action === "delete" || action === "publish") {
      const id = uuid(get("id"));
      return [action === "delete" ? "DELETE" : "PATCH",
        action === "read" ? `${base}/notifications/${id}/read` : `${base}/shifts/${id}${action === "publish" ? "/publish" : ""}`, null, saved];
    }
  } else if (module === "hr") {
    const base = "/api/v1/hr";
    if (action === "create" || action === "update") {
      const body = fields(context, ["fullName", "jobTitle", "employmentStartDate"]);
      body.teamName = "";
      body.teamId = uuid(get("teamId"));
      const deputy = optionalUuid(get("deputyEmployeeId"));
      if (deputy !== null) body.deputyEmployeeId = deputy;
      return [action === "create" ? "POST" : "PUT", `${base}/employees` + (action === "update" ? `/${uuid(get("id"))}` : ""), body, saved];
    }
    if (action === "createTeam" || action === "updateTeam" || action === "deleteTeam") {
      return [action === "createTeam" ? "POST" : action === "updateTeam" ? "PUT" : "DELETE",
        `${base}/teams` + (action === "createTeam" ? "" : `/${uuid(get("teamId"))}`),
        action === "deleteTeam" ? null : { name: get("name") }, saved];
    }
    const path = `${base}/employees/${uuid(get("id"))}`;
    if (action === "absence") return ["POST", `${path}/absences`, fields(context, ["startAt", "endAt", "reason"]), saved];
    if (action === "qualification") return ["POST", `${path}/qualifications`, fields(context, ["roleName"]), saved];
    if (action === "removeAbsence") return ["DELETE", `${path}/absences/${uuid(get("absenceId"))}`, null, saved];
    if (action === "activate" || action === "deactivate") return ["PATCH", `${path}/${action}`, null, saved];
  }
  throw new ValidationError("Neplatná akce.");
}

export async function handleOperations(context: Context): Promise<Page | Response | null> {
  const module = context.url.pathname.slice(1) as Module;
  if (!modules.includes(module) || !["GET", "POST"].includes(context.request.method)) return null;
  if (context.request.method === "POST") {
    try {
      const [method, path, body, message] = mutation(context, module);
      await context.backend.mutate(method, path, body);
      const parameters: Record<string, string> = {};
      if (module === "hr" && context.form.has("id")) parameters.employeeId = uuid(context.form.get("id"));
      return feedback(`/${module}`, message, false, parameters);
    } catch (error) {
      if (error instanceof ValidationError) {
        return feedback(`/${module}`, {
          sales: "Vyplňte platné údaje objednávky.", purchase: "Neplatný nákupní požadavek.",
          manufacturing: "Neplatný výrobní příkaz.", pos: "Zvolte platný způsob platby.",
          planning: "Neplatná směna.", hr: "Neplatný zaměstnanec.",
        }[module], true);
      }
      reportError(error, `${module} mutation failed.`);
      return feedback(`/${module}`, module === "sales" ? "Změnu dokumentu backend odmítl nebo není dostupný."
        : module === "purchase" ? (["create", "update"].includes(String(context.form.get("action")))
          ? "Objednávku se nepodařilo uložit." : "Změnu stavu backend odmítl nebo není dostupný.")
          : module === "manufacturing" ? "Změnu výroby backend odmítl nebo není dostupný."
            : module === "pos" ? "Platbu backend odmítl nebo není dostupný."
              : "Změnu se nepodařilo uložit. Backend požadavek odmítl nebo není dostupný.", true);
    }
  }

  const role = normalizedRole(context);
  const admin = ["administrator", "admin"].includes(role);
  const manageAll = admin || ["hr", "personalista", "planovac", "planner"].includes(role);
  const data: Row = {
    overview: null, warehouses: [], products: [], employees: [], workplaces: [],
    notifications: [], events: null, roleOptions: [], availability: null,
    selectedEmployeeId: null, error: null, message: context.url.searchParams.get("message"),
    actionError: context.url.searchParams.get("error"), admin, manageAll,
    edit: module === "hr" ? admin || ["hr", "personalista"].includes(role)
      : manageAll || ["vedouci tymu", "team lead"].includes(role),
  };
  try {
    data.overview = await context.backend.json("GET", `/api/v1/${module}/overview`);
    if (module === "purchase") {
      data.warehouses = await context.backend.json("GET", "/api/v1/purchase/warehouses");
      data.products = await context.backend.json("GET", "/api/v1/catalog/products");
    } else if (module === "planning") {
      for (const collection of ["employees", "workplaces", "notifications"]) {
        data[collection] = await context.backend.json("GET", `/api/v1/planning/${collection}`);
      }
      data.roleOptions = await context.backend.json("GET", "/api/v1/planning/roles");
      if (context.url.searchParams.has("audit")) {
        data.events = await context.backend.json("GET", `/api/v1/planning/shifts/${uuid(context.url.searchParams.get("audit"))}/events`);
      }
    } else if (module === "hr") {
      try {
        data.roleOptions = await context.backend.json("GET", "/api/v1/roles");
      } catch (error) {
        reportError(error, "HR roles request failed; using employee roles.");
        const roles = new Map<string, Row>();
        for (const employee of rows(row(data.overview).employees ?? [])) {
          const name = typeof employee.userRoleName === "string" ? employee.userRoleName : "";
          if (name !== "") roles.set(name, { id: null, name, initial: name.slice(0, 1).toUpperCase(), color: "#D9ED62" });
        }
        data.roleOptions = [...roles.values()];
      }
      if (context.url.searchParams.has("employeeId")) {
        data.selectedEmployeeId = uuid(context.url.searchParams.get("employeeId"));
        data.availability = await context.backend.json("GET", `/api/v1/hr/employees/${data.selectedEmployeeId}/availability`);
      }
    }
  } catch (error) {
    if (error instanceof ValidationError) data.error = "Neplatný identifikátor.";
    else {
      reportError(error, `${module} overview request failed.`);
      data.error = "Backend pro tento modul není dostupný. Data se nepodařilo načíst.";
    }
  }
  return context.render(`${module}/index`, data);
}
