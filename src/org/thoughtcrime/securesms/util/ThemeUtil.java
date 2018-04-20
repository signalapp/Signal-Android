package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.TypedValue;

import org.thoughtcrime.securesms.R;

public class ThemeUtil {

  public static boolean isDarkTheme(@NonNull Context context) {
    return getAttribute(context, R.attr.theme_type, "light").equals("dark");
  }

  private static String getAttribute(Context context, int attribute, String defaultValue) {
    TypedValue outValue = new TypedValue();

    if (context.getTheme().resolveAttribute(attribute, outValue, true)) {
      return outValue.coerceToString().toString();
    } else {
      return defaultValue;
    }
  }

}
