package org.thoughtcrime.securesms.util;

import android.support.annotation.NonNull;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.ShortNumberInfo;

import org.thoughtcrime.securesms.logging.Log;

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
    try {
      PhoneNumberUtil         util              = PhoneNumberUtil.getInstance();
      Phonenumber.PhoneNumber localNumberObject = util.parse(localNumber, null);
      String                  localCountryCode  = util.getRegionCodeForNumber(localNumberObject);
      String                  bareNumber        = number.replaceAll("[^0-9+]", "");

      // libphonenumber seems incorrect for Russia and a few other countries with 4 digit short codes.
      if (bareNumber.length() <= 4 && !SHORT_COUNTRIES.contains(localCountryCode)) {
        return true;
      }

      Phonenumber.PhoneNumber shortCode = util.parse(number, localCountryCode);
      return ShortNumberInfo.getInstance().isPossibleShortNumberForRegion(shortCode, localCountryCode);
    } catch (NumberParseException e) {
      Log.w(TAG, e);
      return false;
    }
  }

}
