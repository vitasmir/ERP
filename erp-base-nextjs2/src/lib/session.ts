import { createHash, randomBytes } from "node:crypto";
import { mkdir, readFile, rename, unlink, writeFile } from "node:fs/promises";
import path from "node:path";
import { row, type Session } from "./context";

export const SESSION_COOKIE = "erp_nextjs2_session";
export const MAX_SESSION_AGE = 8 * 60 * 60 * 1000;
export const IDLE_SESSION_AGE = 30 * 60 * 1000;
const locks = new Map<string, Promise<void>>();

function filename(id: string): string {
  if (!/^[a-f0-9]{64}$/.test(id)) throw new Error("Invalid session identifier.");
  return path.resolve(process.env.SESSION_DIR || "var/sessions", `${createHash("sha256").update(id).digest("hex")}.json`);
}

function missing(error: unknown): boolean {
  return error instanceof Error && "code" in error && error.code === "ENOENT";
}

export function newSession(): Session {
  const now = Date.now();
  return { cart: {}, expiresAt: now + MAX_SESSION_AGE, lastActivityAt: now };
}

export function newSessionId(): string {
  return randomBytes(32).toString("hex");
}

export function expired(session: Session, now = Date.now()): boolean {
  return Boolean(session.token) && (session.expiresAt <= now || now - session.lastActivityAt >= IDLE_SESSION_AGE);
}

export function sessionId(request: Request): string | undefined {
  const cookie = (request.headers.get("cookie") || "").split(";")
    .map((part) => part.trim()).find((part) => part.startsWith(`${SESSION_COOKIE}=`));
  const value = cookie?.slice(SESSION_COOKIE.length + 1);
  return value && /^[a-f0-9]{64}$/.test(value) ? value : undefined;
}

export async function readSession(id?: string): Promise<Session | null> {
  if (!id) return null;
  let data: unknown;
  try { data = JSON.parse(await readFile(filename(id), "utf8")); }
  catch (error) { if (missing(error)) return null; throw error; }
  const candidate = row(data);
  if (typeof candidate.expiresAt !== "number" || !Number.isFinite(candidate.expiresAt)
    || typeof candidate.lastActivityAt !== "number" || !Number.isFinite(candidate.lastActivityAt)
    || candidate.token !== undefined && typeof candidate.token !== "string"
    || candidate.userName !== undefined && typeof candidate.userName !== "string"
    || candidate.roleName !== undefined && typeof candidate.roleName !== "string") {
    throw new Error("Invalid stored session.");
  }
  const cart = row(candidate.cart);
  for (const [id, quantity] of Object.entries(cart)) {
    if (!/^[a-f0-9-]{36}$/i.test(id) || typeof quantity !== "number"
      || !Number.isSafeInteger(quantity) || quantity < 1) throw new Error("Invalid stored cart.");
  }
  let delivery: Record<string, string> | undefined;
  if (candidate.delivery !== undefined) {
    delivery = {};
    for (const [key, value] of Object.entries(row(candidate.delivery))) {
      if (typeof value !== "string") throw new Error("Invalid stored delivery.");
      delivery[key] = value;
    }
  }
  const session: Session = {
    token: candidate.token, userName: candidate.userName, roleName: candidate.roleName,
    expiresAt: candidate.expiresAt, lastActivityAt: candidate.lastActivityAt,
    cart: Object.fromEntries(Object.entries(cart).map(([id, quantity]) => [id, Number(quantity)])), delivery,
  };
  if (expired(session)) { await destroySession(id); return null; }
  return session;
}

export async function saveSession(id: string, session: Session): Promise<void> {
  const file = filename(id);
  await mkdir(path.dirname(file), { recursive: true, mode: 0o700 });
  const temporary = `${file}.${randomBytes(8).toString("hex")}.tmp`;
  await writeFile(temporary, JSON.stringify(session), { mode: 0o600 });
  try { await rename(temporary, file); }
  catch (error) { await unlink(temporary); throw error; }
}

export async function destroySession(id: string): Promise<void> {
  try { await unlink(filename(id)); } catch (error) { if (!missing(error)) throw error; }
}

export function sessionCookie(id: string): string {
  return `${SESSION_COOKIE}=${id}; Path=/; HttpOnly; SameSite=Lax${process.env.COOKIE_SECURE === "true" ? "; Secure" : ""}`;
}

export async function withSessionLock<T>(id: string, action: () => Promise<T>): Promise<T> {
  const previous = locks.get(id) || Promise.resolve();
  let release!: () => void;
  const lock = new Promise<void>((resolve) => { release = resolve; });
  locks.set(id, lock);
  await previous;
  try { return await action(); }
  finally {
    release();
    if (locks.get(id) === lock) locks.delete(id);
  }
}
