///*
// * Copyright (C) 2011 Whisper Systems
// *
// * This program is free software: you can redistribute it and/or modify
// * it under the terms of the GNU General Public License as published by
// * the Free Software Foundation, either version 3 of the License, or
// * (at your option) any later version.
// *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// * GNU General Public License for more details.
// *
// * You should have received a copy of the GNU General Public License
// * along with this program.  If not, see <http://www.gnu.org/licenses/>.
// */
//
//package org.thoughtcrime.redphone.util;
//
//import android.content.Context;
//import android.content.SharedPreferences;
//import android.preference.PreferenceManager;
//import android.util.Log;
//
//import com.google.i18n.phonenumbers.NumberParseException;
//import com.google.i18n.phonenumbers.PhoneNumberUtil;
//import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
//import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
//
//import org.thoughtcrime.redphone.ApplicationContext;
//import org.thoughtcrime.redphone.Constants;
//
//import java.util.Locale;
//
///**
// * Phone number formats are a pain.
// *
// * @author Moxie Marlinspike
// *
// */
//public class PhoneNumberFormatter {
//
//  public static boolean isValidNumber(String number) {
//    return number.matches("^\\+[0-9]{10,}");
//  }
//
//  private static String impreciseFormatNumber(String number, String localNumber) {
//    number = number.replaceAll("[^0-9+]", "");
//
//    if (number.charAt(0) == '+')
//      return number;
//
//    if (localNumber.charAt(0) == '+')
//      localNumber = localNumber.substring(1);
//
//    if (localNumber.length() == number.length() || number.length() > localNumber.length())
//      return "+" + number;
//
//    int difference = localNumber.length() - number.length();
//
//    return "+" + localNumber.substring(0, difference) + number;
//  }
//
//  public static String formatNumberInternational(String number) {
//    try {
//      PhoneNumberUtil util     = PhoneNumberUtil.getInstance();
//      PhoneNumber parsedNumber = util.parse(number, null);
//      return util.format(parsedNumber, PhoneNumberFormat.INTERNATIONAL);
//    } catch (NumberParseException e) {
//      Log.w("PhoneNumberFormatter", e);
//      return number;
//    }
//  }
//
//  public static String formatNumber(Context context, String number) {
//    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
//    String localNumber            = preferences.getString(Constants.NUMBER_PREFERENCE, "No Stored Number");
//    number                        = number.replaceAll("[^0-9+]", "");
//
//    if (Util.isEmpty(number))
//      return number;
//
//    if (number.charAt(0) == '+')
//      return number;
//
//    try {
//      PhoneNumberUtil util          = PhoneNumberUtil.getInstance();
//      PhoneNumber localNumberObject = util.parse(localNumber, null);
//
//      String localCountryCode       = util.getRegionCodeForNumber(localNumberObject);
//      Log.w("PhoneNumberFormatter", "Got local CC: " + localCountryCode);
//
//      PhoneNumber numberObject      = util.parse(number, localCountryCode);
//      return util.format(numberObject, PhoneNumberFormat.E164);
//    } catch (NumberParseException e) {
//      Log.w("PhoneNumberFormatter", e);
//      return impreciseFormatNumber(number, localNumber);
//    }
//  }
//
//  public static String formatNumber(String number) {
//    return formatNumber(ApplicationContext.getInstance().getContext(), number);
//  }
//
//  public static String getRegionDisplayName(String regionCode) {
//    return (regionCode == null || regionCode.equals("ZZ") || regionCode.equals(PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY))
//          ? "Unknown country" : new Locale("", regionCode).getDisplayCountry(Locale.getDefault());
//  }
//
//  public static String formatE164(String countryCode, String number) {
//    try {
//      PhoneNumberUtil util     = PhoneNumberUtil.getInstance();
//      int parsedCountryCode    = Integer.parseInt(countryCode);
//      PhoneNumber parsedNumber = util.parse(number,
//                                            util.getRegionCodeForCountryCode(parsedCountryCode));
//
//      return util.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
//    } catch (NumberParseException npe) {
//      Log.w("CreateAccountActivity", npe);
//    } catch (NumberFormatException nfe) {
//      Log.w("CreateAccountActivity", nfe);
//    }
//
//    return "+"                                                     +
//        countryCode.replaceAll("[^0-9]", "").replaceAll("^0*", "") +
//        number.replaceAll("[^0-9]", "");
//  }
//
//  public static String getInternationalFormatFromE164(String e164number) {
//    try {
//      PhoneNumberUtil util     = PhoneNumberUtil.getInstance();
//      PhoneNumber parsedNumber = util.parse(e164number, null);
//      return util.format(parsedNumber, PhoneNumberFormat.INTERNATIONAL);
//    } catch (NumberParseException e) {
//      Log.w("PhoneNumberFormatter", e);
//      return e164number;
//    }
//  }
//
//
//}
