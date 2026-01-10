package com.bqka.pdfservice.template.sbi2;

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

public class SbiTemplate2 implements BankPdfTemplate {

        @Override
        public byte[] generate(Statement stmt) throws Exception {

                ByteArrayOutputStream out = new ByteArrayOutputStream();

                Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
                PdfWriter writer = PdfWriter.getInstance(doc, out);

                writer.setPdfVersion(PdfWriter.PDF_VERSION_1_4);

                writer.setEncryption(
                stmt.details.accountNumber.getBytes(),
                null,
                0,
                PdfWriter.ENCRYPTION_AES_128);

                PdfDictionary info = writer.getInfo();
                info.put(PdfName.PRODUCER,
                                new PdfString("iText 2.0.4 (by lowagie.com); modified using iText® 5.5.10 ©2000-2015 iText Group NV (AGPL-version)"));
                setPdfDates(writer);

                Fonts fonts = new Fonts();

                writer.setPageEvent(new SbiFirstPageHeaderEvent(fonts, stmt));

                int addrlines = stmt.details.address.split("\\r?\n").length;
                float pageHeight = doc.getPageSize().getHeight();
                float startY = 806f;
                float marginTop = pageHeight - startY + 54f + (15 + addrlines) * 13.5f + 3 * 18f;

                doc.setMargins(
                                doc.leftMargin(),
                                doc.rightMargin(),
                                marginTop,
                                doc.bottomMargin());

                doc.open();

                addTransactions(doc, fonts, stmt);
                addFooterExact(doc, writer, fonts);

                doc.close();
                return out.toByteArray();
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
                        table.addCell(bodyCell("", fonts.body, Element.ALIGN_LEFT));
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

        private void addFooterExact(Document doc, PdfWriter writer, Fonts fonts) {

                PdfContentByte cb = writer.getDirectContent();
                BaseFont bf1 = fonts.body.getBaseFont(); // F1
                BaseFont bf2 = fonts.bold.getBaseFont(); // F2

                final float LEFT = 36f;
                final float BOTTOM_LIMIT = doc.bottomMargin() + 4f;
                final float TOP_Y = 806f;

                final float[] y = { writer.getVerticalPosition(true) };

                cb.beginText();
                cb.setTextMatrix(LEFT, y[0]);

                // helper with mutable Y
                class Line {
                        void down(float dy) {
                                if (y[0] - dy < BOTTOM_LIMIT) {
                                        cb.endText();
                                        doc.newPage();
                                        y[0] = TOP_Y;
                                        cb.beginText();
                                        cb.setTextMatrix(LEFT, y[0]);

                                        return;
                                }
                                cb.moveText(0, -dy);
                                y[0] -= dy;
                        }
                }

                Line line = new Line();

                // -------- footer content --------

                // line.down(219);

                line.down(13.5f);
                cb.setFontAndSize(bf1, 9);
                cb.showText("\t\t\t\t\t\t Please do not share your ATM, Debit/Credit card number, PIN and OTP with anyone over mail, SMS, phone call or any other");

                line.down(13.5f);
                cb.showText("media. Bank never asks for such information.");

                line.down(18);
                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(18);
                cb.setFontAndSize(bf2, 9);
                cb.showText("ATM: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Automated Teller Machine");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("OTP: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("One Time Password");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("PIN: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Personal Identification Number");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("MICR: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Magnetic Ink Character Recognition technology");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("CIF: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Customer Information File");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("MOD: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Multi Option Deposit");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("IFS Code: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Indian Financial System Code");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("RTGS: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Real Time Gross Settlement");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("NEFT: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("National Electronic Fund Transfer");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("IMPS: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Immediate Payment Service");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("UPI: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Unified Payments Interface");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(16);
                cb.setFontAndSize(bf2, 9);
                cb.showText("CKYCR: ");
                cb.setFontAndSize(bf1, 9);
                cb.showText("Central Know Your Customer Record");

                cb.setFontAndSize(bf1, 12);
                cb.showText(" ");

                line.down(18);
                cb.showText(" ");

                line.down(13.5f);
                cb.setFontAndSize(bf1, 9);
                cb.showText("\t\t **This is a computer generated statement and does not require a signature.");

                line.down(18);
                cb.setFontAndSize(bf1, 12);
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

        private void setPdfDates(PdfWriter writer) {

                // base time (now)
                OffsetDateTime creation = OffsetDateTime.now();

                // mod date = creation + 18 seconds (pick any value 15–20)
                OffsetDateTime modified = creation.plusSeconds(18);

                // format to PDF date string
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

                String creationPdfDate = "D:" + creation.withOffsetSameInstant(java.time.ZoneOffset.UTC).format(fmt)
                                + "+00'00'";

                String modifiedPdfDate = "D:" + modified.withOffsetSameInstant(java.time.ZoneOffset.UTC).format(fmt)
                                + "+00'00'";

                PdfDictionary info = writer.getInfo();

                info.put(PdfName.CREATIONDATE, new PdfString(creationPdfDate));
                info.put(PdfName.MODDATE, new PdfString(modifiedPdfDate));
        }

}