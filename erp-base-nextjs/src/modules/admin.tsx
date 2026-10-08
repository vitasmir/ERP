"use client";

import { useEffect, useState } from "react";
import { Action, Alert, ApiForm, Editor, Module, Table, type Field, type Option } from "@/components/ui";
import { useApi } from "@/lib/client-api";
import { number, record, roleKey, rows, text, type Json, type Row } from "@/lib/data";

const permissionLabels: Record<string, string> = {
  canRead: "Prohlížet data", canInsert: "Vkládat záznamy", canEdit: "Upravovat záznamy",
  canManage: "Spravovat nastavení", canDelete: "Mazat záznamy",
};
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isAdministrator(role: Row): boolean {
  return ["administrator", "admin"].includes(roleKey(text(role.name)));
}

export function modulePermissions(roles: Row[], modules: Row[], form: FormData): Json {
  const permissions: Json[] = [];
  for (const role of roles) {
    const roleId = text(role.id);
    if (!uuidPattern.test(roleId)) throw new Error("Role nemá platný identifikátor. Obnovte stránku.");
    // The backend restores administrator access itself; sending it twice violates its unique key.
    if (isAdministrator(role)) continue;
    for (const module of modules) {
      const moduleKey = text(module.key);
      if (moduleKey && form.has(`permission_${roleId}_${moduleKey}`)) permissions.push({ roleId, moduleKey });
    }
  }
  return { permissions };
}

function clean(values: Row): Row {
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, typeof value === "string" && key !== "password" ? value.trim() : value]));
}

function choices(data: Row[], value: string, label = value): Option[] {
  return data.map((item) => ({ value: text(item[value]), label: text(item[label]) }));
}

function RoleForm({ role, reload }: { role?: Row; reload: () => void }) {
  const immutable = role !== undefined && isAdministrator(role);
  const fields: Field[] = [
    { name: "name", label: "Název role", type: immutable ? "hidden" : "text", required: true, maxLength: 120 },
    { name: "initial", label: "Zkratka (jedno písmeno)", required: true, maxLength: 1 },
    { name: "description", label: "Popis", type: "textarea", maxLength: 240 },
    { name: "color", label: "Barva", type: "color" },
    ...Object.entries(permissionLabels).filter(() => !immutable).map(([name, label]): Field => ({ name, label, type: "checkbox" })),
  ];
  return <ApiForm path={role ? `roles/${text(role.id)}` : "roles"} method={role ? "PUT" : "POST"}
    initial={role ?? { canRead: true, color: "#D9ED62" }} fields={fields} onSaved={reload}
    transform={(values) => {
      const body = clean(values);
      if (!/^\p{L}$/u.test(text(body.initial))) throw new Error("Zkratka role musí být jedno písmeno.");
      if (immutable) {
        body.name = role.name;
        for (const key of Object.keys(permissionLabels)) body[key] = true;
      }
      return body;
    }} label={role ? "Uložit roli" : "Vytvořit roli"}>
    {immutable && <p>{text(role.name)} má vždy všechna oprávnění. Jeho název a oprávnění nelze změnit.</p>}
  </ApiForm>;
}

function Roles({ matrixOnly }: { matrixOnly: boolean }) {
  const result = useApi("roles");
  const matrixResult = useApi("roles/matrix");
  const roles = rows(result.data);
  const matrix = record(matrixResult.data);
  const modules = rows(matrix.modules);
  const permissions = rows(matrix.permissions);
  return <Module title={matrixOnly ? "Role pro moduly" : "Role a oprávnění"} error={result.error || matrixResult.error}
    loading={result.loading || matrixResult.loading}>
    <nav><a href="/roles">Role a oprávnění</a> · <a href="/role-modules">Přístupy k modulům</a></nav>
    {!matrixOnly ? <>
      <Editor title="Nová role"><RoleForm reload={result.reload} /></Editor>
      <Table data={roles} columns={[
        { key: "initial", label: "Zkratka" }, { key: "name", label: "Role" },
        { key: "description", label: "Popis" }, { key: "userCount", label: "Uživatelů" },
        { key: "permissions", label: "Oprávnění", render: (role) => Object.entries(permissionLabels).filter(([key]) => role[key] === true).map(([, label]) => label).join(", ") },
      ]} actions={(role) => <Editor title="Upravit roli"><RoleForm role={role} reload={result.reload} /></Editor>} />
    </> : !result.error && !matrixResult.error && <>
      <p>Administrátor má trvalý přístup ke všem modulům.</p>
      <ApiForm key={JSON.stringify(matrix)} path="roles/matrix" method="PUT" fields={[]} onSaved={matrixResult.reload}
        transform={(_values, form) => modulePermissions(roles, modules, form)} label="Uložit oprávnění">
        <div className="table-scroll"><table><thead><tr><th>Modul</th>{roles.map((role) => <th key={text(role.id)}>{text(role.name)}</th>)}</tr></thead>
          <tbody>{modules.map((module) => <tr key={text(module.key)}><th scope="row">{text(module.name)}</th>
            {roles.map((role) => <td key={text(role.id)}><input type="checkbox"
              name={`permission_${text(role.id)}_${text(module.key)}`} disabled={isAdministrator(role)}
              defaultChecked={isAdministrator(role) || permissions.some((permission) => permission.roleId === role.id && permission.moduleKey === module.key)}
              aria-label={`${text(module.name)} – ${text(role.name)}`} /></td>)}</tr>)}
            {!modules.length && <tr><td colSpan={roles.length + 1}>Moduly nejsou dostupné.</td></tr>}
          </tbody></table></div>
      </ApiForm>
    </>}
  </Module>;
}

function UserForm({ user, employees, companies, requestedId = "", reload }: {
  user?: Row; employees: Row[]; companies: Row[]; requestedId?: string; reload: () => void;
}) {
  const [employeeId, setEmployeeId] = useState(text(user?.employeeId) || requestedId);
  const employee = employees.find((item) => text(item.id) === employeeId);
  const available = employees.filter((item) => !item.hasAccount || item.id === user?.employeeId);
  const fields: Field[] = [
    { name: "fullName", label: "Jméno a příjmení", required: true, maxLength: 200 },
    { name: "username", label: "Uživatelské jméno", type: user ? "hidden" : "text", required: true, maxLength: 64 },
    { name: "password", label: user ? "Nové heslo (prázdné = beze změny)" : "Heslo (alespoň 10 znaků)", type: "password", required: !user },
    { name: "companyName", label: "Společnost", type: "select", required: true, options: [{ value: "", label: "Vyberte společnost" }, ...choices(companies, "name")] },
    { name: "status", label: "Stav", type: "select", options: [
      { value: "ACTIVE", label: "Aktivní" }, { value: "INVITED", label: "Pozvánka čeká" }, { value: "SUSPENDED", label: "Pozastavený" },
    ] },
    { name: "color", label: "Barva", type: "color" },
  ];
  return <ApiForm key={employeeId} path={user ? `users/${text(user.id)}` : "users"} method={user ? "PUT" : "POST"}
    fields={fields} initial={user ? { ...user, fullName: employeeId === text(user.employeeId) ? user.fullName : employee?.fullName ?? "", password: "" } : { fullName: employee?.fullName ?? "", color: "#DCE9D7" }}
    onSaved={reload} label={user ? "Uložit uživatele" : "Přidat uživatele"}
    transform={(values) => {
      const body = clean(values);
      if (!uuidPattern.test(employeeId)) throw new Error("Vyberte platného zaměstnance.");
      if (!/^[A-Za-z0-9._-]{3,64}$/.test(text(body.username))) throw new Error("Uživatelské jméno musí obsahovat 3–64 písmen, číslic, teček, pomlček nebo podtržítek.");
      if ((!user || text(body.password)) && text(body.password).length < 10) throw new Error("Heslo musí obsahovat alespoň 10 znaků.");
      return { ...body, employeeId };
    }}>
    <label>Zaměstnanec<select required value={employeeId} onChange={(event) => setEmployeeId(event.target.value)}>
      <option value="">Vyberte zaměstnance</option>
      {available.map((item) => <option key={text(item.id)} value={text(item.id)}>{text(item.fullName)} · {text(item.teamName)}</option>)}
    </select></label>
    <p>Role účtu: <strong>{text(employee?.jobTitle) || "Vyberte zaměstnance"}</strong>. Role se přebírá z profilu zaměstnance v modulu <a href="/hr">Lidé</a>.</p>
    {user && <p>Uživatelské jméno: {text(user.username)} (nelze změnit).</p>}
  </ApiForm>;
}

function Users() {
  const users = useApi("users");
  const employees = useApi("users/employee-options");
  const companies = useApi("companies");
  const [search, setSearch] = useState("");
  const [requestedId, setRequestedId] = useState("");
  useEffect(() => { setRequestedId(new URLSearchParams(window.location.search).get("employeeId") ?? ""); }, []);
  const items = rows(users.data).filter((user) => [user.fullName, user.username, user.roleName, user.companyName].some((value) => roleKey(text(value)).includes(roleKey(search))));
  const reload = () => { users.reload(); employees.reload(); };
  const optionsReady = !employees.error && !companies.error;
  return <Module title="Uživatelé" error={users.error || employees.error || companies.error} loading={users.loading || employees.loading || companies.loading}>
    {optionsReady && <section><h2>Nový uživatel</h2><UserForm key={requestedId} requestedId={requestedId} employees={rows(employees.data)} companies={rows(companies.data)} reload={reload} /></section>}
    <label>Hledat uživatele<input value={search} onChange={(event) => setSearch(event.target.value)} /></label>
    <p>{items.length} uživatelů</p>
    <Table data={items} columns={[
      { key: "fullName", label: "Uživatel" }, { key: "username", label: "Přihlašovací jméno" },
      { key: "roleName", label: "Role" }, { key: "companyName", label: "Společnost" },
      { key: "status", label: "Stav", render: (user) => ({ INVITED: "Pozvánka čeká", SUSPENDED: "Pozastavený" }[text(user.status)] ?? "Aktivní") },
      { key: "lastAccessAt", label: "Poslední přístup", render: (user) => text(user.lastAccessAt).replace("T", " ") || "Dosud se nepřihlásil" },
    ]} actions={(user) => <>
      {optionsReady && <Editor title="Upravit uživatele"><UserForm user={user} employees={rows(employees.data)} companies={rows(companies.data)} reload={reload} /></Editor>}
      <Action path={`users/${text(user.id)}`} method="DELETE" label="Smazat" confirm="Opravdu chcete tohoto uživatele smazat?" onSaved={reload} />
    </>} />
  </Module>;
}

const companyFields: Field[] = [
  { name: "name", label: "Název společnosti", required: true, maxLength: 200 },
  { name: "type", label: "Typ společnosti", required: true, maxLength: 100 },
  { name: "currency", label: "Měna", type: "select", options: ["CZK", "EUR", "USD"].map((value) => ({ value, label: value })) },
  { name: "status", label: "Stav", type: "select", options: [{ value: "ACTIVE", label: "Aktivní" }, { value: "INACTIVE", label: "Neaktivní" }] },
  { name: "color", label: "Barva", type: "color" },
];

function Companies() {
  const result = useApi("companies");
  return <Module title="Společnosti" error={result.error} loading={result.loading}>
    <Editor title="Nová společnost"><ApiForm path="companies" fields={companyFields} initial={{ color: "#D9ED62" }} transform={clean} onSaved={result.reload} label="Vytvořit společnost" /></Editor>
    <Table data={rows(result.data)} columns={[
      { key: "name", label: "Společnost" }, { key: "type", label: "Typ" }, { key: "currency", label: "Měna" },
      { key: "status", label: "Stav", render: (item) => item.status === "ACTIVE" ? "Aktivní" : "Neaktivní" },
    ]} actions={(company) => <Editor title="Upravit společnost"><ApiForm path={`companies/${text(company.id)}`} method="PUT" fields={companyFields} initial={company} transform={clean} onSaved={result.reload} /></Editor>} />
  </Module>;
}

function Settings() {
  const result = useApi("settings");
  const fields: Field[] = [
    { name: "companyName", label: "Název společnosti", required: true },
    { name: "companyEmail", label: "E-mail společnosti", type: "email", required: true },
    { name: "currencyCode", label: "Výchozí měna", required: true },
    { name: "timezone", label: "Časové pásmo", required: true },
    { name: "fiscalYearStartMonth", label: "Začátek fiskálního roku (měsíc)", type: "number", required: true, min: 1, max: 12, step: "1" },
    { name: "defaultPaymentTermsDays", label: "Splatnost faktur ve dnech", type: "number", required: true, min: 0, step: "1" },
    { name: "deliveryFee", label: "Poplatek za doručení (Kč)", type: "number", required: true, min: 0, step: "0.01" },
    { name: "eshopMarginPercent", label: "Globální marže (%)", type: "number", required: true, min: 0, max: 99.98, step: "0.01" },
    { name: "eshopRoundingUnit", label: "Zaokrouhlení ceny (Kč)", type: "select", options: [1, 10, 100].map((value) => ({ value: String(value), label: `${value} Kč` })) },
    { name: "eshopDefaultVatRate", label: "Výchozí DPH (%)", type: "number", required: true, min: 0, max: 100, step: "0.01" },
  ];
  return <Module title="Nastavení" error={result.error} loading={result.loading}>
    {result.data && <ApiForm key={JSON.stringify(result.data)} path="settings" method="PATCH" fields={fields} initial={record(result.data)}
      transform={(values) => ({ ...clean(values), eshopRoundingUnit: number(values.eshopRoundingUnit) })} onSaved={result.reload} label="Uložit nastavení" />}
    {!result.data && !result.error && <Alert>Nastavení není dostupné.</Alert>}
  </Module>;
}

export function AdminModule({ module }: { module: string }) {
  switch (module) {
    case "users": return <Users />;
    case "roles": return <Roles matrixOnly={false} />;
    case "role-modules": return <Roles matrixOnly />;
    case "companies": return <Companies />;
    case "settings": return <Settings />;
    default: return <Module title="Administrace" error="Neznámý modul."><p>Zvolte modul v přehledu aplikací.</p></Module>;
  }
}
