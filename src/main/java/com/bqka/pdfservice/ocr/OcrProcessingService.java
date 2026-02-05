package com.bqka.pdfservice.ocr;

import com.bqka.pdfservice.ocr.extractors.AccountExtractor;
import com.bqka.pdfservice.ocr.extractors.AddressExtractor;
import com.bqka.pdfservice.ocr.extractors.BranchAddressExtractor;
import com.bqka.pdfservice.ocr.extractors.BranchExtractor;
import com.bqka.pdfservice.ocr.extractors.IfscExtractor;
import com.bqka.pdfservice.ocr.extractors.NameExtractor;
import com.bqka.pdfservice.ocr.utils.AddressUtils;
import com.bqka.pdfservice.ocr.utils.TextNormalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OcrProcessingService {

    public static OcrResult process(String rawText) {
        String cleaned = TextNormalizer.normalize(
            TextNormalizer.deNoise(rawText)
        );

        OcrResult r = new OcrResult();

        // r.rawText = "";
        r.rawText = cleaned;

        r.ifsc = IfscExtractor.extract(cleaned);
        r.bankName = BankDetector.detectFromIFSC(r.ifsc);
        
        if(r.bankName == null){
            throw new Error("No Bank Name Detected");
        }

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
            Pattern SBI_BLOCK_PATTERN = Pattern.compile(
                "(?is)" +
                    "\\b(\\d{10})\\b\\s*" +
                    "(?::\\s*CIF\\s*Number\\s*)" +
                    "(?::\\s*Account\\s*Number\\s*)" +
                    "(?::\\s*Account\\s*Status\\s*)" +
                    "(?::\\s*Currency\\s*)" +
                    "(?::\\s*IFSC\\s*Code\\s*)" +
                    "(?::\\s*Product\\s*)" +
                    "(?::\\s*Nominee\\s*Name\\s*)" +
                    "\\b(\\d{9,14})\\b\\s*" +
                    "\\b(\\d{9,16})\\b\\s*" +
                    "-\\s*" +
                    "\\b([A-Z]{4}0[A-Z0-9]{6})\\b"
            );
            Matcher sbi = SBI_BLOCK_PATTERN.matcher(cleaned);

            if (sbi.find()) {
                r.branchPhoneNo = sbi.group(1);
                r.customerRelNo = sbi.group(2);
                r.accountNumber = sbi.group(3);
                r.ifsc = sbi.group(4);
                r.accountType = null;
                r.ckycr = extractCkycr(cleaned);

                Matcher m = Pattern.compile(
                    "(?is)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\s*([\\s\\S]*?)\\s*Date\\s+of\\s+Statement"
                ).matcher(cleaned);

                if (m.find()) {
                    r.address = m.group(1).trim();
                }

                m = Pattern.compile(
                    "(?is)2\\.50\\s*%\\s*p\\.a\\.\\s*" +
                        "0\\.00\\s*" +
                        "\\d{2}/\\d{2}/\\d{4}\\s*" +
                        "([\\s\\S]*?)\\s*" +
                        "Branch\\s+Code"
                ).matcher(cleaned);

                if (m.find()) {
                    r.branchAddress = m.group(1).trim();
                }
            }
        } else if (r.bankName.equalsIgnoreCase("KOTAK")) {
            // r.accountType = null;
            // r.address = null;
        } else if (r.bankName.equalsIgnoreCase("AXIS")) {
            r.phoneNumber = extractPhone(cleaned);
            r.pan = extractPan(cleaned);
            r.branch = null;
        } else if (r.bankName.equalsIgnoreCase("HDFC")) {
            Matcher m = Pattern.compile(
                "(?is)city\\s*:\\s*(.*?)\\n+state\\s*:\\s*(.*?)\\n+"
            ).matcher(cleaned);
            if (m.find()) r.city = m.group(1);
            if (m.find()) r.state = m.group(2);

            m = Pattern.compile("(?is)phone no.\\s*:\\s*(.*?)\\n+OD").matcher(
                cleaned
            );
            if (m.find()) r.phoneNumber = m.group(1);
        }

        return r;
    }

    public static String extractPan(String text) {
        if (text == null) return null;

        Matcher m = Pattern.compile(
            "(?i)PAN\\s*:([A-Z]{5}[0-9]{4}[A-Z])"
        ).matcher(text);

        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    public static String extractCkycr(String text) {
        if (text == null) return null;

        Matcher m = Pattern.compile(
            "(?i)ckycr\\s*(number)?\\s*:\\s*([0-9]{14})"
        ).matcher(text);

        if (m.find()) {
            return m.group(2);
        }

        return null;
    }

    public static String extractEmail(String text) {
        if (text == null) return null;

        Matcher m = Pattern.compile(
            "(?i)([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})"
        ).matcher(text);

        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    public static String extractCrn(String text) {
        if (text == null) return null;

        Matcher m = Pattern.compile(
            "(?i)(customer|cust reln|cif|crn|cust)\\s*(id|no\\.?)?\\s*[:\\-]?\s*([xX\\d]{9,11})"
        ).matcher(text);

        if (m.find()) {
            return m.group(3);
        }

        return null;
    }

    public static String extractPhone(String text) {
        if (text == null) return null;

        Matcher m = Pattern.compile(
            "(?i)(registered\\s*)?(mobile|phone)\\s*(no\\.?|number)?\\s*[:\\-]?\\s*([xX\\d]{10})"
        ).matcher(text);

        if (m.find()) {
            return m.group(4);
        }

        return null;
    }

    public static String extractBranchPhone(String text, String bank) {
        if (text == null) return null;

        String normalized = text
            .replaceAll("[ \t]+", " ")
            .replaceAll("\\r", "")
            .toUpperCase();

        if ("AXIS".equalsIgnoreCase(bank)) {
            Matcher m = Pattern.compile(
                "TEL\\s*[:\\-]?\\s*([+0-9()\\s\\-]{8,20})",
                Pattern.CASE_INSENSITIVE
            ).matcher(normalized);

            if (m.find()) {
                return cleanPhone(m.group(1));
            }
        }

        Matcher labeled = Pattern.compile(
            "(PHONE|TEL|CONTACT|BRANCH\\h+PHONE(?:\\h+NUMBER)?)\\h*[:\\-]?\\h*([+0-9()\\s\\-]{8,20})",
            Pattern.CASE_INSENSITIVE
        ).matcher(normalized);

        if (labeled.find()) {
            return cleanPhone(labeled.group(2));
        }

        Matcher loose = Pattern.compile(
            "(?:\\+91\\s*)?(?:\\(?0?\\)?\\s*)?[6-9]\\d{9}",
            Pattern.CASE_INSENSITIVE
        ).matcher(normalized);

        if (loose.find()) {
            return cleanPhone(loose.group());
        }

        return null;
    }

    private static String cleanPhone(String s) {
        if (s == null) return null;

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
        if (text == null) return null;

        String normalized = text
            .replaceAll("[ \t]+", " ")
            .replaceAll("\\r", "")
            .toUpperCase();

        Matcher labeled = Pattern.compile(
            "MICR\\s*(?:CODE|NUMBER)?\\s*[:\\-]?\\s*(\\d{9})",
            Pattern.CASE_INSENSITIVE
        ).matcher(normalized);

        if (labeled.find()) {
            return labeled.group(1);
        }

        Matcher loose = Pattern.compile("\\b\\d{9}\\b").matcher(normalized);

        while (loose.find()) {
            String candidate = loose.group();

            // Reject common false positives
            if (
                !candidate.matches("000000000") &&
                !candidate.matches("111111111")
            ) {
                return candidate;
            }
        }

        return null;
    }

    public static String extractAccountType(String text, String bank) {
        if (text == null) return null;
        
        String normalized = text
            .replaceAll("[ \t]+", " ")
            .replaceAll("\\r", "")
            .toUpperCase();

        if ("AXIS".equals(bank)) {
            Matcher m = Pattern.compile(
                "(SCHEME)\\s*(?:[:\\-]\\s*)?" +
                    "([^\\r\\n]{3,60}?)" +
                    "(?=\\s*CKYC|\\r|\\n|$)",
                Pattern.CASE_INSENSITIVE
            ).matcher(normalized);

            if (m.find()) {
                return cleanAccountType(m.group(2));
            }
        } else if ("KOTAK".equals(bank)) {
            Matcher m = Pattern.compile(
                "\\bAccount\\h+Type\\h+([^\\r\\n]+)",
                Pattern.CASE_INSENSITIVE
            ).matcher(text);

            if (m.find()) {
                String accountType = m.group(1).trim();
                return accountType;
            }
        }


        // 1️⃣ Label-based (highest confidence)
        Matcher labeled = Pattern.compile(
            "(ACCOUNT\\s*(?:TYPE|DESCRIPTION)|A/C\\s*TYPE|SCHEME)\\s*(?:[:\\-]\\s*)?([^\\r\\n]{3,60})",
            Pattern.CASE_INSENSITIVE
        ).matcher(normalized);

        if (labeled.find()) {
            return cleanAccountType(labeled.group(2));
        }

        // 2️⃣ Common Indian account types (fallback)
        Matcher common = Pattern.compile(
            "\\b(SAVINGS?|CURRENT|SALARY|NRE|NRO|OD|OVERDRAFT|CASH\\s*CREDIT|LOAN)\\b",
            Pattern.CASE_INSENSITIVE
        ).matcher(normalized);

        if (common.find()) {
            return cleanAccountType(common.group(1));
        }

        return null;
    }

    private static String cleanAccountType(String s) {
        if (s == null) return null;

        s = s.replaceAll("\\s{2,}", " ").trim();

        // Normalize variants
        if (s.matches("SAVING(S)?")) return "SAVINGS";
        if (s.contains("CURRENT")) return "CURRENT";
        if (s.contains("SALARY")) return "SALARY";
        if (s.contains("NRE")) return "NRE";
        if (s.contains("NRO")) return "NRO";
        if (s.contains("OVERDRAFT") || s.contains("OD")) return "OVERDRAFT";
        if (s.contains("CASH CREDIT")) return "CASH CREDIT";

        return s;
    }
}
