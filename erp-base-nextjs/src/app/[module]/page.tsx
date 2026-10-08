import { notFound } from "next/navigation";
import { Shell } from "@/components/shell";
import { AdminModule } from "@/modules/admin";
import { WorkforceModule } from "@/modules/workforce";
import { IndependentModule } from "@/modules/independent";
import { OperationsModule } from "@/modules/operations";
import { CatalogModule } from "@/modules/catalog";
import { PublicPage } from "@/components/public-page";

export default async function ModulePage({ params }: { params: Promise<{ module: string }> }) {
  const { module } = await params;
  const stylesheets: Record<string, string[]> = {
    users: ["users.css"],
    roles: ["roles.css"],
    "role-modules": ["roles.css"],
    companies: ["companies.css"],
    settings: ["settings.css"],
    dashboard: ["dashboard.css"],
    accounting: ["accounting.css"],
    crm: ["crm.css"],
    sales: ["sales.css"],
    purchase: ["purchase.css"],
    inventory: ["inventory.css", "inventory-views.css"],
    manufacturing: ["manufacturing.css"],
    promo: ["promo.css", "promo-form.css", "promo-table.css"],
    pos: ["pos.css", "pos-table.css"],
    hr: ["hr.css", "workforce.css"],
    documents: ["documents.css"],
    projects: ["projects.css"],
    helpdesk: ["helpdesk.css"],
    website: ["website.css"],
    ecommerce: ["ecommerce.css"],
    marketing: ["marketing.css"],
    planning: ["planning.css", "workforce.css"],
  };
  const component = ["users", "roles", "role-modules", "companies", "settings"].includes(module) ? <AdminModule module={module} />
    : ["hr", "planning"].includes(module) ? <WorkforceModule module={module} />
    : ["dashboard", "accounting", "crm", "documents", "projects", "helpdesk", "marketing", "website"].includes(module) ? <IndependentModule module={module} />
    : ["sales", "purchase", "manufacturing", "pos", "inventory", "promo"].includes(module) ? <OperationsModule module={module} />
    : module === "ecommerce" ? <CatalogModule /> : null;
  if (!component) {
    if (["logout", "favicon.ico"].includes(module)) notFound();
    return <PublicPage slug={`/${module}`} />;
  }
  return <>
    {stylesheets[module]?.map((stylesheet) => <link key={stylesheet} rel="stylesheet" href={`/assets/${stylesheet}?v=20261008-1`} />)}
    <Shell activePath={module}>{component}</Shell>
  </>;
}
