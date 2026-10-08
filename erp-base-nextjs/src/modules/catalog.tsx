"use client";

import { useEffect, useState } from "react";
import { Action, Alert, ApiForm, Editor, Module, Table, type Field, type Option } from "@/components/ui";
import { useApi } from "@/lib/client-api";
import { money, number, record, rows, text, type Json, type Row } from "@/lib/data";
import { decimalValue, integerValue } from "./operations";

export function flattenCategories(categories: Row[], depth = 0): Row[] {
  return categories.flatMap((category) => [{ ...category, depth }, ...flattenCategories(rows(category.children), depth + 1)]);
}

function categoryOptions(categories: Row[], current?: Row): Option[] {
  const excluded = new Set(current ? flattenCategories([current]).map((category) => text(category.id)) : []);
  return [{ value: "", label: "Bez kategorie / kořen" }, ...categories.filter((category) => !excluded.has(text(category.id))).map((category) => ({
    value: text(category.id), label: `${"— ".repeat(number(category.depth))}${text(category.name)}`,
  }))];
}

export function productPayload(values: Row, form: FormData): Row {
  const payload: Row = { ...values, categoryId: text(values.categoryId) || null, active: values.active === true };
  for (const name of ["price", "purchasePrice", "vatRate", "eshopMarginPercent"]) payload[name] = decimalValue(form, name, true);
  if (payload.price === null && payload.purchasePrice === null) throw new Error("Vyplňte prodejní nebo nákupní cenu.");
  if (payload.vatRate !== null && number(payload.vatRate) > 100) throw new Error("DPH musí být od 0 do 100 %.");
  if (payload.eshopMarginPercent !== null && number(payload.eshopMarginPercent) >= 99.99) throw new Error("Marže musí být menší než 99,99 %.");
  return payload;
}

function isJson(value: unknown): value is Json {
  if (value === null || typeof value === "string" || typeof value === "boolean") return true;
  if (typeof value === "number") return Number.isFinite(value);
  if (Array.isArray(value)) return value.every(isJson);
  if (typeof value === "object") return Object.values(value).every(isJson);
  return false;
}

export function importPayload(form: FormData): Json {
  const input = form.get("products");
  let parsed: unknown;
  try { parsed = JSON.parse(typeof input === "string" ? input : ""); }
  catch { throw new Error("Import není platný JSON."); }
  if (!isJson(parsed) || !Array.isArray(parsed) || !parsed.length || parsed.some((product) => product === null || typeof product !== "object" || Array.isArray(product))) {
    throw new Error("Import musí obsahovat neprázdné pole objektů produktů.");
  }
  for (const product of rows(parsed)) {
    if (!text(product.sku).trim() || !text(product.name).trim() || !text(product.unit).trim()) throw new Error("Každý produkt musí obsahovat SKU, název a jednotku.");
    if ((product.price === null || product.price === undefined) && (product.purchasePrice === null || product.purchasePrice === undefined)) {
      throw new Error("Každý produkt musí obsahovat prodejní nebo nákupní cenu.");
    }
  }
  return parsed;
}

function HomepageEditor({ homepage, onSaved }: { homepage: Row; onSaved: () => void }) {
  const [design, setDesign] = useState(text(homepage.design) || "BOTANICAL");
  const [headline, setHeadline] = useState(text(homepage.headline));
  const [subheadline, setSubheadline] = useState(text(homepage.subheadline));
  const [x, setX] = useState(text(homepage.textX) || "50");
  const [y, setY] = useState(text(homepage.textY) || "50");
  const background = design === "BOTANICAL" ? "#d8ead4" : design === "MARKET" ? "#ffe4b8" : "#f2f2f2";
  function position(nextX: number, nextY: number) {
    setX(String(Math.round(Math.max(0, Math.min(100, nextX)) * 100) / 100));
    setY(String(Math.round(Math.max(0, Math.min(100, nextY)) * 100) / 100));
  }
  return <section className="panel"><h2>Úvodní stránka: vzhled a sdělení</h2>
    <p>Klikněte do náhledu nebo použijte šipky pro posun textu. Souřadnice jsou v procentech.</p>
    <div tabIndex={0} role="group" aria-label="Náhled úvodní stránky; šipkami posunete text" style={{ position: "relative", height: 280, background, overflow: "hidden", border: "1px solid #777", borderRadius: 12 }}
      onClick={(event) => {
        const bounds = event.currentTarget.getBoundingClientRect();
        position((event.clientX - bounds.left) / bounds.width * 100, (event.clientY - bounds.top) / bounds.height * 100);
      }} onKeyDown={(event) => {
        if (!["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown"].includes(event.key)) return;
        event.preventDefault();
        position(number(x) + (event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0),
          number(y) + (event.key === "ArrowDown" ? 1 : event.key === "ArrowUp" ? -1 : 0));
      }}>
      <div style={{ position: "absolute", left: `${number(x)}%`, top: `${number(y)}%`, transform: "translate(-50%, -50%)", width: "min(80%, 360px)", textAlign: "center", pointerEvents: "none" }}>
        <small>Doručení ještě dnes</small><h3>{headline}</h3><p>{subheadline}</p>
      </div>
    </div>
    <ApiForm path="catalog/homepage" method="PUT" fields={[]} onSaved={onSaved} label="Uložit úvodní stránku"
      transform={(_values, form) => ({
        design, headline, subheadline, textX: decimalValue(form, "textX"), textY: decimalValue(form, "textY"),
      })}>
      <label>Design<select name="design" value={design} onChange={(event) => setDesign(event.target.value)}>
        <option value="BOTANICAL">Botanická sklizeň</option><option value="MARKET">Městský trh</option><option value="MINIMAL">Čistý minimalismus</option>
      </select></label>
      <label>Nadpis<input name="headline" maxLength={200} required value={headline} onChange={(event) => setHeadline(event.target.value)} /></label>
      <label>Podnadpis<input name="subheadline" maxLength={500} required value={subheadline} onChange={(event) => setSubheadline(event.target.value)} /></label>
      <label>Pozice X (%)<input name="textX" type="number" min={0} max={100} step="0.01" required value={x} onChange={(event) => setX(event.target.value)} /></label>
      <label>Pozice Y (%)<input name="textY" type="number" min={0} max={100} step="0.01" required value={y} onChange={(event) => setY(event.target.value)} /></label>
    </ApiForm>
  </section>;
}

function CategoryForm({ category, categories, onSaved }: { category?: Row; categories: Row[]; onSaved: () => void }) {
  const fields: Field[] = [
    { name: "name", label: "Název kategorie", required: true },
    { name: "slug", label: "Slug", required: true },
    { name: "parentId", label: "Nadřazená kategorie", type: "select", options: categoryOptions(categories, category) },
    { name: "sortOrder", label: "Pořadí", type: "number", min: 0, step: "1", required: true },
    { name: "active", label: "Aktivní", type: "checkbox" },
  ];
  return <ApiForm path={`catalog/categories${category ? `/${text(category.id)}` : ""}`} method={category ? "PUT" : "POST"}
    fields={fields} initial={category ?? { sortOrder: 10, active: true }} onSaved={onSaved} label={category ? "Uložit kategorii" : "Přidat kategorii"}
    transform={(values) => ({ ...values, parentId: text(values.parentId) || null, sortOrder: integerValue(values.sortOrder) })} />;
}

function CategoryTree({ tree, categories, selected, onSelect, onSaved }: {
  tree: Row[]; categories: Row[]; selected: string; onSelect: (id: string) => void; onSaved: () => void;
}) {
  return <ul>{tree.map((category) => <li key={text(category.id)}>
    <button type="button" aria-pressed={selected === text(category.id)} onClick={() => onSelect(text(category.id))}>{text(category.name)}</button>
    <small> /{text(category.slug)} · {category.active === true ? "Aktivní" : "Skrytá"}</small>
    <Editor><CategoryForm key={JSON.stringify(category)} category={category} categories={categories} onSaved={onSaved} /></Editor>
    <Action path={`catalog/categories/${text(category.id)}`} method="DELETE" label="Smazat kategorii"
      confirm="Opravdu smazat kategorii? Kategorie musí být bez produktů a podkategorií." onSaved={onSaved} />
    {rows(category.children).length > 0 && <CategoryTree tree={rows(category.children)} categories={categories} selected={selected} onSelect={onSelect} onSaved={onSaved} />}
  </li>)}</ul>;
}

function ProductForm({ product, categories, settings, onSaved }: { product?: Row; categories: Row[]; settings: Row; onSaved: () => void }) {
  const initial: Row = product ?? { unit: "ks", active: true, vatRate: settings.eshopDefaultVatRate ?? "21", eshopMarginPercent: settings.eshopMarginPercent ?? null };
  const fields: Field[] = [
    { name: "sku", label: "SKU", required: true },
    { name: "name", label: "Název", required: true },
    { name: "unit", label: "Jednotka", required: true },
    { name: "description", label: "Popis", type: "textarea" },
    { name: "categoryId", label: "Kategorie", type: "select", options: categoryOptions(categories) },
    { name: "price", label: "Prodejní cena s DPH (Kč; ruční cena bez nákupní ceny)", type: "number", min: 0, step: "0.01" },
    { name: "purchasePrice", label: "Nákupní cena bez DPH (Kč; přepočítá prodejní cenu)", type: "number", min: 0, step: "0.01" },
    { name: "vatRate", label: "DPH (%)", type: "number", min: 0, max: 100, step: "0.01" },
    { name: "eshopMarginPercent", label: "Marže (%; prázdná = globální)", type: "number", min: 0, max: 99.98, step: "0.01" },
    { name: "imageUrl", label: "URL obrázku" },
    { name: "active", label: "Aktivní v e-shopu", type: "checkbox" },
  ];
  return <><p>Je-li vyplněna nákupní cena, backend přepočítá prodejní cenu podle marže a DPH. Pro ruční cenu nákupní cenu vymažte. Jednotku s historií skladových pohybů nelze změnit.</p>
    <ApiForm path={`catalog/products${product ? `/${text(product.id)}` : ""}`} method={product ? "PUT" : "POST"}
      fields={fields} initial={initial} transform={productPayload} onSaved={onSaved} label={product ? "Uložit produkt" : "Přidat produkt"} /></>;
}

function PricingForm({ product, settings, onSaved }: { product: Row; settings: Row; onSaved: () => void }) {
  const fields: Field[] = [
    { name: "purchasePrice", label: "Nákupní cena bez DPH", type: "number", min: 0, step: "0.01", required: true },
    { name: "vatRate", label: "DPH (%)", type: "number", min: 0, max: 100, step: "0.01", required: true },
    { name: "eshopMarginPercent", label: "Marže (%)", type: "number", min: 0, max: 99.98, step: "0.01" },
  ];
  return <ApiForm path={`catalog/products/${text(product.id)}`} method="PUT" fields={fields}
    initial={{ ...product, vatRate: product.vatRate ?? settings.eshopDefaultVatRate ?? "21", eshopMarginPercent: product.eshopMarginPercent ?? settings.eshopMarginPercent ?? null }}
    label="Přepočítat cenu" onSaved={onSaved} transform={(values, form) => {
      const payload: Row = {
        sku: product.sku ?? "", name: product.name ?? "", unit: product.unit ?? "", description: product.description ?? "",
        imageUrl: product.imageUrl ?? "", categoryId: product.categoryId ?? null, active: product.active === true, price: product.price ?? null,
        purchasePrice: decimalValue(form, "purchasePrice"), vatRate: decimalValue(form, "vatRate"), eshopMarginPercent: decimalValue(form, "eshopMarginPercent", true),
      };
      if (number(payload.vatRate) > 100 || (payload.eshopMarginPercent !== null && number(payload.eshopMarginPercent) >= 99.99)) {
        throw new Error("Zkontrolujte DPH a marži.");
      }
      return { ...payload, active: product.active === true, ...("active" in values ? { active: values.active === true } : {}) };
    }} />;
}

function ProductCard({ product, categories, settings, onSaved }: { product: Row; categories: Row[]; settings: Row; onSaved: () => void }) {
  const availability = useApi(`catalog/products/${text(product.id)}/availability`);
  const [estimate, setEstimate] = useState<Row | null>(null);
  function saved() { availability.reload(); onSaved(); }
  const productPath = `catalog/products/${text(product.id)}`;
  const category = categories.find((entry) => entry.id === product.categoryId);
  return <article id={`product-${text(product.id)}`} className="panel">
    <header>{text(product.imageUrl) && <a href={text(product.imageUrl)} target="_blank" rel="noreferrer"><img src={text(product.imageUrl)} alt={text(product.name)} width={160} height={160} loading="lazy" style={{ objectFit: "contain" }} /></a>}
      <h3>{text(product.name)}</h3><p>{text(product.sku)} · {text(category?.name) || "Bez kategorie"}</p>
      <p>{text(product.description)}</p><strong>{money(product.price)} / {text(product.unit)}</strong><p>{product.active === true ? "Aktivní v e-shopu" : "Skrytý"}</p>
    </header>
    <h4>Dostupnost podle lokace</h4>
    <Alert>{availability.error}</Alert>{availability.loading && <p role="status">Načítání dostupnosti…</p>}
    <Table data={rows(availability.data)} columns={[
      { key: "locationName", label: "Lokace" }, { key: "quantity", label: `Množství (${text(product.unit)})` },
      { key: "stockStatus", label: "Dostupnost", render: (row) => row.stockStatus === "AVAILABLE" ? "Dostupné" : row.stockStatus === "LOW" ? "Nízká zásoba" : "Vyprodáno" },
    ]} />
    <Editor title="Ověřit doručení">
      <ApiForm path="catalog/delivery-estimates" fields={[
        { name: "quantity", label: "Množství", type: "number", min: 1, max: 2147483647, step: "1", required: true, value: 1 },
        { name: "postalCode", label: "PSČ (5 číslic)", required: true, maxLength: 5 },
        { name: "method", label: "Způsob", type: "select", required: true, options: [{ value: "HOME", label: "Domů" }, { value: "BOX", label: "Box" }] },
      ]} transform={(values) => {
        if (!/^\d{5}$/.test(text(values.postalCode))) throw new Error("PSČ musí obsahovat přesně 5 číslic.");
        return { ...values, quantity: integerValue(values.quantity, 1), productId: product.id ?? null };
      }} label="Zjistit doručení" onSaved={(result) => setEstimate(record(result))} />
      {estimate && <Alert error={estimate.available !== true}>{estimate.available === true
        ? `${text(estimate.label)}: doručení do ${text(estimate.estimatedDate)}`
        : text(estimate.reason) || "Doručení není dostupné."}</Alert>}
    </Editor>
    <Editor title="Upravit produkt"><ProductForm key={JSON.stringify(product)} product={product} categories={categories} settings={settings} onSaved={saved} /></Editor>
    <Editor title="Cenotvorba"><p>Globální marže: {text(settings.eshopMarginPercent) || "—"} % · Cena bez DPH: {money(number(product.price) / (1 + number(product.vatRate) / 100))} · Cena s DPH: {money(product.price)}</p>
      <PricingForm key={JSON.stringify(product)} product={product} settings={settings} onSaved={saved} /></Editor>
    <Action path={`${productPath}/active`} method="PUT" body={{ active: product.active !== true }} label={product.active === true ? "Nastavit neaktivní" : "Nastavit aktivní"} onSaved={saved} />
    {text(product.categoryId) && <Action path={`${productPath}/category`} method="PUT" label="Odebrat z kategorie" onSaved={saved} />}
    <Editor title="Galerie obrázků"><p>Aktivní obrázek se používá pro nové faktury.</p>
      {rows(product.images).map((image) => <div key={text(image.id)}>
        <a href={text(image.imageUrl)} target="_blank" rel="noreferrer"><img src={text(image.imageUrl)} alt={text(product.name)} width={96} height={96} loading="lazy" style={{ objectFit: "contain" }} /></a>
        {image.active === true ? <strong>Aktivní obrázek</strong> : <Action path={`${productPath}/images/${text(image.id)}/active`} method="PUT" label="Nastavit jako aktivní" onSaved={saved} />}
        <Action path={`${productPath}/images/${text(image.id)}`} method="DELETE" label="Odstranit obrázek" confirm="Opravdu odstranit obrázek?" onSaved={saved} />
      </div>)}
      <ApiForm path={`${productPath}/images`} fields={[{ name: "imageUrl", label: "URL obrázku", required: true }, { name: "active", label: "Nastavit jako aktivní", type: "checkbox" }]}
        label="Přidat obrázek" onSaved={saved} />
    </Editor>
    <Action path={productPath} method="DELETE" label="Smazat produkt" confirm="Opravdu smazat produkt? Produkt se zásobami nebo historií lze pouze deaktivovat." onSaved={saved} />
    <nav><a href="/inventory?view=products">Sklad a zásobování</a> · <a href="/accounting">Faktury</a></nav>
  </article>;
}

export function CatalogModule() {
  const homepage = useApi("catalog/homepage");
  const settings = useApi("settings");
  const categories = useApi("catalog/categories/tree");
  const products = useApi("catalog/products");
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [imported, setImported] = useState<number | null>(null);
  const tree = rows(categories.data);
  const flat = flattenCategories(tree);
  const [initialFilter, setInitialFilter] = useState("");
  useEffect(() => { setInitialFilter(new URLSearchParams(window.location.search).get("categoryId") ?? ""); }, []);
  const categoryFilter = selectedCategory ?? (flat.some((category) => category.id === initialFilter) ? initialFilter : "");
  const visible = rows(products.data).filter((product) => (!categoryFilter || product.categoryId === categoryFilter)
    && `${text(product.name)} ${text(product.sku)}`.toLocaleLowerCase("cs").includes(search.toLocaleLowerCase("cs")));
  function reload() { products.reload(); categories.reload(); }
  const error = [homepage.error, settings.error, categories.error, products.error].filter(Boolean).join(" ");
  return <Module title="eCommerce – správa katalogu" error={error} loading={homepage.loading || settings.loading || categories.loading || products.loading}>
    <div>
      {homepage.data && <HomepageEditor key={JSON.stringify(homepage.data)} homepage={record(homepage.data)} onSaved={homepage.reload} />}
      <section className="panel"><h2>Strom kategorií</h2>
        <button type="button" onClick={() => setSelectedCategory("")}>Zobrazit všechny produkty</button>
        <CategoryTree tree={tree} categories={flat} selected={categoryFilter} onSelect={setSelectedCategory} onSaved={reload} />
        <Editor title="Nová kategorie"><CategoryForm categories={flat} onSaved={reload} /></Editor>
      </section>
      <section className="panel"><h2>Přidat produkt do e-shopu</h2>
        <ProductForm categories={flat} settings={record(settings.data)} onSaved={products.reload} />
      </section>
      <section id="catalog"><h2>Katalog produktů a dostupnost</h2>
        <div className="form-fields"><label>Hledat název nebo SKU<input type="search" value={search} onChange={(event) => setSearch(event.target.value)} /></label>
          <label>Kategorie<select value={categoryFilter} onChange={(event) => setSelectedCategory(event.target.value)}>{categoryOptions(flat).map((option) =>
            <option key={option.value} value={option.value}>{option.value ? option.label : "Všechny kategorie"}</option>)}</select></label>
        </div>
        <p role="status">{visible.length} produktů</p>
        {visible.map((product) => <ProductCard key={text(product.id)} product={product} categories={flat} settings={record(settings.data)} onSaved={products.reload} />)}
        {!visible.length && <p>Žádné produkty pro tento filtr.</p>}
      </section>
      <section className="panel"><h2>Import katalogu</h2>
        <p>JSON pole produktů. Existující SKU se aktualizuje. Desetinné ceny doporučujeme uvádět jako řetězce, např. &quot;12.50&quot;.</p>
        <p>Každý objekt: sku, name, unit, price nebo purchasePrice; volitelně description, vatRate, eshopMarginPercent, categoryId, imageUrl, active.</p>
        <ApiForm path="catalog/products/import" fields={[{ name: "products", label: "Produkty (JSON)", type: "textarea", required: true, value: "[]" }]}
          transform={(_values, form) => importPayload(form)} label="Importovat produkty" onSaved={(result) => { setImported(number(record(result).imported)); reload(); }} />
        {imported !== null && <p role="status">Naimportováno: {imported} produktů.</p>}
      </section>
    </div>
  </Module>;
}
