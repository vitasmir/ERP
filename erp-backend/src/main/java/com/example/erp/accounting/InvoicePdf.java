package com.example.erp.accounting;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

final class InvoicePdf {
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float MARGIN = 40;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private InvoicePdf() { }

    static byte[] render(AccountInvoice invoice) throws IOException {
        return render(invoice, null);
    }

    static byte[] render(AccountInvoice invoice, String orderNumber) throws IOException {
        return render(invoice, orderNumber, Map.of());
    }

    static byte[] render(AccountInvoice invoice, String orderNumber, Map<UUID, String> units) throws IOException {
        try (PDDocument document = new PDDocument();
                var source = InvoicePdf.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (source == null) throw new IOException("Invoice font is missing.");
            PDType0Font font = PDType0Font.load(document, source);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float y = drawHeader(stream, font, invoice, orderNumber);
            y = drawTableHeader(stream, font, y);
            for (InvoiceLine line : invoice.getLines()) {
                String unit = line.getProductId() == null ? "ks" : units.getOrDefault(line.getProductId(), "ks");
                String description = line.getDescription();
                float rowHeight = Math.max(24, wrap(description, font, 10, 210).size() * 13 + 10);
                if (y - rowHeight < 145) {
                    stream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    stream = new PDPageContentStream(document, page);
                    y = drawHeader(stream, font, invoice, orderNumber);
                    y = drawTableHeader(stream, font, y);
                }
                drawLine(stream, MARGIN, y - rowHeight, PAGE_WIDTH - MARGIN, y - rowHeight, new Color(220, 225, 224));
                drawWrapped(stream, font, description, MARGIN + 6, y - 16, 10, 210, 13);
                drawRight(stream, font, line.getQuantity().stripTrailingZeros().toPlainString() + " " + unit, 8, 320, y - 16);
                drawRight(stream, font, money(line.getNetAmount()), 8, 410, y - 16);
                drawRight(stream, font, money(line.getVatAmount()), 8, 475, y - 16);
                drawRight(stream, font, money(line.getTotalAmount()), 8, PAGE_WIDTH - MARGIN - 6, y - 16);
                y -= rowHeight;
            }
            drawTotals(stream, font, invoice, y - 12);
            stream.close();
            document.save(output);
            return output.toByteArray();
        }
    }

    private static float drawHeader(PDPageContentStream stream, PDType0Font font, AccountInvoice invoice,
            String orderNumber) throws IOException {
        float top = PDRectangle.A4.getHeight() - MARGIN;
        drawText(stream, font, "FAKTURA", 24, MARGIN, top);
        drawRight(stream, font, invoice.getInvoiceNumber(), 14, PAGE_WIDTH - MARGIN, top + 2);
        drawLine(stream, MARGIN, top - 12, PAGE_WIDTH - MARGIN, top - 12, new Color(20, 126, 185));
        drawText(stream, font, "DODAVATEL", 9, MARGIN, top - 38);
        drawText(stream, font, "ERP CORE", 12, MARGIN, top - 55);
        drawText(stream, font, "ERP systém", 10, MARGIN, top - 71);
        drawText(stream, font, "ODBĚRATEL", 9, 315, top - 38);
        drawText(stream, font, invoice.getPartnerName(), 12, 315, top - 55);
        drawText(stream, font, "Číslo faktury: " + invoice.getInvoiceNumber(), 10, MARGIN, top - 101);
        drawText(stream, font, "Datum vystavení: " + DATE.format(invoice.getIssueDate()), 10, MARGIN, top - 117);
        drawText(stream, font, "Datum splatnosti: " + DATE.format(invoice.getDueDate()), 10, MARGIN, top - 133);
        if (orderNumber != null && !orderNumber.isBlank()) {
            drawText(stream, font, "Objednávka: " + orderNumber, 10, 315, top - 101);
        }
        return top - 160;
    }

    private static float drawTableHeader(PDPageContentStream stream, PDType0Font font, float y) throws IOException {
        stream.setNonStrokingColor(new Color(20, 126, 185));
        stream.addRect(MARGIN, y - 24, CONTENT_WIDTH, 24);
        stream.fill();
        drawText(stream, font, "Položka", 9, MARGIN + 6, y - 16, Color.WHITE);
        drawRight(stream, font, "Množství", 8, 320, y - 16, Color.WHITE);
        drawRight(stream, font, "Bez DPH", 8, 410, y - 16, Color.WHITE);
        drawRight(stream, font, "DPH", 8, 475, y - 16, Color.WHITE);
        drawRight(stream, font, "S DPH", 8, PAGE_WIDTH - MARGIN - 6, y - 16, Color.WHITE);
        return y - 24;
    }

    private static void drawTotals(PDPageContentStream stream, PDType0Font font, AccountInvoice invoice, float y)
            throws IOException {
        drawLine(stream, 330, y, PAGE_WIDTH - MARGIN, y, new Color(20, 126, 185));
        drawText(stream, font, "Celkem bez DPH", 10, 330, y - 20);
        drawRight(stream, font, money(invoice.getLines().stream().map(InvoiceLine::getNetAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)), 10, PAGE_WIDTH - MARGIN, y - 20);
        drawText(stream, font, "Celkem s DPH", 11, 330, y - 39);
        drawRight(stream, font, money(invoice.getTotalAmount()), 11, PAGE_WIDTH - MARGIN, y - 39);
        drawText(stream, font, "Uhrazeno", 10, 330, y - 58);
        drawRight(stream, font, money(invoice.getPaidAmount()), 10, PAGE_WIDTH - MARGIN, y - 58);
        drawText(stream, font, "Zbývá uhradit", 10, 330, y - 77);
        drawRight(stream, font, money(invoice.outstandingAmount()), 10, PAGE_WIDTH - MARGIN, y - 77);
        drawText(stream, font, "Děkujeme za váš obchod.", 9, MARGIN, y - 77);
    }

    private static void drawWrapped(PDPageContentStream stream, PDType0Font font, String text, float x, float y,
            float size, float width, float leading) throws IOException {
        for (String line : wrap(text, font, size, width)) {
            drawText(stream, font, line, size, x, y);
            y -= leading;
        }
    }

    private static List<String> wrap(String text, PDType0Font font, float size, float width) throws IOException {
        List<String> result = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.replaceAll("[\\p{Cntrl}]", " ").split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.getStringWidth(candidate) * size / 1000 > width && line.length() > 0) {
                result.add(line.toString());
                line.setLength(0);
            }
            line.append(line.length() == 0 ? word : " " + word);
        }
        if (line.length() > 0) result.add(line.toString());
        return result.isEmpty() ? List.of("") : result;
    }

    private static void drawText(PDPageContentStream stream, PDType0Font font, String text, float size, float x,
            float y) throws IOException {
        drawText(stream, font, text, size, x, y, Color.BLACK);
    }

    private static void drawText(PDPageContentStream stream, PDType0Font font, String text, float size, float x,
            float y, Color color) throws IOException {
        stream.beginText();
        stream.setNonStrokingColor(color);
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(text == null ? "" : text);
        stream.endText();
    }

    private static void drawRight(PDPageContentStream stream, PDType0Font font, String text, float size, float right,
            float y) throws IOException {
        drawRight(stream, font, text, size, right, y, Color.BLACK);
    }

    private static void drawRight(PDPageContentStream stream, PDType0Font font, String text, float size, float right,
            float y, Color color) throws IOException {
        float width = font.getStringWidth(text) * size / 1000;
        drawText(stream, font, text, size, right - width, y, color);
    }

    private static void drawLine(PDPageContentStream stream, float x1, float y1, float x2, float y2, Color color)
            throws IOException {
        stream.setStrokingColor(color);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
    }

    private static String money(java.math.BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " Kč";
    }
}