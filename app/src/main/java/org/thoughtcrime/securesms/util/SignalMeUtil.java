package org.thoughtcrime.securesms.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SignalMeUtil {
  private static final String  HOST             = "^(https|sgnl)://" + "signal\\.me";
  private static final Pattern E164_PATTERN     = Pattern.compile(HOST + "/#p/(\\+[0-9]+)$");
  private static final Pattern USERNAME_PATTERN = Pattern.compile(HOST + "/#u/(.+)$");

  /**
   * If this is a valid signal.me link and has a valid e164, it will return the e164. Otherwise, it will return null.
   */
  public static @Nullable String parseE164FromLink(@NonNull Context context, @Nullable String link) {
    if (Util.isEmpty(link)) {
      return null;
    }

    Matcher matcher = E164_PATTERN.matcher(link);

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

  /**
   * If this is a valid signal.me link and has a valid username, it will return the username. Otherwise, it will return null.
   */
  public static @Nullable String parseUsernameFromLink(@Nullable String link) {
    if (Util.isEmpty(link)) {
      return null;
    }

    Matcher matcher = USERNAME_PATTERN.matcher(link);

    if (matcher.matches()) {
      String username = matcher.group(2);
      try {
        return username == null || username.isEmpty() ? null : URLDecoder.decode(username, StandardCharsets.UTF_8.toString());
      } catch (UnsupportedEncodingException e) {
        return null;
      }
    } else {
      return null;
    }
  }
}
