package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class CyanMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "cyan";

  CyanMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFE0F7FA);
      put("100", 0xFFB2EBF2);
      put("200", 0xFF80DEEA);
      put("300", 0xFF4DD0E1);
      put("400", 0xFF26C6DA);
      put("500", 0xFF00BCD4);
      put("600", 0xFF00ACC1);
      put("700", 0xFF0097A7);
      put("800", 0xFF00838F);
      put("900", 0xFF006064);
      put("A100", 0xFF84FFFF);
      put("A200", 0xFF18FFFF);
      put("A400", 0xFF00E5FF);
      put("A700", 0xFF00B8D4);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
