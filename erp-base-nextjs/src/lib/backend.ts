export async function backendFetch(path: string, init: RequestInit = {}, token?: string): Promise<Response> {
  const base = process.env.BACKEND_URL || "http://localhost:8080";
  const headers = new Headers(init.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  return fetch(`${base.replace(/\/$/, "")}/api/v1/${path}`, {
    ...init, headers, cache: "no-store", redirect: "manual", signal: AbortSignal.timeout(15_000),
  });
}
