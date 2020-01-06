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
package org.thoughtcrime.securesms.phonenumbers;

import android.telephony.PhoneNumberUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberUtil {

  private static final Pattern EMAIL_PATTERN = android.util.Patterns.EMAIL_ADDRESS;
  private static final Pattern PHONE_PATTERN = android.util.Patterns.PHONE;

  public static boolean isValidEmail(String number) {
    Matcher matcher = EMAIL_PATTERN.matcher(number);
    return matcher.matches();
  }

  public static boolean isVisuallyValidNumber(String number) {
    Matcher matcher = PHONE_PATTERN.matcher(number);
    return matcher.matches();
  }

  /**
   * Whether or not a number entered by the user is a valid phone or email address. Differs from
   * {@link #isValidSmsOrEmail(String)} in that it only returns true for numbers that a user would
   * enter themselves, as opposed to the crazy network prefixes that could theoretically be in an
   * SMS address.
   */
  public static boolean isVisuallyValidNumberOrEmail(String number) {
    return isVisuallyValidNumber(number) || isValidEmail(number);
  }

  public static boolean isValidSmsOrEmail(String number) {
    return PhoneNumberUtils.isWellFormedSmsAddress(number) || isValidEmail(number);
  }
}
