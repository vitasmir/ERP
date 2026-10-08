import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";
import test from "node:test";
import { appCatalog } from "../src/lib/apps";
import { Backend, type Context } from "../src/lib/context";
import { newSession } from "../src/lib/session";
import { renderPage } from "../src/lib/templates";

const phpProject = path.resolve("../erp-base-php");
const phpAvailable = existsSync(path.join(phpProject, "vendor/autoload.php"))
  && spawnSync("php", ["-v"], { encoding: "utf8" }).status === 0;
const id = "11111111-1111-1111-1111-111111111111";
const role = { id, name: "Administrátor", color: "#D9ED62", initial: "A", description: "Správa", permissions: [] };
const workerRole = { ...role, id: "22222222-2222-2222-2222-222222222222", name: "Skladník", initial: "S" };
const employee = { id, fullName: "Jan Test", teamId: id, teamName: "Tým", jobTitle: role.name, userRoleName: role.name,
  status: "ACTIVE", employmentStartDate: "2026-01-01", hasUserAccount: false };
const product = { id, name: "Produkt", sku: "SKU", unit: "ks", price: 12.50, purchasePrice: 10,
  categoryId: id, active: true, description: "Popis", images: [{ id, imageUrl: "/fixture.png", active: true }] };
const common = {
  error: null, actionError: null, message: null, edit: true, admin: true,
  roles: [role, workerRole], roleOptions: [role, workerRole], products: [product], workplaces: [], employees: [employee],
  warehouses: [{ id, name: "Sklad", ownerType: "COMPANY" }], companies: [], users: [],
  availability: null, requestedEmployeeId: "", requestedEmployeeName: "", settings: {},
  page: { title: "Veřejný obsah", content: "Obsah <b>bez HTML</b>" },
  overview: {
    employees: [employee], teams: [{ id, name: "Tým" }], orders: [{
      id, orderNumber: "PO-001", status: "REQUESTED", supplierName: "Dodavatel",
      sourceWarehouseId: id, destinationWarehouseId: id, requestedOn: "2026-09-01", expectedDeliveryDate: null,
      quantity: 2, receivedQuantity: 0, totalAmount: 20, lines: [{ productId: id, quantity: 2, unitPrice: 10 }],
    }],
  },
  matrix: { modules: [{ key: "hr", name: "Lidé" }, { key: "planning", name: "Plánování" }],
    permissions: [{ roleId: id, moduleKey: "hr" }, { roleId: workerRole.id, moduleKey: "hr" }] },
  allowedModules: ["base", "hr"], moduleCatalog: Object.fromEntries(appCatalog.map((app) => [app.id, app])),
};

function normalizeHtml(html: string): string {
  return html.replace(/<link rel="stylesheet" href="\/compatibility\.css\?v=\d+">\s*/g, "")
    .replace(/\/assets\/base\.js\?v=20261008-2/g, "/assets/base.js?v=20261008-1")
    .replace(/&#0?39;|&#x27;/g, "'").replace(/&quot;/g, '"').replace(/&amp;/g, "&")
    .replace(/>\s+</g, "><").replace(/\s+/g, " ").trim();
}

const phpCode = `
require $argv[1].'/vendor/autoload.php';
$twig = new Twig\\Environment(new Twig\\Loader\\FilesystemLoader($argv[1].'/templates'), ['autoescape' => 'html']);
$twig->addFunction(new Twig\\TwigFunction('asset', fn($name) => '/'.$name));
$twig->addFunction(new Twig\\TwigFunction('path', fn($name) => ['app_home'=>'/apps','app_login'=>'/login','app_logout'=>'/logout'][$name]));
$data = json_decode($argv[3], true, 512, JSON_THROW_ON_ERROR);
$data['app'] = ['request'=>['pathInfo'=>$argv[4]], 'session'=>new class {
  public function get($key) { return ['userName'=>'Jan Test','roleName'=>'Administrátor'][$key] ?? null; }
}];
echo $twig->render($argv[2], $data);
`;

test("Node HTML matches original PHP Twig for launcher, HR, purchase, role matrix, catalog and public content", { skip: !phpAvailable }, async () => {
  const session = { ...newSession(), userName: "Jan Test", roleName: "Administrátor" };
  for (const [template, pathname] of [
    ["home/index.html.twig", "/apps"], ["hr/index.html.twig", "/hr"], ["purchase/index.html.twig", "/purchase"],
    ["admin/role_modules.html.twig", "/role-modules"], ["ecommerce/index.html.twig", "/ecommerce"], ["website/public.html.twig", "/public-test"],
  ]) {
    const context: Context = { request: new Request(`http://localhost${pathname}`), url: new URL(`http://localhost${pathname}`),
      session, form: new FormData(), backend: new Backend(session), render: (template, data = {}, status = 200) => ({ template, data, status }) };
    const data = {
      ...common, eshopMarginPercent: 25, eshopDefaultVatRate: 21, selectedCategoryId: null,
      categoryOptions: [{ id, name: "Potraviny", depth: 0 }],
      ecommerce: { homepage: { headline: "Obchod", subheadline: "Nabídka", design: "CLASSIC", textX: 20, textY: 30 },
        products: [{ ...product, availability: [{ warehouseName: "Sklad", quantity: 8 }] }],
        categories: [{ id, name: "Potraviny", slug: "potraviny", active: true,
          children: [{ id: workerRole.id, name: "Pečivo", slug: "pecivo", children: [], active: true }] }] },
    };
    const php = spawnSync("php", ["-r", phpCode, phpProject, template, JSON.stringify(data), pathname], { encoding: "utf8" });
    assert.equal(php.status, 0, php.stderr);
    const html = await renderPage({ template, data: structuredClone(data), status: 200 }, context);
    assert.equal(normalizeHtml(html), normalizeHtml(php.stdout), template);
  }
});
