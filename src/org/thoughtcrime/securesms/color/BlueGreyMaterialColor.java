package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class BlueGreyMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "blue_grey";

  BlueGreyMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFECEFF1);
      put("100", 0xFFCFD8DC);
      put("200", 0xFFB0BEC5);
      put("300", 0xFF90A4AE);
      put("400", 0xFF78909C);
      put("500", 0xFF607D8B);
      put("600", 0xFF546E7A);
      put("700", 0xFF455A64);
      put("800", 0xFF37474F);
      put("900", 0xFF263238);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
