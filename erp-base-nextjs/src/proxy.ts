import { NextResponse, type NextRequest } from "next/server";
import { MODULE_PATHS } from "@/lib/policy";
import { readSession, SESSION_COOKIE } from "@/lib/session-store";

export async function proxy(request: NextRequest) {
  const module = request.nextUrl.pathname.split("/")[1];
  if (request.nextUrl.pathname === "/" || MODULE_PATHS.some((path) => path === module)) {
    try {
      if (!await readSession(request.cookies.get(SESSION_COOKIE)?.value, true)) {
        return NextResponse.redirect(new URL("/login", request.url));
      }
    } catch (error) {
      console.error("Session lookup failed.", error);
      return new NextResponse("Přihlášení není dostupné.", { status: 503 });
    }
  }
  const response = NextResponse.next();
  response.headers.set("Cache-Control", "no-store");
  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("Referrer-Policy", "same-origin");
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|assets/|favicon.ico|api/).*)"],
};
