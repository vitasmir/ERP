import { handleAdmin } from "../controllers/admin";
import { handleAuth } from "../controllers/auth";
import { handleEcommerce } from "../controllers/ecommerce";
import { handleHome } from "../controllers/home";
import { handleIndependent } from "../controllers/independent";
import { handleInventory } from "../controllers/inventory";
import { handleOperations } from "../controllers/operations";
import { handlePromo } from "../controllers/promo";
import { handleShop } from "../controllers/shop";
import { Backend, feedback, redirect, reportError, type Context } from "./context";
import { renderPage } from "./templates";
import {
  destroySession, newSession, newSessionId, readSession, saveSession, sessionCookie, sessionId, withSessionLock,
} from "./session";

const moduleChecks: Record<string, [string, string]> = {
  companies: ["companies", "Společnosti"], accounting: ["accounting/overview", "Účetnictví"],
  crm: ["crm/overview", "CRM"], documents: ["documents/overview", "Dokumenty"],
  ecommerce: ["catalog/homepage", "eCommerce"], helpdesk: ["helpdesk/overview", "Helpdesk"],
  hr: ["hr/overview", "Lidé"], inventory: ["inventory/overview", "Sklad"],
  manufacturing: ["manufacturing/overview", "Výroba"], marketing: ["marketing/overview", "Marketing"],
  planning: ["planning/overview", "Plánování"], pos: ["pos/overview", "Pokladna"],
  projects: ["projects/overview", "Projekty"], promo: ["promo-campaigns", "Promo kampaně"],
  purchase: ["purchase/overview", "Nákup"], sales: ["sales/overview", "Prodej"],
  website: ["website/overview", "Web"], roles: ["roles", "Role a oprávnění"],
  "role-modules": ["roles", "Role pro moduly"], settings: ["settings", "Nastavení"], users: ["users", "Uživatelé"],
};

export function sameOrigin(request: Request): boolean {
  const source = request.headers.get("origin") ?? request.headers.get("referer");
  if (!source) return false;
  try {
    const url = new URL(request.url);
    const target = new URL(`${url.protocol}//${request.headers.get("host") || url.host}`);
    return new URL(source).origin === target.origin;
  } catch { return false; }
}

function secureResponse(response: Response): Response {
  response.headers.set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
  response.headers.set("Pragma", "no-cache");
  response.headers.set("Expires", "0");
  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("Referrer-Policy", "same-origin");
  return response;
}

export async function dispatch(request: Request): Promise<Response> {
  if (!["GET", "HEAD", "POST", "PUT"].includes(request.method)) {
    return secureResponse(new Response("Method not allowed.", { status: 405, headers: { Allow: "GET, HEAD, POST, PUT" } }));
  }
  if (["POST", "PUT"].includes(request.method) && !sameOrigin(request)) {
    return secureResponse(new Response("Request origin could not be verified.", { status: 403 }));
  }
  const incomingId = sessionId(request);
  const id = incomingId || newSessionId();
  return withSessionLock(id, async () => {
    try {
      const existing = await readSession(incomingId);
      const session = existing || newSession();
      if (session.token) session.lastActivityAt = Date.now();
      const url = new URL(request.url);
      const module = url.pathname.split("/")[1];
      const privatePath = url.pathname === "/" || module === "apps" || Object.hasOwn(moduleChecks, module);
      if (privatePath && !session.token) return secureResponse(redirect("/login", 302));
      const form = request.method === "POST" ? await request.formData() : new FormData();
      const context: Context = {
        request: request.method === "HEAD" ? new Request(request.url, { headers: request.headers }) : request,
        url, form, session, backend: new Backend(session),
        render: (template, data = {}, status = 200) => ({ template, data, status }),
      };
      if ((request.method === "GET" || request.method === "HEAD") && moduleChecks[module]) {
        const [path, name] = moduleChecks[module];
        try {
          const response = await context.backend.request("GET", `/api/v1/${path}`);
          if (response.status === 403) return secureResponse(feedback("/apps", `Uživatel nemá oprávnění k modulu ${name}.`, true));
        } catch (error) { reportError(error, "Module permission check could not reach the backend."); }
      }
      const handlers = [handleAuth, handleHome, handleAdmin, handleEcommerce, handleShop, handleInventory,
        handleOperations, handlePromo, handleIndependent];
      let response: Response | undefined;
      for (const handler of handlers) {
        const result = await handler(context);
        if (result === null) continue;
        response = result instanceof Response ? result : new Response(
          request.method === "HEAD" ? null : await renderPage(result, context),
          { status: result.status, headers: { "Content-Type": "text/html; charset=utf-8" } },
        );
        break;
      }
      response ??= new Response("Stránka nebyla nalezena.", { status: 404 });
      const rotating = url.pathname === "/logout"
        || url.pathname === "/login" && request.method === "POST" && response.status === 303 && Boolean(session.token);
      if (rotating && incomingId) await destroySession(incomingId);
      const outputId = rotating || !existing ? newSessionId() : id;
      await saveSession(outputId, session);
      if (rotating || !existing) response.headers.append("Set-Cookie", sessionCookie(outputId));
      return secureResponse(request.method === "HEAD" ? new Response(null, {
        status: response.status, headers: response.headers,
      }) : response);
    } catch (error) {
      reportError(error, "Frontend request failed.");
      return secureResponse(new Response("Požadavek se nepodařilo zpracovat. Zkuste to prosím znovu.", { status: 500 }));
    }
  });
}
