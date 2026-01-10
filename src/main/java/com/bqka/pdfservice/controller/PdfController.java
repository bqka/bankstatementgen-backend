package com.bqka.pdfservice.controller;

import com.bqka.pdfservice.generator.PdfGenerator;
import com.bqka.pdfservice.model.SampleStatements;
import com.bqka.pdfservice.model.Statement;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
public class PdfController {

  @PostMapping("/pdf")
  public ResponseEntity<byte[]> createPdf(@RequestBody Statement statement)
      throws Exception {

    byte[] pdf = PdfGenerator.generate(statement);

    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(statement.meta);
    System.out.println(json);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=statement.pdf")
        .body(pdf);
  }

  @GetMapping(value = "/pdf/preview", produces = "application/pdf")
  public ResponseEntity<byte[]> preview() throws Exception {

    Statement stmt = SampleStatements.kotak();

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"statement-preview.pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(PdfGenerator.generate(stmt));
  }
}