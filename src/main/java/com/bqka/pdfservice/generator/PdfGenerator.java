package com.bqka.pdfservice.generator;

import com.bqka.pdfservice.model.Statement;
import com.bqka.pdfservice.template.BankPdfTemplate;
import com.bqka.pdfservice.template.TemplateRegistry;

public class PdfGenerator {

  public static byte[] generate(Statement stmt) throws Exception {

    BankPdfTemplate template =
        TemplateRegistry.get(stmt.meta.template);

    if (template == null) {
      throw new IllegalArgumentException(
        "No PDF template registered for bank: " + stmt.meta.template
      );
    }

    return template.generate(stmt);
  }
}