package org.thoughtcrime.securesms.payments.currency;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Currency;
import java.util.Locale;

/**
 * Utility class for interacting with currencies.
 */
public final class CurrencyUtil {

  public static Currency EURO = Currency.getInstance("EUR");

  public static @Nullable Currency getCurrencyByCurrencyCode(@NonNull String currencyCode) {
    try {
      return Currency.getInstance(currencyCode);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public static @Nullable Currency getCurrencyByE164(@NonNull String e164) {
    PhoneNumberUtil         phoneNumberUtil = PhoneNumberUtil.getInstance();
    Phonenumber.PhoneNumber number;

    try {
      number = PhoneNumberUtil.getInstance().parse(e164, "");
    } catch (NumberParseException e) {
      return null;
    }

    String regionCodeForNumber = phoneNumberUtil.getRegionCodeForNumber(number);

    for (Locale l : Locale.getAvailableLocales()) {
      if (l.getCountry().equals(regionCodeForNumber)) {
        return getCurrencyByLocale(l);
      }
    }

    return null;
  }

  public static @Nullable Currency getCurrencyByLocale(@Nullable Locale locale) {
    if (locale == null) {
      return null;
    }

    try {
      return Currency.getInstance(locale);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
