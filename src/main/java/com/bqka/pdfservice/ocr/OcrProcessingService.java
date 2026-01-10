package com.bqka.pdfservice.ocr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bqka.pdfservice.ocr.extractors.AccountExtractor;
import com.bqka.pdfservice.ocr.extractors.AddressExtractor;
import com.bqka.pdfservice.ocr.extractors.BranchAddressExtractor;
import com.bqka.pdfservice.ocr.extractors.BranchExtractor;
import com.bqka.pdfservice.ocr.extractors.IfscExtractor;
import com.bqka.pdfservice.ocr.extractors.NameExtractor;
import com.bqka.pdfservice.ocr.utils.AddressUtils;
import com.bqka.pdfservice.ocr.utils.TextNormalizer;

public class OcrProcessingService {

    public static OcrResult process(String rawText) {

        String cleaned = TextNormalizer.normalize(
                TextNormalizer.deNoise(rawText));

        OcrResult r = new OcrResult();

        // r.rawText = "";
        r.rawText = rawText;

        r.ifsc = IfscExtractor.extract(cleaned);
        r.bankName = BankDetector.detectFromIFSC(r.ifsc);

        r.name = NameExtractor.extract(cleaned, r.bankName);
        r.accountNumber = AccountExtractor.extract(cleaned, r.bankName);

        r.branchAddress = BranchAddressExtractor.extract(cleaned, r.bankName);
        r.micr = extractMICR(cleaned);
        r.pincode = AddressUtils.extractPincode(r.branchAddress);
        r.address = AddressExtractor.extract(cleaned, r.bankName);
        r.accountType = extractAccountType(cleaned, r.bankName);

        r.branchPhoneNo = extractBranchPhone(cleaned, r.bankName);
        r.branch = BranchExtractor.extract(cleaned, r.bankName);
        r.customerRelNo = extractCrn(cleaned);
        r.email = extractEmail(cleaned);

        if (r.bankName.equalsIgnoreCase("SBI")) {
            r.branchAddress = null;
            r.branchPhoneNo = null;
        } else if (r.bankName.equalsIgnoreCase("KOTAK")) {
            r.accountType = null;
            // r.address = null;
        } else if (r.bankName.equalsIgnoreCase("AXIS")) {
            r.phoneNumber = extractPhone(cleaned);
            r.branch = null;
        }

        return r;
    }

    public static String extractEmail(String text) {
        if (text == null)
            return null;

        Matcher m = Pattern.compile(
                "(?i)(registered\\s*)?(email( id)?)\\s*[:\\-]\\s*([A-Za-z0-9._%+-]{3,20}@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})")
                .matcher(text);

        if (m.find()) {
            return m.group(4);
        }

        return null;
    }

    public static String extractCrn(String text) {
        if (text == null)
            return null;

        Matcher m = Pattern.compile("(?i)(customer|cust reln|cif)\\s*(id|no\\.?)\\s*[:\\-]\s*([xX\\d]{9,11})").matcher(text);

        if (m.find()) {
            return m.group(3);
        }

        return null;
    }

    public static String extractPhone(String text) {
        if (text == null)
            return null;

        Matcher m = Pattern
                .compile("(?i)(registered\\s*)?(mobile|phone)\\s*(no\\.?|number)?\\s*[:\\-]?\\s*([xX\\d]{10})")
                .matcher(text);

        if (m.find()) {
            return m.group(4);
        }

        return null;
    }

    public static String extractBranchPhone(String text, String bank) {
        if (text == null)
            return null;

        String normalized = text
                .replaceAll("[ \t]+", " ")
                .replaceAll("\\r", "")
                .toUpperCase();

        if ("AXIS".equalsIgnoreCase(bank)) {
            Matcher m = Pattern.compile(
                    "TEL\\s*[:\\-]?\\s*([+0-9()\\s\\-]{8,20})",
                    Pattern.CASE_INSENSITIVE).matcher(normalized);

            if (m.find()) {
                return cleanPhone(m.group(1));
            }
        }

        Matcher labeled = Pattern.compile(
                "(PHONE|TEL|CONTACT|BRANCH\\s+PHONE)\\s*[:\\-]?\\s*([+0-9()\\s\\-]{8,20})",
                Pattern.CASE_INSENSITIVE).matcher(normalized);

        if (labeled.find()) {
            return cleanPhone(labeled.group(2));
        }

        Matcher loose = Pattern.compile(
                "(?:\\+91\\s*)?(?:\\(?0?\\)?\\s*)?[6-9]\\d{9}",
                Pattern.CASE_INSENSITIVE).matcher(normalized);

        if (loose.find()) {
            return cleanPhone(loose.group());
        }

        return null;
    }

    private static String cleanPhone(String s) {
        if (s == null)
            return null;

        String digits = s.replaceAll("[^0-9+]", "");

        // Normalize Indian numbers
        if (digits.startsWith("91") && digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }

        if (digits.length() == 10) {
            return digits;
        }

        return null;
    }

    public static String extractMICR(String text) {
        if (text == null)
            return null;

        String normalized = text
                .replaceAll("[ \t]+", " ")
                .replaceAll("\\r", "")
                .toUpperCase();

        Matcher labeled = Pattern.compile(
                "MICR\\s*(?:CODE|NUMBER)?\\s*[:\\-]?\\s*(\\d{9})",
                Pattern.CASE_INSENSITIVE).matcher(normalized);

        if (labeled.find()) {
            return labeled.group(1);
        }

        Matcher loose = Pattern.compile(
                "\\b\\d{9}\\b").matcher(normalized);

        while (loose.find()) {
            String candidate = loose.group();

            // Reject common false positives
            if (!candidate.matches("000000000") &&
                    !candidate.matches("111111111")) {
                return candidate;
            }
        }

        return null;
    }

    public static String extractAccountType(String text, String bank) {
        if (text == null)
            return null;

        if ("AXIS".equals(bank)) {
            Matcher m = Pattern.compile(
                    "(SCHEME)\\s*(?:[:\\-]\\s*)?" +
                            "([^\\r\\n]{3,60}?)" +
                            "(?=\\s*CKYC|\\r|\\n|$)",
                    Pattern.CASE_INSENSITIVE).matcher(text);

            if (m.find()) {
                return cleanAccountType(m.group(2));
            }
        }

        String normalized = text
                .replaceAll("[ \t]+", " ")
                .replaceAll("\\r", "")
                .toUpperCase();

        // 1️⃣ Label-based (highest confidence)
        Matcher labeled = Pattern.compile(
                "(ACCOUNT\\s*(?:TYPE|DESCRIPTION)|A/C\\s*TYPE|SCHEME)\\s*(?:[:\\-]\\s*)?([^\\r\\n]{3,60})",
                Pattern.CASE_INSENSITIVE).matcher(normalized);

        if (labeled.find()) {
            return cleanAccountType(labeled.group(2));
        }

        // 2️⃣ Common Indian account types (fallback)
        Matcher common = Pattern.compile(
                "\\b(SAVINGS?|CURRENT|SALARY|NRE|NRO|OD|OVERDRAFT|CASH\\s*CREDIT|LOAN)\\b",
                Pattern.CASE_INSENSITIVE).matcher(normalized);

        if (common.find()) {
            return cleanAccountType(common.group(1));
        }

        return null;
    }

    private static String cleanAccountType(String s) {
        if (s == null)
            return null;

        s = s.replaceAll("\\s{2,}", " ").trim();

        // Normalize variants
        if (s.matches("SAVING(S)?"))
            return "SAVINGS";
        if (s.contains("CURRENT"))
            return "CURRENT";
        if (s.contains("SALARY"))
            return "SALARY";
        if (s.contains("NRE"))
            return "NRE";
        if (s.contains("NRO"))
            return "NRO";
        if (s.contains("OVERDRAFT") || s.contains("OD"))
            return "OVERDRAFT";
        if (s.contains("CASH CREDIT"))
            return "CASH CREDIT";

        return s;
    }
}