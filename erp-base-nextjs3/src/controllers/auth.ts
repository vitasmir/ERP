import { BackendError, field, redirect, reportError, row, str, type Context, type Page } from "../lib/context";
import { MAX_SESSION_AGE } from "../lib/session";

export async function handleAuth(context: Context): Promise<Page | Response | null> {
  const { request, url, backend, session, render } = context;
  if (url.pathname === "/logout") {
    if (request.method !== "POST") return new Response("Method not allowed.", { status: 405, headers: { Allow: "POST" } });
    try {
      const response = await backend.request("POST", "/api/v1/auth/logout");
      if (response.status >= 500) reportError(response.status, "Backend logout failed.");
    } catch (error) { reportError(error, "Backend unavailable during logout."); }
    delete session.token;
    delete session.userName;
    delete session.roleName;
    session.cart = {};
    delete session.delivery;
    return redirect("/login");
  }
  if (url.pathname !== "/login") return null;
  if (request.method === "GET" || request.method === "HEAD") {
    return render("auth/login", { error: url.searchParams.get("error"), authenticated: Boolean(session.token) });
  }
  const username = field(context, "username");
  const password = field(context, "password");
  if (!username || !password) {
    return render("auth/login", { error: "Zadejte uživatelské jméno a heslo.", authenticated: false }, 400);
  }
  try {
    const user = row(await backend.json("POST", "/api/v1/auth/login", { username, password }, false));
    if (typeof user.token !== "string" || !user.token) throw new Error("Login response did not include a token.");
    session.token = user.token;
    session.userName = str(user.fullName) || "Uživatel";
    session.roleName = str(user.roleName) || "Bez role";
    session.lastActivityAt = Date.now();
    session.expiresAt = session.lastActivityAt + MAX_SESSION_AGE;
    return redirect("/apps");
  } catch (error) {
    reportError(error, "ERP login failed.");
    return render("auth/login", {
      error: error instanceof BackendError && error.status < 500 ? "Přihlášení se nezdařilo." : "Přihlašovací služba není dostupná.",
      authenticated: false,
    }, error instanceof BackendError ? 401 : 503);
  }
}
