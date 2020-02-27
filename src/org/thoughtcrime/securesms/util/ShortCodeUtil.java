package org.thoughtcrime.securesms.util;

import android.support.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;

public class ShortCodeUtil {

  private static final String TAG = ShortCodeUtil.class.getSimpleName();

  private static final Set<String> SHORT_COUNTRIES = new HashSet<String>() {{
    add("NU");
    add("TK");
    add("NC");
    add("AC");
  }};

  public static boolean isShortCode(@NonNull String localNumber, @NonNull String number) {
    return false;
  }

}
