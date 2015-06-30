package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class GreenMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "green";

  GreenMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFE8F5E9);
      put("100", 0xFFC8E6C9);
      put("200", 0xFFA5D6A7);
      put("300", 0xFF81C784);
      put("400", 0xFF66BB6A);
      put("500", 0xFF4CAF50);
      put("600", 0xFF43A047);
      put("700", 0xFF388E3C);
      put("800", 0xFF2E7D32);
      put("900", 0xFF1B5E20);
      put("A100", 0xFFB9F6CA);
      put("A200", 0xFF69F0AE);
      put("A400", 0xFF00E676);
      put("A700", 0xFF00C853);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
