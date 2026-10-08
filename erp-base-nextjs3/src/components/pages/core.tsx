import type { CSSProperties } from "react";
import { InlinePageScript, list, record, text, value, type ViewProps } from "../shared";

const cssVariable = (color: string): CSSProperties => ({ "--card-color": color }) as CSSProperties;

function DialogFallback({ kind }: { kind: "company" | "user" | "role" }) {
  const selectors = {
    company: { modal: "company-modal", open: "#add-company, #edit-company", close: "#close-company-dialog, #cancel-company-dialog, .company-modal-backdrop" },
    user: { modal: "user-modal", open: "#add-user, .edit-user", close: "#close-user-dialog, #cancel-user-dialog, .user-modal-backdrop" },
    role: { modal: "role-modal", open: "#add-role, #edit-role", close: "#close-role-dialog, #cancel-role-dialog, .role-modal-backdrop" },
  }[kind];
  const script = `(function(){var s=${JSON.stringify(selectors)};document.addEventListener("click",function(e){var t=e.target;if(!(t instanceof Element))return;var m=document.getElementById(s.modal);if(!m)return;if(t.closest(s.open)){m.classList.add("open");m.setAttribute("aria-hidden","false")}else if(t.closest(s.close)){m.classList.remove("open");m.setAttribute("aria-hidden","true")}});document.addEventListener("keydown",function(e){if(e.key==="Escape"){var m=document.getElementById(s.modal);if(m){m.classList.remove("open");m.setAttribute("aria-hidden","true")}}})})()`;
  return <InlinePageScript source={script} />;
}

export function LoginPage({ data }: ViewProps) {
  const error = text(data.error);
  return (
    <main className="login-page">
      <a href="/eshop">E-shop</a>
      <h1>Retail ERP</h1>
      {error && <p role="alert">{error}</p>}
      <form method="post" action="/login" className="workflow-fields">
        <label>Uživatelské jméno<input name="username" required maxLength={120} autoComplete="username" /></label>
        <label>Heslo<input name="password" type="password" required autoComplete="current-password" /></label>
        <button type="submit" className="primary">Přihlásit se</button>
      </form>
      {data.authenticated === true && (
        <form method="post" action="/logout"><button type="submit">Odhlásit se</button></form>
      )}
    </main>
  );
}

export function HomePage({ data }: ViewProps) {
  const catalog = record(data.moduleCatalog);
  const allowed = new Set(Array.isArray(data.allowedModules) ? data.allowedModules.filter((item): item is string => typeof item === "string") : []);
  const error = text(data.error);
  return (
    <>
      {error && <div className="page-alert error-alert module-access-error" role="alert">{error}</div>}
      <section className="launcher-head">
        <div><span className="eyebrow">ERP WORKSPACE</span><h2>Vše, co vaše firma potřebuje.</h2><p>Vyberte aplikaci a začněte pracovat. Každý modul sdílí stejná data a oprávnění.</p></div>
        <div className="launcher-stat"><strong>{allowed.size}</strong><span>aplikací<br />v katalogu</span></div>
      </section>
      <section className="launcher-tools">
        <div className="launcher-search"><span>⌕</span><input id="module-search" placeholder="Hledat aplikaci nebo funkci..." aria-label="Hledat aplikaci nebo funkci" /></div>
        <div className="launcher-filter"><button className="module-filter active" data-filter="all">Vše</button><button className="module-filter" data-filter="core">Základ</button><button className="module-filter" data-filter="operations">Provoz</button><button className="module-filter" data-filter="sales">Obchod</button></div>
      </section>
      <section className="module-grid" id="module-grid">
        {Object.entries(catalog).filter(([key]) => allowed.has(key)).map(([key, item]) => {
          const module = record(item);
          return (
            <button key={key} className="module-tile" data-module={key} data-category={value(module, "category")} data-title={value(module, "title")} data-subtitle={value(module, "subtitle")} data-description={value(module, "description")}>
              <span className={`module-icon icon-${key}`}>{text(module.icon)}</span><b>{text(module.title)}</b><small>{text(module.subtitle)}</small>
              {module.badge !== undefined && <i className="module-badge">{text(module.badge)}</i>}
            </button>
          );
        })}
      </section>
      <p className="module-empty" id="module-empty">Žádná aplikace neodpovídá hledání.</p>
      <aside className="module-drawer" id="module-drawer" aria-hidden="true">
        <button className="drawer-close" id="drawer-close" aria-label="Zavřít detail">×</button>
        <span className="drawer-icon module-icon" /><span className="eyebrow">APLIKACE ERP</span>
        <h2 id="drawer-title" /><p className="drawer-subtitle" id="drawer-subtitle" /><p id="drawer-description" />
        <div className="drawer-status"><span className="online" /><span>Modul připraven k návrhu</span></div>
        <div className="drawer-section"><span className="eyebrow">CO BUDE OBSAHOVAT</span><ul><li>Role a oprávnění podle pracovních týmů</li><li>Seznamy, formuláře a filtrování záznamů</li><li>Napojení na společná ERP data</li><li>Auditní stopa a přehledy výkonu</li></ul></div>
        <button className="primary drawer-action" id="drawer-action">Otevřít modul <span>→</span></button>
      </aside>
    </>
  );
}

export function AdminCompaniesPage({ data }: ViewProps) {
  const companies = list(data.companies);
  const message = text(data.message);
  const error = text(data.error);
  return (
    <>
      <section className="section-head">
        <div><span className="eyebrow">BASE / ENTITIES</span><h2>Společnosti</h2><p>Organizační jednotky a jejich konfigurace.</p></div>
        <div className="company-actions"><button className="primary" id="edit-company" type="button">Upravit společnost</button><button className="primary" id="add-company" type="button">+ Nová společnost</button></div>
      </section>
      {message && <div className="company-alert success-alert">{message}</div>}
      {error && <div className="company-alert error-alert">{error}</div>}
      <section className="company-grid">
        {companies.map((company, index) => {
          const color = value(company, "color", "#D9ED62");
          const status = value(company, "status");
          const name = value(company, "name");
          return (
            <article key={value(company, "id", String(index))} className={`company-card ${status.toLowerCase()}`} data-company-id={value(company, "id")} data-name={name} data-type={value(company, "type")} data-currency={value(company, "currency")} data-status={status} data-color={color}>
              <span className="company-logo" style={cssVariable(color)}>{Array.from(name)[0]?.toUpperCase() ?? ""}</span>
              <h3>{name}</h3><small>{value(company, "type")} · {value(company, "currency")} · {status === "ACTIVE" ? "aktivní" : "neaktivní"}</small>
            </article>
          );
        })}
        {companies.length === 0 && !error && <p className="empty-state">Zatím nejsou evidovány žádné společnosti.</p>}
      </section>
      <div className="company-modal" id="company-modal" aria-hidden="true">
        <div className="company-modal-backdrop" />
        <section className="company-dialog" role="dialog" aria-modal="true" aria-labelledby="company-dialog-title">
          <button className="dialog-close" id="close-company-dialog" type="button" aria-label="Zavřít">×</button>
          <span className="eyebrow">BASE / ENTITIES</span><h2 id="company-dialog-title">Nová společnost</h2><p id="company-dialog-description">Přidejte organizační jednotku do ERP.</p>
          <form method="post" action="/companies" id="company-form">
            <input type="hidden" name="action" id="company-action" value="create" /><input type="hidden" name="id" id="company-id" />
            <label>Název společnosti<input name="name" id="company-name" required maxLength={200} /></label>
            <label>Typ společnosti<input name="type" id="company-type" required maxLength={100} placeholder="Např. Centrála" /></label>
            <label>Měna<select name="currency" id="company-currency"><option value="CZK">CZK</option><option value="EUR">EUR</option><option value="USD">USD</option></select></label>
            <label>Stav<select name="status" id="company-status"><option value="ACTIVE">Aktivní</option><option value="INACTIVE">Neaktivní</option></select></label>
            <div className="dialog-actions"><button className="secondary-button" id="cancel-company-dialog" type="button">Zrušit</button><button className="primary" id="save-company" type="submit">Vytvořit společnost</button></div>
          </form>
        </section>
      </div>
      <DialogFallback kind="company" />
    </>
  );
}

export function AdminUsersPage({ data }: ViewProps) {
  const users = list(data.users);
  const employees = list(data.employees);
  const roles = list(data.roles);
  const companies = list(data.companies);
  const requestedEmployeeId = value(data, "requestedEmployeeId");
  const message = text(data.message);
  const error = text(data.error);
  const employeeRoles = Object.fromEntries(employees.map((employee) => [value(employee, "id"), text(employee.jobTitle)]));
  const safeEmployeeRoles = JSON.stringify(employeeRoles).replace(/</g, "\\u003c");
  return (
    <>
      <section className="section-head">
        <div><span className="eyebrow">BASE / USERS</span><h2>Uživatelé</h2><p>Účty a přístupy do ERP systému.</p></div>
        <button className="primary" id="add-user" type="button">+ Nový uživatel</button>
      </section>
      {message && <div className="page-alert success-alert">{message}</div>}
      {error && <div className="page-alert error-alert">{error}</div>}
      <section className="panel table-panel">
        <div className="table-tools"><input id="user-search" placeholder="Hledat uživatele..." aria-label="Hledat uživatele" /><span id="user-count">{users.length} uživatelů</span></div>
        <div className="table-scroll"><table>
          <thead><tr><th>UŽIVATEL</th><th>ROLE</th><th>SPOLEČNOST</th><th>STAV</th><th>POSLEDNÍ PŘÍSTUP</th><th><span className="sr-only">AKCE</span></th></tr></thead>
          <tbody id="user-table">
            {users.map((user, index) => {
              const color = value(user, "color", "#DCE9D7");
              const status = value(user, "status");
              return (
                <tr key={value(user, "id", String(index))} data-user-id={value(user, "id")} data-employee-id={value(user, "employeeId")} data-full-name={value(user, "fullName")} data-role-name={value(user, "roleName")} data-company-name={value(user, "companyName")} data-username={value(user, "username")} data-status={status} data-color={color}>
                  <td><span className="table-avatar" style={cssVariable(color)}>{value(user, "initials")}</span>{value(user, "fullName")}</td>
                  <td>{value(user, "roleName")}</td><td>{value(user, "companyName")}</td>
                  <td><span className={`status status-${status.toLowerCase()}`}>{value(user, "statusLabel")}</span></td><td>{value(user, "lastAccessLabel")}</td>
                  <td className="table-actions"><button className="table-action edit-user" type="button" title="Upravit uživatele">Upravit</button>
                    <form method="post" action="/users" className="delete-user-form" data-confirm-delete data-confirm-message="Opravdu chcete tohoto uživatele smazat?">
                      <input type="hidden" name="action" value="delete" /><input type="hidden" name="id" value={value(user, "id")} />
                      <button className="table-action danger-action" type="submit" title="Smazat uživatele">Smazat</button>
                    </form>
                  </td>
                </tr>
              );
            })}
            {users.length === 0 && !error && <tr><td colSpan={6}>Zatím nejsou evidováni žádní uživatelé.</td></tr>}
          </tbody>
        </table></div>
      </section>
      <div className={`user-modal${requestedEmployeeId ? " open" : ""}`} id="user-modal" data-requested-employee-id={requestedEmployeeId} aria-hidden={requestedEmployeeId ? "false" : "true"}>
        <div className="user-modal-backdrop" />
        <section className="user-dialog" role="dialog" aria-modal="true" aria-labelledby="user-dialog-title">
          <button className="dialog-close" id="close-user-dialog" type="button" aria-label="Zavřít">×</button>
          <span className="eyebrow">BASE / USERS</span><h2 id="user-dialog-title">Nový uživatel</h2><p>Účet bude propojený se zaměstnancem v modulu Lidé.</p>
          <form method="post" action="/users" id="user-form">
            <input type="hidden" name="action" id="user-action" value="create" /><input type="hidden" name="id" id="user-id" />
            <label>Zaměstnanec<select name="employeeId" id="user-employee-id" required defaultValue={requestedEmployeeId}>
              <option value="" disabled>Vyberte zaměstnance</option>
              {employees.map((employee, index) => {
                const id = value(employee, "id");
                const hasAccount = employee.hasAccount === true;
                return <option key={id || String(index)} value={id} data-has-account={hasAccount ? "true" : "false"}>{value(employee, "fullName")} · {value(employee, "teamName")}{hasAccount ? " · účet již existuje" : ""}</option>;
              })}
            </select></label>
            <label>Jméno a příjmení<input name="fullName" id="user-full-name" defaultValue={value(data, "requestedEmployeeName")} required maxLength={200} /></label>
            <label>Uživatelské jméno<input name="username" id="user-username" required maxLength={64} pattern="[A-Za-z0-9._\-]{3,64}" /></label>
            <label>Heslo<input name="password" id="user-password" type="password" minLength={10} required autoComplete="new-password" /></label>
            <label>Role<select name="roleName" id="user-role-name" required defaultValue=""><option value="" disabled>Vyberte roli</option>{roles.map((role, index) => <option key={value(role, "id", String(index))} value={value(role, "name")}>{value(role, "name")}</option>)}</select></label>
            <label>Společnost<input name="companyName" id="user-company-name" required maxLength={200} /></label>
            <label>Stav<select name="status" id="user-status"><option value="ACTIVE">Aktivní</option><option value="INVITED">Pozvánka čeká</option><option value="SUSPENDED">Pozastavený</option></select></label>
            <div className="dialog-actions"><button className="secondary-button" id="cancel-user-dialog" type="button">Zrušit</button><button className="primary" type="submit" id="save-user">Přidat uživatele</button></div>
          </form>
        </section>
      </div>
      <InlinePageScript source={`window.employeeRoles = ${safeEmployeeRoles};`} />
      <template id="company-options-source"><option value="" disabled>Vyberte společnost</option>{companies.map((company, index) => <option key={value(company, "id", String(index))} value={value(company, "name")}>{value(company, "name")}</option>)}</template>
      <DialogFallback kind="user" />
    </>
  );
}

export function AdminRolesPage({ data }: ViewProps) {
  const roles = list(data.roles);
  const message = text(data.message);
  const error = text(data.error);
  return (
    <>
      {message && <div className="page-alert success-alert">{message}</div>}{error && <div className="page-alert error-alert">{error}</div>}
      <div className="role-toolbar"><button className="primary" id="edit-role" type="button">Upravit roli</button></div>
      <section className="section-head"><div><span className="eyebrow">BASE / ACCESS</span><h2>Role a oprávnění</h2><p>Řízení přístupů podle pracovních odpovědností.</p></div><button className="primary" id="add-role" type="button">+ Nová role</button></section>
      <section className="role-grid">
        {roles.map((role, index) => {
          const color = value(role, "color", "#D9ED62");
          const bool = (key: string) => role[key] === true ? "true" : "false";
          return (
            <article key={value(role, "id", String(index))} className="role-card" style={cssVariable(color)} data-role-id={value(role, "id")} data-color={color} data-can-read={bool("canRead")} data-can-insert={bool("canInsert")} data-can-edit={bool("canEdit")} data-can-manage={bool("canManage")} data-can-delete={bool("canDelete")}>
              <span className="role-mark custom">{value(role, "initial")}</span><h3>{value(role, "name")}</h3><p>{value(role, "description")}</p><b>{value(role, "userCount")} uživatelů</b>
            </article>
          );
        })}
        {roles.length === 0 && !error && <p className="empty-state">Zatím nejsou evidovány žádné role.</p>}
      </section>
      <div className="role-modal" id="role-modal" aria-hidden="true">
        <div className="role-modal-backdrop" />
        <section className="role-dialog" role="dialog" aria-modal="true" aria-labelledby="role-dialog-title">
          <button className="dialog-close" id="close-role-dialog" type="button" aria-label="Zavřít">×</button>
          <span className="eyebrow">BASE / ACCESS</span><h2 id="role-dialog-title">Nová role</h2><p>Definujte pracovní odpovědnost a přístup k modulům ERP.</p>
          <form id="role-form" method="post" action="/roles">
            <input type="hidden" name="id" id="role-id" /><input type="hidden" name="action" id="role-action" value="create" />
            <label>Název role<input name="name" id="role-name" required maxLength={120} placeholder="Např. Vedoucí prodejny" /></label>
            <label>Zkratka<input name="initial" id="role-initial" required maxLength={1} pattern="[A-Za-zÁ-ž]" placeholder="V" /></label>
            <label>Popis<textarea name="description" id="role-description" rows={3} maxLength={240} placeholder="Co tato role spravuje?" /></label>
            <fieldset className="permission-fields"><legend>Oprávnění</legend>
              <label><input type="checkbox" name="canRead" id="permission-read" defaultChecked /> Prohlížet data</label>
              <label><input type="checkbox" name="canInsert" id="permission-insert" /> Vkládat záznamy</label>
              <label><input type="checkbox" name="canEdit" id="permission-edit" /> Upravovat záznamy</label>
              <label><input type="checkbox" name="canDelete" id="permission-delete" /> Mazat záznamy</label>
              <label><input type="checkbox" name="canManage" id="permission-manage" /> Spravovat nastavení</label>
            </fieldset>
            <div className="dialog-actions"><button className="secondary-button" id="cancel-role-dialog" type="button">Zrušit</button><button className="primary" type="submit">Vytvořit roli</button></div>
          </form>
        </section>
      </div>
      <DialogFallback kind="role" />
    </>
  );
}

export function AdminRoleModulesPage({ data }: ViewProps) {
  const roles = list(data.roles);
  const matrix = record(data.matrix);
  const modules = list(matrix.modules);
  const permissions = list(matrix.permissions);
  const message = text(data.message);
  const error = text(data.error);
  return (
    <>
      <section className="section-head compact">
        <div><span className="eyebrow">BASE / MODULE ACCESS</span><h2>Role pro moduly</h2><p>Nastavení přístupu jednotlivých rolí k modulům ERP.</p></div>
        <button className="primary" id="save-module-permissions" type="submit" form="module-permissions-form">Uložit oprávnění</button>
      </section>
      {message && <div className="page-alert success-alert">{message}</div>}{error && <div className="page-alert error-alert">{error}</div>}
      <form id="module-permissions-form" method="post" action="/role-modules">
        <input type="hidden" name="action" value="save-module-permissions" />
        <div className="permissions-table-wrap"><table className="permissions-table">
          <thead><tr><th>Modul</th>{roles.map((role, index) => <th key={value(role, "id", String(index))}><span className="role-column-mark" style={cssVariable(value(role, "color", "#D9ED62"))}>{value(role, "initial")}</span>{value(role, "name")}</th>)}</tr></thead>
          <tbody>
            {modules.map((module, index) => {
              const key = value(module, "key");
              const name = value(module, "name");
              return <tr key={key || String(index)}><th scope="row">{name}</th>
                {roles.map((role, roleIndex) => {
                  const roleId = value(role, "id");
                  const isAdmin = value(role, "name") === "Administrátor";
                  const enabled = isAdmin || permissions.some((permission) => value(permission, "roleId") === roleId && value(permission, "moduleKey") === key);
                  return <td key={roleId || String(roleIndex)}><input type="checkbox" name={`permission_${roleId}_${key}`} defaultChecked={enabled} disabled={isAdmin} aria-label={`${name} - ${value(role, "name")}`} /></td>;
                })}
              </tr>;
            })}
            {modules.length === 0 && !error && <tr><td colSpan={roles.length + 1}>Moduly nejsou dostupné.</td></tr>}
          </tbody>
        </table></div>
      </form>
    </>
  );
}

export function AdminSettingsPage({ data }: ViewProps) {
  const settings = record(data.settings);
  const hasSettings = data.settings !== null && data.settings !== undefined && Object.keys(settings).length > 0;
  const message = text(data.message);
  const error = text(data.error);
  return (
    <>
      <section className="section-head"><div><span className="eyebrow">BASE / CONFIGURATION</span><h2>Nastavení</h2><p>Základní konfigurace instance ERP.</p></div></section>
      {message && <p className="settings-message success">{message}</p>}{error && <p className="settings-message error">{error}</p>}
      {hasSettings && <form method="post" action="/settings" className="panel settings-panel">
        <label>Název společnosti<input name="companyName" defaultValue={value(settings, "companyName")} required /></label>
        <label>E-mail společnosti<input type="email" name="companyEmail" defaultValue={value(settings, "companyEmail")} required /></label>
        <label>Výchozí měna<input name="currencyCode" defaultValue={value(settings, "currencyCode")} required /></label>
        <label>Časové pásmo<input name="timezone" defaultValue={value(settings, "timezone")} required /></label>
        <label>Začátek fiskálního roku<input type="number" name="fiscalYearStartMonth" min={1} max={12} defaultValue={value(settings, "fiscalYearStartMonth")} required /></label>
        <label>Splatnost faktur ve dnech<input type="number" name="defaultPaymentTermsDays" min={0} defaultValue={value(settings, "defaultPaymentTermsDays")} required /></label>
        <label>Poplatek za doručení (Kč)<input type="number" name="deliveryFee" min={0} step="0.01" defaultValue={value(settings, "deliveryFee")} required /></label>
        <label>Globální marže (%)<input type="number" name="eshopMarginPercent" min={0} max={99.98} step="0.01" defaultValue={value(settings, "eshopMarginPercent")} required /></label>
        <label>Zaokrouhlení ceny (Kč)<select name="eshopRoundingUnit" defaultValue={value(settings, "eshopRoundingUnit")}><option value="1">1 Kč</option><option value="10">10 Kč</option><option value="100">100 Kč</option></select></label>
        <label>Výchozí DPH (%)<input type="number" name="eshopDefaultVatRate" min={0} max={100} step="0.01" defaultValue={value(settings, "eshopDefaultVatRate")} required /></label>
        <button className="primary" type="submit">Uložit nastavení</button>
      </form>}
    </>
  );
}

function money(input: unknown, overviewAvailable: boolean): string {
  if (!overviewAvailable) return "-";
  const numeric = Number(text(input));
  return Number.isFinite(numeric) ? numeric.toFixed(2) : "0.00";
}

export function DashboardPage({ data }: ViewProps) {
  const overviewAvailable = data.overview !== null && data.overview !== undefined;
  const overview = record(data.overview);
  const invoices = list(overview.invoices);
  const leads = list(overview.leads);
  const campaigns = list(overview.campaigns);
  const error = text(data.error);
  const currentDate = new Date();
  const today = `${String(currentDate.getDate()).padStart(2, "0")}. ${String(currentDate.getMonth() + 1).padStart(2, "0")}. ${currentDate.getFullYear()}`;
  const metrics = [
    ["receivables", "Pohledávky", "čeká na úhradu", "/accounting", "Účetnictví"],
    ["overdue", "Po splatnosti", "vyžaduje pozornost", "/accounting", "Zobrazit faktury"],
    ["pipeline", "Hodnota pipeline", "v otevřených příležitostech", "/crm", "CRM pipeline"],
    ["forecast", "Vážený forecast", "podle pravděpodobnosti", "/crm", "Detail forecastu"],
  ] as const;
  return (
    <div className="dashboard-page">
      <header className="dashboard-header">
        <div className="dashboard-title"><a href="/apps" className="back-link">← Aplikace</a><span className="eyebrow">OVERVIEW / MANAGEMENT</span><h1>Dashboard</h1><p>Pracovní přehled financí, obchodních příležitostí a promo aktivit.</p></div>
        <div className="dashboard-date"><span>DNEŠNÍ PŘEHLED</span><strong>{today}</strong></div>
      </header>
      {error && <p className="dashboard-message error" role="alert">{error}</p>}
      <section className="dashboard-metrics" aria-label="Klíčové ukazatele">
        {metrics.map(([key, title, caption, href, link]) => <article key={key} className={`metric-card ${key}`}><span>{title}</span><strong>{money(overview[key], overviewAvailable)} Kč</strong><small>{caption}</small><a href={href}>{link} →</a></article>)}
        <article className="metric-card campaigns"><span>Aktivní kampaně</span><strong>{text(overview.activeCampaignCount, "-")}</strong><small>právě v běhu</small><a href="/promo">Promo kampaně →</a></article>
      </section>
      <section className="dashboard-grid">
        <section className="dashboard-panel invoice-panel">
          <div className="panel-head"><div><span className="eyebrow">FINANCE</span><h2>Neuhrazené faktury</h2></div><a href="/accounting">Všechny faktury →</a></div>
          <div className="panel-list">{invoices.map((invoice, index) => <article key={value(invoice, "id", String(index))} className="list-row"><div><span className={`status status-${value(invoice, "status").toLowerCase()}`}>{value(invoice, "status")}</span><strong>{value(invoice, "invoiceNumber")}</strong><small>{value(invoice, "partnerName")} · splatnost {value(invoice, "dueDate")}</small></div><b>{money(invoice.outstandingAmount, true)} Kč</b></article>)}</div>
        </section>
        <section className="dashboard-panel lead-panel">
          <div className="panel-head"><div><span className="eyebrow">OBCHOD</span><h2>Nejbližší příležitosti</h2></div><a href="/crm">Otevřít CRM →</a></div>
          <div className="panel-list">{leads.map((lead, index) => <article key={value(lead, "id", String(index))} className="list-row"><div><span className={`status stage-${value(lead, "stage").toLowerCase()}`}>{value(lead, "stage")}</span><strong>{value(lead, "name")}</strong><small>{value(lead, "customerName")} · uzavření {value(lead, "expectedCloseDate")}</small></div><b>{money(lead.expectedRevenue, true)} Kč <i>{value(lead, "probability")} %</i></b></article>)}</div>
        </section>
        <section className="dashboard-panel campaign-panel">
          <div className="panel-head"><div><span className="eyebrow">PROMO</span><h2>Stav kampaní</h2></div><a href="/promo">Správa kampaní →</a></div>
          <div className="campaign-list">{campaigns.map((campaign, index) => <article key={value(campaign, "id", String(index))} className="campaign-row"><span className={`status campaign-${value(campaign, "status").toLowerCase()}`}>{value(campaign, "status")}</span><div><strong>{value(campaign, "name")}</strong><small>{value(campaign, "startsOn")} až {value(campaign, "endsOn")}</small></div></article>)}</div>
        </section>
      </section>
    </div>
  );
}
