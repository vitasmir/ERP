import { createElement, type ComponentType } from "react";
import { renderToStaticMarkup } from "react-dom/server.edge";
import type { Page, Row, Session } from "../lib/context";
import {
  AccountingPage, CrmPage, DocumentsPage, HelpdeskPage, ProjectsPage, MarketingPage,
  WebsitePage, PublicWebsitePage, SalesPage, ManufacturingPage, PosPage,
} from "./pages/business";
import {
  LoginPage, HomePage, AdminCompaniesPage, AdminUsersPage, AdminRolesPage,
  AdminRoleModulesPage, AdminSettingsPage, DashboardPage,
} from "./pages/core";
import {
  HrPage, PlanningPage, InventoryPage, PromoPage, EcommercePage, ShopPage, PurchasePage,
} from "./pages/operations";
import { AppDocument, type ViewProps } from "./shared";

type PageSpec = {
  title: string;
  component: ComponentType<ViewProps>;
  styles: string[];
  scripts?: string[];
  layout?: "app" | "login" | "shop";
};

const asset = (name: string) => `/assets/${name}`;
const pages: Record<string, PageSpec> = {
  "auth/login": { title: "Přihlášení", component: LoginPage, styles: [asset("workflows.css")], layout: "login" },
  "home/index": { title: "Přehled", component: HomePage, styles: [] },
  "dashboard/index": { title: "Dashboard", component: DashboardPage, styles: [asset("dashboard.css")] },
  "admin/companies": { title: "Společnosti", component: AdminCompaniesPage, styles: [asset("companies.css")] },
  "admin/users": { title: "Uživatelé", component: AdminUsersPage, styles: [asset("users.css")] },
  "admin/roles": { title: "Role a oprávnění", component: AdminRolesPage, styles: [asset("roles.css")] },
  "admin/role_modules": { title: "Role pro moduly", component: AdminRoleModulesPage, styles: [asset("roles.css")] },
  "admin/settings": { title: "Nastavení", component: AdminSettingsPage, styles: [asset("settings.css")] },
  "accounting/index": { title: "Účetnictví", component: AccountingPage, styles: [asset("accounting.css")] },
  "crm/index": { title: "CRM", component: CrmPage, styles: [asset("crm.css")] },
  "documents/index": { title: "Dokumenty", component: DocumentsPage, styles: [asset("documents.css")] },
  "helpdesk/index": { title: "Helpdesk", component: HelpdeskPage, styles: [asset("helpdesk.css")] },
  "projects/index": { title: "Projekty", component: ProjectsPage, styles: [asset("projects.css")] },
  "marketing/index": { title: "Marketing", component: MarketingPage, styles: [asset("marketing.css")] },
  "website/index": { title: "Web", component: WebsitePage, styles: [asset("website.css")] },
  "website/public": { title: "Veřejná stránka", component: PublicWebsitePage, styles: [asset("website.css")] },
  "sales/index": { title: "Prodej", component: SalesPage, styles: [asset("sales.css")] },
  "manufacturing/index": { title: "Výroba", component: ManufacturingPage, styles: [asset("manufacturing.css")] },
  "pos/index": { title: "Pokladna", component: PosPage, styles: [asset("pos.css"), asset("pos-table.css")] },
  "hr/index": { title: "Lidé", component: HrPage, styles: [asset("hr.css"), asset("workforce.css")] },
  "planning/index": { title: "Plánování", component: PlanningPage, styles: [asset("planning.css"), asset("workforce.css")] },
  "inventory/index": {
    title: "Sklad", component: InventoryPage, styles: [asset("inventory.css"), asset("inventory-views.css")],
    scripts: [asset("inventory.js")],
  },
  "promo/index": {
    title: "Promo kampaně", component: PromoPage, styles: [asset("promo.css"), asset("promo-table.css"), asset("promo-form.css")],
    scripts: [asset("promo.js")],
  },
  "ecommerce/index": {
    title: "eCommerce", component: EcommercePage, styles: [asset("ecommerce.css")],
    scripts: [asset("ecommerce.js")],
  },
  "shop/index": { title: "E-shop", component: ShopPage, styles: [asset("shop.css")], scripts: [asset("shop.js")], layout: "shop" },
  "purchase/index": { title: "Nákup", component: PurchasePage, styles: [asset("purchase.css"), asset("workforce.css")] },
};

export function renderPage(page: Page, pathname: string, session: Session): string {
  const spec = pages[page.view];
  if (!spec) throw new Error(`Unknown React page: ${page.view}`);
  const title = String(page.data.pageTitle ?? spec.title);
  const component = createElement(spec.component, { data: page.data as Row, pathname });
  const document = createElement(AppDocument, {
    title,
    pathname,
    breadcrumb: String(page.data.breadcrumb ?? "BASE / ADMINISTRATION"),
    userName: session.userName ?? "",
    roleName: session.roleName ?? "",
    layout: spec.layout,
    pageStyles: spec.styles,
    pageScripts: spec.scripts,
    children: component,
  });
  const inlineScripts: string[] = [];
  const markup = renderToStaticMarkup(document).replace(
    /<script data-page-script="true">([\s\S]*?)<\/script>/g,
    (_match, source: string) => {
      inlineScripts.push(`<script>${source}</script>`);
      return "";
    },
  );
  return `<!doctype html>${markup.replace("</body>", `${inlineScripts.join("")}</body>`)}`;
}
