package org.thoughtcrime.securesms.color;

import java.util.HashMap;

class IndigoMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "indigo";

  IndigoMaterialColor() {
    super(new HashMap<String, Integer>(){{
      put("50",  0xFFE8EAF6);
      put("100",  0xFFC5CAE9);
      put("200",  0xFF9FA8DA);
      put("300",  0xFF7986CB);
      put("400",  0xFF5C6BC0);
      put("500",  0xFF3F51B5);
      put("600",  0xFF3949AB);
      put("700",  0xFF303F9F);
      put("800",  0xFF283593);
      put("900",  0xFF1A237E);
      put("A100",  0xFF8C9EFF);
      put("A200",  0xFF536DFE);
      put("A400",  0xFF3D5AFE);
      put("A700",  0xFF304FFE);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
