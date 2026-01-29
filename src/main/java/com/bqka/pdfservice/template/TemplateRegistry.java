package com.bqka.pdfservice.template;

import com.bqka.pdfservice.model.BankTemplate;
import com.bqka.pdfservice.template.axis.AxisTemplate;
import com.bqka.pdfservice.template.boi.BoiTemplate;
import com.bqka.pdfservice.template.hdfc.HdfcTemplate;
import com.bqka.pdfservice.template.kotak.KotakTemplate;
import com.bqka.pdfservice.template.kotaknew.KotakNewTemplate;
import com.bqka.pdfservice.template.sbi.SbiTemplate;
import com.bqka.pdfservice.template.sbi2.SbiTemplate2;
import com.bqka.pdfservice.template.sbinew.SbiNewTemplate;

import java.util.EnumMap;
import java.util.Map;

public class TemplateRegistry {

  private static final Map<BankTemplate, BankPdfTemplate> MAP =
      new EnumMap<>(BankTemplate.class);

  static {
    MAP.put(BankTemplate.SBI, new SbiTemplate());
    MAP.put(BankTemplate.SBI2, new SbiTemplate2());
    MAP.put(BankTemplate.KOTAK, new KotakTemplate());
    MAP.put(BankTemplate.KOTAKNEW, new KotakNewTemplate());
    MAP.put(BankTemplate.AXIS, new AxisTemplate());
    MAP.put(BankTemplate.SBINEW, new SbiNewTemplate());
    MAP.put(BankTemplate.BOI, new BoiTemplate());
    MAP.put(BankTemplate.HDFC, new HdfcTemplate());
  }

  public static BankPdfTemplate get(BankTemplate template) {
    return MAP.get(template);
  }
}