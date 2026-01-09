package com.bqka.pdfservice.template;

import com.bqka.pdfservice.model.Statement;

public interface BankPdfTemplate {
  byte[] generate(Statement stmt) throws Exception;
}