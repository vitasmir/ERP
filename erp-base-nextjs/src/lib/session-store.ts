import { createHash, randomBytes } from "node:crypto";
import { mkdir, readFile, rename, unlink, writeFile } from "node:fs/promises";
import path from "node:path";

export const SESSION_COOKIE = "erp_session";
export const MAX_SESSION_AGE = 8 * 60 * 60;
export const IDLE_SESSION_AGE = 30 * 60;

export type Session = {
  token: string;
  userName: string;
  roleName: string;
  expiresAt: number;
  lastActivityAt: number;
};

function filename(id: string): string | null {
  if (!/^[a-f0-9]{64}$/.test(id)) return null;
  return path.join(process.env.SESSION_DIR || path.join(process.cwd(), "var/sessions"),
    `${createHash("sha256").update(id).digest("hex")}.json`);
}

function missing(error: unknown): boolean {
  return error instanceof Error && "code" in error && error.code === "ENOENT";
}

export function isExpired(session: Session, now = Date.now()): boolean {
  return session.expiresAt <= now || now - session.lastActivityAt >= IDLE_SESSION_AGE * 1000;
}

async function persist(id: string, session: Session): Promise<void> {
  const file = filename(id);
  if (!file) throw new Error("Invalid session identifier.");
  await mkdir(path.dirname(file), { recursive: true, mode: 0o700 });
  const temporary = `${file}.${randomBytes(8).toString("hex")}.tmp`;
  await writeFile(temporary, JSON.stringify(session), { mode: 0o600 });
  try { await rename(temporary, file); }
  catch (error) { await unlink(temporary); throw error; }
}

export async function createSession(user: Pick<Session, "token" | "userName" | "roleName">): Promise<string> {
  const id = randomBytes(32).toString("hex");
  const now = Date.now();
  await persist(id, { ...user, expiresAt: now + MAX_SESSION_AGE * 1000, lastActivityAt: now });
  return id;
}

export async function destroySession(id?: string): Promise<void> {
  const file = id ? filename(id) : null;
  if (!file) return;
  try { await unlink(file); } catch (error) { if (!missing(error)) throw error; }
}

export async function readSession(id?: string, touch = false): Promise<Session | null> {
  const file = id ? filename(id) : null;
  if (!file || !id) return null;
  let content: string;
  try { content = await readFile(file, "utf8"); }
  catch (error) { if (missing(error)) return null; throw error; }
  const candidate: unknown = JSON.parse(content);
  if (!candidate || typeof candidate !== "object"
    || !("token" in candidate) || typeof candidate.token !== "string"
    || !("userName" in candidate) || typeof candidate.userName !== "string"
    || !("roleName" in candidate) || typeof candidate.roleName !== "string"
    || !("expiresAt" in candidate) || typeof candidate.expiresAt !== "number"
    || !("lastActivityAt" in candidate) || typeof candidate.lastActivityAt !== "number") {
    throw new Error("Invalid stored session.");
  }
  const session: Session = {
    token: candidate.token, userName: candidate.userName, roleName: candidate.roleName,
    expiresAt: candidate.expiresAt, lastActivityAt: candidate.lastActivityAt,
  };
  if (isExpired(session)) { await destroySession(id); return null; }
  if (touch) {
    session.lastActivityAt = Date.now();
    await persist(id, session);
  }
  return session;
}
