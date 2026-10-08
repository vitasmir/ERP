export type Row = Record<string, unknown>;

export class BackendError extends Error {
  constructor(public readonly status: number, public readonly path: string) {
    super(`Backend požadavek odmítl (HTTP ${status}).`);
  }
}

export class ValidationError extends Error {}

export type Session = {
  token?: string;
  userName?: string;
  roleName?: string;
  expiresAt: number;
  lastActivityAt: number;
  cart: Record<string, number>;
  delivery?: Record<string, string>;
};

export type Page = { template: string; data: Row; status: number };

export type Context = {
  request: Request;
  url: URL;
  form: FormData;
  session: Session;
  backend: Backend;
  render: (template: string, data?: Row, status?: number) => Page;
};

export class Backend {
  constructor(private readonly session: Session, private readonly baseUrl = process.env.BACKEND_URL || "http://localhost:8080") {}

  async request(method: string, path: string, body?: unknown, authenticated = true): Promise<Response> {
    if (!path.startsWith("/api/v1/") || path.includes("..") || path.includes("\\")) {
      throw new ValidationError("Neplatná API cesta.");
    }
    const headers = new Headers();
    if (authenticated && this.session.token) headers.set("Authorization", `Bearer ${this.session.token}`);
    if (body !== undefined && body !== null) headers.set("Content-Type", "application/json");
    return fetch(`${this.baseUrl.replace(/\/$/, "")}${path}`, {
      method, headers, body: body === undefined || body === null ? undefined : JSON.stringify(body),
      cache: "no-store", signal: AbortSignal.timeout(15_000), redirect: "error",
    });
  }

  async mutate(method: string, path: string, body?: unknown, authenticated = true): Promise<Response> {
    const response = await this.request(method, path, body, authenticated);
    if (!response.ok) throw new BackendError(response.status, path);
    return response;
  }

  async json(method: string, path: string, body?: unknown, authenticated = true): Promise<unknown> {
    const response = await this.mutate(method, path, body, authenticated);
    const value: unknown = await response.json();
    if (value === null || typeof value !== "object") throw new Error(`Neplatná JSON odpověď z ${path}.`);
    return value;
  }
}

export function row(value: unknown): Row {
  if (value === null || typeof value !== "object" || Array.isArray(value)) throw new Error("Očekávána JSON struktura.");
  return value as Row;
}

export function rows(value: unknown): Row[] {
  if (!Array.isArray(value)) throw new Error("Očekáván seznam záznamů.");
  return value.map(row);
}

export function str(value: unknown): string {
  return value == null ? "" : String(value);
}

export function field(context: Context, name: string): string {
  const value = context.form.get(name);
  if (value !== null && typeof value !== "string") throw new ValidationError("Neplatná hodnota formuláře.");
  return value ?? "";
}

export function fields(context: Context, names: readonly string[]): Row {
  return Object.fromEntries(names.map((name) => [name, context.form.get(name)]));
}

export function uuid(value: unknown): string {
  if (typeof value !== "string" || !/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value)) {
    throw new ValidationError("Neplatný identifikátor.");
  }
  return value.toLowerCase();
}

export function optionalUuid(value: unknown): string | null {
  return value == null || typeof value === "string" && !value.trim() ? null : uuid(value);
}

export function integer(value: unknown, minimum = Number.MIN_SAFE_INTEGER): number {
  if ((typeof value !== "string" && typeof value !== "number") || !/^[+-]?\d+$/.test(String(value))) {
    throw new ValidationError("Neplatné množství.");
  }
  const result = Number(value);
  if (!Number.isSafeInteger(result) || result < minimum) throw new ValidationError("Neplatné množství.");
  return result;
}

export function decimal(value: unknown, optional = false): string | null {
  if (optional && (value == null || typeof value === "string" && !value.trim())) return null;
  if ((typeof value !== "string" && typeof value !== "number")
    || !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(String(value)) || !Number.isFinite(Number(value))) {
    throw new ValidationError("Neplatná cena.");
  }
  return String(value);
}

export function date(value: unknown): string {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)
    || !Number.isFinite(Date.parse(value)) || new Date(value).toISOString().slice(0, 10) !== value) {
    throw new ValidationError("Neplatné datum.");
  }
  return value;
}

export function normalizedRole(context: Context): string {
  return (context.session.roleName || "").normalize("NFD").replace(/\p{M}/gu, "").trim().toLowerCase();
}

export function redirect(path: string, status = 303): Response {
  return new Response(null, { status, headers: { Location: path } });
}

export function feedback(path: string, message: string, error = false, parameters: Record<string, string> = {}, fragment = ""): Response {
  const query = new URLSearchParams({ [error ? "error" : "message"]: message, ...parameters });
  return redirect(`${path}?${query}${fragment}`);
}

export function reportError(error: unknown, message: string): void {
  console.error(message, error);
}
