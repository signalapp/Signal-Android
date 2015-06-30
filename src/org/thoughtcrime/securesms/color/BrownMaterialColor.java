package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class BrownMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "brown";

  BrownMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFEFEBE9);
      put("100", 0xFFD7CCC8);
      put("200", 0xFFBCAAA4);
      put("300", 0xFFA1887F);
      put("400", 0xFF8D6E63);
      put("500", 0xFF795548);
      put("600", 0xFF6D4C41);
      put("700", 0xFF5D4037);
      put("800", 0xFF4E342E);
      put("900", 0xFF3E2723);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
