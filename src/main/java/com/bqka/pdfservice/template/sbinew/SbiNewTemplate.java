package com.bqka.pdfservice.template.sbinew;

import com.bqka.pdfservice.model.Statement;
import com.bqka.pdfservice.model.Transaction;
import com.bqka.pdfservice.template.BankPdfTemplate;
import com.bqka.pdfservice.template.sbinew.XObjectFactory.RawFormDef;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;

@SuppressWarnings("deprecation")
public class SbiNewTemplate implements BankPdfTemplate {

    private Map<String, BufferedImage> extractedImages = new LinkedHashMap<>();
    private final PDType1Font font = PDType1Font.HELVETICA;
    private Statement stmt;
    private PageCursor pc;
    private PDPageContentStream cs;

    private static final float LINE_GAP = 9.31f;
    private static final float TEXT_TOP_PADDING = 10.5f;

    private static final float X_DATE = 27.48f;
    private static final float X_POST_DATE = 82.48f;
    private static final float X_DETAILS = 138f;

    static final float X_REF_COL = 280f;
    static final float W_REF_COL = 50f;

    private static final float X_DEBIT_COL = 330f;
    private static final float W_DEBIT_COL = 80f;

    private static final float X_CREDIT_COL = 410f;
    private static final float W_CREDIT_COL = 80f;

    private static final float X_BALANCE_COL = 490f;
    private static final float W_BALANCE_COL = 85f;

    private static final float ROW_LEFT = 20f;
    private static final float ROW_RIGHT = 575f;

    private static final float PAGE1_TXN_Y = 315f;
    private static final float PAGE_TXN_Y = 785f;

    private static final int MAX_NARRATION_LINES = 6;
    private static final int MAX_CHARS_PER_LINE = 30; // SBI-accurate for Helvetica 8

    private int pageno = 1;

    record Column(float x, float width) {}

    static final Column[] COLS = {
        new Column(20f, 55f), // Date
        new Column(75f, 55f), // Post date
        new Column(130f, 150f), // Details
        new Column(280f, 50f), // Debit
        new Column(330f, 80f), // Credit
        new Column(410f, 80f), // Balance
        new Column(490f, 85f), // Extra
    };

    @Override
    public byte[] generate(Statement statement) {
        this.stmt = statement;

        try (PDDocument doc = new PDDocument()) {
            
            if(stmt.meta.password == null){
                throw new Error("No Password Provided");
            }
            
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(true);
            
            // Standard protection policy
            StandardProtectionPolicy spp =
                    new StandardProtectionPolicy(
                            stmt.meta.password,   // owner password
                            stmt.meta.password,    // user password
                            ap
                    );
            
            spp.setEncryptionKeyLength(40); // 🔑 40-bit
            spp.setPreferAES(false);        // ❗ forces RC4
            
            doc.protect(spp);
            PDDocumentInformation info = new PDDocumentInformation();
            info.setProducer("OpenPDF 1.3.32");
            info.setCreator(
                "JasperReports Library version 6.21.2-8434a0bd7c3bbc37cbf916f2968d35e4b165821a"
            );
            Calendar now = Calendar.getInstance();
            info.setCreationDate(now);
            doc.setDocumentInformation(info);

            PDDocument original = PDDocument.load(new File("sbinew.pdf"));
            extractImagesFromPage(original, 2, extractedImages);

            PDRectangle A4_EXACT = new PDRectangle(595f, 842f);
            PDPage page = new PDPage(A4_EXACT);
            doc.addPage(page);
            pageno = 1;

            attachPage1FormXObjects(doc, page);

            pc = new PageCursor(doc, font, page, 0f);
            cs = pc.cs();

            String template = load("/sbinewstreams/headerinfo.pdfops");
            String branchCode = stmt.details.ifsc.substring(6);

            float name_right_edge =
                485.18f +
                (font.getStringWidth("Ved Prakash Patel") / 1000f) * 10f;
            float name_x = AlignXtoRight(
                stmt.details.name,
                font,
                name_right_edge,
                10f
            );

            String[] baddr = stmt.details.branchAddress
                .toUpperCase()
                .split("\n");
            String[] addr = stmt.details.address.split("\n");

            StringBuilder sb = new StringBuilder(
                "1 0 0 1 67 680.65 Tm\n" +
                    "/F1 9 Tf\n" +
                    "0 0 0 rg\n" +
                    "(" +
                    pdfEscapeLiteral(addr[0]) +
                    ")Tj\n" +
                    "0 g\n"
            );

            float addr_y = 670.17f;

            for (int i = 1; i < addr.length; i++) {
                sb.append(
                    "1 0 0 1 67 %.2f Tm\n".formatted(addr_y) +
                        "0 0 0 rg\n" +
                        "(" +
                        pdfEscapeLiteral(addr[i]) +
                        ")Tj\n" +
                        "0 g\n"
                );
                addr_y -= 10.48f;
            }
            
            Map<String, Object> fields = Map.ofEntries(
                Map.entry("NAME", stmt.details.name),
                Map.entry("NAME_X", name_x),
                Map.entry("ACCOUNT_NO", stmt.details.accountNumber),
                Map.entry("IFSC", stmt.details.ifsc),
                Map.entry("MICR", stmt.details.micr),
                Map.entry("CIF", stmt.details.customerRelNo),
                Map.entry("BRANCH_NAME", stmt.details.branch),
                Map.entry("BRANCH_PHONE", stmt.details.branchPhoneNo),
                Map.entry("BRANCH_CODE", branchCode),
                Map.entry("OPENING_BAL", formatIndianAmount(stmt.details.startingBalance) + "CR"),
                Map.entry(
                    "EMAIL",
                    stmt.details.email == null
                        ? "test@gmail.com"
                        : stmt.details.email
                ),
                Map.entry("ADDRSTREAM", sb.toString()),
                Map.entry("BADDRL1", baddr[0]),
                Map.entry("BADDRL2", baddr[1]),
                Map.entry("BADDRL3", 2 < baddr.length ? baddr[2] : ""),
                Map.entry(
                    "END_DATE",
                    formatIsoInstantDate(stmt.meta.statementPeriodEnd)
                ),
                Map.entry(
                    "START_DATE",
                    formatIsoInstantDate(stmt.meta.statementPeriodStart)
                )
            );

            String rendered = renderPdfOps(template, fields);
            cs.appendRawCommands(rendered + "\n");

            drawTableHeader(pc, true);

            // template = load("/sbinewstreams/laststream.pdfops");
            // cs.appendRawCommands(template + "\n");

            float y = 315f;

            for (Transaction tx : stmt.transactions) {
                String dr = tx.debit == 0 ? "-" : formatIndianAmount(tx.debit);
                String cr =
                    tx.credit == 0 ? "-" : formatIndianAmount(tx.credit);

                y = drawTransactionRow(
                    y,
                    formatIsoInstantDate2(tx.date),
                    buildNarrationLines(tx.description),
                    dr,
                    cr,
                    formatIndianAmount(tx.balance)
                );
            }
            y = drawTableFooterAt(y);

            y -= 47f;

            drawStatementSummaryAt(pc, y);

            drawPageNo();

            cs.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.getDocument().setVersion(1.5f);
            COSDictionary catalog = doc.getDocumentCatalog().getCOSObject();
            catalog.removeItem(COSName.VERSION);

            COSDictionary viewerPrefs =
                (COSDictionary) catalog.getDictionaryObject(
                    COSName.VIEWER_PREFERENCES
                );

            if (viewerPrefs == null) {
                viewerPrefs = new COSDictionary();
                catalog.setItem(COSName.VIEWER_PREFERENCES, viewerPrefs);
            }

            viewerPrefs.setName("PrintScaling", "AppDefault");

            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private float drawStatementSummaryAt(PageCursor pc, float topY)
        throws Exception {
        float summaryHeight = 136.43f;
        if (topY - summaryHeight < 124f) {
            drawPageNo();
            pc.nextPage();
            cs = pc.cs();
            topY = 822f;
        }
        PDPage page = pc.page();
        PDDocument doc = pc.doc();
        var first = extractedImages.entrySet().iterator().next();
        PDImageXObject ximg = LosslessFactory.createFromImage(
            doc,
            first.getValue()
        );
        page.getResources().put(COSName.getPDFName(first.getKey()), ximg);

        // Stream was authored with its TOP at Y = 348
        final float BASE_Y = 348f;
        final float BLOCK_HEIGHT = 87f; // 348 - 261

        float deltaY = topY - BASE_Y;
        float bottomY = topY - BLOCK_HEIGHT;

        // Summary columns
        float BF_X_COL = 20f;
        float BF_W_COL = 99.5f;

        float DR_X_COL = 119.5f;
        float DR_W_COL = 58f;

        float CR_X_COL = 177.5f;
        float CR_W_COL = 52f;

        float TD_X_COL = 229.5f;
        float TD_W_COL = 101f;

        float TC_X_COL = 330.5f;
        float TC_W_COL = 100f;

        float CB_X_COL = 430.5f;
        float CB_W_COL = 144.5f;

        int drCount = 0;
        int crCount = 0;
        double totalDebits = 0.0;
        double totalCredits = 0.0;

        for (Transaction tx : stmt.transactions) {
            if (tx.debit != 0f && tx.debit > 0) {
                drCount++;
                totalDebits += tx.debit;
            }
            if (tx.credit != 0f && tx.credit > 0) {
                crCount++;
                totalCredits += tx.credit;
            }
        }

        // ---- Pre-format all display strings once ----
        String bfText = formatIndianAmount(stmt.details.startingBalance) + "CR";
        String drText = String.valueOf(drCount);
        String crText = String.valueOf(crCount);
        String tdText = formatIndianAmount(totalDebits);
        String tcText = formatIndianAmount(totalCredits);

        Transaction lastTx = stmt.transactions.get(
            stmt.transactions.size() - 1
        );
        String closingText = formatIndianAmount(lastTx.balance) + "CR";

        // ---- Fields map ----
        Map<String, Object> fields = Map.ofEntries(
            Map.entry("BROUGHT_FORWARD", bfText),
            Map.entry("BF_X", centerAlignedX(bfText, BF_X_COL, BF_W_COL, 10f)),
            Map.entry("DR_COUNT", drText),
            Map.entry("DR_X", centerAlignedX(drText, DR_X_COL, DR_W_COL, 10f)),
            Map.entry("CR_COUNT", crText),
            Map.entry("CR_X", centerAlignedX(crText, CR_X_COL, CR_W_COL, 10f)),
            Map.entry("TOTAL_DEBITS", tdText),
            Map.entry("TD_X", centerAlignedX(tdText, TD_X_COL, TD_W_COL, 10f)),
            Map.entry("TOTAL_CREDITS", tcText),
            Map.entry("TC_X", centerAlignedX(tcText, TC_X_COL, TC_W_COL, 10f)),
            Map.entry("CLOSING_BAL", closingText),
            Map.entry(
                "CB_X",
                centerAlignedX(closingText, CB_X_COL, CB_W_COL, 10f)
            ),
            Map.entry(
                "SUMMARY_RANGE",
                formatIsoInstantDate(stmt.meta.statementPeriodStart) +
                    " To " +
                    formatIsoInstantDate(stmt.meta.statementPeriodEnd)
            )
        );

        String template = load("/sbinewstreams/summary.pdfops");
        String rendered = renderPdfOps(template, fields);

        cs.appendRawCommands(
            String.format(
                Locale.ROOT,
                """
                q
                1 0 0 1 0 %.2f cm
                %s
                Q
                """,
                deltaY,
                rendered
            )
        );

        return bottomY;
    }

    private float drawTableFooterAt(float rowTopY) throws IOException {
        float baseY = 124f;
        float deltaY = rowTopY - baseY;
        float footerHeight = 30.25f;
        float footerBottomY = rowTopY - footerHeight;

        cs.appendRawCommands(
            String.format(
                Locale.ROOT,
                """
                q
                1 0 0 1 0 %.2f cm
                %s
                Q
                """,
                deltaY,
                load("/sbinewstreams/tablefooter.pdfops")
            )
        );

        return footerBottomY;
    }

    private static List<String> buildNarrationLines(String narration) {
        if (narration == null || narration.isBlank()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();

        // Split on hard line breaks FIRST
        String[] hardLines = narration.strip().split("\\r?\\n");

        for (String hardLine : hardLines) {
            if (lines.size() == MAX_NARRATION_LINES) {
                break;
            }

            hardLine = hardLine.trim().replaceAll("[ \\t]+", " "); // ← only collapse spaces/tabs

            StringBuilder current = new StringBuilder();

            for (String token : tokenizeNarration(hardLine)) {
                if (current.length() == 0) {
                    current.append(token);
                    continue;
                }

                if (
                    current.length() + 1 + token.length() <= MAX_CHARS_PER_LINE
                ) {
                    current.append(token);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(token);

                    if (lines.size() == MAX_NARRATION_LINES) {
                        break;
                    }
                }
            }

            if (lines.size() < MAX_NARRATION_LINES && current.length() > 0) {
                lines.add(current.toString());
            }
        }

        return lines;
    }

    private static List<String> tokenizeNarration(String narration) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (char c : narration.toCharArray()) {
            if (c == ' ' || c == '/' || c == '-') {
                if (sb.length() > 0) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                sb.append(c);
            }
        }

        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }

        // Merge separators smartly
        List<String> merged = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if ((t.equals("/") || t.equals("-")) && !merged.isEmpty()) {
                merged.set(
                    merged.size() - 1,
                    merged.get(merged.size() - 1) + t
                );
            } else {
                merged.add(t);
            }
        }

        return merged;
    }

    private void drawCell(
        float x,
        float topY,
        float width,
        float height,
        boolean drawBottom
    ) throws IOException {
        float bottomY = topY - height;

        // 2️⃣ Fill AFTER borders
        cs.appendRawCommands(
            String.format(
                Locale.ROOT,
                """
                1 1 1 rg
                %.2f %.2f %.2f -%.2f re
                f
                """,
                x,
                topY,
                width,
                height
            )
        );

        // 1️⃣ Borders FIRST (SBI order)
        cs.appendRawCommands(
            String.format(
                Locale.ROOT,
                """
                0.5 w
                0 J
                0.72157 0.72157 0.72157 RG
                [] 0 d

                %.2f %.2f m %.2f %.2f l S
                %.2f %.2f m %.2f %.2f l S
                %s
                %.2f %.2f m %.2f %.2f l S
                """,
                x - 0.25f,
                topY,
                x + width + 0.25f,
                topY, // top
                x,
                topY + 0.25f,
                x,
                bottomY - 0.25f, // left
                drawBottom
                    ? String.format(
                          Locale.ROOT,
                          "%.2f %.2f m %.2f %.2f l S\n",
                          x - 0.25f,
                          bottomY,
                          x + width + 0.25f,
                          bottomY
                      )
                    : "",
                x + width,
                topY + 0.25f,
                x + width,
                bottomY - 0.25f // right
            )
        );
    }

    private float drawTransactionRow(
        float rowTopY,
        String date,
        List<String> narrationLines,
        String debit,
        String credit,
        String balance
    ) throws Exception {
        int lineCount = Math.min(narrationLines.size(), 6);
        float rowHeight = rowHeightForLines(lineCount);
        float rowBottomY = rowTopY - rowHeight;

        if (rowBottomY < 124f) {
            drawTableFooterAt(rowTopY);
            drawPageNo();
            pc.nextPage();
            cs = pc.cs();
            cs.appendRawCommands(
                """
                q
                BT
                36 806 Td
                ET
                Q
                2 J
                BT
                1 0 0 1 0 842 Tm
                /F1 10 Tf
                ()Tj
                ET
                """
            );
            drawTableHeader(pc, false);
            rowTopY = PAGE_TXN_Y;
            rowBottomY = rowTopY - rowHeight;
        }

        // ───────── DATE ─────────
        drawCell(20f, rowTopY, 55f, rowHeight, true);
        drawText(X_DATE, rowTopY - 18f, date);

        // ───────── POST DATE ─────────
        drawCell(75f, rowTopY, 55f, rowHeight, true);
        drawText(X_POST_DATE, rowTopY - 18f, date);

        // ───────── DETAILS ─────────
        drawCell(130f, rowTopY, 150f, rowHeight, true);

        float firstLineY;
        if (lineCount <= 2) {
            float textBlockHeight = (lineCount - 1) * LINE_GAP;
            float centerY = rowTopY - (rowHeight / 2f);
            firstLineY = centerY + (textBlockHeight / 2f) - 3.6f; // Helvetica 8pt baseline
        } else {
            firstLineY = rowTopY - TEXT_TOP_PADDING;
        }

        for (int i = 0; i < lineCount; i++) {
            drawText(
                X_DETAILS,
                firstLineY - (i * LINE_GAP),
                narrationLines.get(i)
            );
        }

        drawCell(X_REF_COL, rowTopY, W_REF_COL, rowHeight, true);
        drawText(
            centerAlignedX("-", X_REF_COL, W_REF_COL, 8f),
            rowTopY - 17f,
            "-"
        );

        // ───────── DEBIT ─────────
        drawCell(X_DEBIT_COL, rowTopY, W_DEBIT_COL, rowHeight, true);
        String debitText = (debit == null || debit.isBlank()) ? "-" : debit;
        drawText(
            centerAlignedX(debitText, X_DEBIT_COL, W_DEBIT_COL, 8f),
            rowTopY - 17f,
            debitText
        );

        // ───────── CREDIT ─────────
        drawCell(X_CREDIT_COL, rowTopY, W_CREDIT_COL, rowHeight, true);
        String creditText = (credit == null || credit.isBlank()) ? "-" : credit;
        drawText(
            centerAlignedX(creditText, X_CREDIT_COL, W_CREDIT_COL, 8f),
            rowTopY - 17f,
            creditText
        );

        // ───────── BALANCE ─────────
        drawCell(X_BALANCE_COL, rowTopY, W_BALANCE_COL, rowHeight, true);
        drawText(
            centerAlignedX(balance, X_BALANCE_COL, W_BALANCE_COL, 8f),
            rowTopY - 17f,
            balance
        );

        return rowBottomY;
    }

    private void drawText(float x, float y, String text) throws IOException {
        cs.appendRawCommands(
            String.format(
                Locale.ROOT,
                """
                BT
                1 0 0 1 %.2f %.2f Tm
                /F1 8 Tf
                0 0 0 rg
                (%s)Tj
                ET
                """,
                x,
                y,
                pdfEscapeLiteral(text)
            )
        );
    }

    private void drawTableHeader(PageCursor pc, boolean firstPage)
        throws IOException {
        if (firstPage) {
            String template = load("/sbinewstreams/tableheader1.pdfops");
            cs.appendRawCommands(template + "\n");
        } else {
            PDPage page = pc.page();
            PDDocument doc = pc.doc();

            List<RawFormDef> defs = List.of(
                new RawFormDef(
                    "Xf24",
                    new PDRectangle(0, 0, 43, 8),
                    load("/sbinewstreams/xf24.pdfops")
                ),
                new RawFormDef(
                    "Xf23",
                    new PDRectangle(0, 0, 49, 8),
                    load("/sbinewstreams/xf23.pdfops")
                ),
                new RawFormDef(
                    "Xf18",
                    new PDRectangle(0, 0, 34, 37),
                    load("/sbinewstreams/xf18.pdfops")
                ),
                new RawFormDef(
                    "Xf27",
                    new PDRectangle(0, 0, 35, 8),
                    load("/sbinewstreams/xf27.pdfops")
                ),
                new RawFormDef(
                    "Xf26",
                    new PDRectangle(0, 0, 32, 8),
                    load("/sbinewstreams/xf26.pdfops")
                ),
                new RawFormDef(
                    "Xf25",
                    new PDRectangle(0, 0, 31, 8),
                    load("/sbinewstreams/xf25.pdfops")
                )
            );

            List<RawFormDef> ch = List.of(
                new RawFormDef(
                    "Xf12",
                    new PDRectangle(0, 0, 49, 8),
                    load("/sbinewstreams/xf12.pdfops")
                ),
                new RawFormDef(
                    "Xf21",
                    new PDRectangle(0, 0, 35, 8),
                    load("/sbinewstreams/xf21.pdfops")
                ),
                new RawFormDef(
                    "Xf19",
                    new PDRectangle(0, 0, 32, 8),
                    load("/sbinewstreams/xf19.pdfops")
                ),
                new RawFormDef(
                    "Xf16",
                    new PDRectangle(0, 0, 31, 8),
                    load("/sbinewstreams/xf16.pdfops")
                ),
                new RawFormDef(
                    "Xf14",
                    new PDRectangle(0, 0, 43, 8),
                    load("/sbinewstreams/xf14.pdfops")
                )
            );

            Map<String, PDFormXObject> forms = XObjectFactory.createRawForms(
                doc,
                defs
            );

            Map<String, PDFormXObject> childs = XObjectFactory.createRawForms(
                doc,
                ch
            );

            PDFormXObject xfparent;
            PDFormXObject xfchild;
            PDResources res;

            // Xf24 -> Xf14
            xfparent = forms.get("Xf24");
            xfchild = childs.get("Xf14");
            res = xfparent.getResources();
            if (res == null) {
                res = new PDResources();
                xfparent.setResources(res);
            }
            res.put(COSName.getPDFName("Xf14"), xfchild);

            // Xf23 -> Xf12
            xfparent = forms.get("Xf23");
            xfchild = childs.get("Xf12");
            res = xfparent.getResources();
            if (res == null) {
                res = new PDResources();
                xfparent.setResources(res);
            }
            res.put(COSName.getPDFName("Xf12"), xfchild);

            // Xf27 -> Xf21
            xfparent = forms.get("Xf27");
            xfchild = childs.get("Xf21");
            res = xfparent.getResources();
            if (res == null) {
                res = new PDResources();
                xfparent.setResources(res);
            }
            res.put(COSName.getPDFName("Xf21"), xfchild);

            // Xf26 -> Xf19
            xfparent = forms.get("Xf26");
            xfchild = childs.get("Xf19");
            res = xfparent.getResources();
            if (res == null) {
                res = new PDResources();
                xfparent.setResources(res);
            }
            res.put(COSName.getPDFName("Xf19"), xfchild);

            // Xf25 -> Xf16
            xfparent = forms.get("Xf25");
            xfchild = childs.get("Xf16");
            res = xfparent.getResources();
            if (res == null) {
                res = new PDResources();
                xfparent.setResources(res);
            }
            res.put(COSName.getPDFName("Xf16"), xfchild);

            XObjectFactory.attachFormsToPage(page, forms);

            String template = load("/sbinewstreams/tableheader2.pdfops");
            cs.appendRawCommands(template + "\n");
        }
    }

    private static float AlignXtoRight(
        String text,
        PDType1Font font,
        float rightEdge,
        float fontSize
    ) throws IOException {
        float textWidth = (font.getStringWidth(text) / 1000f) * fontSize;
        float x = rightEdge - textWidth;
        return x;
    }

    private float centerAlignedX(
        String text,
        float colX,
        float colWidth,
        float fontSize
    ) throws IOException {
        float textWidth = (font.getStringWidth(text) / 1000f) * fontSize;
        return colX + (colWidth - textWidth) / 2f;
    }

    private static String debitAmount(Double debit) {
        return debit != null && debit > 0 ? formatIndianAmount(debit) : "-";
    }

    private static String creditAmount(Double credit) {
        return credit != null && credit > 0 ? formatIndianAmount(credit) : "-";
    }

    private static String balanceAmount(double balance) {
        return formatIndianAmount(balance);
    }

    private static String formatIndianAmount(double value) {
        boolean negative = value < 0;
        value = Math.abs(value);

        // Format to plain 2-decimal string
        String s = String.format(Locale.ROOT, "%.2f", value);

        int dot = s.indexOf('.');
        String intPart = dot >= 0 ? s.substring(0, dot) : s;
        String fracPart = dot >= 0 ? s.substring(dot) : ".00";

        String grouped = groupIndian(intPart);

        return (negative ? "-" : "") + grouped + fracPart;
    }

    private static String groupIndian(String digits) {
        int len = digits.length();
        if (len <= 3) {
            return digits;
        }

        String last3 = digits.substring(len - 3);
        String rest = digits.substring(0, len - 3);

        StringBuilder sb = new StringBuilder();

        int i = rest.length();
        while (i > 0) {
            int start = Math.max(0, i - 2);
            if (sb.length() > 0) {
                sb.insert(0, ',');
            }
            sb.insert(0, rest.substring(start, i));
            i = start;
        }

        sb.append(',').append(last3);
        return sb.toString();
    }

    private static String formatIsoInstantDate(String isoInstant) {
        Instant instant = Instant.parse(isoInstant);
        return DateTimeFormatter.ofPattern("dd-MM-yyyy")
            .withZone(ZoneOffset.UTC)
            .format(instant);
    }

    private static String formatIsoInstantDate2(String isoInstant) {
        Instant instant = Instant.parse(isoInstant);
        return DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneOffset.UTC)
            .format(instant);
    }

    private static String renderPdfOps(String template, Map<String, ?> values) {
        String result = template;

        for (var e : values.entrySet()) {
            String key = "{{" + e.getKey() + "}}";
            Object v = e.getValue();

            if (!result.contains(key)) {
                throw new IllegalArgumentException(
                    "Placeholder not found: " + key
                );
            }

            String replacement;
            if (v instanceof Number n) {
                replacement = String.format(
                    Locale.ROOT,
                    "%.2f",
                    n.doubleValue()
                );
            } else if ("ADDRSTREAM".equals(e.getKey())) {
                replacement = v.toString();
            } else {
                replacement = pdfEscapeLiteral(v.toString());
            }

            result = result.replace(key, replacement);
        }

        if (result.contains("{{")) {
            throw new IllegalStateException(
                "Unresolved placeholders in PDF ops"
            );
        }

        return result;
    }

    private static String pdfEscapeLiteral(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String load(String path) throws IOException {
        try (var is = SbiNewTemplate.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    private void attachPage1FormXObjects(PDDocument doc, PDPage page)
        throws IOException {
        List<RawFormDef> defs = List.of(
            new RawFormDef(
                "Xf3",
                new PDRectangle(0, 0, 16, 19),
                load("/sbinewstreams/xf3.pdfops")
            ),
            new RawFormDef(
                "Xf13",
                new PDRectangle(0, 0, 49, 8),
                load("/sbinewstreams/xf13.pdfops")
            ),
            new RawFormDef(
                "Xf4",
                new PDRectangle(0, 0, 16, 16),
                load("/sbinewstreams/xf4.pdfops")
            ),
            new RawFormDef(
                "Xf1",
                new PDRectangle(0, 0, 20, 20),
                load("/sbinewstreams/xf1.pdfops")
            ),
            new RawFormDef(
                "Xf11",
                new PDRectangle(0, 0, 93, 32),
                load("/sbinewstreams/xf11.pdfops")
            ),
            new RawFormDef(
                "Xf22",
                new PDRectangle(0, 0, 35, 8),
                load("/sbinewstreams/xf22.pdfops")
            ),
            new RawFormDef(
                "Xf2",
                new PDRectangle(0, 0, 20, 20),
                load("/sbinewstreams/xf2.pdfops")
            ),
            new RawFormDef(
                "Xf10",
                new PDRectangle(0, 0, 20, 20),
                load("/sbinewstreams/xf10.pdfops")
            ),
            new RawFormDef(
                "Xf20",
                new PDRectangle(0, 0, 32, 8),
                load("/sbinewstreams/xf20.pdfops")
            ),
            new RawFormDef(
                "Xf9",
                new PDRectangle(0, 0, 13, 15),
                load("/sbinewstreams/xf9.pdfops")
            ),
            new RawFormDef(
                "Xf7",
                new PDRectangle(0, 0, 18, 13),
                load("/sbinewstreams/xf7.pdfops")
            ),
            new RawFormDef(
                "Xf8",
                new PDRectangle(0, 0, 117, 23),
                load("/sbinewstreams/xf8.pdfops")
            ),
            new RawFormDef(
                "Xf5",
                new PDRectangle(0, 0, 16, 16),
                load("/sbinewstreams/xf5.pdfops")
            ),
            new RawFormDef(
                "Xf6",
                new PDRectangle(0, 0, 14, 14),
                load("/sbinewstreams/xf6.pdfops")
            ),
            new RawFormDef(
                "Xf18",
                new PDRectangle(0, 0, 34, 37),
                load("/sbinewstreams/xf18.pdfops")
            ),
            new RawFormDef(
                "Xf17",
                new PDRectangle(0, 0, 31, 8),
                load("/sbinewstreams/xf17.pdfops")
            ),
            new RawFormDef(
                "Xf15",
                new PDRectangle(0, 0, 43, 8),
                load("/sbinewstreams/xf15.pdfops")
            )
        );

        List<RawFormDef> ch = List.of(
            new RawFormDef(
                "Xf12",
                new PDRectangle(0, 0, 49, 8),
                load("/sbinewstreams/xf12.pdfops")
            ),
            new RawFormDef(
                "Xf21",
                new PDRectangle(0, 0, 35, 8),
                load("/sbinewstreams/xf21.pdfops")
            ),
            new RawFormDef(
                "Xf19",
                new PDRectangle(0, 0, 32, 8),
                load("/sbinewstreams/xf19.pdfops")
            ),
            new RawFormDef(
                "Xf16",
                new PDRectangle(0, 0, 31, 8),
                load("/sbinewstreams/xf16.pdfops")
            ),
            new RawFormDef(
                "Xf14",
                new PDRectangle(0, 0, 43, 8),
                load("/sbinewstreams/xf14.pdfops")
            )
        );

        Map<String, PDFormXObject> forms = XObjectFactory.createRawForms(
            doc,
            defs
        );

        Map<String, PDFormXObject> childs = XObjectFactory.createRawForms(
            doc,
            ch
        );

        // Xf13 -> Xf12
        PDFormXObject xfparent = forms.get("Xf13");
        PDFormXObject xfchild = childs.get("Xf12");
        PDResources res = xfparent.getResources();
        if (res == null) {
            res = new PDResources();
            xfparent.setResources(res);
        }
        res.put(COSName.getPDFName("Xf12"), xfchild);

        // Xf22 -> Xf21
        xfparent = forms.get("Xf22");
        xfchild = childs.get("Xf21");
        res = xfparent.getResources();
        if (res == null) {
            res = new PDResources();
            xfparent.setResources(res);
        }
        res.put(COSName.getPDFName("Xf21"), xfchild);

        // Xf20 -> Xf19
        xfparent = forms.get("Xf20");
        xfchild = childs.get("Xf19");
        res = xfparent.getResources();
        if (res == null) {
            res = new PDResources();
            xfparent.setResources(res);
        }
        res.put(COSName.getPDFName("Xf19"), xfchild);

        // Xf17 -> Xf16
        xfparent = forms.get("Xf17");
        xfchild = childs.get("Xf16");
        res = xfparent.getResources();
        if (res == null) {
            res = new PDResources();
            xfparent.setResources(res);
        }
        res.put(COSName.getPDFName("Xf16"), xfchild);

        // Xf15 -> Xf14
        xfparent = forms.get("Xf15");
        xfchild = childs.get("Xf14");
        res = xfparent.getResources();
        if (res == null) {
            res = new PDResources();
            xfparent.setResources(res);
        }
        res.put(COSName.getPDFName("Xf14"), xfchild);

        XObjectFactory.attachFormsToPage(page, forms);
    }

    private void drawPageNo() throws IOException {
        String[] stream = {
            """
                1 0 0 1 0 0 cm
                BT
                1 0 0 1 313 78.72 Tm
                /F1 10 Tf
                0 0 0 rg
                (%d)Tj
                0 g
                ET
                1 0 0 1 0 0 cm
                [] 0 d
                2 J
                1 0 0 1 0 0 cm
                BT
                1 0 0 1 269 78.72 Tm
                /F1 10 Tf
                0 0 0 rg
                (Page no.)Tj
                0 g
                ET
                1 0 0 1 0 0 cm
                [] 0 d
                2 J
                """,
        };

        String formatted = String.format(Locale.ROOT, stream[0], pageno++);
        cs.appendRawCommands(formatted + "\n");
    }

    private static float rowHeightForLines(int lines) {
        return switch (lines) {
            case 1, 2 -> 30f;
            case 3 -> 31f;
            case 4 -> 41f;
            case 5 -> 50f;
            default -> 59f; // 6+ (cap)
        };
    }

    private static void extractImagesFromPage(
        PDDocument doc,
        int pageIndex,
        Map<String, BufferedImage> out
    ) throws IOException {
        PDPage page = doc.getPage(pageIndex);
        PDResources resources = page.getResources();
        if (resources == null) return;

        for (COSName name : resources.getXObjectNames()) {
            PDXObject xobj = resources.getXObject(name);

            if (xobj instanceof PDImageXObject img) {
                // store once (avoid duplicates)
                out.putIfAbsent(name.getName(), img.getImage());
            }
        }
    }
}
