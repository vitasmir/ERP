"use client";

import { useState, type ReactNode, type FormEvent } from "react";
import { api } from "@/lib/client-api";
import { money, text, type Json, type Row } from "@/lib/data";

export type Option = { value: string; label: string };
export type Field = {
  name: string;
  label: string;
  type?: "text" | "number" | "date" | "datetime-local" | "email" | "password" | "color" | "checkbox" | "textarea" | "select" | "hidden";
  required?: boolean;
  min?: number;
  max?: number;
  step?: string;
  maxLength?: number;
  options?: Option[];
  value?: string | number | boolean;
};

export function Alert({ children, error = true }: { children: ReactNode; error?: boolean }) {
  return children ? <p className={`notice ${error ? "error" : "success"}`} role={error ? "alert" : "status"}>{children}</p> : null;
}

export function Module({ title, children, error, loading }: { title: string; children: ReactNode; error?: string; loading?: boolean }) {
  return <section className="module-page"><header className="module-header"><a href="/apps">← Aplikace</a><h1>{title}</h1></header>
    <Alert>{error}</Alert>{loading ? <p role="status">Načítání…</p> : children}</section>;
}

export function Metrics({ data, labels }: { data: Row; labels: Record<string, string> }) {
  return <section className="metrics">{Object.entries(labels).map(([key, label]) =>
    <article key={key}><span>{label}</span><strong>{text(data[key]) || "0"}</strong></article>)}</section>;
}

export function Input({ field, initial }: { field: Field; initial?: Row }) {
  const value = field.value ?? initial?.[field.name];
  const props = { name: field.name, required: field.required, min: field.min, max: field.max, maxLength: field.maxLength };
  if (field.type === "hidden") return <input type="hidden" name={field.name} value={text(value)} />;
  return <label>{field.label}{field.type === "select"
    ? <select {...props} defaultValue={text(value)}>{field.options?.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}</select>
    : field.type === "textarea" ? <textarea {...props} defaultValue={text(value)} rows={3} />
    : field.type === "checkbox" ? <input name={field.name} type="checkbox" defaultChecked={value === true} />
    : <input {...props} type={field.type ?? "text"} step={field.step ?? (field.type === "number" ? "any" : undefined)} defaultValue={text(value)} />}</label>;
}

export function formValues(fields: Field[], form: FormData): Row {
  return Object.fromEntries(fields.map((field) => {
    const raw = form.get(field.name);
    if (field.type === "checkbox") return [field.name, raw !== null];
    if (field.type === "number") {
      if (raw === null || raw === "") return [field.name, null];
      const value = Number(raw);
      if (!Number.isFinite(value)) throw new Error(`${field.label}: neplatné číslo.`);
      return [field.name, value];
    }
    return [field.name, typeof raw === "string" ? raw : ""];
  }));
}

export function ApiForm({ path, method = "POST", fields, initial, transform, onSaved, label = "Uložit", children }:
  { path: string; method?: string; fields: Field[]; initial?: Row; transform?: (values: Row, form: FormData) => Json;
    onSaved?: (data: Json) => void; label?: string; children?: ReactNode }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPending(true); setError(""); setMessage("");
    try {
      const values = formValues(fields, form);
      const result = await api(path, method, transform ? transform(values, form) : values);
      setMessage("Změna byla uložena.");
      onSaved?.(result);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Změnu nelze uložit.");
    } finally { setPending(false); }
  }
  return <form onSubmit={submit} className="erp-form"><Alert>{error}</Alert><Alert error={false}>{message}</Alert>
    <div className="form-fields">{fields.map((field) => <Input key={field.name} field={field} initial={initial} />)}{children}</div>
    <button className="primary" disabled={pending}>{pending ? "Ukládání…" : label}</button></form>;
}

export function Action({ path, method = "PATCH", body, label, onSaved, confirm }:
  { path: string; method?: string; body?: Json; label: string; onSaved?: () => void; confirm?: string }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  async function run() {
    if (confirm && !window.confirm(confirm)) return;
    setPending(true); setError(""); setDone(false);
    try { await api(path, method, body); setDone(true); onSaved?.(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Akci nelze provést."); }
    finally { setPending(false); }
  }
  return <span className="action"><button type="button" disabled={pending} onClick={run}>{pending ? "…" : label}</button>
    <Alert>{error}</Alert>{done && <small role="status">Uloženo</small>}</span>;
}

export type Column = { key: string; label: string; money?: boolean; render?: (row: Row) => ReactNode };
export function Table({ data, columns, actions }: { data: Row[]; columns: Column[]; actions?: (row: Row) => ReactNode }) {
  return <div className="table-scroll"><table><thead><tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}{actions && <th>Akce</th>}</tr></thead>
    <tbody>{data.map((row, index) => <tr key={text(row.id) || index}>{columns.map((column) =>
      <td key={column.key}>{column.render ? column.render(row) : column.money ? money(row[column.key]) : text(row[column.key])}</td>)}
      {actions && <td className="actions">{actions(row)}</td>}</tr>)}
      {!data.length && <tr><td colSpan={columns.length + (actions ? 1 : 0)}>Žádné záznamy.</td></tr>}</tbody></table></div>;
}

export function Editor({ title = "Upravit", children }: { title?: string; children: ReactNode }) {
  return <details className="editor"><summary>{title}</summary>{children}</details>;
}
