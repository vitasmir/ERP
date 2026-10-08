import { NextRequest } from "next/server";
import { backendFetch } from "@/lib/backend";
import { CART_COOKIE, loadCart, saveCart } from "@/lib/cart-store";
import { record, text, type Json } from "@/lib/data";
import { isSameOrigin } from "@/lib/policy";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request.headers, request.url)) return Response.json({ error: "Neplatný původ požadavku." }, { status: 403 });
  let paymentMethod;
  try { paymentMethod = text(record(await request.json() as Json).paymentMethod); }
  catch { return Response.json({ error: "Neplatná platební metoda." }, { status: 400 }); }
  if (!["CARD", "CASH"].includes(paymentMethod)) return Response.json({ error: "Vyberte způsob platby." }, { status: 400 });
  try {
    const { id, cart } = await loadCart(request.cookies.get(CART_COOKIE)?.value);
    if (!cart.delivery || !Object.keys(cart.quantities).length) {
      return Response.json({ error: "Vyplňte adresu a přidejte produkty do košíku." }, { status: 400 });
    }
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    const upstream = await backendFetch("sales/orders/checkout", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        customerName: `${text(cart.delivery.firstName)} ${text(cart.delivery.lastName)}`,
        orderDate: today.toISOString().slice(0, 10), deliveryDate: tomorrow.toISOString().slice(0, 10),
        paymentMethod, lines: Object.entries(cart.quantities).map(([productId, quantity]) => ({ productId, quantity })),
      }),
    });
    if (!upstream.ok) {
      console.error("Checkout rejected.", upstream.status);
      return Response.json({ error: `Objednávku nelze dokončit (HTTP ${upstream.status}). Zkontrolujte dostupnost produktů.` }, { status: upstream.status });
    }
    const order: Json = await upstream.json();
    await saveCart(id, { quantities: {}, delivery: null, updatedAt: Date.now() });
    return Response.json(order, { status: 201 });
  } catch (error) {
    console.error("Checkout failed.", error);
    return Response.json({ error: "Objednávku nelze dokončit. Ověřte její stav před opakováním." }, { status: 503 });
  }
}
