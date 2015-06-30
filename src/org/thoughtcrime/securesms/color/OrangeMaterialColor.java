package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class OrangeMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "orange";

  OrangeMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFFFF3E0);
      put("100", 0xFFFFE0B2);
      put("200", 0xFFFFCC80);
      put("300", 0xFFFFB74D);
      put("400", 0xFFFFA726);
      put("500", 0xFFFF9800);
      put("600", 0xFFFB8C00);
      put("700", 0xFFF57C00);
      put("800", 0xFFEF6C00);
      put("900", 0xFFE65100);
      put("A100", 0xFFFFD180);
      put("A200", 0xFFFFAB40);
      put("A400", 0xFFFF9100);
      put("A700", 0xFFFF6D00);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
