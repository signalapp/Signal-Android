package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class DeepPurpleMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "deep_purple";

  DeepPurpleMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50",0xFFEDE7F6);
      put("100", 0xFFD1C4E9);
      put("200", 0xFFB39DDB);
      put("300", 0xFF9575CD);
      put("400", 0xFF7E57C2);
      put("500", 0xFF673AB7);
      put("600", 0xFF5E35B1);
      put("700", 0xFF512DA8);
      put("800", 0xFF4527A0);
      put("900", 0xFF311B92);
      put("A100", 0xFFB388FF);
      put("A200", 0xFF7C4DFF);
      put("A400", 0xFF651FFF);
      put("A700", 0xFF6200EA);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
