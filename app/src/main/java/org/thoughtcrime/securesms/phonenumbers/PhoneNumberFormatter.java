package org.thoughtcrime.securesms.phonenumbers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.google.i18n.phonenumbers.ShortNumberInfo;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.signal.core.util.SetUtil;
import org.signal.core.util.StringUtil;
import org.thoughtcrime.securesms.util.Util;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhoneNumberFormatter {

  private static final String TAG = Log.tag(PhoneNumberFormatter.class);

  private static final String UNKNOWN_NUMBER = "Unknown";

  private static final Set<String>  EXCLUDE_FROM_MANUAL_SHORTCODE_4 = SetUtil.newHashSet("AC", "NC", "NU", "TK");
  private static final Set<String>  MANUAL_SHORTCODE_6              = SetUtil.newHashSet("DE", "FI", "GB", "SK");
  private static final Set<Integer> NATIONAL_FORMAT_COUNTRY_CODES   = SetUtil.newHashSet(1 /*US*/, 44 /*UK*/);

  private static final Pattern US_NO_AREACODE = Pattern.compile("^(\\d{7})$");
  private static final Pattern BR_NO_AREACODE = Pattern.compile("^(9?\\d{8})$");

  private static final AtomicReference<Pair<String, PhoneNumberFormatter>> cachedFormatter = new AtomicReference<>();

  private final Optional<PhoneNumber> localNumber;
  private final String                localCountryCode;

  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
  private final Pattern         ALPHA_PATTERN   = Pattern.compile("[a-zA-Z]");

  public static @NonNull PhoneNumberFormatter get(Context context) {
    String localNumber = SignalStore.account().getE164();

    if (!Util.isEmpty(localNumber)) {
      Pair<String, PhoneNumberFormatter> cached = cachedFormatter.get();

      if (cached != null && cached.first().equals(localNumber)) return cached.second();

      PhoneNumberFormatter formatter = new PhoneNumberFormatter(localNumber);
      cachedFormatter.set(new Pair<>(localNumber, formatter));

      return formatter;
    } else {
      return new PhoneNumberFormatter(Util.getSimCountryIso(context).orElse("US"), true);
    }
  }

  PhoneNumberFormatter(@NonNull String localNumberString) {
    try {
      Phonenumber.PhoneNumber libNumber   = phoneNumberUtil.parse(localNumberString, null);
      int                     countryCode = libNumber.getCountryCode();

      this.localNumber       = Optional.of(new PhoneNumber(localNumberString, countryCode, parseAreaCode(localNumberString, countryCode)));
      this.localCountryCode  = phoneNumberUtil.getRegionCodeForNumber(libNumber);
    } catch (NumberParseException e) {
      throw new AssertionError(e);
    }
  }

  PhoneNumberFormatter(@NonNull String localCountryCode, boolean countryCode) {
    this.localNumber      = Optional.empty();
    this.localCountryCode = localCountryCode;
  }

  public static @NonNull String prettyPrint(@NonNull String e164) {
    return StringUtil.forceLtr(get(AppDependencies.getApplication()).prettyPrintFormat(e164));
  }

  public @NonNull String prettyPrintFormat(@NonNull String e164) {
    try {
      Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(e164, localCountryCode);

      if (localNumber.isPresent()                                        &&
          localNumber.get().countryCode == parsedNumber.getCountryCode() &&
          NATIONAL_FORMAT_COUNTRY_CODES.contains(localNumber.get().getCountryCode()))
      {
        return StringUtil.isolateBidi(phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL));
      } else {
        return StringUtil.isolateBidi(phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL));
      }
    } catch (NumberParseException e) {
      Log.w(TAG, "Failed to format number: " + e.toString());
      return StringUtil.isolateBidi(e164);
    }
  }

  public static int getLocalCountryCode() {
    Optional<PhoneNumber> localNumber = get(AppDependencies.getApplication()).localNumber;
    return localNumber != null && localNumber.isPresent() ? localNumber.get().countryCode : 0;
  }

  public @Nullable String formatOrNull(@Nullable String number) {
    String formatted = format(number);
    if (formatted.equals(UNKNOWN_NUMBER)) {
      return null;
    }
    return formatted;
  }


  public @NonNull String format(@Nullable String number) {
    if (number == null)                       return UNKNOWN_NUMBER;
    if (GroupId.isEncodedGroup(number))       return number;
    if (ALPHA_PATTERN.matcher(number).find()) return number.trim();

    String bareNumber = number.replaceAll("[^0-9+]", "");

    if (bareNumber.length() == 0) {
      if (number.trim().length() == 0) return "Unknown";
      else                             return number.trim();
    }

    if (bareNumber.length() <= 6 && MANUAL_SHORTCODE_6.contains(localCountryCode)) {
      return bareNumber;
    }

    if (bareNumber.length() <= 4 && !EXCLUDE_FROM_MANUAL_SHORTCODE_4.contains(localCountryCode)) {
      return bareNumber;
    }

    if (isShortCode(bareNumber, localCountryCode)) {
      Log.i(TAG, "Recognized number as short code.");
      return bareNumber;
    }

    String processedNumber = applyAreaCodeRules(localNumber, bareNumber);

    try {
      Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(processedNumber, localCountryCode);
      String                  formatted    = phoneNumberUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164);

      if (formatted.startsWith("+")) {
        return formatted;
      } else {
        throw new NumberParseException(NumberParseException.ErrorType.NOT_A_NUMBER, "After formatting, the number did not start with +! hasRawInput: " + parsedNumber.hasRawInput());
      }
    } catch (NumberParseException e) {
      Log.w(TAG, e.toString());
      if (bareNumber.charAt(0) == '+') {
        return bareNumber;
      }

      if (localNumber.isPresent()) {
        String localNumberImprecise = localNumber.get().getE164Number();

        if (localNumberImprecise.charAt(0) == '+') {
          localNumberImprecise = localNumberImprecise.substring(1);
        }

        if (localNumberImprecise.length() == bareNumber.length() || bareNumber.length() > localNumberImprecise.length()) {
          return "+" + number;
        }

        int difference = localNumberImprecise.length() - bareNumber.length();

        return "+" + localNumberImprecise.substring(0, difference) + bareNumber;
      } else {
        String countryCode = String.valueOf(phoneNumberUtil.getCountryCodeForRegion(localCountryCode));
        return "+" + (bareNumber.startsWith(countryCode) ? bareNumber : countryCode + bareNumber);
      }
    }
  }

  private boolean isShortCode(@NonNull String bareNumber, String localCountryCode) {
    try {
      Phonenumber.PhoneNumber parsedNumber = phoneNumberUtil.parse(bareNumber, localCountryCode);
      return ShortNumberInfo.getInstance().isValidShortNumberForRegion(parsedNumber, localCountryCode);
    } catch (NumberParseException e) {
      return false;
    }
  }

  private @Nullable String parseAreaCode(@NonNull String e164Number, int countryCode) {
    switch (countryCode) {
      case 1:
        return e164Number.substring(2, 5);
      case 55:
        return e164Number.substring(3, 5);
    }
    return null;
  }


  private @NonNull String applyAreaCodeRules(@NonNull Optional<PhoneNumber> localNumber, @NonNull String testNumber) {
    if (!localNumber.isPresent() || !localNumber.get().getAreaCode().isPresent()) {
      return testNumber;
    }

    Matcher matcher;
    switch (localNumber.get().getCountryCode()) {
      case 1:
        matcher = US_NO_AREACODE.matcher(testNumber);
        if (matcher.matches()) {
          return localNumber.get().getAreaCode() + matcher.group();
        }
        break;

      case 55:
        matcher = BR_NO_AREACODE.matcher(testNumber);
        if (matcher.matches()) {
          return localNumber.get().getAreaCode() + matcher.group();
        }
    }
    return testNumber;
  }

  private static class PhoneNumber {
    private final String           e164Number;
    private final int              countryCode;
    private final Optional<String> areaCode;

    PhoneNumber(String e164Number, int countryCode, @Nullable String areaCode) {
      this.e164Number  = e164Number;
      this.countryCode = countryCode;
      this.areaCode    = Optional.ofNullable(areaCode);
    }

    String getE164Number() {
      return e164Number;
    }

    int getCountryCode() {
      return countryCode;
    }

    Optional<String> getAreaCode() {
      return areaCode;
    }
  }
}
