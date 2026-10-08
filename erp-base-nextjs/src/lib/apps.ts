export const appCatalog = [
  { id: "base", permission: "users", path: "users", category: "core", title: "Base", subtitle: "Uživatelé, role a společnosti", description: "Centrální správa ERP instance. Nastavte, kdo má přístup ke kterým datům a modulům.", icon: "E" },
  { id: "accounting", permission: "accounting", path: "accounting", category: "core", title: "Účetnictví", subtitle: "Finance a doklady", description: "Účetní agenda, faktury, bankovní pohyby a finanční přehledy na jednom místě.", icon: "∿" },
  { id: "crm", permission: "crm", path: "crm", category: "sales", title: "CRM", subtitle: "Příležitosti a zákazníci", description: "Sledujte obchodní příležitosti od prvního kontaktu až po uzavřenou objednávku.", icon: "◆" },
  { id: "sales", permission: "sales", path: "sales", category: "sales", title: "Prodej", subtitle: "Nabídky a objednávky", description: "Tvorba nabídek, objednávek a cenových pravidel pro zákazníky.", icon: "▥" },
  { id: "purchase", permission: "purchase", path: "purchase", category: "operations", title: "Nákup", subtitle: "Dodavatelé a nákupní objednávky", description: "Řiďte dodavatele, poptávky, nákupní objednávky a příjem zboží.", icon: "═" },
  { id: "inventory", permission: "inventory", path: "inventory", category: "operations", title: "Sklad", subtitle: "Zásoby a pohyb zboží", description: "Stav zásob, skladové lokace, příjem, výdej a inventury v reálném čase.", icon: "⬡" },
  { id: "manufacturing", permission: "manufacturing", path: "manufacturing", category: "operations", title: "Výroba", subtitle: "Plánování a výrobní příkazy", description: "Plánujte výrobu, kusovníky, pracovní postupy a spotřebu materiálu.", icon: "▰" },
  { id: "promotions", permission: "promo-campaigns", path: "promo", category: "sales", title: "Promo kampaně", subtitle: "Akce a letákové nabídky", description: "Plánujte promo akce, alokujte zboží do prodejen a sledujte prodeje i příspěvky dodavatelů.", icon: "✦", badge: "MVP" },
  { id: "pos", permission: "pos", path: "pos", category: "sales", title: "Pokladna", subtitle: "Prodej na prodejně", description: "Rychlé pokladní rozhraní, směny, platby a denní uzávěrky pro prodejny.", icon: "▤" },
  { id: "hr", permission: "hr", path: "hr", category: "core", title: "Lidé", subtitle: "Zaměstnanci a docházka", description: "Evidence zaměstnanců, týmů, pracovních rolí, dovolených a přístupů.", icon: "●" },
  { id: "documents", permission: "documents", path: "documents", category: "core", title: "Dokumenty", subtitle: "Soubory a schvalování", description: "Ukládejte dokumenty u záznamů, nastavte schvalovací kroky a najděte vše během okamžiku.", icon: "▣" },
  { id: "project", permission: "projects", path: "projects", category: "operations", title: "Projekty", subtitle: "Úkoly a termíny", description: "Rozdělte práci do projektů, úkolů a milníků. Sledujte odpovědnosti i termíny.", icon: "◢" },
  { id: "helpdesk", permission: "helpdesk", path: "helpdesk", category: "operations", title: "Helpdesk", subtitle: "Požadavky a podpora", description: "Přijímejte požadavky, přiřazujte je týmům a dodržujte SLA vůči interním i externím zákazníkům.", icon: "＋" },
  { id: "website", permission: "website", path: "website", category: "sales", title: "Web", subtitle: "Veřejný web a obsah", description: "Spravujte obsah webu, katalog a zákaznické formuláře napojené na ERP data.", icon: "◓" },
  { id: "ecommerce", permission: "catalog", path: "ecommerce", category: "sales", title: "eCommerce", subtitle: "Katalog a online obchod", description: "Navrhněte úvodní stránku, spravujte kategorie a ověřte skladovou dostupnost i doručení.", icon: "◫", badge: "NOVÉ" },
  { id: "marketing", permission: "marketing", path: "marketing", category: "sales", title: "Marketing", subtitle: "Kampaně a komunikace", description: "Segmentujte zákazníky, připravujte kampaně a měřte jejich dopad na obchod.", icon: "➤" },
  { id: "planning", permission: "planning", path: "planning", category: "operations", title: "Plánování", subtitle: "Směny a kapacity", description: "Plánujte směny, zdroje a kapacity týmů tak, aby provoz držel krok s poptávkou.", icon: "◀" },
  { id: "dashboard", permission: "dashboard", path: "dashboard", category: "core", title: "Dashboard", subtitle: "Přehledy a KPI", description: "Sestavte si pracovní přehled z klíčových ukazatelů napříč všemi moduly ERP.", icon: "▦" },
] as const;

export const appFilters = [
  ["all", "Vše"],
  ["core", "Základ"],
  ["operations", "Provoz"],
  ["sales", "Obchod"],
] as const;

export type App = (typeof appCatalog)[number];
export type AppFilter = (typeof appFilters)[number][0];

const basePermissions = ["companies", "users", "roles", "settings"];

export function getAllowedApps(administrator: boolean, permissions: readonly string[]): App[] {
  const hasBaseAccess = permissions.some((permission) => basePermissions.includes(permission));
  return appCatalog.filter(({ id, permission }) =>
    administrator || (id === "base" ? hasBaseAccess : permissions.includes(permission)),
  );
}

export function filterApps(apps: readonly App[], query: string, filter: AppFilter): App[] {
  const normalizedQuery = query.trim().toLocaleLowerCase("cs");
  return apps.filter((app) => {
    const matchesQuery = `${app.title} ${app.subtitle} ${app.description}`
      .toLocaleLowerCase("cs")
      .includes(normalizedQuery);
    return matchesQuery && (filter === "all" || app.category === filter);
  });
}
