import { randomBytes } from "node:crypto";
import { mkdir, readFile, rename, unlink, writeFile } from "node:fs/promises";
import path from "node:path";
import type { Row } from "./data";
import { record, text, type Json } from "./data";

export const CART_COOKIE = "erp_cart";
export type Cart = { quantities: Record<string, number>; delivery: Row | null; updatedAt: number };
export const UUID = /^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i;

function filename(id: string) {
  if (!/^[a-f0-9]{64}$/.test(id)) throw new Error("Invalid cart identifier.");
  return path.join(process.env.SESSION_DIR || path.join(process.cwd(), "var/sessions"), "carts", `${id}.json`);
}

export async function loadCart(id?: string): Promise<{ id: string; cart: Cart }> {
  if (id && /^[a-f0-9]{64}$/.test(id)) {
    try {
      const value: Cart = JSON.parse(await readFile(filename(id), "utf8"));
      if (Date.now() - value.updatedAt < 7 * 24 * 60 * 60 * 1000) return { id, cart: value };
    } catch (error) {
      if (!(error instanceof Error && "code" in error && error.code === "ENOENT")) throw error;
    }
  }
  return { id: randomBytes(32).toString("hex"), cart: { quantities: {}, delivery: null, updatedAt: Date.now() } };
}

export async function saveCart(id: string, cart: Cart) {
  const file = filename(id);
  await mkdir(path.dirname(file), { recursive: true, mode: 0o700 });
  const temporary = `${file}.${randomBytes(8).toString("hex")}.tmp`;
  await writeFile(temporary, JSON.stringify({ ...cart, updatedAt: Date.now() }), { mode: 0o600 });
  try { await rename(temporary, file); } catch (error) { await unlink(temporary); throw error; }
}

export function deliveryValues(input: Json): Row {
  const source = record(input);
  const delivery = Object.fromEntries(["firstName", "lastName", "phone", "street", "city", "postalCode"].map((key) => [key, text(source[key]).trim()]));
  if (Object.values(delivery).some((value) => !value || value.length > 160)
    || !/^[+0-9 ()-]{9,20}$/.test(delivery.phone)
    || !/^\d{3} ?\d{2}$/.test(delivery.postalCode)) {
    throw new Error("Vyplňte jméno, telefon a úplnou adresu zákazníka.");
  }
  return delivery;
}

export function changeQuantity(current: number, action: string, quantity: number, available: number): number {
  if (!Number.isInteger(quantity) || quantity < 1) throw new Error("Zadejte kladné celé množství.");
  const desired = action === "remove" ? 0 : action === "decrease" ? current - 1
    : action === "increase" ? current + 1 : action === "add" ? current + quantity
    : action === "set" ? quantity : NaN;
  if (!Number.isFinite(desired)) throw new Error("Neznámá akce košíku.");
  return Math.max(0, Math.min(desired, available));
}
