"use client";

import { useEffect, useState } from "react";
import { appFilters, filterApps, getAllowedApps, type App, type AppFilter } from "@/lib/apps";
import { useApi } from "@/lib/client-api";
import { record, text } from "@/lib/data";
import { Alert } from "./ui";

export function Apps() {
  const access = useApi("auth/me");
  const user = record(access.data);
  const permissions = Array.isArray(user.modules) ? user.modules.map(text) : [];
  const allowed = getAllowedApps(user.administrator === true, permissions);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<AppFilter>("all");
  const [selected, setSelected] = useState<App | null>(null);
  const visibleApps = filterApps(allowed, query, filter);

  useEffect(() => {
    if (!selected) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setSelected(null);
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [selected]);

  function openSelectedApp() {
    if (selected) window.location.assign(`/${selected.path}`);
  }

  return <section className="launcher-page">
    <Alert>{access.error}</Alert>
    <section className="launcher-head">
      <div>
        <span className="eyebrow">ERP WORKSPACE</span>
        <h2>Vše, co vaše firma potřebuje.</h2>
        <p>Vyberte aplikaci a začněte pracovat. Každý modul sdílí stejná data a oprávnění.</p>
      </div>
      <div className="launcher-stat"><strong>{allowed.length}</strong><span>aplikací<br />v katalogu</span></div>
    </section>
    <section className="launcher-tools">
      <div className="launcher-search">
        <span aria-hidden="true">⌕</span>
        <input
          aria-label="Hledat aplikaci nebo funkci"
          placeholder="Hledat aplikaci nebo funkci..."
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>
      <div className="launcher-filter" aria-label="Filtrovat aplikace">
        {appFilters.map(([value, label]) => <button
          key={value}
          type="button"
          className={`module-filter${filter === value ? " active" : ""}`}
          aria-pressed={filter === value}
          onClick={() => setFilter(value)}
        >{label}</button>)}
      </div>
    </section>
    {access.loading ? <p role="status">Načítání oprávnění…</p> : <>
      <section className="module-grid" aria-label="Aplikace ERP">
        {visibleApps.map((app) => <button
          key={app.id}
          type="button"
          className="module-tile"
          aria-label={`${app.title}: ${app.subtitle}`}
          onClick={() => setSelected(app)}
        >
          <span className={`module-icon icon-${app.id}`}>{app.icon}</span>
          <b>{app.title}</b>
          <small>{app.subtitle}</small>
          {"badge" in app && <i className="module-badge">{app.badge}</i>}
        </button>)}
      </section>
      {!visibleApps.length && <p className="module-empty">Žádná aplikace neodpovídá hledání.</p>}
      {!access.error && !allowed.length && <p>Nemáte oprávnění k žádnému modulu.</p>}
    </>}
    {selected && <>
      <button
        type="button"
        className="drawer-backdrop visible"
        aria-label="Zavřít detail aplikace"
        onClick={() => setSelected(null)}
      />
      <aside
        className="module-drawer open"
        role="dialog"
        aria-modal="true"
        aria-labelledby="drawer-title"
      >
        <button type="button" className="drawer-close" aria-label="Zavřít detail" onClick={() => setSelected(null)}>×</button>
        <span className={`drawer-icon module-icon icon-${selected.id}`}>{selected.icon}</span>
        <span className="eyebrow">APLIKACE ERP</span>
        <h2 id="drawer-title">{selected.title}</h2>
        <p className="drawer-subtitle">{selected.subtitle}</p>
        <p>{selected.description}</p>
        <div className="drawer-status"><span className="online" /><span>Modul připraven k návrhu</span></div>
        <div className="drawer-section">
          <span className="eyebrow">CO BUDE OBSAHOVAT</span>
          <ul>
            <li>Role a oprávnění podle pracovních týmů</li>
            <li>Seznamy, formuláře a filtrování záznamů</li>
            <li>Napojení na společná ERP data</li>
            <li>Auditní stopa a přehledy výkonu</li>
          </ul>
        </div>
        <button type="button" className="primary drawer-action" onClick={openSelectedApp}>
          Otevřít modul <span>→</span>
        </button>
      </aside>
    </>}
  </section>;
}
