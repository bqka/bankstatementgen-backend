package com.bqka.pdfservice.ocr.extractors;

import java.util.regex.*;

public class AddressExtractor {

    public static String extract(String text, String bank) {
        if (text == null)
            return null;

        if ("AXIS".equals(bank)) {
            return extractAxisCustomerAddress(text);
        }

        if ("KOTAK".equals(bank)) {
            return extractKotakCustomerAddress(text);
        }

        String normalized = text
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\r", "");

        Matcher m = Pattern.compile(
                "\\bADDRESS\\b\\s*[:\\-]?\\s*" +
                        "(.{20,400}?)" + 
                        "(?=\\n\\s*(DATE|ACCOUNT|IFSC|MICR)|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(normalized);

        if (m.find()) {
            return clean(m.group(1));
        }

        Matcher pinBlock = Pattern.compile(
                "([A-Za-z0-9,\\-/ ]{20,200}\\b\\d{6}\\b)",
                Pattern.CASE_INSENSITIVE).matcher(text);

        if (pinBlock.find()) {
            return clean(pinBlock.group(1));
        }

        return null;
    }

    /* ---------------- AXIS ---------------- */

    private static String extractAxisCustomerAddress(String text) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder address = new StringBuilder();
        boolean capture = false;

        for (String line : lines) {
            String upper = line.toUpperCase();

            if (upper.contains("JOINT HOLDER")) {
                capture = true;
                continue;
            }

            if (capture) {
                if (upper.contains("IFSC") ||
                        upper.contains("MICR") ||
                        upper.contains("CUSTOMER ID") ||
                        upper.contains("PAN") ||
                        upper.contains("SCHEME")) {
                    break;
                }

                if (line.trim().length() >= 5) {
                    address.append(line.trim()).append(", ");
                }
            }
        }

        if (address.length() == 0)
            return null;

        return clean(address.toString());
    }

    /* ---------------- KOTAK ---------------- */

    private static String extractKotakCustomerAddress(String text) {
        Matcher m = Pattern.compile(
                "ACCOUNT\\s+HOLDER.*?\\n(.{30,200}?\\b\\d{6}\\b)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);

        if (m.find()) {
            return clean(m.group(1));
        }

        return null;
    }

    /* ---------------- CLEANER ---------------- */

    private static String clean(String s) {
        return s
                .replaceAll("[ \t]+", " ")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll(",\\s*$", "")
                .replaceAll("\\b(Date|Period|Statement).*", "")
                .trim();
    }
}