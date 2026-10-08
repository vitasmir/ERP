import assert from "node:assert/strict";
import test from "node:test";
import {
  createInvoicePayload,
  paymentPayload,
  updateInvoicePayload,
  websitePayload,
} from "../src/modules/independent";

test("invoice creation maps the total to a valid backend line", () => {
  assert.deepEqual(createInvoicePayload({
    invoiceNumber: " FV-001 ", partnerName: " Zákazník ",
    issueDate: "2026-01-01", dueDate: "2026-01-15", totalAmount: "1234.50",
  }), {
    invoiceNumber: "FV-001", partnerName: "Zákazník",
    issueDate: "2026-01-01", dueDate: "2026-01-15",
    lines: [{ description: "Faktura FV-001", quantity: 1, unitPrice: "1234.50", vatRate: 0 }],
  });
  assert.throws(() => createInvoicePayload({
    invoiceNumber: "FV-001", partnerName: "Zákazník", totalAmount: -1,
  }), /Celkem/);
});

test("invoice editing includes version and preserves product and image references", () => {
  assert.deepEqual(updateInvoicePayload({
    version: "3", invoiceNumber: "FV-001", partnerName: "Zákazník",
    issueDate: "2026-01-01", dueDate: "2026-01-15",
    line0Description: " Licence ", line0Quantity: 2, line0Price: "120.50", line0Vat: 21,
    line1Description: "Podpora", line1Quantity: 1, line1Price: 100, line1Vat: 0,
  }, [
    { productId: "product-id", imageUrl: "/image.png" },
    { productId: null, imageUrl: null },
  ]), {
    version: 3,
    invoice: {
      invoiceNumber: "FV-001", partnerName: "Zákazník",
      issueDate: "2026-01-01", dueDate: "2026-01-15",
      lines: [
        { productId: "product-id", imageUrl: "/image.png", description: "Licence", quantity: "2", unitPrice: "120.50", vatRate: "21" },
        { productId: null, imageUrl: null, description: "Podpora", quantity: "1", unitPrice: "100", vatRate: "0" },
      ],
    },
  });
  assert.throws(() => updateInvoicePayload({ version: null }, [{}]), /verze/);
  assert.throws(() => updateInvoicePayload({ version: 1 }, []), /položku/);
});

test("payment sends all required backend fields, not the legacy amount-only body", () => {
  assert.deepEqual(paymentPayload({ amount: "123.40", paidOn: "2020-01-01", reference: " Bankovní převod " }), {
    amount: "123.40", paidOn: "2020-01-01", reference: "Bankovní převod",
  });
  assert.throws(() => paymentPayload({ amount: 0, paidOn: "2020-01-01", reference: "Platba" }), /Úhrada/);
  assert.throws(() => paymentPayload({ amount: 1, paidOn: "2020-01-01", reference: " " }), /povinná/);
  assert.throws(() => paymentPayload({ amount: 1, paidOn: "9999-01-01", reference: "Platba" }), /Datum úhrady/);
  assert.throws(() => paymentPayload({ amount: 1, paidOn: "2020-02-30", reference: "Platba" }), /Datum úhrady/);
});

test("website writes only request fields and preserves content formatting", () => {
  assert.deepEqual(websitePayload({
    id: "ignored", status: "PUBLISHED", title: " O nás ", slug: " /o-nas ",
    contentType: "CONTENT", ownerName: " Správce ", content: "\n<p>Obsah</p>\n",
  }), {
    title: "O nás", slug: "/o-nas", contentType: "CONTENT",
    ownerName: "Správce", content: "\n<p>Obsah</p>\n",
  });
  assert.equal(websitePayload({
    title: "Prázdná", slug: "/prazdna", contentType: "LANDING", ownerName: "Správce",
  }).content, "");
  assert.throws(() => websitePayload({
    title: "Stránka", slug: "/stranka", contentType: "INVALID", ownerName: "Správce",
  }), /typ stránky/);
});
