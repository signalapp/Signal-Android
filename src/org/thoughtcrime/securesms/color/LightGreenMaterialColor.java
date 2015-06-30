package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class LightGreenMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "light_green";

  LightGreenMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFF1F8E9);
      put("100", 0xFFDCEDC8);
      put("200", 0xFFC5E1A5);
      put("300", 0xFFAED581);
      put("400", 0xFF9CCC65);
      put("500", 0xFF8BC34A);
      put("600", 0xFF7CB342);
      put("700", 0xFF689F38);
      put("800", 0xFF558B2F);
      put("900", 0xFF33691E);
      put("A100", 0xFFCCFF90);
      put("A200", 0xFFB2FF59);
      put("A400", 0xFF76FF03);
      put("A700", 0xFF64DD17);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
