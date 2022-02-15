package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SignalMeUtil {
  private static final String  HOST         = "signal.me";
  private static final Pattern HOST_PATTERN = Pattern.compile("^(https|sgnl)://" + HOST + "/#p/(\\+[0-9]+)$");

  /**
   * If this is a valid signal.me link and has a valid e164, it will return the e164. Otherwise, it will return null.
   */
  public static @Nullable String parseE164FromLink(@NonNull Context context, @Nullable String link) {
    if (Util.isEmpty(link)) {
      return null;
    }

    Matcher matcher = HOST_PATTERN.matcher(link);

    if (matcher.matches()) {
      String e164 = matcher.group(2);

      if (PhoneNumberUtil.getInstance().isPossibleNumber(e164, Locale.getDefault().getCountry())) {
        return PhoneNumberFormatter.get(context).format(e164);
      } else {
        return null;
      }
    } else {
      return null;
    }
  }
}
