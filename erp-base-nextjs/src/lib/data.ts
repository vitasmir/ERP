export type Json = string | number | boolean | null | Json[] | { [key: string]: Json };
export type Row = { [key: string]: Json };

export function record(value: Json | undefined): Row {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value : {};
}

export function rows(value: Json | undefined): Row[] {
  return Array.isArray(value) ? value.map(record) : [];
}

export function text(value: Json | undefined): string {
  return typeof value === "string" || typeof value === "number" ? String(value) : "";
}

export function number(value: Json | undefined): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

export function money(value: Json | undefined): string {
  return new Intl.NumberFormat("cs-CZ", { style: "currency", currency: "CZK" }).format(number(value));
}

export function roleKey(value: string): string {
  return value.normalize("NFD").replace(/\p{M}/gu, "").trim().toLowerCase();
}

export function uniqueRoles(names: string[]): string[] {
  return [...new Set(names.map((name) => name.trim()).filter(Boolean))];
}
