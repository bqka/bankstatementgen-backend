package com.bqka.pdfservice.controller;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bqka.pdfservice.ocr.OcrProcessingService;
import com.bqka.pdfservice.ocr.OcrResult;
import com.bqka.pdfservice.ocr.utils.OcrResponse;
import com.bqka.pdfservice.service.PdfOcrService;

@RestController
@CrossOrigin(value = "*")
@RequestMapping("/ocr")
public class OcrController {
    private final PdfOcrService ocrService;

    public OcrController(PdfOcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OcrResponse> ocrPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password) throws Exception {

        String text;
        String name = file.getOriginalFilename().toLowerCase();
        byte[] bytes = file.getBytes();

        if (name.endsWith(".pdf")) {
            boolean encrypted = PdfOcrService.isEncrypted(new ByteArrayInputStream(bytes), password);

            if (encrypted && password == null) {
                OcrResponse r = OcrResponse.encrypted("PDF is Password Protected");
                return ResponseEntity.ok(r);
            }

            text = ocrService.extractTextFromPdf(bytes, password);

        } else {
            text = ocrService.extractTextFromImage(bytes);
        }

        OcrResult result = OcrProcessingService.process(text);
        result.rawText = "";
        OcrResponse r = OcrResponse.success(result);

        return ResponseEntity.ok(r);
    }

    // OCR a generated test PDF
    @GetMapping("/preview")
    public ResponseEntity<OcrResponse> previewOcr() throws Exception {

        ClassPathResource pdfResource = new ClassPathResource("test/axis.pdf");
        byte[] pdf = pdfResource.getInputStream().readAllBytes();

        boolean encrypted = PdfOcrService.isEncrypted(new ByteArrayInputStream(pdf), null);

        if (encrypted) {
            OcrResponse r = OcrResponse.encrypted("PDF is Password Protected");
            return ResponseEntity.ok(r);
        }

        String text = ocrService.extractTextFromPdf(pdf, null);
        OcrResult result = OcrProcessingService.process(text);
        OcrResponse r = OcrResponse.success(result);

        return ResponseEntity.ok(r);
    }
}
