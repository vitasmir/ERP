import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/backend";
import { isSameOrigin } from "@/lib/policy";
import { createSession, destroySession, MAX_SESSION_AGE, SESSION_COOKIE } from "@/lib/session-store";
import { record, text, type Json } from "@/lib/data";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request.headers, request.url)) return Response.json({ error: "Neplatný původ požadavku." }, { status: 403 });
  let credentials: unknown;
  try { credentials = await request.json(); }
  catch { return Response.json({ error: "Neplatné údaje přihlášení." }, { status: 400 }); }
  if (!credentials || typeof credentials !== "object" || !("username" in credentials) || !("password" in credentials)
    || typeof credentials.username !== "string" || typeof credentials.password !== "string"
    || !credentials.username.trim() || !credentials.password || credentials.username.length > 120 || credentials.password.length > 1024) {
    return Response.json({ error: "Zadejte uživatelské jméno a heslo." }, { status: 400 });
  }
  try {
    const upstream = await backendFetch("auth/login", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: credentials.username, password: credentials.password }),
    });
    if (!upstream.ok) return Response.json({ error: upstream.status >= 500 ? "Přihlašovací služba není dostupná." : "Přihlášení se nezdařilo." },
      { status: upstream.status >= 500 ? 503 : 401 });
    const user = record(await upstream.json() as Json);
    const token = text(user.token);
    if (!token) throw new Error("Login response did not include a token.");
    const id = await createSession({ token, userName: text(user.fullName), roleName: text(user.roleName) });
    await destroySession(request.cookies.get(SESSION_COOKIE)?.value);
    const response = NextResponse.json({ redirect: "/apps" });
    response.cookies.set(SESSION_COOKIE, id, {
      httpOnly: true, sameSite: "lax", secure: process.env.COOKIE_SECURE === "true" || request.nextUrl.protocol === "https:",
      path: "/", maxAge: MAX_SESSION_AGE,
    });
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    console.error("ERP login failed.", error);
    return Response.json({ error: "Přihlašovací služba není dostupná." }, { status: 503 });
  }
}
