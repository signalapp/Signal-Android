package org.thoughtcrime.securesms.color;

import java.util.HashMap;

public class PinkMaterialColor extends MaterialColor {

  static final String SERIALIZED_NAME = "pink";

  PinkMaterialColor() {
    super(new HashMap<String, Integer>() {{
      put("50", 0xFFFCE4EC);
      put("100", 0xFFF8BBD0);
      put("200", 0xFFF48FB1);
      put("300", 0xFFF06292);
      put("400", 0xFFEC407A);
      put("500", 0xFFE91E63);
      put("600", 0xFFD81B60);
      put("700", 0xFFC2185B);
      put("800", 0xFFAD1457);
      put("900", 0xFF880E4F);
      put("A100", 0xFFFF80AB);
      put("A200", 0xFFFF4081);
      put("A400", 0xFFF50057);
      put("A700", 0xFFC51162);
    }});
  }

  @Override
  public String serialize() {
    return SERIALIZED_NAME;
  }
}
