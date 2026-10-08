import type { ReactNode } from "react";
import type { Row } from "../lib/context";

export type ViewProps = {
  data: Row;
  pathname: string;
};

export function record(value: unknown): Row {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value as Row : {};
}

export function list(value: unknown): Row[] {
  return Array.isArray(value) ? value.map(record) : [];
}

export function value(row: Row, key: string, fallback = ""): string {
  const candidate = row[key];
  return candidate === undefined || candidate === null ? fallback : String(candidate);
}

export function text(value: unknown, fallback = ""): string {
  return value === undefined || value === null ? fallback : String(value);
}

export function InlinePageScript({ source }: { source: string }) {
  return <script data-page-script="true" dangerouslySetInnerHTML={{ __html: source }} />;
}

export function AppDocument({
  title,
  pathname,
  breadcrumb,
  userName,
  roleName,
  children,
  layout = "app",
  pageStyles = [],
  pageScripts = [],
}: {
  title: string;
  pathname: string;
  breadcrumb: string;
  userName: string;
  roleName: string;
  children: ReactNode;
  layout?: "app" | "login" | "shop";
  pageStyles?: string[];
  pageScripts?: string[];
}) {
  const isShop = layout === "shop";
  const isLogin = layout === "login";
  const baseStyles = isShop ? [] : ["/assets/base.css"];
  const active = (path: string) => pathname === path || path === "/apps" && pathname === "/";
  const navigation = [
    ["/apps", "⌂", "Přehled"], ["/users", "♙", "Uživatelé"], ["/roles", "◈", "Role a oprávnění"],
    ["/role-modules", "▤", "Role pro moduly"], ["/companies", "▦", "Společnosti"], ["/settings", "⚙", "Nastavení"],
  ];
  return (
    <html lang="cs">
      <head>
        <meta charSet="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <title>{`ERP | ${title}`}</title>
        {[...baseStyles, ...pageStyles, "/compatibility.css?v=2"].map((href) => (
          <link key={href} rel="stylesheet" href={href} />
        ))}
      </head>
      <body>
        {isShop || isLogin ? children : (
          <div className="app-shell">
            <aside className="sidebar">
              <div className="brand"><span className="mark">E</span><div><strong>ERP CORE</strong><small>base module</small></div></div>
              <div className="workspace-label">WORKSPACE</div>
              <nav>
                {navigation.map(([href, icon, label]) => (
                  <a key={href} className={`nav-item${active(href) ? " active" : ""}`} href={href}>
                    <span>{icon}</span> {label}
                  </a>
                ))}
              </nav>
              <div className="sidebar-bottom"><span className="online" /> Systém je online<small>PostgreSQL 18 · lokální režim</small></div>
            </aside>
            <main className="main">
              <header className="topbar">
                <div><p className="breadcrumb">{breadcrumb}</p></div>
                <div className="top-actions">
                  <button className="search" aria-label="Hledat">⌕ <span>Hledat</span></button>
                  <button className="bell" aria-label="Oznámení">♧<i /></button>
                  <div className="profile">
                    <span className="avatar">{userName.split(" ").map((part) => part[0]).join("").slice(0, 2)}</span>
                    <span><b>{userName || "Uživatel"}</b><small>{roleName || "Bez role"}</small></span>
                    <span>⌄</span>
                  </div>
                  <form method="post" action="/logout" className="logout-form"><button className="logout-button" type="submit">Odhlásit se</button></form>
                </div>
              </header>
              {children}
              <footer>ERP CORE / BASE MODULE <span>v0.1.0 · Session zabezpečena</span></footer>
            </main>
          </div>
        )}
        {!isShop && <script src="/assets/base.js?v=2" />}
        {pageScripts.map((src) => <script key={src} src={src} />)}
      </body>
    </html>
  );
}
