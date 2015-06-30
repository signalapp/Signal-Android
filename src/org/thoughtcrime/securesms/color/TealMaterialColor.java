package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class TealMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "teal";

  TealMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFE0F2F1);
      put("100", 0xFFB2DFDB);
      put("200", 0xFF80CBC4);
      put("300", 0xFF4DB6AC);
      put("400", 0xFF26A69A);
      put("500", 0xFF009688);
      put("600", 0xFF00897B);
      put("700", 0xFF00796B);
      put("800", 0xFF00695C);
      put("900", 0xFF004D40);
      put("A100", 0xFFA7FFEB);
      put("A200", 0xFF64FFDA);
      put("A400", 0xFF1DE9B6);
      put("A700", 0xFF00BFA5);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
