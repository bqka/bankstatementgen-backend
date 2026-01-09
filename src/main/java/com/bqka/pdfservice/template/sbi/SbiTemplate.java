package com.bqka.pdfservice.template.sbi;

import com.bqka.pdfservice.model.Statement;
import com.bqka.pdfservice.model.Transaction;
import com.bqka.pdfservice.template.BankPdfTemplate;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SbiTemplate implements BankPdfTemplate {
        @Override
        public byte[] generate(Statement stmt) throws Exception {

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
                PdfWriter writer = PdfWriter.getInstance(doc, out);

                writer.setPdfVersion(PdfWriter.VERSION_1_4);
                PdfDictionary info = writer.getInfo();
                info.put(PdfName.PRODUCER,
                                new PdfString("iText 2.0.4 (by lowagie.com)"));

                Fonts fonts = new Fonts();
                writer.setPageEvent(new SbiFirstPageHeaderEvent(fonts, stmt));

                int addrlines = stmt.details.address.split("\\r?\n").length;
                float pageHeight = doc.getPageSize().getHeight();
                float startY = 806f;
                float marginTop = pageHeight - startY + 54f + (16 + addrlines) * 13.5f + 3 * 18f;

                doc.setMargins(
                                doc.leftMargin(),
                                doc.rightMargin(),
                                marginTop,
                                doc.bottomMargin());

                doc.open();

                addTransactions(doc, fonts, stmt);
                addFooterExact(writer, fonts);

                doc.close();
                return out.toByteArray();
        }

        public void render(Document doc, PdfWriter writer, Fonts fonts, Statement stmt) throws Exception {

                writer.setPageEvent(new SbiFirstPageHeaderEvent(fonts, stmt));

                // DEPENDS ON LINES OF ADDRESS
                int addrlines = stmt.details.address.split("\\r?\n").length;

                float pageHeight = doc.getPageSize().getHeight();
                float startY = 806f;
                float marginTop = pageHeight - startY + 54f + (16 + addrlines) * 13.5f + 3 * 18f;

                doc.setMargins(
                                doc.leftMargin(),
                                doc.rightMargin(),
                                marginTop,
                                doc.bottomMargin());

                doc.open();

                addTransactions(doc, fonts, stmt);
                addFooterExact(writer, fonts);
        }

        // ================= TRANSACTIONS =================

        private void addTransactions(Document doc, Fonts fonts, Statement stmt) throws Exception {

                PdfPTable table = new PdfPTable(7);

                table.setSplitLate(false);
                table.setSplitRows(false);
                // table.setSpacingBefore(6f);
                table.setKeepTogether(false);

                table.setHeaderRows(1);

                float pageWidth = doc.getPageSize().getWidth()
                                - doc.leftMargin()
                                - doc.rightMargin();

                table.setTotalWidth(pageWidth);
                table.setLockedWidth(true);

                table.setWidths(new float[] {
                                52.83f, // Txn Date
                                52.83f, // Value Date
                                132.07f, // Description
                                79.24f, // Ref No
                                63.39f, // Debit
                                63.39f, // Credit
                                79.24f // Balance
                });

                addHeader(table, "Txn Date", fonts, Element.ALIGN_LEFT);
                addHeader(table, "Value Date", fonts, Element.ALIGN_LEFT);
                addHeader(table, "Description", fonts, Element.ALIGN_LEFT);
                addHeader(table, "Ref No./Cheque No.", fonts, Element.ALIGN_LEFT);
                addHeader(table, "Debit", fonts, Element.ALIGN_RIGHT);
                addHeader(table, "Credit", fonts, Element.ALIGN_RIGHT);
                addHeader(table, "Balance", fonts, Element.ALIGN_RIGHT);

                for (Transaction tx : stmt.transactions) {
                        table.addCell(bodyCell(format(tx.date), fonts.body, Element.ALIGN_RIGHT));
                        table.addCell(bodyCell(format(tx.date), fonts.body, Element.ALIGN_RIGHT));
                        table.addCell(bodyCell(tx.description, fonts.body, Element.ALIGN_LEFT));
                        table.addCell(bodyCell(tx.reference, fonts.body, Element.ALIGN_LEFT));
                        table.addCell(bodyCell(amount(tx.debit), fonts.body, Element.ALIGN_RIGHT));
                        table.addCell(bodyCell(amount(tx.credit), fonts.body, Element.ALIGN_RIGHT));
                        table.addCell(bodyCell(amount(tx.balance), fonts.body, Element.ALIGN_RIGHT));
                }

                doc.add(table);
        }

        private void addHeader(PdfPTable table, String text, Fonts fonts, int align) {
                PdfPCell c = new PdfPCell(new Phrase(text, fonts.header));
                c.setPadding(2);
                c.setHorizontalAlignment(align);
                table.addCell(c);
        }

        private PdfPCell bodyCell(String text, Font font, int align) {
                PdfPCell c = new PdfPCell(new Phrase(text != null ? text : "", font));
                c.setPadding(2);
                c.setHorizontalAlignment(align);
                return c;
        }

        // ================= FOOTER =================

        private void addFooter(Document doc, Fonts fonts) throws Exception {
                Paragraph p = new Paragraph(
                                "Please do not share your ATM, Debit/Credit card number, PIN (Personal Identification Number) and OTP (One Time Password) with anyone over mail, SMS, phone call or any other media. Bank never asks for such information.",
                                fonts.body);
                p.setFirstLineIndent(15f);
                p.setSpacingAfter(18f);
                p.setLeading(13f);
                doc.add(p);

                p = new Paragraph(
                                "**This is a computer generated statement and does not require a signature.",
                                fonts.body);
                p.setIndentationLeft(5f);
                doc.add(p);
        }

        private void addFooterExact(PdfWriter writer, Fonts fonts) {

                PdfContentByte cb = writer.getDirectContent();

                float left = 36f; // same as document left margin
                float startY = writer.getVerticalPosition(true);

                // =====================================================
                // FOOTER BLOCK 1 : WARNING TEXT
                // =====================================================
                cb.beginText();

                // anchor at current vertical position
                cb.setTextMatrix(left, startY);

                cb.moveText(0, -13.5f);
                cb.setFontAndSize(fonts.body.getBaseFont(), 9);

                cb.showText(
                                "\t\t\t\t\t\t Please do not share your ATM, Debit/Credit card number, PIN " +
                                                "(Personal Identification Number) and OTP (One Time Password)");

                cb.moveText(0, -13f);
                cb.showText(
                                "with anyone over mail, SMS, phone call or any other media. Bank never asks for such information.");

                cb.moveText(0, -18);
                cb.setFontAndSize(fonts.body.getBaseFont(), 12);
                cb.showText(" ");

                cb.endText();

                // =====================================================
                // FOOTER BLOCK 2 : SIGNATURE DISCLAIMER
                // =====================================================
                cb.beginText();

                cb.setTextMatrix(left, startY - 45); // small offset below block 1

                cb.moveText(0, -13.5f);
                cb.setFontAndSize(fonts.body.getBaseFont(), 9);

                cb.showText(
                                "\t\t **This is a computer generated statement and does not require a signature.");

                cb.moveText(0, -18);
                cb.setFontAndSize(fonts.body.getBaseFont(), 12);
                cb.showText(" ");

                cb.moveText(0, -18);
                cb.showText(" ");

                cb.endText();
        }

        // ================= HELPERS =================

        private static final DateTimeFormatter SBI_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

        private String format(String iso) {
                return OffsetDateTime.parse(iso).format(SBI_FORMAT);
        }

        private String amount(double value) {
                if (value == 0)
                        return "";
                boolean negative = value <= 0;

                // Convert safely
                BigDecimal bd = BigDecimal.valueOf(value).abs()
                                .setScale(2, RoundingMode.HALF_UP);

                String str = bd.toPlainString();
                int dotIndex = str.indexOf('.');

                String intPart = dotIndex >= 0 ? str.substring(0, dotIndex) : str;
                String decPart = dotIndex >= 0 ? str.substring(dotIndex) : ".00";

                int len = intPart.length();
                if (len <= 3) {
                        return (negative ? "-" : "") + intPart + decPart;
                }

                StringBuilder result = new StringBuilder();

                // last 3 digits
                result.insert(0, intPart.substring(len - 3));
                int i = len - 3;

                // remaining digits in groups of 2
                while (i > 0) {
                        int start = Math.max(0, i - 2);
                        result.insert(0, intPart.substring(start, i) + ",");
                        i = start;
                }

                return (negative ? "-" : "") + result + decPart;
        }
}