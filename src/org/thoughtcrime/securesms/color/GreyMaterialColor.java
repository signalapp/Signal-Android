package org.thoughtcrime.securesms.color;

import java.util.HashMap;

public class GreyMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "grey";

  GreyMaterialColor() {
    super(new HashMap<String, Integer>(){{
      put("50", 0xFFFAFAFA);
      put("100", 0xFFF5F5F5);
      put("200", 0xFFEEEEEE);
      put("300", 0xFFE0E0E0);
      put("400", 0xFFBDBDBD);
      put("500", 0xFF9E9E9E);
      put("600", 0xFF757575);
      put("700", 0xFF616161);
      put("800", 0xFF424242);
      put("900", 0xFF212121);
    }});
  }
  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
