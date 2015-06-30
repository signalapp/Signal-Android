package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class LightBlueMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "light_blue";

  LightBlueMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFE1F5FE);
      put("100", 0xFFB3E5FC);
      put("200", 0xFF81D4FA);
      put("300", 0xFF4FC3F7);
      put("400", 0xFF29B6F6);
      put("500", 0xFF03A9F4);
      put("600", 0xFF039BE5);
      put("700", 0xFF0288D1);
      put("800", 0xFF0277BD);
      put("900", 0xFF01579B);
      put("A100", 0xFF80D8FF);
      put("A200", 0xFF40C4FF);
      put("A400", 0xFF00B0FF);
      put("A700", 0xFF0091EA);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
