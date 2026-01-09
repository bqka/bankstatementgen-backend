package com.bqka.pdfservice.ocr.extractors;

import java.util.regex.*;

public class AccountExtractor {

    public static String extract(String text, String bank) {

        Matcher labeled = Pattern.compile(
                "ACCOUNT\\s*(NO|NUMBER)?\\s*[:\\-]?\\s*([\\d\\s]{9,20})",
                Pattern.CASE_INSENSITIVE
        ).matcher(text);

        if (labeled.find()) {
            return labeled.group(2).replaceAll("\\s+", "");
        }

        // fallback: longest digit sequence
        Matcher all = Pattern.compile("\\b\\d{9,18}\\b").matcher(text);
        String best = null;

        while (all.find()) {
            String c = all.group();
            if (best == null || c.length() > best.length()) {
                best = c;
            }
        }

        return best;
    }
}