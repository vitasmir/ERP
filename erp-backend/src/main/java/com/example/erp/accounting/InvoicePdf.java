package com.example.erp.accounting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

final class InvoicePdf {
    private InvoicePdf() { }

    static byte[] render(AccountInvoice invoice) throws IOException {
        try (PDDocument document = new PDDocument();
                var source = InvoicePdf.class.getResourceAsStream("/fonts/DejaVuSans.ttf");
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (source == null) throw new IOException("Invoice font is missing.");
            PDType0Font font = PDType0Font.load(document, source);
            List<String> content = new ArrayList<>(List.of("Invoice " + invoice.getInvoiceNumber(),
                    "Customer: " + invoice.getPartnerName(), "Status: " + invoice.getStatus(),
                    "Issued: " + invoice.getIssueDate() + "     Due: " + invoice.getDueDate(), ""));
            for (InvoiceLine line : invoice.getLines()) {
                content.add(line.getDescription());
                content.add(line.getQuantity() + " x " + line.getUnitPrice() + " CZK | VAT " + line.getVatRate()
                        + "%: " + line.getVatAmount() + " CZK | Total " + line.getTotalAmount() + " CZK");
            }
            content.addAll(List.of("", "Total: " + invoice.getTotalAmount() + " CZK", "Paid: " + invoice.getPaidAmount()
                    + " CZK", "Outstanding: " + invoice.outstandingAmount() + " CZK"));
            List<String> lines = new ArrayList<>();
            for (String text : content) lines.addAll(wrap(text, font));
            for (int offset = 0; offset < lines.size(); offset += 42) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 11);
                    stream.setLeading(17);
                    stream.newLineAtOffset(48, page.getMediaBox().getHeight() - 55);
                    for (String line : lines.subList(offset, Math.min(offset + 42, lines.size()))) {
                        stream.showText(line);
                        stream.newLine();
                    }
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static List<String> wrap(String text, PDType0Font font) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int codePoint : text.replaceAll("[\\p{Cntrl}]", " ").codePoints().toArray()) {
            String character = new String(Character.toChars(codePoint));
            try { font.getStringWidth(character); } catch (IllegalArgumentException exception) { character = "?"; }
            if (font.getStringWidth(line.toString() + character) * 11 / 1000 > 490) {
                result.add(line.toString());
                line.setLength(0);
            }
            line.append(character);
        }
        result.add(line.toString());
        return result;
    }
}