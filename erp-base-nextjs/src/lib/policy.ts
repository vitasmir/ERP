export const MODULE_PATHS = [
  "apps", "dashboard", "users", "roles", "role-modules", "companies", "settings",
  "accounting", "crm", "sales", "purchase", "inventory", "manufacturing", "promo", "pos",
  "hr", "documents", "projects", "helpdesk", "website", "ecommerce", "marketing", "planning",
] as const;

export function isSameOrigin(headers: Headers, requestUrl: string): boolean {
  const source = headers.get("origin") || headers.get("referer");
  if (!source) return false;
  try {
    const request = new URL(requestUrl);
    const origin = new URL(source);
    return origin.protocol === request.protocol && origin.host === (headers.get("host") || request.host);
  } catch { return false; }
}

export function validApiPath(segments: string[]): boolean {
  return segments.length > 0 && segments.every((segment) =>
    /^[A-Za-z0-9_.-]+$/.test(segment) && segment !== "." && segment !== "..");
}

export function publicApi(method: string, path: string): boolean {
  if (method === "POST") return path === "sales/orders/checkout" || path === "website/pages/visit";
  if (method !== "GET") return false;
  return ["catalog/categories/tree", "catalog/products", "website/pages/public", "settings/public"].includes(path)
    || /^catalog\/products\/[a-f0-9-]{36}\/availability$/i.test(path);
}
