package org.thoughtcrime.securesms.color;

import android.content.Context;
import android.util.TypedValue;

import org.thoughtcrime.securesms.R;

public enum ThemeType {
  LIGHT("light"), DARK("dark");

  private static final String TAG = ThemeType.class.getSimpleName();

  private final String type;

  private ThemeType(String type) {
    this.type = type;
  }

  public static ThemeType getCurrent(Context context) {
    TypedValue outValue = new TypedValue();
    context.getTheme().resolveAttribute(R.attr.theme_type, outValue, true);

    if ("dark".equals(outValue.coerceToString())) return ThemeType.DARK;
    else                                          return ThemeType.LIGHT;
  }

  @Override
  public String toString() {
    return type;
  }

}
