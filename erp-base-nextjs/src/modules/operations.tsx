"use client";

import { useEffect, useState } from "react";
import { Action, Alert, ApiForm, Editor, Metrics, Module, Table, type Field, type Option } from "@/components/ui";
import { useApi } from "@/lib/client-api";
import { money, number, record, rows, text, type Json, type Row } from "@/lib/data";

export function decimalValue(form: FormData, name: string, optional = false): string | null {
  const raw = form.get(name);
  const value = typeof raw === "string" ? raw.trim().replace(",", ".") : "";
  if (!value && optional) return null;
  if (!/^\+?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)) throw new Error("Zadejte platnou nezápornou cenu nebo procento.");
  return value;
}

export function integerValue(value: Json | undefined, minimum = 0, maximum = 2147483647): number {
  const parsed = Number(value);
  if (value === null || value === undefined || value === "" || !Number.isInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`Zadejte celé číslo od ${minimum} do ${maximum}.`);
  }
  return parsed;
}

function options(data: Row[], label = "name"): Option[] {
  return [{ value: "", label: "Vyberte…" }, ...data.map((row) => ({ value: text(row.id), label: text(row[label]) }))];
}

const statuses: Record<string, string> = {
  QUOTE: "Nabídka", CONFIRMED: "Potvrzeno", REQUESTED: "Požadavek", ORDERED: "Objednáno",
  RECEIVED: "Přijato", PLANNED: "Plánováno", IN_PROGRESS: "Probíhá", COMPLETED: "Dokončeno",
  OPEN: "Otevřeno", PAID: "Uhrazeno", ACTIVE: "Aktivní", CANCELLED: "Zrušeno",
  CARD: "Karta", CASH: "Hotovost", VOUCHER: "Poukázka",
};

function status(value: Json | undefined): string {
  return statuses[text(value)] ?? text(value);
}

function Photo({ row, name = "name" }: { row: Row; name?: string }) {
  const url = text(row.imageUrl);
  return url ? <a href={url} target="_blank" rel="noreferrer"><img src={url} alt={text(row[name])} width={72} height={72} loading="lazy" style={{ objectFit: "contain" }} /></a> : <small>Bez obrázku</small>;
}

const salesFields: Field[] = [
  { name: "orderNumber", label: "Číslo dokumentu", required: true, maxLength: 30 },
  { name: "customerName", label: "Zákazník", required: true, maxLength: 200 },
  { name: "orderDate", label: "Vystaveno", type: "date", required: true },
  { name: "deliveryDate", label: "Dodání", type: "date", required: true },
  { name: "totalAmount", label: "Celkem (Kč)", type: "number", min: 0, step: "0.01", required: true },
];

function Sales() {
  const overview = useApi("sales/overview");
  const data = record(overview.data);
  return <Module title="Prodej" error={overview.error} loading={overview.loading}>
    <Metrics data={{ ...data, quoteValue: money(data.quoteValue), confirmedValue: money(data.confirmedValue) }}
      labels={{ quoteValue: "Hodnota nabídek", confirmedValue: "Potvrzené objednávky", quoteCount: "Otevřené nabídky" }} />
    <a href="/accounting">Faktury a účetnictví</a>
    <Editor title="Nová nabídka"><ApiForm path="sales/orders" fields={salesFields} onSaved={overview.reload}
      transform={(values, form) => ({ ...values, totalAmount: decimalValue(form, "totalAmount") })} label="Vytvořit nabídku" /></Editor>
    <Table data={rows(data.orders)} columns={[
      { key: "orderNumber", label: "Dokument" }, { key: "customerName", label: "Zákazník" },
      { key: "orderDate", label: "Vystaveno" }, { key: "deliveryDate", label: "Dodání" },
      { key: "totalAmount", label: "Celkem", money: true }, { key: "status", label: "Stav", render: (row) => status(row.status) },
    ]} actions={(row) => <>
      {row.status === "QUOTE" && <Action path={`sales/orders/${text(row.id)}/confirm`} label="Potvrdit objednávku" onSaved={overview.reload} />}
      <Editor><ApiForm key={`${text(row.id)}-${text(row.totalAmount)}-${text(row.orderNumber)}`} path={`sales/orders/${text(row.id)}`} method="PUT"
        fields={salesFields} initial={row} transform={(values, form) => ({ ...values, totalAmount: decimalValue(form, "totalAmount") })} onSaved={overview.reload} /></Editor>
      <Action path={`sales/orders/${text(row.id)}`} method="DELETE" label="Smazat" confirm="Opravdu smazat tento dokument?" onSaved={overview.reload} />
    </>} />
  </Module>;
}

type PurchaseLine = { key: number; productId: string; quantity: string; unitPrice: string };

export function purchasePayload(values: Row, lines: { productId: string; quantity: string; unitPrice: string }[]): Row {
  if (!lines.length) throw new Error("Objednávka musí obsahovat alespoň jednu položku.");
  if (text(values.expectedDeliveryDate) < text(values.requestedOn)) throw new Error("Dodání nesmí být před datem požadavku.");
  return {
    ...values,
    lines: lines.map((line) => {
      if (!line.productId) throw new Error("Vyberte produkt pro každou položku.");
      const form = new FormData();
      form.set("unitPrice", line.unitPrice);
      return { productId: line.productId, quantity: integerValue(line.quantity, 1), unitPrice: decimalValue(form, "unitPrice") };
    }),
  };
}

function PurchaseForm({ order, warehouses, products, onSaved }: { order?: Row; warehouses: Row[]; products: Row[]; onSaved: () => void }) {
  const initial = rows(order?.lines);
  const [lines, setLines] = useState<PurchaseLine[]>(initial.length
    ? initial.map((line, key) => ({ key, productId: text(line.productId), quantity: text(line.quantity), unitPrice: text(line.unitPrice) }))
    : [{ key: 0, productId: "", quantity: "1", unitPrice: "0.00" }]);
  const [nextKey, setNextKey] = useState(lines.length);
  const fields: Field[] = [
    { name: "supplierName", label: "Dodavatel", required: true, maxLength: 200 },
    { name: "requestedOn", label: "Požadováno", type: "date", required: true },
    { name: "expectedDeliveryDate", label: "Očekávané dodání", type: "date", required: true },
    { name: "sourceWarehouseId", label: "Dodavatelský sklad", type: "select", required: true, options: options(warehouses.filter((warehouse) => warehouse.ownerType === "SUPPLIER")) },
    { name: "destinationWarehouseId", label: "Firemní cílový sklad", type: "select", required: true, options: options(warehouses.filter((warehouse) => warehouse.ownerType === "COMPANY")) },
  ];
  function change(key: number, value: Partial<PurchaseLine>) {
    setLines((current) => current.map((line) => line.key === key ? { ...line, ...value } : line));
  }
  return <ApiForm path={`purchase/orders${order ? `/${text(order.id)}` : ""}`} method={order ? "PUT" : "POST"}
    fields={fields} initial={order} transform={(values) => purchasePayload(values, lines)} onSaved={onSaved} label={order ? "Uložit změny" : "Vytvořit objednávku"}>
    <fieldset><legend>Položky objednávky</legend>
      {lines.map((line, index) => <div className="form-fields" key={line.key}>
        <label>Produkt {index + 1}<select required value={line.productId} onChange={(event) => {
          const product = products.find((entry) => entry.id === event.target.value);
          change(line.key, { productId: event.target.value, unitPrice: text(product?.purchasePrice) || "0.00" });
        }}><option value="">Vyberte produkt</option>{products.map((product) =>
          <option key={text(product.id)} value={text(product.id)}>{text(product.name)} ({text(product.sku)})</option>)}</select></label>
        <label>Množství<input type="number" min={1} max={2147483647} step="1" required value={line.quantity} onChange={(event) => change(line.key, { quantity: event.target.value })} /></label>
        <label>Cena / ks (Kč)<input type="number" min={0} step="0.01" required value={line.unitPrice} onChange={(event) => change(line.key, { unitPrice: event.target.value })} /></label>
        <button type="button" disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((entry) => entry.key !== line.key))}>Odebrat položku {index + 1}</button>
      </div>)}
      <button type="button" onClick={() => {
        setLines((current) => [...current, { key: nextKey, productId: "", quantity: "1", unitPrice: "0.00" }]);
        setNextKey((key) => key + 1);
      }}>+ Přidat produkt</button>
      <p><output>Orientačně celkem: {money(lines.reduce((sum, line) => sum + number(line.quantity) * number(line.unitPrice), 0))}</output></p>
    </fieldset>
  </ApiForm>;
}

function Purchase() {
  const overview = useApi("purchase/overview");
  const warehouses = useApi("purchase/warehouses");
  const products = useApi("catalog/products");
  const data = record(overview.data);
  const warehouseRows = rows(warehouses.data);
  const productRows = rows(products.data);
  const auxiliaryError = [warehouses.error, products.error].filter(Boolean).join(" ");
  return <Module title="Nákup" error={[overview.error, auxiliaryError].filter(Boolean).join(" ")} loading={overview.loading || warehouses.loading || products.loading}>
    <Metrics data={{ ...data, requestedValue: money(data.requestedValue), orderedValue: money(data.orderedValue) }}
      labels={{ requestedValue: "Požadavky k objednání", orderedValue: "Objednáno u dodavatelů", requestedCount: "Otevřené požadavky" }} />
    <nav><a href="/inventory">Sklad a historie příjmů</a> · <a href="/accounting">Faktury</a></nav>
    {!auxiliaryError && <Editor title="Nová nákupní objednávka"><PurchaseForm warehouses={warehouseRows} products={productRows} onSaved={overview.reload} /></Editor>}
    <Table data={rows(data.orders)} columns={[
      { key: "orderNumber", label: "Dokument", render: (row) => <><strong>{text(row.orderNumber)}</strong><Editor title="Obsah objednávky">
        <Table data={rows(row.lines)} columns={[
          { key: "productId", label: "Produkt", render: (line) => {
            const product = productRows.find((entry) => entry.id === line.productId) ?? {};
            return <><Photo row={product} /><span>{text(product.name) || text(line.productId)} · {text(product.sku)}</span></>;
          } },
          { key: "quantity", label: "Množství" }, { key: "receivedQuantity", label: "Přijato" }, { key: "unitPrice", label: "Cena / ks", money: true },
        ]} /></Editor></> },
      { key: "supplierName", label: "Dodavatel" },
      { key: "sourceWarehouseId", label: "Tok skladu", render: (row) => `${text(warehouseRows.find((entry) => entry.id === row.sourceWarehouseId)?.name) || text(row.sourceWarehouseId)} → ${text(warehouseRows.find((entry) => entry.id === row.destinationWarehouseId)?.name) || text(row.destinationWarehouseId)}` },
      { key: "requestedOn", label: "Požadováno" }, { key: "expectedDeliveryDate", label: "Dodání" },
      { key: "quantity", label: "Přijato / objednáno", render: (row) => `${text(row.receivedQuantity)} / ${text(row.quantity) || "—"}` },
      { key: "totalAmount", label: "Celkem", money: true }, { key: "status", label: "Stav", render: (row) => status(row.status) },
    ]} actions={(row) => <>
      {row.status === "REQUESTED" && <>
        {!auxiliaryError && <Editor><PurchaseForm key={JSON.stringify(row)} order={row} warehouses={warehouseRows} products={productRows} onSaved={overview.reload} /></Editor>}
        <Action path={`purchase/orders/${text(row.id)}/order`} label="Vystavit objednávku" onSaved={overview.reload} />
      </>}
      {row.status === "ORDERED" && row.quantity !== null && row.quantity !== undefined && <Action
        path={`purchase/orders/${text(row.id)}/receive`} body={{ quantity: Math.max(1, number(row.quantity) - number(row.receivedQuantity)) }}
        label="Přijmout na sklad" confirm="Přijmout zbývající zboží objednávky na cílový sklad?" onSaved={overview.reload} />}
    </>} />
  </Module>;
}

function Manufacturing() {
  const overview = useApi("manufacturing/overview");
  const data = record(overview.data);
  return <Module title="Výroba" error={overview.error} loading={overview.loading}>
    <Metrics data={data} labels={{ plannedQuantity: "Plánovaná výroba", completedQuantity: "Dokončená výroba", activeOrderCount: "Rozpracované příkazy" }} />
    <Table data={rows(data.orders)} columns={[
      { key: "orderNumber", label: "Výrobní příkaz" }, { key: "productName", label: "Produkt" }, { key: "workCenter", label: "Pracoviště" },
      { key: "plannedDate", label: "Plánované datum" },
      { key: "completedQuantity", label: "Průběh", render: (row) => <>{text(row.completedQuantity)} / {text(row.plannedQuantity)} ks <progress value={number(row.completedQuantity)} max={Math.max(1, number(row.plannedQuantity))} /></> },
      { key: "status", label: "Stav", render: (row) => status(row.status) },
    ]} actions={(row) => <>
      <Editor><ApiForm key={`${text(row.id)}-${text(row.completedQuantity)}`} path={`manufacturing/orders/${text(row.id)}/progress`} method="PATCH" initial={row}
        fields={[{ name: "completedQuantity", label: "Vyrobeno kusů", type: "number", min: 0, max: number(row.plannedQuantity), step: "1", required: true }]}
        transform={(values) => ({ completedQuantity: integerValue(values.completedQuantity, 0, number(row.plannedQuantity)) })} onSaved={overview.reload} /></Editor>
      {row.status !== "COMPLETED" && <Action path={`manufacturing/orders/${text(row.id)}/complete`} label="Dokončit příkaz" onSaved={overview.reload} />}
    </>} />
  </Module>;
}

function Pos() {
  const overview = useApi("pos/overview");
  const data = record(overview.data);
  return <Module title="Pokladna" error={overview.error} loading={overview.loading}>
    <Metrics data={{ ...data, paidToday: money(data.paidToday) }} labels={{ paidToday: "Dnešní tržba", openTransactionCount: "Otevřené účtenky", itemCount: "Položky v obsluze" }} />
    <Table data={rows(data.transactions)} columns={[
      { key: "receiptNumber", label: "Účtenka" }, { key: "storeName", label: "Prodejna" }, { key: "openedAt", label: "Otevřeno" },
      { key: "itemCount", label: "Položky" }, { key: "totalAmount", label: "Celkem", money: true },
      { key: "paymentMethod", label: "Platba", render: (row) => status(row.paymentMethod) || "—" },
      { key: "status", label: "Stav", render: (row) => status(row.status) },
    ]} actions={(row) => row.status === "OPEN" ? <ApiForm path={`pos/transactions/${text(row.id)}/pay`} method="PATCH"
      fields={[{ name: "method", label: "Platba", type: "select", options: ["CARD", "CASH", "VOUCHER"].map((value) => ({ value, label: status(value) })), required: true }]}
      label="Přijmout platbu" onSaved={overview.reload} /> : <span>Platba přijata</span>} />
  </Module>;
}

function MovementForm({ item, dispatch, onSaved }: { item: Row; dispatch: boolean; onSaved: () => void }) {
  const fields: Field[] = [
    { name: "quantity", label: `Množství (${text(item.unit)})`, type: "number", min: 1, max: dispatch ? number(item.quantity) : 2147483647, step: "1", required: true, value: 1 },
    { name: "reference", label: "Doklad / reference", required: true, maxLength: 120 },
    { name: "note", label: "Poznámka", type: "textarea", maxLength: 500 },
    ...(!dispatch ? [
      { name: "reorderLevel", label: "Minimum", type: "number", min: 0, step: "1", required: true },
      { name: "unitCost", label: "Jednotková cena (Kč)", type: "number", min: 0, max: 9999999999.99, step: "0.01", required: true },
    ] satisfies Field[] : []),
  ];
  return <ApiForm path={`inventory/items/${text(item.id)}/${dispatch ? "dispatch" : "receive"}`} method="PATCH" fields={fields} initial={item} onSaved={onSaved}
    label={dispatch ? "Zaúčtovat výdej" : "Zaúčtovat příjem"} transform={(values, form) => {
      const reference = text(values.reference).trim();
      if (!reference) throw new Error("Vyplňte doklad / referenci.");
      const payload: Row = { quantity: integerValue(values.quantity, 1, dispatch ? number(item.quantity) : 2147483647), reference, note: text(values.note) || null };
      if (!dispatch) {
        const cost = decimalValue(form, "unitCost");
        if (!cost || !/^\+?\d{0,10}(?:\.\d{0,2})?$/.test(cost)) throw new Error("Cena smí mít nejvýše 10 číslic a 2 desetinná místa.");
        payload.reorderLevel = integerValue(values.reorderLevel);
        payload.unitCost = cost;
      }
      return payload;
    }} />;
}

function Inventory() {
  const overview = useApi("inventory/overview");
  const data = record(overview.data);
  const [view, setView] = useState("stock");
  const [search, setSearch] = useState("");
  const [lowOnly, setLowOnly] = useState(false);
  const [warehouse, setWarehouse] = useState("");
  const [itemId, setItemId] = useState("");
  const [page, setPage] = useState(0);
  const [queryError, setQueryError] = useState("");
  useEffect(() => {
    const query = new URLSearchParams(window.location.search);
    const requestedView = query.get("view");
    if (requestedView === "history" || requestedView === "products") setView(requestedView);
    setWarehouse(query.get("warehouseName") ?? "");
    const requestedId = query.get("itemId") ?? "";
    const requestedPage = query.get("page") ?? "0";
    if ((requestedId && !/^[0-9a-f-]{36}$/i.test(requestedId)) || !/^\d+$/.test(requestedPage) || !Number.isSafeInteger(Number(requestedPage))) {
      setQueryError("Neplatný filtr nebo stránka historie.");
      return;
    }
    setItemId(requestedId);
    setPage(Number(requestedPage));
  }, []);
  const history = useApi(view === "history" && !queryError ? `inventory/movements?page=${page}${itemId ? `&itemId=${encodeURIComponent(itemId)}` : ""}` : null);
  const movements = record(history.data);
  const items = rows(data.items);
  const products = rows(data.products);
  const lowSkus = new Set(items.filter((item) => number(item.quantity) < number(item.reorderLevel)).map((item) => text(item.sku)));
  const match = (row: Row) => [row.productName, row.sku, row.locationName].map(text).join(" ").toLocaleLowerCase("cs").includes(search.toLocaleLowerCase("cs"));
  const filteredItems = items.filter((item) => match(item) && (!lowOnly || lowSkus.has(text(item.sku))));
  const categories = [...new Set(filteredItems.map((item) => text(item.categoryPath)))];
  const warehouseNames = [...new Set(products.flatMap((product) => rows(product.warehouses).map((stock) => text(stock.locationName))))];
  const selectedWarehouse = warehouse || warehouseNames[0] || "";
  const canEdit = data.canEdit === true;
  const totalPages = number(movements.size) > 0 ? Math.ceil(number(movements.totalElements) / number(movements.size)) : 0;
  function reload() { overview.reload(); history.reload(); }
  return <Module title="Sklad" error={overview.error} loading={overview.loading}>
    <Metrics data={{ ...data, stockValue: money(data.stockValue) }} labels={{ totalQuantity: "Zásoba celkem", stockValue: "Hodnota zásob", lowStockCount: "Pod minimem" }} />
    <nav aria-label="Pohled skladu">{[{ value: "stock", label: "Sklad" }, { value: "products", label: "Produkty" }, { value: "history", label: "Historie" }].map((tab) =>
      <button type="button" key={tab.value} aria-pressed={view === tab.value} onClick={() => setView(tab.value)}>{tab.label}</button>)} <a href="/purchase">Nákupní objednávky</a></nav>
    {view !== "history" && <div className="form-fields">
      <label>Hledat produkt, SKU nebo lokaci<input type="search" value={search} onChange={(event) => setSearch(event.target.value)} /></label>
      <label><input type="checkbox" checked={lowOnly} onChange={(event) => setLowOnly(event.target.checked)} /> Jen pod minimem</label>
    </div>}
    {view === "stock" && <>{categories.map((category) => <Editor key={category} title={`Kategorie: ${category || "Bez kategorie"}`}>
      <Table data={filteredItems.filter((item) => text(item.categoryPath) === category)} columns={[
        { key: "locationName", label: "Lokace" }, { key: "productName", label: "Produkt" }, { key: "sku", label: "SKU" },
        { key: "imageUrl", label: "Obrázek", render: (row) => <Photo row={row} name="productName" /> },
        { key: "quantity", label: "Skladem", render: (row) => `${text(row.quantity)} ${text(row.unit)}` },
        { key: "reorderLevel", label: "Minimum" }, { key: "unitCost", label: "Cena / jednotka", money: true },
        { key: "low", label: "Stav", render: (row) => number(row.quantity) < number(row.reorderLevel) ? "Doplnit" : "V pořádku" },
      ]} actions={(row) => <>
        {canEdit ? <><Editor title="Příjem"><MovementForm key={JSON.stringify(row)} item={row} dispatch={false} onSaved={reload} /></Editor>
          <Editor title="Výdej"><MovementForm key={JSON.stringify(row)} item={row} dispatch onSaved={reload} /></Editor></> : <span>Pouze pro čtení</span>}
        <button type="button" onClick={() => { setView("history"); setItemId(text(row.id)); setPage(0); setQueryError(""); }}>Historie položky</button>
      </>} />
    </Editor>)}{!filteredItems.length && <p>Žádné skladové položky pro tento filtr.</p>}</>}
    {view === "products" && <>
      <label>Vybraný sklad<select value={selectedWarehouse} onChange={(event) => setWarehouse(event.target.value)}>
        {warehouseNames.map((name) => <option key={name} value={name}>{name}</option>)}</select></label>
      {products.filter((product) => match(product) && (!lowOnly || lowSkus.has(text(product.sku)))).map((product) => <article className="panel" key={text(product.productId)}>
        <Photo row={product} name="productName" /><h2>{text(product.productName)}</h2><p>{text(product.sku)} · {text(product.categoryPath)}</p><p>{text(product.description) || "Bez popisu"}</p>
        <p>Celkem na skladech: {text(product.warehouseQuantity)} {text(product.unit)} · Hlavní sklad: {text(product.centralQuantity)} {text(product.unit)} · {text(product.locationCount)} lokací</p>
        {rows(product.warehouses).filter((stock) => stock.locationName === selectedWarehouse).map((stock) => <div key={text(stock.locationName)}>
          <p>{text(stock.locationName)}: {text(stock.quantity)} {text(product.unit)}</p>
          {canEdit ? <ApiForm key={`${text(product.productId)}-${text(stock.orderedFromCentral)}`} path="inventory/orders" method="PATCH"
            initial={{ quantity: stock.orderedFromCentral ?? 0 }} fields={[{ name: "quantity", label: "Objednat z hlavního skladu", type: "number", min: 0, max: 2147483647, step: "1", required: true }]}
            transform={(values) => ({ productId: product.productId ?? null, locationName: stock.locationName ?? null, quantity: integerValue(values.quantity) })} onSaved={reload} />
            : <p>Objednáno z hlavního skladu: {text(stock.orderedFromCentral)}</p>}
        </div>)}
      </article>)}
    </>}
    {view === "history" && <>
      <h2>Historie příjmů a výdejů</h2><p>Pohyby nelze upravovat ani mazat. Opravu evidujte opačným pohybem s odkazem na původní doklad. Časy: Europe/Prague.</p>
      <label>Skladová položka<select value={itemId} onChange={(event) => { setItemId(event.target.value); setPage(0); setQueryError(""); }}>
        <option value="">Všechny položky</option>{items.map((item) => <option key={text(item.id)} value={text(item.id)}>{text(item.productName)} / {text(item.locationName)}</option>)}
      </select></label>
      <Alert>{queryError || history.error}</Alert>{history.loading && <p role="status">Načítání historie…</p>}
      <Table data={rows(movements.items)} columns={[
        { key: "createdAt", label: "Datum / ID", render: (row) => {
          const date = new Date(text(row.createdAt));
          return <>{Number.isNaN(date.getTime()) ? text(row.createdAt) : date.toLocaleString("cs-CZ", { timeZone: "Europe/Prague" })}<br />#{text(row.id)}</>;
        } },
        { key: "productName", label: "Produkt / lokace", render: (row) => <>{text(row.productName)} · {text(row.sku)}<br />{text(row.locationName)}</> },
        { key: "type", label: "Typ", render: (row) => row.type === "ISSUE" ? "Výdej" : row.type === "RECEIPT" ? "Příjem" : "Počáteční stav" },
        { key: "quantity", label: "Množství", render: (row) => `${row.type === "ISSUE" ? "−" : "+"}${text(row.quantity)} ${text(row.unit)}` },
        { key: "balanceAfter", label: "Stav po pohybu" }, { key: "reference", label: "Doklad / poznámka", render: (row) => <>{text(row.reference)}<br />{text(row.note)}</> },
        { key: "actorName", label: "Uživatel" },
      ]} />
      <nav aria-label="Stránkování historie"><span>Celkem {text(movements.totalElements) || "0"} pohybů · Strana {totalPages ? page + 1 : 0} / {totalPages}</span>
        <button type="button" disabled={page === 0 || history.loading} onClick={() => setPage((current) => current - 1)}>Předchozí</button>
        <button type="button" disabled={page + 1 >= totalPages || history.loading} onClick={() => setPage((current) => current + 1)}>Další</button>
      </nav>
    </>}
  </Module>;
}

const promoStatusOptions = ["PLANNED", "ACTIVE", "COMPLETED", "CANCELLED"].map((value) => ({ value, label: status(value) }));

export function promoPayload(values: Row, form: FormData): Row {
  if (text(values.endsOn) < text(values.startsOn)) throw new Error("Konec kampaně nesmí být před začátkem.");
  const payload: Row = { ...values, plannedQuantity: integerValue(values.plannedQuantity) };
  for (const name of ["regularPrice", "promoPrice", "supplierPurchasePrice", "marketingContribution"]) payload[name] = decimalValue(form, name);
  return payload;
}

function Promo() {
  const campaigns = useApi("promo-campaigns");
  const loadedOptions = useApi("promo-campaigns/options");
  const data = record(loadedOptions.data);
  const products = rows(data.products);
  const suppliers = rows(data.suppliers);
  const fields: Field[] = [
    { name: "name", label: "Název kampaně", required: true, maxLength: 200 },
    { name: "productId", label: "Produkt", type: "select", required: true, options: options(products) },
    { name: "supplierId", label: "Dodavatel", type: "select", required: true, options: options(suppliers) },
    { name: "startsOn", label: "Začátek", type: "date", required: true },
    { name: "endsOn", label: "Konec", type: "date", required: true },
    ...[{ name: "regularPrice", label: "Běžná cena" }, { name: "promoPrice", label: "Akční cena" }, { name: "supplierPurchasePrice", label: "Nákupní cena" }, { name: "marketingContribution", label: "Příspěvek dodavatele" }]
      .map((field): Field => ({ ...field, type: "number", min: 0, step: "0.01", required: true })),
    { name: "plannedQuantity", label: "Plánované množství", type: "number", min: 0, max: 2147483647, step: "1", required: true },
  ];
  return <Module title="Promo kampaně" error={[campaigns.error, loadedOptions.error].filter(Boolean).join(" ")} loading={campaigns.loading || loadedOptions.loading}>
    {!loadedOptions.error && <Editor title="Nová promo kampaň"><ApiForm path="promo-campaigns" fields={fields} initial={{ marketingContribution: "0" }} transform={promoPayload} onSaved={campaigns.reload} label="Přidat kampaň" /></Editor>}
    <Table data={rows(campaigns.data)} columns={[
      { key: "imageUrl", label: "Fotografie", render: (row) => <Photo row={row} /> },
      { key: "name", label: "Kampaň" },
      { key: "productId", label: "Produkt", render: (row) => text(products.find((product) => product.id === row.productId)?.name) || text(row.productId) },
      { key: "supplierId", label: "Dodavatel", render: (row) => text(suppliers.find((supplier) => supplier.id === row.supplierId)?.name) || text(row.supplierId) },
      { key: "startsOn", label: "Období", render: (row) => `${text(row.startsOn)} – ${text(row.endsOn)}` },
      { key: "regularPrice", label: "Běžná cena", money: true }, { key: "promoPrice", label: "Akční cena", money: true },
      { key: "supplierPurchasePrice", label: "Nákupní cena", money: true }, { key: "marketingContribution", label: "Příspěvek", money: true },
      { key: "plannedQuantity", label: "Plán / skutečnost", render: (row) => `${text(row.plannedQuantity)} / ${text(row.actualQuantity)}` },
      { key: "status", label: "Stav", render: (row) => status(row.status) },
    ]} actions={(row) => <>
      <ApiForm key={`${text(row.id)}-${text(row.status)}`} path={`promo-campaigns/${text(row.id)}/status`} method="PATCH" initial={row}
        fields={[{ name: "status", label: "Stav kampaně", type: "select", required: true, options: promoStatusOptions }]} onSaved={campaigns.reload} label="Změnit stav" />
      {!loadedOptions.error && <Editor><ApiForm key={JSON.stringify(row)} path={`promo-campaigns/${text(row.id)}`} method="PUT"
        fields={fields} initial={row} transform={promoPayload} onSaved={campaigns.reload} /></Editor>}
    </>} />
  </Module>;
}

export function OperationsModule({ module }: { module: string }) {
  switch (module) {
    case "sales": return <Sales />;
    case "purchase": return <Purchase />;
    case "manufacturing": return <Manufacturing />;
    case "pos": return <Pos />;
    case "inventory": return <Inventory />;
    case "promo": return <Promo />;
    default: return <Module title="Provoz" error="Neznámý provozní modul."><p>Vyberte modul z nabídky aplikací.</p></Module>;
  }
}
