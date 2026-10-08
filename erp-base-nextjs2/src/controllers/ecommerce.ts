import {
  decimal, feedback, field, fields, integer, optionalUuid, reportError, row, rows, str, uuid,
  ValidationError, type Context, type Page, type Row,
} from "../lib/context";

export function categoryOptions(categories: Row[], depth = 0): Row[] {
  return categories.flatMap((category) => [
    { ...category, depth },
    ...categoryOptions(rows(category.children ?? []), depth + 1),
  ]);
}

export async function handleEcommerce(context: Context): Promise<Page | Response | null> {
  if (context.url.pathname !== "/ecommerce") return null;
  if (context.request.method === "POST") {
    try { return await save(context); }
    catch (error) {
      reportError(error, "Could not save eCommerce change.");
      return feedback("/ecommerce", error instanceof ValidationError || error instanceof SyntaxError
        ? "Zkontrolujte zadané hodnoty." : "Změnu se nepodařilo uložit. Backend požadavek odmítl nebo není dostupný.", true);
    }
  }
  const data: Row = {
    ecommerce: null, selectedCategoryId: null, categoryOptions: [], eshopMarginPercent: null,
    eshopDefaultVatRate: null, error: null, message: context.url.searchParams.get("message"),
    actionError: context.url.searchParams.get("error"),
  };
  try {
    const homepage = row(await context.backend.json("GET", "/api/v1/catalog/homepage"));
    const settings = row(await context.backend.json("GET", "/api/v1/settings"));
    const categories = rows(await context.backend.json("GET", "/api/v1/catalog/categories/tree"));
    const products = rows(await context.backend.json("GET", "/api/v1/catalog/products"));
    for (const product of products) {
      product.availability = rows(await context.backend.json("GET", `/api/v1/catalog/products/${uuid(product.id)}/availability`));
    }
    try { data.selectedCategoryId = optionalUuid(context.url.searchParams.get("categoryId")); }
    catch (error) { reportError(error, "Invalid catalog category filter; showing all products."); }
    data.eshopMarginPercent = settings.eshopMarginPercent ?? null;
    data.eshopDefaultVatRate = settings.eshopDefaultVatRate ?? null;
    data.categoryOptions = categoryOptions(categories);
    data.ecommerce = { homepage, categories, products: data.selectedCategoryId === null
      ? products : products.filter((product) => product.categoryId === data.selectedCategoryId) };
  } catch (error) {
    reportError(error, "Could not load eCommerce.");
    data.error = "Backend pro eCommerce není dostupný. Katalog se nepodařilo načíst.";
  }
  return context.render("ecommerce/index.html.twig", data);
}

async function save(context: Context): Promise<Response> {
  const action = field(context, "action");
  const base = "/api/v1/catalog";
  let method = "POST";
  let path: string;
  let body: unknown;
  let message: string;
  switch (action) {
    case "homepage":
      method = "PUT"; path = `${base}/homepage`;
      body = { ...fields(context, ["design", "headline", "subheadline"]),
        textX: decimal(field(context, "textX")), textY: decimal(field(context, "textY")) };
      message = "Homepage byla uložena."; break;
    case "category":
    case "updateCategory":
      method = action === "category" ? "POST" : "PUT";
      path = `${base}/categories${action === "category" ? "" : `/${uuid(field(context, "categoryId"))}`}`;
      body = { ...fields(context, ["name", "slug"]), parentId: optionalUuid(field(context, "parentId")), sortOrder: 10, active: true };
      message = action === "category" ? "Kategorie byla přidána." : "Kategorie byla přejmenována."; break;
    case "deleteCategory":
      method = "DELETE"; path = `${base}/categories/${uuid(field(context, "categoryId"))}`;
      message = "Kategorie byla smazána."; break;
    case "product": {
      const id = optionalUuid(field(context, "productId"));
      const product: Row = {
        ...fields(context, ["sku", "name", "unit", "description", "imageUrl"]),
        categoryId: optionalUuid(field(context, "categoryId")), active: field(context, "active") === "on",
      };
      for (const name of ["price", "purchasePrice", "vatRate", "eshopMarginPercent"]) {
        product[name] = decimal(context.form.get(name), true);
      }
      const saved = row(await context.backend.json(id ? "PUT" : "POST", `${base}/products${id ? `/${id}` : ""}`, product));
      return feedback("/ecommerce", "Produkt byl uložen.", false,
        saved.categoryId ? { categoryId: uuid(saved.categoryId) } : {}, saved.id ? `#product-${uuid(saved.id)}` : "");
    }
    case "import":
      body = JSON.parse(field(context, "products"));
      if (!Array.isArray(body) || body.some((product: unknown) => product === null || typeof product !== "object" || Array.isArray(product))) {
        throw new ValidationError("Neplatný seznam produktů.");
      }
      path = `${base}/products/import`; message = "Produkty byly naimportovány."; break;
    case "estimate": {
      const estimate = row(await context.backend.json("POST", `${base}/delivery-estimates`, {
        productId: uuid(field(context, "productId")), quantity: integer(field(context, "quantity"), 1),
        postalCode: context.form.get("postalCode"), method: context.form.get("method"),
      }));
      const available = estimate.available === true;
      return feedback("/ecommerce", available
        ? `${str(estimate.label)}: doručení do ${str(estimate.estimatedDate)}`
        : str(estimate.reason) || "Doručení není dostupné.", !available);
    }
    case "toggleProduct":
    case "addImage":
    case "activateImage":
    case "deleteImage":
    case "removeFromCategory":
    case "deleteProduct":
      path = `${base}/products/${uuid(field(context, "productId"))}`;
      if (action === "toggleProduct") {
        method = "PUT"; path += "/active"; body = { active: field(context, "active") === "on" };
        message = "Stav produktu byl změněn.";
      } else if (action === "addImage") {
        path += "/images"; body = { imageUrl: context.form.get("imageUrl"), active: field(context, "active") === "on" };
        message = "Obrázek byl přidán.";
      } else if (action === "activateImage" || action === "deleteImage") {
        path += `/images/${uuid(field(context, "imageId"))}${action === "activateImage" ? "/active" : ""}`;
        method = action === "activateImage" ? "PUT" : "DELETE";
        message = action === "activateImage" ? "Aktivní obrázek byl změněn." : "Obrázek byl odstraněn.";
      } else if (action === "removeFromCategory") {
        path += "/category"; method = "PUT"; message = "Produkt byl odebrán z kategorie.";
      } else { method = "DELETE"; message = "Produkt byl smazán."; }
      break;
    default: return feedback("/ecommerce", "Neznámá eCommerce akce.", true);
  }
  await context.backend.mutate(method, path, body);
  return feedback("/ecommerce", message);
}
