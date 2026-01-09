package com.bqka.pdfservice.ocr.extractors;

import java.util.regex.*;

public class IfscExtractor {

    public static String extract(String text) {

        String normalized = text.toUpperCase()
                .replace('O', '0');

        Pattern p = Pattern.compile("\\b[A-Z]{4}0[A-Z0-9]{6}\\b");
        Matcher m = p.matcher(normalized);

        return m.find() ? m.group() : null;
    }
}