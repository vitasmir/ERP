import { type Context, type Page, type Row, ValidationError, redirect, reportError, row, str, uuid } from "../lib/context";

function feedback(path: string, message: string, error = false, parameters: Record<string, string> = {}): Response {
  const encode = (value: string) => encodeURIComponent(value).replace(/[!'()*]/g,
    (character) => `%${character.charCodeAt(0).toString(16).toUpperCase()}`);
  const query = Object.entries({ [error ? "error" : "message"]: message, ...parameters })
    .map(([key, value]) => `${encode(key)}=${encode(value)}`).join("&");
  return redirect(`${path}?${query}`);
}

function text(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function parseInt32(value: unknown): number | null {
  if (typeof value !== "string" || !/^[+-]?\d+$/.test(value)
    || value.replace(/^[+-]/, "").replace(/^0+/, "").length > 10) return null;
  const number = Number(value);
  return number > 2147483647 || number < -2147483648 ? null : number;
}

async function backendError(response: Response, fallback: string): Promise<string> {
  reportError(new Error(`HTTP ${response.status}`), "Inventory backend request rejected.");
  if (response.status === 401) return "Přihlášení vypršelo. Přihlaste se znovu.";
  if (response.status === 403) return "Nemáte oprávnění k této skladové operaci.";
  if ((response.headers.get("content-type") ?? "").includes("json")) {
    try {
      const body = row(await response.json());
      for (const key of ["detail", "message"]) {
        if (typeof body[key] === "string" && body[key].trim()) return body[key];
      }
    } catch (error) {
      reportError(error, "Inventory backend error response could not be parsed.");
    }
  }
  return `${fallback} (HTTP ${response.status})`;
}

async function saveOrder(context: Context): Promise<Response> {
  const productId = text(context.form.get("productId"));
  const locationName = text(context.form.get("locationName"));
  const quantity = parseInt32(context.form.get("quantity"));
  let id: string;
  try {
    id = uuid(productId);
    if (quantity === null || quantity < 0 || locationName === null || !locationName.trim()) throw new ValidationError();
  } catch (error) {
    if (!(error instanceof ValidationError)) throw error;
    return feedback("/inventory", "Zadejte nezáporné množství a platný sklad.", true);
  }
  try {
    const response = await context.backend.request("PATCH", "/api/v1/inventory/orders", {
      productId: id, locationName, quantity,
    });
    const success = response.status === 200;
    return feedback("/inventory", success ? "Objednávka z hlavního skladu byla uložena."
      : await backendError(response, "Objednávku backend odmítl."), !success,
    { warehouseName: locationName!, view: "products" });
  } catch (error) {
    reportError(error, "Inventory order request failed.");
    return feedback("/inventory", "Objednávku se nepodařilo odeslat. Ověřte stav před opakováním.", true);
  }
}

async function saveMovement(context: Context): Promise<Response> {
  const get = (key: string) => context.form.get(key);
  const invalid = "Zadejte kladné celočíselné množství, doklad, platné minimum a cenu.";
  const action = text(get("action"));
  const reference = text(get("reference"));
  const note = text(get("note"));
  const quantity = parseInt32(get("quantity"));
  let id: string;
  let payload: Row;
  const dispatch = action === "dispatch";
  try {
    id = uuid(get("id"));
    if (quantity === null || quantity <= 0 || (action !== null && !["receive", "dispatch"].includes(action))
      || reference === null || !reference.trim() || [...reference].length > 120
      || (note !== null && [...note].length > 500)) throw new ValidationError();
    payload = { quantity, reference: reference.trim(), note };
    if (!dispatch) {
      const minimum = parseInt32(get("reorderLevel"));
      const cost = text(get("unitCost"));
      if (minimum === null || minimum < 0 || cost === null || !cost.trim()) throw new ValidationError();
      const normalizedCost = cost.replace(/,/g, ".");
      const parts = /^\+?(\d*)(?:\.(\d*))?$/.exec(normalizedCost);
      if (!parts || (!parts[1] && !parts[2]) || (parts[2] ?? "").length > 2
        || parts[1].replace(/^0+/, "").length > 10) throw new ValidationError();
      // Authorization and the unit-cost edit restrictions remain backend-owned.
      payload = { quantity, reorderLevel: minimum, unitCost: normalizedCost, reference: reference.trim(), note };
    }
  } catch (error) {
    if (!(error instanceof ValidationError)) throw error;
    return feedback("/inventory", invalid, true);
  }
  try {
    const response = await context.backend.request("PATCH", `/api/v1/inventory/items/${id}/${dispatch ? "dispatch" : "receive"}`, payload);
    if (response.status === 200) return feedback("/inventory", dispatch ? "Výdej zásoby byl zaevidován." : "Příjem zásoby byl zaevidován.");
    return feedback("/inventory", await backendError(response, "Skladový pohyb backend odmítl."), true);
  } catch (error) {
    reportError(error, "Inventory movement request failed.");
    return feedback("/inventory", "Spojení se skladem selhalo. Před opakováním ověřte historii pohybů.", true);
  }
}

function formatCreatedAt(value: unknown): string {
  const created = str(value);
  const date = new Date(created || Date.now());
  if (!Number.isFinite(date.getTime())) return created;
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/Prague", day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
  }).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((entry) => entry.type === type)?.value;
  return `${part("day")}.${part("month")}.${part("year")} ${part("hour")}:${part("minute")}:${part("second")}`;
}

async function loadMovements(context: Context): Promise<[Row | null, string | null, string | null]> {
  const params = context.url.searchParams;
  const page = params.has("page") ? parseInt32(params.get("page")) : 0;
  const itemId = params.get("itemId");
  const invalid = "Neplatný filtr nebo stránka historie.";
  if (page === null || page < 0) return [null, invalid, null];
  let selected: string | null = null;
  if (itemId !== null && itemId.trim()) {
    try {
      selected = uuid(itemId);
    } catch {
      return [null, invalid, null];
    }
  }
  try {
    const response = await context.backend.request("GET",
      `/api/v1/inventory/movements?page=${page}${selected ? `&itemId=${selected}` : ""}`);
    if (response.status !== 200) return [null,
      `Historii pohybů se nepodařilo načíst: ${await backendError(response, "Historii pohybů backend odmítl.")}`, selected];
    const data = row(await response.json());
    const items = Array.isArray(data.items) ? data.items.map((value) => {
      const movement = row(value);
      return { ...movement, createdAtFormatted: formatCreatedAt(movement.createdAt) };
    }) : [];
    const size = Math.trunc(Number(data.size ?? 0));
    const total = Math.trunc(Number(data.totalElements ?? 0));
    return [{ items, page: Math.trunc(Number(data.page ?? 0)), totalElements: total,
      totalPages: size > 0 ? Math.trunc((total + size - 1) / size) : 0 }, null, selected];
  } catch (error) {
    reportError(error, "Inventory movements request failed.");
    return [null, `Historii pohybů se nepodařilo načíst: ${error instanceof Error ? error.message : String(error)}`, selected];
  }
}

export async function handleInventory(context: Context): Promise<Page | Response | null> {
  if (context.url.pathname !== "/inventory" || !["GET", "POST"].includes(context.request.method)) return null;
  if (context.request.method === "POST") {
    return context.form.get("action") === "order" ? saveOrder(context) : saveMovement(context);
  }
  let overview: Row | null = null;
  let error: string | null = null;
  try {
    overview = row(await context.backend.json("GET", "/api/v1/inventory/overview"));
  } catch (exception) {
    reportError(exception, "Inventory overview request failed.");
    error = `Backend pro sklad není dostupný: ${exception instanceof Error ? exception.message : String(exception)}`;
  }
  const [movements, historyError, selectedItemId] = await loadMovements(context);
  const view = context.url.searchParams.get("view");
  const items = Array.isArray(overview?.items) ? overview.items.map(row) : [];
  const products = Array.isArray(overview?.products) ? overview.products.map((value) => ({ ...row(value) })) : [];
  const lowStockSkus = new Set<string>();
  const groups: { path: string; items: Row[] }[] = [];
  for (const original of items) {
    const low = Number(original.quantity ?? 0) < Number(original.reorderLevel ?? 0);
    const item: Row = { ...original, lowStock: low };
    if (low) lowStockSkus.add(str(item.sku));
    const path = str(item.categoryPath);
    if (!groups.length || groups[groups.length - 1].path !== path) groups.push({ path, items: [] });
    groups[groups.length - 1].items.push(item);
  }
  for (const product of products) product.lowStock = lowStockSkus.has(str(product.sku));
  const warehouses = Array.isArray(products[0]?.warehouses) ? products[0].warehouses : [];
  return context.render("inventory/index.html.twig", {
    pageTitle: "Sklad", breadcrumb: "PROVOZ / SKLAD", overview, groups, itemCount: items.length,
    products, warehouseOptions: warehouses, canEdit: overview?.canEdit === true, loadError: error,
    actionError: context.url.searchParams.get("error"), message: context.url.searchParams.get("message"),
    selectedWarehouse: context.url.searchParams.get("warehouseName"), selectedView: ["products", "history"].includes(view ?? "") ? view : "stock",
    allItems: items, movements, historyError, selectedItemId,
  });
}
