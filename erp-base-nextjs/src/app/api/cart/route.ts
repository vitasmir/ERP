import { NextRequest, NextResponse } from "next/server";
import { backendFetch } from "@/lib/backend";
import { CART_COOKIE, loadCart, saveCart, UUID, changeQuantity, deliveryValues } from "@/lib/cart-store";
import { record, rows, number, text, type Json } from "@/lib/data";
import { isSameOrigin } from "@/lib/policy";

export async function GET(request: NextRequest) {
  try {
    const state = await loadCart(request.cookies.get(CART_COOKIE)?.value);
    await saveCart(state.id, state.cart);
    const response = NextResponse.json(state.cart, { headers: { "Cache-Control": "no-store" } });
    response.cookies.set(CART_COOKIE, state.id, { httpOnly: true, sameSite: "lax", path: "/",
      secure: process.env.COOKIE_SECURE === "true" || request.nextUrl.protocol === "https:", maxAge: 7 * 24 * 60 * 60 });
    return response;
  } catch (error) {
    console.error("Cart lookup failed.", error);
    return Response.json({ error: "Košík není dostupný." }, { status: 503 });
  }
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request.headers, request.url)) return Response.json({ error: "Neplatný původ požadavku." }, { status: 403 });
  let input;
  try { input = record(await request.json() as Json); }
  catch { return Response.json({ error: "Neplatná data košíku." }, { status: 400 }); }
  try {
    const { id, cart } = await loadCart(request.cookies.get(CART_COOKIE)?.value);
    const action = text(input.action);
    if (action === "delivery") {
      try { cart.delivery = deliveryValues(input.delivery ?? null); }
      catch (error) { return Response.json({ error: error instanceof Error ? error.message : "Neplatná adresa." }, { status: 400 }); }
    } else {
      const productId = text(input.productId);
      if (!UUID.test(productId)) return Response.json({ error: "Neplatný produkt." }, { status: 400 });
      const stocks = await backendFetch(`catalog/products/${productId}/availability`);
      if (!stocks.ok) return Response.json({ error: "Dostupnost produktu nelze ověřit." }, { status: stocks.status });
      const available = rows(await stocks.json() as Json).reduce((sum, stock) => sum + number(stock.quantity), 0);
      let quantity;
      try { quantity = changeQuantity(cart.quantities[productId] || 0, action, number(input.quantity ?? 1), available); }
      catch (error) { return Response.json({ error: error instanceof Error ? error.message : "Neplatné množství." }, { status: 400 }); }
      if (quantity) cart.quantities[productId] = quantity;
      else delete cart.quantities[productId];
    }
    await saveCart(id, cart);
    const response = NextResponse.json(cart);
    response.cookies.set(CART_COOKIE, id, { httpOnly: true, sameSite: "lax", path: "/",
      secure: process.env.COOKIE_SECURE === "true" || request.nextUrl.protocol === "https:", maxAge: 7 * 24 * 60 * 60 });
    return response;
  } catch (error) {
    console.error("Cart update failed.", error);
    return Response.json({ error: "Košík nelze upravit. Backend není dostupný." }, { status: 503 });
  }
}
