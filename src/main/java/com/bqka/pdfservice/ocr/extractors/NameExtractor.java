package com.bqka.pdfservice.ocr.extractors;

import java.util.regex.*;

public class NameExtractor {

    public static String extract(String text, String bank) {

        Matcher labeled = Pattern.compile(
                "(NAME|CUSTOMER|ACCOUNT HOLDER|ACCOUNT\\s+NAME)\\s*[:\\-]\\s*([^\r\n]+)",
                Pattern.CASE_INSENSITIVE).matcher(text);

        if (labeled.find()) {
            return clean(labeled.group(2));
        }

        // Kotak-style fallback (no label)
        if ("KOTAK".equals(bank)) {
            Matcher m = Pattern.compile(
                    "\\n(MR|MRS|MS)?\\s*([A-Z][A-Z\\s]{5,40})\\n",
                    Pattern.CASE_INSENSITIVE).matcher(text);

            if (m.find()) {
                return clean(m.group(2));
            }
        }

        return null;
    }

    private static String clean(String s) {
        return s.replaceAll("\\s{2,}", " ").trim();
    }
}