"use client";

import { useEffect, useState, type FormEvent } from "react";
import { api, useApi } from "@/lib/client-api";
import { record, rows, text, number, money, type Json, type Row } from "@/lib/data";
import { Alert } from "./ui";

async function shopRequest(path: string, body?: Json): Promise<Json> {
  const response = await fetch(path, { method: body === undefined ? "GET" : "POST", cache: "no-store",
    headers: body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body) });
  const value: Json = await response.json();
  if (!response.ok) throw new Error(text(record(value).error) || "Požadavek nelze dokončit.");
  return value;
}

function categories(items: Row[], depth = 0): { id: string; name: string }[] {
  return items.flatMap((item) => [{ id: text(item.id), name: `${"— ".repeat(depth)}${text(item.name)}` }, ...categories(rows(item.children), depth + 1)]);
}

export function Shop() {
  const productsRequest = useApi("catalog/products");
  const categoriesRequest = useApi("catalog/categories/tree");
  const settings = useApi("settings/public");
  const [cart, setCart] = useState<Row>({});
  const [stocks, setStocks] = useState<Record<string, number>>({});
  const [category, setCategory] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState(false);
  const [step, setStep] = useState<"cart" | "delivery" | "payment">("cart");
  const [method, setMethod] = useState("CASH");
  const [completed, setCompleted] = useState("");
  const products = rows(productsRequest.data).filter((product) => product.active === true);
  useEffect(() => {
    const filter = new URLSearchParams(window.location.search).get("categoryId");
    if (filter) setCategory(filter);
    shopRequest("/api/cart").then((value) => setCart(record(value))).catch((cause: unknown) =>
      setError(cause instanceof Error ? cause.message : "Košík nelze načíst."));
    api("website/pages/visit?slug=%2Feshop", "POST").catch((cause: unknown) =>
      setError(cause instanceof Error ? cause.message : "Návštěvu nelze zaznamenat."));
  }, []);
  useEffect(() => {
    let active = true;
    if (!productsRequest.data) return;
    Promise.all(rows(productsRequest.data).filter((product) => product.active === true).map(async (product) =>
      [text(product.id), rows(await api(`catalog/products/${text(product.id)}/availability`)).reduce((sum, stock) => sum + number(stock.quantity), 0)] as const))
      .then((values) => { if (active) setStocks(Object.fromEntries(values)); })
      .catch((cause: unknown) => { if (active) setError(cause instanceof Error ? cause.message : "Dostupnost nelze načíst."); });
    return () => { active = false; };
  }, [productsRequest.data]);
  async function update(body: Row) {
    setPending(true); setError("");
    try { setCart(record(await shopRequest("/api/cart", body))); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Košík nelze upravit."); }
    finally { setPending(false); }
  }
  const quantities = record(cart.quantities);
  const lines = products.filter((product) => number(quantities[text(product.id)]) > 0);
  const totalCents = lines.reduce((sum, product) => sum + Math.round(number(product.price) * 100) * number(quantities[text(product.id)]), 0);
  async function delivery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPending(true); setError("");
    const fields = Object.fromEntries(new FormData(event.currentTarget));
    const values: Row = Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, typeof value === "string" ? value : ""]));
    try { setCart(record(await shopRequest("/api/cart", { action: "delivery", delivery: values }))); setStep("payment"); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Adresu nelze uložit."); }
    finally { setPending(false); }
  }
  async function checkout(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setPending(true); setError("");
    try {
      const result = record(await shopRequest("/api/shop/checkout", { paymentMethod: method }));
      setCompleted(text(result.orderNumber)); setCart({}); setStep("cart");
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Objednávku nelze dokončit."); }
    finally { setPending(false); }
  }
  return <main className="shop-page"><header><a href="/eshop"><h1>ALL MARKET</h1></a><a href="/apps">Administrace ERP</a></header>
    <h2>Dobré věci na dosah ruky.</h2><p>Čerstvé produkty, lokální nákup, rychlé doručení.</p>
    <Alert>{error || productsRequest.error || categoriesRequest.error || settings.error}</Alert>
    {completed && <Alert error={false}>Objednávka {completed} byla dokončena.</Alert>}
    <label>Kategorie<select value={category} onChange={(event) => setCategory(event.target.value)}><option value="">Všechny produkty</option>
      {categories(rows(categoriesRequest.data)).map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}</select></label>
    <div className="shop-layout"><section className="product-grid">{products.filter((product) => category ? text(product.categoryId) === category : Boolean(product.categoryId)).map((product) =>
      <article className="product-card" key={text(product.id)}>
        {/^https?:\/\//i.test(text(product.imageUrl)) && <img src={text(product.imageUrl)} alt={text(product.name)} />}
        <small>{text(product.sku)}</small><h3>{text(product.name)}</h3><p>{text(product.description)}</p>
        <strong>{money(product.price)} / {text(product.unit)}</strong><p>{stocks[text(product.id)] ?? "…"} skladem</p>
        <button className="primary" disabled={pending || !stocks[text(product.id)]} onClick={() => update({ action: "add", productId: product.id, quantity: 1 })}>Přidat do košíku</button>
      </article>)}{productsRequest.loading && <p role="status">Načítání katalogu…</p>}</section>
      <aside className="cart-panel"><h2>Košík</h2>{lines.map((product) => <div className="cart-line" key={text(product.id)}>
        <strong>{text(product.name)}</strong><p>{number(quantities[text(product.id)])} × {money(product.price)}</p>
        <button disabled={pending} aria-label={`Odebrat kus ${text(product.name)}`} onClick={() => update({ action: "decrease", productId: product.id })}>−</button>
        <button disabled={pending} aria-label={`Přidat kus ${text(product.name)}`} onClick={() => update({ action: "increase", productId: product.id })}>+</button>
        <button disabled={pending} onClick={() => update({ action: "remove", productId: product.id })}>Odstranit</button>
      </div>)}
        <p>Celkem za zboží: <strong>{money(totalCents / 100)}</strong></p>
        <button className="primary" disabled={!lines.length || pending} onClick={() => setStep("delivery")}>Pokračovat k doručení</button>
      </aside></div>
    {step === "delivery" && <section><h2>Adresa zákazníka</h2><form className="erp-form" onSubmit={delivery}><div className="form-fields">
      {[["firstName", "Jméno"], ["lastName", "Příjmení"], ["phone", "Telefon"], ["street", "Ulice a číslo domu"], ["city", "Město"], ["postalCode", "PSČ"]].map(([key, label]) =>
        <label key={key}>{label}<input name={key} required maxLength={160} defaultValue={text(record(cart.delivery)[key])}
          pattern={key === "postalCode" ? "[0-9]{3} ?[0-9]{2}" : key === "phone" ? "[+0-9 ()-]{9,20}" : undefined} /></label>)}
      </div><button className="primary" disabled={pending}>Pokračovat k platbě</button></form></section>}
    {step === "payment" && <section><h2>Platba</h2><p>Tato ERP ukázka není napojena na platební bránu. Nezadávejte skutečné údaje karty.</p>
      <form onSubmit={checkout} className="erp-form"><label>Platební metoda<select value={method} onChange={(event) => setMethod(event.target.value)}>
        <option value="CASH">Platba při doručení</option><option value="CARD">Simulovaná platba kartou (demo)</option></select></label>
        <p>Zboží: {money(totalCents / 100)}. Nastavený poplatek za doručení: {money(record(settings.data).deliveryFee)}.</p>
        <p>Backend nyní účtuje pouze cenu zboží.</p>
        <button className="primary" disabled={pending || !lines.length}>Dokončit objednávku</button><button type="button" onClick={() => setStep("delivery")}>Zpět</button>
      </form></section>}
  </main>;
}
