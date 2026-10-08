"use client";

import { useEffect, useState } from "react";
import { Action, Alert, ApiForm, Editor, Metrics, Module, Table, type Field, type Option } from "@/components/ui";
import { useApi } from "@/lib/client-api";
import { number, record, roleKey, rows, text, type Json, type Row } from "@/lib/data";

export function workforceRoles(data: Json | undefined, extra: string[] = []): string[] {
  const names = Array.isArray(data) ? data.map((item) => typeof item === "string" ? item : text(record(item).name)) : [];
  const seen = new Set<string>();
  return [...names, ...extra].map((name) => name.trim()).filter((name) => {
    const key = roleKey(name);
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

export function validateInterval(values: Row): Row {
  const start = text(values.startAt);
  const end = text(values.endAt);
  if (!start || !end || !Number.isFinite(Date.parse(start)) || !Number.isFinite(Date.parse(end)) || Date.parse(end) <= Date.parse(start)) {
    throw new Error("Konec musí být později než začátek.");
  }
  return values;
}

function options(values: string[]): Option[] {
  return values.map((value) => ({ value, label: value }));
}

function datetime(value: Json | undefined): string {
  return text(value).replace("T", " ");
}

function useAccess() {
  const current = useApi("auth/me");
  const user = record(current.data);
  const role = roleKey(text(user.roleName));
  const admin = user.administrator === true || ["administrator", "admin"].includes(role);
  const manageAll = admin || ["hr", "personalista", "planovac", "planner"].includes(role);
  return { ...current, admin, manageAll, hrEdit: admin || ["hr", "personalista"].includes(role),
    planningEdit: manageAll || ["vedouci tymu", "team lead"].includes(role) };
}

function ShiftForm({ shift, employees, workplaces, roles, manageAll, reload }: {
  shift?: Row; employees: Row[]; workplaces: Row[]; roles: string[]; manageAll: boolean; reload: () => void;
}) {
  const fields: Field[] = [
    { name: "employeeId", label: "Zaměstnanec", type: "select", required: !manageAll, options: [
      { value: "", label: manageAll ? "Neobsazeno" : "Vyberte zaměstnance" },
      ...employees.map((employee) => ({ value: text(employee.id), label: `${text(employee.fullName)} / ${text(employee.teamName)}` })),
    ] },
    { name: "department", label: "Pracoviště", type: "select", required: true,
      options: options([...new Set([...workplaces.map((item) => text(item.name)), text(shift?.department)].filter(Boolean))]) },
    { name: "startAt", label: "Začátek", type: "datetime-local", required: true },
    { name: "endAt", label: "Konec", type: "datetime-local", required: true },
  ];
  return <ApiForm path={shift ? `planning/shifts/${text(shift.id)}` : "planning/shifts"} method={shift ? "PUT" : "POST"}
    fields={fields} initial={shift} label={shift ? "Uložit směnu" : "Vytvořit směnu"} onSaved={reload}
    transform={(values, form) => {
      const roleName = text(form.get("roleName")?.toString()).trim();
      if (!roleName) throw new Error("Vyplňte pracovní roli.");
      const body: Row = { ...validateInterval(values), roleName, employeeId: text(values.employeeId) || null };
      if (shift) body.version = number(shift.version);
      return body;
    }}>
    <label>Pracovní role<input name="roleName" required maxLength={150} list="planning-role-options" defaultValue={text(shift?.roleName)} /></label>
    {!roles.length && <p>Seznam pracovních rolí je prázdný. Roli můžete zadat ručně.</p>}
    {shift?.status === "PUBLISHED" && <p>Změna publikované směny odešle oznámení dotčeným zaměstnancům.</p>}
  </ApiForm>;
}

const eventLabels: Record<string, string> = {
  CREATED: "Vytvořeno", UPDATED: "Upraveno", PUBLISHED: "Publikováno", CHANGED_AFTER_PUBLICATION: "Změna po publikaci",
};

function ShiftHistory({ id }: { id: string }) {
  const result = useApi(`planning/shifts/${id}/events`);
  return <section id="audit"><h2>Historie směny</h2><Alert>{result.error}</Alert>
    {result.loading ? <p role="status">Načítání historie…</p> : <Table data={rows(result.data)} columns={[
      { key: "occurredAt", label: "Čas", render: (event) => datetime(event.occurredAt) },
      { key: "action", label: "Událost", render: (event) => eventLabels[text(event.action)] ?? text(event.action) },
      { key: "actor", label: "Uživatel" }, { key: "details", label: "Podrobnosti" },
    ]} />}
    <button type="button" onClick={result.reload}>Obnovit historii</button>
  </section>;
}

function Planning() {
  const access = useAccess();
  const overview = useApi("planning/overview");
  const employees = useApi("planning/employees");
  const workplaces = useApi("planning/workplaces");
  const roleResult = useApi("planning/roles");
  const notifications = useApi("planning/notifications");
  const [selected, setSelected] = useState<string[]>([]);
  const [auditId, setAuditId] = useState("");
  const [historyRevision, setHistoryRevision] = useState(0);
  const [employeeFilter, setEmployeeFilter] = useState("");
  const [departmentFilter, setDepartmentFilter] = useState("");
  useEffect(() => { setAuditId(new URLSearchParams(window.location.search).get("audit") ?? ""); }, []);
  const data = record(overview.data);
  const shifts = rows(data.shifts);
  const roles = workforceRoles(roleResult.data);
  const filtered = shifts.filter((shift) => (!employeeFilter || text(shift.employeeId) === employeeFilter || employeeFilter === "open" && !shift.employeeId)
    && (!departmentFilter || text(shift.department) === departmentFilter));
  const reload = () => {
    overview.reload(); notifications.reload(); employees.reload(); setSelected([]); setHistoryRevision((value) => value + 1);
  };
  const available = !employees.error && !workplaces.error;
  const drafts = shifts.filter((shift) => shift.status === "DRAFT");
  const selectedDrafts = selected.filter((id) => drafts.some((shift) => text(shift.id) === id));
  return <Module title="Plánování" loading={access.loading || overview.loading} error={access.error || overview.error}>
    <Metrics data={data} labels={{ shiftCount: "Naplánované směny", openShiftCount: "Neobsazené sloty", plannedHours: "Plánované hodiny", draftShiftCount: "Směny v návrhu" }} />
    <Alert>{employees.error}</Alert><Alert>{workplaces.error}</Alert><Alert>{roleResult.error}</Alert>
    <datalist id="planning-role-options">{roles.map((role) => <option key={role} value={role} />)}</datalist>
    {access.planningEdit && available && <Editor title="Nová směna">
      <ShiftForm employees={rows(employees.data)} workplaces={rows(workplaces.data)} roles={roles} manageAll={access.manageAll} reload={reload} />
    </Editor>}
    {access.manageAll && <Editor title="Pracoviště a kapacity">
      <Table data={rows(workplaces.data)} columns={[{ key: "name", label: "Pracoviště" }, { key: "capacity", label: "Souběžné sloty" }]} />
      <ApiForm path="planning/workplaces" fields={[
        { name: "name", label: "Pracoviště", required: true, maxLength: 150 },
        { name: "capacity", label: "Souběžné sloty", type: "number", required: true, min: 1, max: 10000, step: "1", value: 1 },
      ]} label="Uložit kapacitu" onSaved={workplaces.reload} />
      <p>Zadáním existujícího názvu změníte jeho kapacitu.</p>
    </Editor>}
    <section id="schedule"><h2>Směny a kapacity</h2>
      <div className="form-fields">
        <label>Zaměstnanec<select value={employeeFilter} onChange={(event) => setEmployeeFilter(event.target.value)}>
          <option value="">Všichni zaměstnanci</option><option value="open">Neobsazeno</option>
          {rows(employees.data).map((employee) => <option key={text(employee.id)} value={text(employee.id)}>{text(employee.fullName)}</option>)}
        </select></label>
        <label>Pracoviště<select value={departmentFilter} onChange={(event) => setDepartmentFilter(event.target.value)}>
          <option value="">Všechna pracoviště</option>{[...new Set(shifts.map((shift) => text(shift.department)))].map((name) => <option key={name} value={name}>{name}</option>)}
        </select></label>
      </div>
      {access.planningEdit && <ApiForm path="planning/publish" fields={[]} label={`Publikovat vybrané směny (${selectedDrafts.length})`} onSaved={reload}
        transform={() => {
          if (!selectedDrafts.length) throw new Error("Vyberte alespoň jednu směnu v návrhu.");
          if (selectedDrafts.length > 500) throw new Error("Najednou lze publikovat nejvýše 500 směn.");
          return { shiftIds: selectedDrafts };
        }}>
        <button type="button" onClick={() => setSelected(filtered.filter((shift) => shift.status === "DRAFT").map((shift) => text(shift.id)))}>Vybrat zobrazené návrhy</button>
        <button type="button" onClick={() => setSelected([])}>Zrušit výběr</button>
      </ApiForm>}
      <Table data={filtered} columns={[
        { key: "status", label: "Stav", render: (shift) => shift.status === "PUBLISHED" ? "Publikováno" : "Návrh" },
        { key: "roleName", label: "Pracovní role" }, { key: "department", label: "Pracoviště" },
        { key: "employeeName", label: "Zaměstnanec", render: (shift) => text(shift.employeeName) || "Neobsazeno" },
        { key: "startAt", label: "Začátek", render: (shift) => datetime(shift.startAt) },
        { key: "endAt", label: "Konec", render: (shift) => datetime(shift.endAt) },
      ]} actions={(shift) => <>
        {access.planningEdit && shift.status === "DRAFT" && <>
          <label><input type="checkbox" checked={selectedDrafts.includes(text(shift.id))} onChange={(event) => {
            const id = text(shift.id);
            setSelected((current) => event.target.checked ? [...new Set([...current, id])] : current.filter((value) => value !== id));
          }} /> Vybrat</label>
          <Action path={`planning/shifts/${text(shift.id)}/publish`} label="Publikovat směnu" onSaved={reload} />
          <Action path={`planning/shifts/${text(shift.id)}`} method="DELETE" label="Smazat koncept" onSaved={reload} confirm="Opravdu chcete tento koncept smazat?" />
        </>}
        <button type="button" onClick={() => { setAuditId(text(shift.id)); setHistoryRevision((value) => value + 1); }}>Historie</button>
        {access.planningEdit && available && <>
          <Editor title="Upravit směnu"><ShiftForm shift={shift} employees={rows(employees.data)} workplaces={rows(workplaces.data)} roles={roles} manageAll={access.manageAll} reload={reload} /></Editor>
          <Editor title="Přiřadit zaměstnance"><ApiForm path={`planning/shifts/${text(shift.id)}/assign`} method="PATCH" initial={shift} onSaved={reload}
            fields={[{ name: "employeeId", label: "Zaměstnanec", type: "select", required: !access.manageAll, options: [
              { value: "", label: "Neobsazeno" }, ...rows(employees.data).map((employee) => ({ value: text(employee.id), label: text(employee.fullName) })),
            ] }]} transform={(values) => ({ employeeId: text(values.employeeId) || null, version: number(shift.version) })} label="Uložit přiřazení" /></Editor>
        </>}
      </>} />
    </section>
    {auditId && <ShiftHistory key={`${auditId}-${historyRevision}`} id={auditId} />}
    <section><h2>Moje oznámení</h2><Alert>{notifications.error}</Alert>
      {notifications.loading ? <p role="status">Načítání oznámení…</p> : <Table data={rows(notifications.data)} columns={[
        { key: "message", label: "Oznámení" }, { key: "createdAt", label: "Čas", render: (notice) => datetime(notice.createdAt) },
        { key: "readAt", label: "Stav", render: (notice) => notice.readAt ? "Přečteno" : "Nepřečteno" },
      ]} actions={(notice) => !notice.readAt && <Action path={`planning/notifications/${text(notice.id)}/read`} label="Označit jako přečtené" onSaved={notifications.reload} />} />}
    </section>
    <button type="button" onClick={reload}>Obnovit plán</button>
  </Module>;
}

function EmployeeForm({ employee, employees, teams, roles, reload }: {
  employee?: Row; employees: Row[]; teams: Row[]; roles: string[]; reload: () => void;
}) {
  const [jobTitle, setJobTitle] = useState(text(employee?.jobTitle));
  const [teamId, setTeamId] = useState(text(employee?.teamId));
  const [deputyId, setDeputyId] = useState(text(employee?.deputyEmployeeId));
  const deputies = employees.filter((item) => item.id !== employee?.id && item.status !== "INACTIVE" && roleKey(text(item.jobTitle)) === roleKey(jobTitle));
  const fields: Field[] = [
    { name: "fullName", label: "Jméno a příjmení", required: true, maxLength: 200 },
    { name: "employmentStartDate", label: "Nástup", type: "date", required: true },
  ];
  return <ApiForm path={employee ? `hr/employees/${text(employee.id)}` : "hr/employees"} method={employee ? "PUT" : "POST"}
    fields={fields} initial={employee} onSaved={reload} label={employee ? "Uložit profil" : "Založit zaměstnance"}
    transform={(values) => {
      if (!teamId || !jobTitle) throw new Error("Vyberte tým a pracovní roli.");
      if (deputyId && !deputies.some((deputy) => text(deputy.id) === deputyId)) throw new Error("Zástupce musí mít stejnou roli a nesmí být neaktivní.");
      return { ...values, fullName: text(values.fullName).trim(), teamName: "", teamId, jobTitle, deputyEmployeeId: deputyId || null };
    }}>
    <label>Tým<select value={teamId} required onChange={(event) => setTeamId(event.target.value)}>
      <option value="">Vyberte tým</option>{teams.map((team) => <option key={text(team.id)} value={text(team.id)}>{text(team.name)}</option>)}
    </select></label>
    <label>Pracovní role<select value={jobTitle} required onChange={(event) => { setJobTitle(event.target.value); setDeputyId(""); }}>
      <option value="">Vyberte roli</option>{workforceRoles(roles, [text(employee?.jobTitle)]).map((role) => <option key={role} value={role}>{role}</option>)}
    </select></label>
    <label>Zástupce<select value={deputyId} onChange={(event) => setDeputyId(event.target.value)}>
      <option value="">Bez zástupce</option>{deputies.map((deputy) => <option key={text(deputy.id)} value={text(deputy.id)}>{text(deputy.fullName)} · {text(deputy.teamName)}</option>)}
    </select></label>
    <p>Zástupce musí mít stejnou pracovní roli. Kvalifikace a absence se spravují v dostupnosti zaměstnance.</p>
  </ApiForm>;
}

function Availability({ employee, edit, roles, onSaved }: { employee: Row; edit: boolean; roles: string[]; onSaved: () => void }) {
  const id = text(employee.id);
  const result = useApi(`hr/employees/${id}/availability`);
  const data = record(result.data);
  const reload = () => { result.reload(); onSaved(); };
  return <section id="availability"><h2>Dostupnost a kvalifikace: {text(employee.fullName)}</h2><Alert>{result.error}</Alert>
    {result.loading ? <p role="status">Načítání dostupnosti…</p> : <>
      <h3>Kvalifikace</h3>
      <ul>{workforceRoles(data.qualifications).map((role) => <li key={role}>{role}</li>)}</ul>
      {!workforceRoles(data.qualifications).length && <p>Žádné kvalifikace.</p>}
      {edit && <ApiForm path={`hr/employees/${id}/qualifications`} fields={[]} onSaved={reload} label="Přidat kvalifikaci"
        transform={(_values, form) => {
          const roleName = text(form.get("roleName")?.toString()).trim();
          if (!roleName) throw new Error("Vyplňte pracovní roli.");
          return { roleName };
        }}>
        <label>Pracovní role<input name="roleName" list="hr-qualification-roles" required maxLength={150} /></label>
        <datalist id="hr-qualification-roles">{roles.map((role) => <option key={role} value={role} />)}</datalist>
      </ApiForm>}
      <h3>Absence</h3>
      <Table data={rows(data.absences)} columns={[
        { key: "startAt", label: "Začátek", render: (absence) => datetime(absence.startAt) },
        { key: "endAt", label: "Konec", render: (absence) => datetime(absence.endAt) }, { key: "reason", label: "Důvod" },
      ]} actions={edit ? (absence) => <Action path={`hr/employees/${id}/absences/${text(absence.id)}`} method="DELETE" label="Zrušit absenci" confirm="Opravdu chcete zrušit tuto absenci?" onSaved={reload} /> : undefined} />
      {edit && <ApiForm path={`hr/employees/${id}/absences`} fields={[
        { name: "startAt", label: "Začátek", type: "datetime-local", required: true },
        { name: "endAt", label: "Konec", type: "datetime-local", required: true },
        { name: "reason", label: "Důvod", required: true, maxLength: 240 },
      ]} transform={validateInterval} onSaved={reload} label="Přidat absenci" />}
    </>}
  </section>;
}

function Hr() {
  const access = useAccess();
  const overview = useApi("hr/overview");
  const roleResult = useApi("roles");
  const fallbackRoles = useApi(roleResult.error ? "planning/roles" : null);
  const [teamFilter, setTeamFilter] = useState("");
  const [search, setSearch] = useState("");
  const [employeeId, setEmployeeId] = useState("");
  useEffect(() => { setEmployeeId(new URLSearchParams(window.location.search).get("employeeId") ?? ""); }, []);
  const data = record(overview.data);
  const employees = rows(data.employees);
  const teams = rows(data.teams);
  const roles = workforceRoles(roleResult.data ?? fallbackRoles.data, employees.flatMap((employee) => [text(employee.jobTitle), text(employee.userRoleName)]));
  const filtered = employees.filter((employee) => (!teamFilter || text(employee.teamId) === teamFilter)
    && (!search || [employee.fullName, employee.jobTitle, employee.teamName].some((value) => roleKey(text(value)).includes(roleKey(search)))));
  const selected = employees.find((employee) => text(employee.id) === employeeId);
  const today = new Date().toLocaleDateString("sv-SE");
  return <Module title="Lidé" error={access.error || overview.error} loading={access.loading || overview.loading}>
    <Metrics data={data} labels={{ activeEmployeeCount: "Aktivní zaměstnanci", onboardingCount: "Nástupy", teamCount: "Týmy" }} />
    {roleResult.error && <Alert>{`${roleResult.error} Nabídka rolí používá dostupné pracovní role.`}</Alert>}
    <Alert>{fallbackRoles.error}</Alert>
    {access.hrEdit && <>
      <Editor title="Týmy">
        <ApiForm path="hr/teams" fields={[{ name: "name", label: "Nový tým", required: true, maxLength: 150 }]} label="Založit tým" onSaved={overview.reload} />
        <Table data={teams} columns={[
          { key: "name", label: "Tým" },
          { key: "members", label: "Členů", render: (team) => employees.filter((employee) => employee.teamId === team.id).length },
        ]} actions={(team) => <>
          <Editor title="Přejmenovat"><ApiForm path={`hr/teams/${text(team.id)}`} method="PUT" fields={[{ name: "name", label: "Název týmu", required: true, maxLength: 150 }]} initial={team} onSaved={overview.reload} /></Editor>
          <Action path={`hr/teams/${text(team.id)}`} method="DELETE" label="Smazat" confirm="Opravdu chcete smazat tým? Tým s přiřazenými zaměstnanci nelze smazat." onSaved={overview.reload} />
          <button type="button" onClick={() => setTeamFilter(text(team.id))}>Zobrazit členy týmu</button>
        </>} />
      </Editor>
      <Editor title="Nový zaměstnanec"><EmployeeForm employees={employees} teams={teams} roles={roles} reload={overview.reload} /></Editor>
    </>}
    <section id="employees"><h2>Organizace a nástupy</h2>
      <div className="form-fields">
        <label>Hledat zaměstnance<input value={search} onChange={(event) => setSearch(event.target.value)} /></label>
        <label>Tým<select value={teamFilter} onChange={(event) => setTeamFilter(event.target.value)}>
          <option value="">Všichni zaměstnanci</option>{teams.map((team) => <option key={text(team.id)} value={text(team.id)}>{text(team.name)}</option>)}
        </select></label>
      </div>
      <p>{filtered.length} zaměstnanců</p>
      <Table data={filtered} columns={[
        { key: "status", label: "Stav", render: (employee) => employee.status === "ACTIVE" ? "Aktivní" : employee.status === "ONBOARDING" ? "Nástup" : "Neaktivní" },
        { key: "fullName", label: "Zaměstnanec" }, { key: "jobTitle", label: "Pracovní role" },
        { key: "teamName", label: "Tým" }, { key: "deputyName", label: "Zástupce" },
        { key: "employmentStartDate", label: "Nástup" },
      ]} actions={(employee) => <>
        <button type="button" onClick={() => setEmployeeId(text(employee.id))}>Dostupnost a kvalifikace</button>
        {access.admin && <a href={employee.hasUserAccount ? "/users" : `/users?employeeId=${encodeURIComponent(text(employee.id))}`}>
          {employee.hasUserAccount ? "Účet v Uživatelích" : "Vytvořit účet"}</a>}
        {access.hrEdit && <>
          {employee.status === "ACTIVE"
            ? <Action path={`hr/employees/${text(employee.id)}/deactivate`} label="Ukončit pracovní poměr" onSaved={overview.reload} confirm="Ukončit pracovní poměr a pozastavit přístup uživatele?" />
            : text(employee.employmentStartDate) <= today
              ? <Action path={`hr/employees/${text(employee.id)}/activate`} label="Aktivovat nástup" onSaved={overview.reload} />
              : <button disabled title="Aktivace bude možná po datu nástupu">Aktivovat po nástupu</button>}
          <Editor title="Upravit zaměstnance"><EmployeeForm employee={employee} employees={employees} teams={teams} roles={roles} reload={overview.reload} /></Editor>
        </>}
      </>} />
    </section>
    {selected && <Availability key={employeeId} employee={selected} edit={access.hrEdit} roles={roles} onSaved={overview.reload} />}
    {employeeId && !selected && <Alert>Vybraný zaměstnanec není dostupný v tomto přehledu.</Alert>}
    <button type="button" onClick={overview.reload}>Obnovit přehled</button>
  </Module>;
}

export function WorkforceModule({ module }: { module: string }) {
  switch (module) {
    case "hr": return <Hr />;
    case "planning": return <Planning />;
    default: return <Module title="Lidé a plánování" error="Neznámý modul."><p>Zvolte modul v přehledu aplikací.</p></Module>;
  }
}
