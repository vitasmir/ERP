import {
  feedback, field, integer, optionalUuid, redirect, reportError, row, rows, uuid,
  ValidationError, type Context, type Page, type Row,
} from "../lib/context";
import { categoryOptions } from "./ecommerce";

async function availableProducts(context: Context): Promise<Row[]> {
  const products = rows(await context.backend.json("GET", "/api/v1/catalog/products", undefined, false));
  const available: Row[] = [];
  for (const product of products) {
    if (!product.active) continue;
    const stocks = rows(await context.backend.json("GET", `/api/v1/catalog/products/${uuid(product.id)}/availability`, undefined, false));
    product.availableQuantity = Math.max(0, stocks.reduce((sum, stock) => sum + Number(stock.quantity), 0));
    available.push(product);
  }
  return available;
}

function categoryFilter(value: string | null): string | null {
  try { return optionalUuid(value); }
  catch (error) { reportError(error, "Invalid shop category filter; showing complete catalog."); return null; }
}

function priceCents(price: unknown): number {
  if ((typeof price !== "string" && typeof price !== "number") || !Number.isFinite(Number(price))) {
    throw new Error("Invalid catalog price.");
  }
  return Math.round(Number(price) * 100);
}

function money(cents: number): string { return (cents / 100).toFixed(2); }

export async function handleShop(context: Context): Promise<Page | Response | null> {
  if (!["/eshop", "/shop"].includes(context.url.pathname)) return null;
  if (context.request.method === "POST") {
    try { return await action(context); }
    catch (error) {
      reportError(error, "Storefront action failed.");
      if (error instanceof ValidationError) return feedback("/eshop", error.message || "Zkontrolujte zadané hodnoty.", true);
      const payment = field(context, "action") === "payment";
      return feedback("/eshop", payment
        ? "Objednávku se nepodařilo dokončit. Backend požadavek odmítl nebo není dostupný."
        : "Požadavek se nepodařilo dokončit. E-shop není dostupný nebo backend požadavek odmítl.", true,
      payment ? { checkout: "payment" } : {});
    }
  }
  const data: Row = { shop: null, error: context.url.searchParams.get("error"), orderCompleted: context.url.searchParams.get("order") === "completed" };
  try {
    try {
      await context.backend.mutate("POST", "/api/v1/website/pages/visit?slug=%2Feshop", undefined, false);
    } catch (error) { reportError(error, "Storefront visit tracking failed."); }
    const categories = rows(await context.backend.json("GET", "/api/v1/catalog/categories/tree", undefined, false));
    const products = await availableProducts(context);
    const settings = row(await context.backend.json("GET", "/api/v1/settings/public", undefined, false));
    const selected = categoryFilter(context.url.searchParams.get("categoryId"));
    const delivery = context.session.delivery;
    const paymentOpen = context.url.searchParams.get("checkout") === "payment" && Boolean(delivery);
    const visible = products.filter((product) => selected === null ? product.categoryId != null : product.categoryId === selected);
    const byId = new Map(products.map((product) => [product.id, product]));
    const lines: Row[] = [];
    let cartCount = 0;
    let cartTotal = 0;
    for (const [id, requested] of Object.entries(context.session.cart)) {
      const product = byId.get(id);
      if (!product) continue;
      const quantity = Math.min(requested, Number(product.availableQuantity));
      if (quantity <= 0) continue;
      const lineTotal = priceCents(product.price) * quantity;
      lines.push({ product, quantity, lineTotal: money(lineTotal) });
      cartCount += quantity; cartTotal += lineTotal;
    }
    data.shop = {
      categories, categoryOptions: categoryOptions(categories).map((category) => ({
        ...category, productCount: products.filter((product) => product.categoryId === category.id).length,
      })),
      products: visible, totalProductCount: visible.length, selectedCategoryId: selected, cart: lines, cartCount,
      cartTotal: money(cartTotal), checkoutOpen: context.url.searchParams.get("checkout") === "delivery" || Boolean(delivery) && !paymentOpen,
      paymentOpen, deliveryFee: settings.deliveryFee ?? 0, delivery,
    };
  } catch (error) {
    reportError(error, "Could not load storefront catalog.");
    data.error = "E-shop není dostupný. Katalog se nepodařilo načíst.";
  }
  return context.render("shop/index.html.twig", data);
}

async function action(context: Context): Promise<Response> {
  const action = field(context, "action");
  if (action === "delivery") {
    const delivery = Object.fromEntries(["firstName", "lastName", "phone", "street", "city", "postalCode"]
      .map((name) => [name, field(context, name).trim()]));
    if (Object.values(delivery).includes("") || !/^[+0-9 ()-]{9,20}$/.test(delivery.phone) || !/^\d{3} ?\d{2}$/.test(delivery.postalCode)) {
      return feedback("/eshop", "Vyplňte jméno, telefon a úplnou adresu zákazníka.", true, { checkout: "delivery" });
    }
    context.session.delivery = delivery;
    return redirect("/eshop?checkout=payment#payment-step");
  }
  if (action === "payment") return payment(context);
  const id = uuid(field(context, "productId"));
  const product = (await availableProducts(context)).find((product) => product.id === id);
  if (!product) throw new ValidationError("Produkt není dostupný.");
  const cart = context.session.cart;
  const current = cart[id] || 0;
  let quantity: number;
  switch (action) {
    case "add": quantity = current + integer(field(context, "quantity"), 1); break;
    case "increase": quantity = current + 1; break;
    case "decrease": quantity = current - 1; break;
    case "set": quantity = integer(field(context, "quantity"), 1); break;
    case "remove": quantity = 0; break;
    default: throw new ValidationError("Neznámá akce košíku.");
  }
  quantity = Math.min(quantity, Number(product.availableQuantity));
  if (quantity <= 0) delete cart[id];
  else cart[id] = quantity;
  const category = categoryFilter(field(context, "categoryId"));
  return redirect(`/eshop${category ? `?categoryId=${category}` : ""}`);
}

async function payment(context: Context): Promise<Response> {
  const method = field(context, "paymentMethod").trim();
  if (!["card", "cod"].includes(method)) return feedback("/eshop", "Vyberte způsob platby.", true, { checkout: "payment" });
  if (method === "card") {
    const number = ["cardNumber1", "cardNumber2", "cardNumber3", "cardNumber4"].map((name) => field(context, name).trim()).join("");
    if (!/^\d{16}$/.test(number) || !/^(0[1-9]|1[0-2])\/\d{2}$/.test(field(context, "cardExpiry").trim())
      || !/^\d{3,4}$/.test(field(context, "cardCvc").trim())) {
      return feedback("/eshop", "Zkontrolujte číslo karty, platnost a CVV.", true, { checkout: "payment" });
    }
  }
  const { cart, delivery } = context.session;
  if (!Object.keys(cart).length) return feedback("/eshop", "Košík je prázdný.", true);
  if (!delivery) return feedback("/eshop", "Vyplňte údaje zákazníka.", true, { checkout: "delivery" });
  const products = new Map((await availableProducts(context)).map((product) => [product.id, product]));
  const lines: Row[] = [];
  for (const [id, quantity] of Object.entries(cart)) {
    const product = products.get(id);
    if (!product || quantity <= 0 || quantity > Number(product.availableQuantity)) {
      return feedback("/eshop", "Produkty v košíku již nejsou dostupné v požadovaném množství.", true, { checkout: "payment" });
    }
    lines.push({ productId: uuid(id), quantity });
  }
  const today = new Date();
  const tomorrow = new Date(today); tomorrow.setDate(tomorrow.getDate() + 1);
  const response = await context.backend.mutate("POST", "/api/v1/sales/orders/checkout", {
    customerName: `${delivery.firstName} ${delivery.lastName}`,
    orderDate: today.toISOString().slice(0, 10), deliveryDate: tomorrow.toISOString().slice(0, 10),
    paymentMethod: method === "card" ? "CARD" : "CASH", lines,
  }, false);
  if (response.status !== 201) return feedback("/eshop", "Objednávku se nepodařilo dokončit.", true, { checkout: "payment" });
  context.session.cart = {};
  delete context.session.delivery;
  return redirect("/eshop?order=completed");
}
