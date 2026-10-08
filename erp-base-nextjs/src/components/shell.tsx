import { cookies } from "next/headers";
import { readSession, SESSION_COOKIE } from "@/lib/session-store";
import { Logout } from "./auth";
import type { ReactNode } from "react";

export async function Shell({ children, activePath = "" }: { children: ReactNode; activePath?: string }) {
  const session = await readSession((await cookies()).get(SESSION_COOKIE)?.value);
  return <div className="app-shell"><aside className="sidebar">
    <a className="brand" href="/apps"><span className="mark">E</span><span><strong>ERP CORE</strong><small>base module</small></span></a>
    <div className="workspace-label">WORKSPACE</div><nav>
      {[["apps", "⌂", "Přehled"], ["users", "♙", "Uživatelé"], ["roles", "◈", "Role a oprávnění"],
        ["role-modules", "▤", "Role pro moduly"], ["companies", "▦", "Společnosti"], ["settings", "⚙", "Nastavení"]].map(([path, icon, label]) =>
        <a key={path} className={`nav-item${activePath === path ? " active" : ""}`} href={`/${path}`}><span>{icon}</span>{label}</a>)}
    </nav><div className="sidebar-bottom"><span className="online" />Systém je online<small>PostgreSQL 18 · lokální režim</small></div>
  </aside><main className="main"><header className="topbar"><p className="breadcrumb">{activePath === "apps" ? "BASE / ADMINISTRATION" : activePath ? `ERP / ${activePath.toUpperCase()}` : "ERP / BASE MODULE"}</p>
    <div className="top-actions"><div className="profile"><span className="avatar">{session?.userName?.trim().split(/\s+/).map((part) => part[0]).slice(0, 2).join("") || "U"}</span>
      <span><b>{session?.userName || "Uživatel"}</b><small>{session?.roleName || "Bez role"}</small></span></div>
      <div className="logout-form"><Logout /></div></div>
  </header>{children}<footer>ERP CORE / BASE MODULE <span>v0.1.0 · Session zabezpečena</span></footer></main></div>;
}
