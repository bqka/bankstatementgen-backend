package com.bqka.pdfservice.controller;

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
    public ResponseEntity<OcrResult> ocrPdf(
            @RequestParam("file") MultipartFile file) throws Exception {

        String text;
        String name = file.getOriginalFilename().toLowerCase();

        if (name.endsWith(".pdf")) {
            text = ocrService.extractTextFromPdf(file.getBytes());
        } else {
            text = ocrService.extractTextFromImage(file.getBytes());
        }

        OcrResult result = OcrProcessingService.process(text);
        result.rawText = "";

        return ResponseEntity.ok(result);
    }

    // OCR a generated test PDF
    @GetMapping("/preview")
    public ResponseEntity<OcrResult> previewOcr() throws Exception {

        ClassPathResource pdfResource = new ClassPathResource("test/axis.pdf");
        byte[] pdf = pdfResource.getInputStream().readAllBytes();

        String text = ocrService.extractTextFromPdf(pdf);
        OcrResult result = OcrProcessingService.process(text);

        return ResponseEntity.ok(result);
    }
}
