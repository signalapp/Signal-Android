package org.thoughtcrime.securesms.push;


import android.content.Context;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class Censorship {

  public static boolean isCensored(Context context) {
    String localNumber = TextSecurePreferences.getLocalNumber(context);
    return isCensored(localNumber);
  }

  public static boolean isCensored(String localNumber) {
    for (String censoredRegion : BuildConfig.CENSORED_COUNTRIES) {
      if (localNumber.startsWith(censoredRegion)) {
        return true;
      }
    }

    return false;
  }

}
