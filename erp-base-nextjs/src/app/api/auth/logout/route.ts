import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/backend";
import { isSameOrigin } from "@/lib/policy";
import { destroySession, readSession, SESSION_COOKIE } from "@/lib/session-store";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request.headers, request.url)) return Response.json({ error: "Neplatný původ požadavku." }, { status: 403 });
  const id = request.cookies.get(SESSION_COOKIE)?.value;
  try {
    const session = await readSession(id);
    if (session) {
      try {
        const response = await backendFetch("auth/logout", { method: "POST" }, session.token);
        if (!response.ok) console.error("Backend logout rejected.", response.status);
      } catch (error) { console.error("Backend logout unavailable.", error); }
    }
    await destroySession(id);
    const response = NextResponse.json({ redirect: "/login" });
    response.cookies.set(SESSION_COOKIE, "", { httpOnly: true, sameSite: "lax", path: "/", maxAge: 0 });
    return response;
  } catch (error) {
    console.error("Session logout failed.", error);
    return Response.json({ error: "Odhlášení se nepodařilo dokončit." }, { status: 503 });
  }
}
