package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class PurpleMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "purple";

  PurpleMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFF3E5F5);
      put("100", 0xFFE1BEE7);
      put("200", 0xFFCE93D8);
      put("300", 0xFFBA68C8);
      put("400", 0xFFAB47BC);
      put("500", 0xFF9C27B0);
      put("600", 0xFF8E24AA);
      put("700", 0xFF7B1FA2);
      put("800", 0xFF6A1B9A);
      put("900", 0xFF4A148C);
      put("A100", 0xFFEA80FC);
      put("A200", 0xFFE040FB);
      put("A400", 0xFFD500F9);
      put("A700", 0xFFAA00FF);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
