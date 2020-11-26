/**
 * Copyright (C) 2012 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.util;

import android.telephony.PhoneNumberUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberUtil {

  private static final Pattern emailPattern = android.util.Patterns.EMAIL_ADDRESS;

  public static boolean isValidEmail(String number) {
    Matcher matcher = emailPattern.matcher(number);
    return matcher.matches();
  }

  public static boolean isValidSmsOrEmail(String number) {
    return PhoneNumberUtils.isWellFormedSmsAddress(number) || isValidEmail(number);
  }

//  public static boolean isValidSmsOrEmailOrGroup(String number) {
//    return PhoneNumberUtils.isWellFormedSmsAddress(number) ||
//        isValidEmail(number) ||
//        GroupUtil.isEncodedGroup(number);
//  }
//
//  public static String filterNumber(String number) {
//    if (number == null) return null;
//
//    int length            = number.length();
//    StringBuilder builder = new StringBuilder(length);
//
//    for (int i = 0; i < length; i++) {
//      char character = number.charAt(i);
//
//      if (Character.isDigit(character) || character == '+')
//        builder.append(character);
//    }
//
//    return builder.toString();
//  }
}
