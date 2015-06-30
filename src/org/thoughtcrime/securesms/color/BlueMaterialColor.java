package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class BlueMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "blue";

  BlueMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFE3F2FD);
      put("100", 0xFFBBDEFB);
      put("200", 0xFF90CAF9);
      put("300", 0xFF64B5F6);
      put("400", 0xFF42A5F5);
      put("500", 0xFF2196F3);
      put("600", 0xFF1E88E5);
      put("700", 0xFF1976D2);
      put("800", 0xFF1565C0);
      put("900", 0xFF0D47A1);
      put("A100", 0xFF82B1FF);
      put("A200", 0xFF448AFF);
      put("A400", 0xFF2979FF);
      put("A700", 0xFF2962FF);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
