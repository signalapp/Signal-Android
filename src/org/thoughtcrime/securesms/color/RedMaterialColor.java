package org.thoughtcrime.securesms.color;

import java.util.HashMap;

public class RedMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "red";

  RedMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFFFEBEE);
      put("100", 0xFFFFCDD2);
      put("200", 0xFFEF9A9A);
      put("300", 0xFFE57373);
      put("400", 0xFFEF5350);
      put("500", 0xFFF44336);
      put("600", 0xFFE53935);
      put("700", 0xFFD32F2F);
      put("800", 0xFFC62828);
      put("900", 0xFFB71C1C);
      put("A100", 0xFFFF8A80);
      put("A200", 0xFFFF5252);
      put("A400", 0xFFFF1744);
      put("A700", 0xFFD50000);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
