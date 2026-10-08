"use client";

import { useState, type FormEvent } from "react";
import { Alert } from "./ui";

export function Login() {
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(""); setPending(true);
    const data = new FormData(event.currentTarget);
    try {
      const response = await fetch("/api/auth/login", { method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: data.get("username"), password: data.get("password") }),
      });
      const body: { error?: string } = await response.json();
      if (!response.ok) throw new Error(body.error || "Přihlášení se nezdařilo.");
      window.location.assign("/apps");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Přihlášení není dostupné."); }
    finally { setPending(false); }
  }
  return <main className="login-page"><a href="/eshop">E-shop</a><h1>Retail ERP</h1><p>Next.js · React · TypeScript</p>
    <Alert>{error}</Alert><form onSubmit={login} className="erp-form">
      <label>Uživatelské jméno<input name="username" required maxLength={120} autoComplete="username" /></label>
      <label>Heslo<input name="password" type="password" required autoComplete="current-password" /></label>
      <button className="primary" disabled={pending}>{pending ? "Přihlašování…" : "Přihlásit se"}</button>
    </form></main>;
}

export function Logout() {
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  async function logout() {
    setPending(true); setError("");
    try {
      const response = await fetch("/api/auth/logout", { method: "POST" });
      if (!response.ok) throw new Error("Odhlášení se nepodařilo dokončit.");
      window.location.assign("/login");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Odhlášení není dostupné."); }
    finally { setPending(false); }
  }
  return <div><button onClick={logout} disabled={pending}>Odhlásit se</button><Alert>{error}</Alert></div>;
}
