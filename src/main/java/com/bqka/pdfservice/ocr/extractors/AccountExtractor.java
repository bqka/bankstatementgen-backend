package com.bqka.pdfservice.ocr.extractors;

import java.util.regex.*;

public class AccountExtractor {

    public static String extract(String text, String bank) {

        if(bank.equalsIgnoreCase("SBI")){
            return extractSbi(text);
        }

        Matcher labeled = Pattern.compile(
                "ACCOUNT\\s*(NO|NUMBER|NO.)?\\s*[:\\-]?\\s*([\\d\\s]{9,20})",
                Pattern.CASE_INSENSITIVE).matcher(text);

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

    public static String extractSbi(String text) {
        if (text == null)
            return null;

        Matcher m = Pattern.compile(
                "ACCOUNT\\s*(NO|NUMBER)?\\s*[:\\-]?\\s*(0{6})(\\d{11})",
                Pattern.CASE_INSENSITIVE).matcher(text);

        if(m.find()){
            return m.group(3);
        }

        return null;
    }
}