import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/backend";
import { isSameOrigin, publicApi, validApiPath } from "@/lib/policy";
import { readSession, SESSION_COOKIE } from "@/lib/session-store";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

async function handle(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path: segments } = await context.params;
  const path = segments.join("/");
  if (!validApiPath(segments) || ["auth/login", "auth/logout"].includes(path)) {
    return Response.json({ error: "Neplatná API cesta." }, { status: 400 });
  }
  if (!["GET", "HEAD", "OPTIONS"].includes(request.method) && !isSameOrigin(request.headers, request.url)) {
    return Response.json({ error: "Původ požadavku nelze ověřit." }, { status: 403 });
  }
  try {
    const session = await readSession(request.cookies.get(SESSION_COOKIE)?.value, true);
    if (!session && !publicApi(request.method, path)) {
      return Response.json({ error: "Přihlášení vypršelo. Přihlaste se znovu." }, { status: 401 });
    }
    const hasBody = !["GET", "HEAD"].includes(request.method);
    if (Number(request.headers.get("content-length")) > 10 * 1024 * 1024) {
      return Response.json({ error: "Soubor je příliš velký." }, { status: 413 });
    }
    const body = hasBody ? await request.arrayBuffer() : undefined;
    if (body && body.byteLength > 10 * 1024 * 1024) {
      return Response.json({ error: "Soubor je příliš velký." }, { status: 413 });
    }
    const headers = new Headers();
    for (const name of ["content-type", "accept"]) {
      const value = request.headers.get(name);
      if (value) headers.set(name, value);
    }
    const upstream = await backendFetch(path + request.nextUrl.search, {
      method: request.method, body, headers,
    }, session?.token);
    const responseHeaders = new Headers({ "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" });
    for (const name of ["content-type", "content-disposition"]) {
      const value = upstream.headers.get(name);
      if (value) responseHeaders.set(name, value);
    }
    if (upstream.status >= 500) console.error("ERP backend request failed.", request.method, path, upstream.status);
    return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  } catch (error) {
    console.error("ERP API request unavailable.", request.method, path, error);
    return Response.json({ error: "Backend není dostupný. Požadavek se nepodařilo dokončit." }, { status: 503 });
  }
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH, handle as DELETE, handle as HEAD };
