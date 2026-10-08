import { type Context, type Page, type Row, ValidationError, redirect, reportError, row, uuid } from "../lib/context";

const statuses = ["PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"];

function feedback(path: string, message: string, error = false): Response {
  const encoded = encodeURIComponent(message).replace(/[!'()*]/g,
    (character) => `%${character.charCodeAt(0).toString(16).toUpperCase()}`);
  return redirect(`${path}?${error ? "error" : "message"}=${encoded}`);
}

async function backendError(response: Response, fallback: string): Promise<string> {
  reportError(new Error(`HTTP ${response.status}`), "Promo backend request rejected.");
  if (response.status === 401) return "Přihlášení vypršelo. Přihlaste se znovu.";
  try {
    const body = row(await response.json());
    for (const key of ["detail", "message"]) {
      if (typeof body[key] === "string" && body[key].trim()) return body[key];
    }
  } catch (error) {
    reportError(error, "Promo backend error response could not be parsed.");
  }
  return `${fallback} (HTTP ${response.status})`;
}

function campaignPayload(context: Context, editing: boolean): { payload: Row; id: string | null } {
  const text = (key: string): string => {
    const value = context.form.get(key);
    if (typeof value !== "string") throw new ValidationError();
    return value;
  };
  const name = text("name");
  const productId = uuid(text("productId"));
  const supplierId = uuid(text("supplierId"));
  const startsOn = text("startsOn");
  const endsOn = text("endsOn");
  const id = editing ? uuid(text("id")) : null;
  const planned = text("plannedQuantity");
  if (!/^[+-]?\d+$/.test(planned) || planned.replace(/^[+-]/, "").replace(/^0+/, "").length > 10
    || Number(planned) > 2147483647 || Number(planned) < -2147483648) throw new ValidationError();
  const decimals: Row = {};
  for (const key of ["regularPrice", "promoPrice", "supplierPurchasePrice", "marketingContribution"]) {
    const value = text(key);
    if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)) throw new ValidationError();
    decimals[key] = value;
  }
  return { id, payload: { name, productId, supplierId, startsOn, endsOn,
    regularPrice: decimals.regularPrice, promoPrice: decimals.promoPrice,
    supplierPurchasePrice: decimals.supplierPurchasePrice, plannedQuantity: Number(planned),
    marketingContribution: decimals.marketingContribution } };
}

export async function handlePromo(context: Context): Promise<Page | Response | null> {
  if (context.url.pathname !== "/promo" || !["GET", "POST"].includes(context.request.method)) return null;
  if (context.request.method === "GET") {
    let campaigns: unknown[] = [];
    let options: Row = { products: [], suppliers: [] };
    let loadError: string | null = null;
    try {
      const loadedCampaigns = await context.backend.json("GET", "/api/v1/promo-campaigns");
      campaigns = Array.isArray(loadedCampaigns) ? loadedCampaigns : Object.values(row(loadedCampaigns));
      const loaded = row(await context.backend.json("GET", "/api/v1/promo-campaigns/options"));
      options = {
        products: Array.isArray(loaded.products) ? loaded.products : [],
        suppliers: Array.isArray(loaded.suppliers) ? loaded.suppliers : [],
      };
    } catch (error) {
      reportError(error, "Promo campaigns request failed.");
      loadError = `Backend pro promo kampaně není dostupný: ${error instanceof Error ? error.message : String(error)}`;
    }
    return context.render("promo/index.html.twig", {
      pageTitle: "Promo kampaně", breadcrumb: "PRODEJ / PROMO", campaigns, options, statuses,
      loadError, actionError: context.url.searchParams.get("error"), message: context.url.searchParams.get("message"),
    });
  }
  const action = context.form.get("action");
  if (action === "create" || action === "edit") {
    const editing = action === "edit";
    let campaign: ReturnType<typeof campaignPayload>;
    try {
      campaign = campaignPayload(context, editing);
    } catch (error) {
      if (!(error instanceof ValidationError)) throw error;
      return feedback("/promo", "Vyplňte platné údaje kampaně.", true);
    }
    try {
      const response = await context.backend.request(editing ? "PUT" : "POST",
        `/api/v1/promo-campaigns${editing ? `/${campaign.id}` : ""}`, campaign.payload);
      if (response.status === (editing ? 200 : 201)) {
        return feedback("/promo", editing ? "Promo kampaň byla upravena." : "Promo kampaň byla přidána.");
      }
      return feedback("/promo", await backendError(response,
        editing ? "Backend odmítl úpravu kampaně." : "Backend odmítl vytvoření kampaně."), true);
    } catch (error) {
      reportError(error, "Promo campaign save failed.");
      return feedback("/promo", "Kampaň se nepodařilo uložit. Ověřte stav kampaní před opakováním.", true);
    }
  }
  let id: string;
  const status = context.form.get("status");
  try {
    id = uuid(context.form.get("id"));
    if (typeof status !== "string" || !statuses.includes(status)) throw new ValidationError();
  } catch (error) {
    if (!(error instanceof ValidationError)) throw error;
    return feedback("/promo", "Neplatná změna stavu.", true);
  }
  try {
    const response = await context.backend.request("PATCH", `/api/v1/promo-campaigns/${id}/status`, { status });
    if (response.status === 200) return feedback("/promo", "Stav kampaně byl změněn.");
    return feedback("/promo", await backendError(response, "Změnu stavu backend odmítl."), true);
  } catch (error) {
    reportError(error, "Promo campaign status change failed.");
    return feedback("/promo", "Změna stavu se nezdařila. Ověřte stav kampaně před opakováním.", true);
  }
}
