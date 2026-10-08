import { appCatalog, getAllowedApps } from "../lib/apps";
import { BackendError, redirect, reportError, row, type Context, type Page } from "../lib/context";

export async function handleHome(context: Context): Promise<Page | Response | null> {
  if (context.url.pathname === "/") return redirect("/apps", 302);
  if (context.url.pathname !== "/apps") return null;
  if (!["GET", "HEAD"].includes(context.request.method)) {
    return new Response("Method not allowed.", { status: 405, headers: { Allow: "GET, HEAD" } });
  }
  let allowedModules: string[] = [];
  let error = context.url.searchParams.get("error");
  try {
    const user = row(await context.backend.json("GET", "/api/v1/auth/me"));
    const modules = user.modules ?? [];
    if (!Array.isArray(modules) || !modules.every((item: unknown) => typeof item === "string")) {
      throw new Error("Invalid module permissions.");
    }
    allowedModules = getAllowedApps(user.administrator === true, modules).map((app) => app.id);
  } catch (cause) {
    reportError(cause, "Could not load module permissions.");
    error = cause instanceof BackendError ? `Oprávnění modulů se nepodařilo načíst (HTTP ${cause.status}).`
      : "Backend není dostupný, oprávnění modulů se nepodařilo načíst.";
  }
  return context.render("home/index", {
    moduleCatalog: Object.fromEntries(appCatalog.map((app) => [app.id, app])),
    allowedModules, error,
  });
}
