package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class DeepOrangeMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "deep_orange";

  DeepOrangeMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFFBE9E7);
      put("100", 0xFFFFCCBC);
      put("200", 0xFFFFAB91);
      put("300", 0xFFFF8A65);
      put("400", 0xFFFF7043);
      put("500", 0xFFFF5722);
      put("600", 0xFFF4511E);
      put("700", 0xFFE64A19);
      put("800", 0xFFD84315);
      put("900", 0xFFBF360C);
      put("A100", 0xFFFF9E80);
      put("A200", 0xFFFF6E40);
      put("A400", 0xFFFF3D00);
      put("A700", 0xFFDD2C00);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
