import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import Twig from "twig";
import type { Context, Page } from "./context";

const routes: Record<string, string> = {
  app_root: "/", app_home: "/apps", app_login: "/login", app_logout: "/logout",
  app_inventory: "/inventory", app_ecommerce: "/ecommerce", app_shop: "/shop", app_eshop: "/eshop", app_promo: "/promo",
};

let loaded: Promise<void> | undefined;

Twig.extendFunction("asset", (...parameters) => {
  const asset = String(parameters[0]);
  if (!/^assets\/[a-z0-9.-]+\.(css|js)$/.test(asset)) throw new Error(`Neplatný asset: ${asset}`);
  return `/${asset}`;
});
Twig.extendFunction("path", (...parameters) => {
  const name = String(parameters[0]);
  const route = routes[name];
  if (!route) throw new Error(`Neznámá route: ${name}`);
  return route;
});
Twig.extendFilter("where", (value, parameters) => {
  if (value == null) return [];
  if (!Array.isArray(value)) throw new Error("Filtr where vyžaduje seznam.");
  const key = String(parameters[0]);
  return value.filter((item: unknown) => item !== null && typeof item === "object"
    && Object.hasOwn(item, key) && Reflect.get(item, key) === parameters[1]);
});
Twig.extendFilter("json_encode", (value) =>
  JSON.stringify(value ?? null).replace(/[<>&\u2028\u2029]/g, (character) =>
    `\\u${character.charCodeAt(0).toString(16).padStart(4, "0")}`),
);

async function loadTemplates(): Promise<void> {
  const directory = path.join(process.cwd(), "templates");
  const names = (await readdir(directory, { recursive: true })).filter((name) => name.endsWith(".twig")).sort();
  for (const name of names) {
    Twig.twig({
      id: name.replaceAll(path.sep, "/"),
      data: await readFile(path.join(directory, name), "utf8"),
      autoescape: true, allowInlineIncludes: true, rethrow: true,
    });
  }
}

export async function renderPage(page: Page, context: Context): Promise<string> {
  loaded ??= loadTemplates();
  await loaded;
  return String(Twig.twig({ ref: page.template }).render({
    ...page.data,
    app: {
      request: { pathInfo: context.url.pathname },
      session: {
        get: (key: string) => {
          if (key === "userName") return context.session.userName;
          if (key === "roleName") return context.session.roleName;
          return undefined;
        },
      },
    },
  }));
}
