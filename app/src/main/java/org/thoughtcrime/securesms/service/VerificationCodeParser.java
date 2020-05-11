/**
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.service;

import android.content.Context;

import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerificationCodeParser {

  private static final Pattern CHALLENGE_PATTERN = Pattern.compile(".*Your (Signal|TextSecure) verification code:? ([0-9]{3,4})-([0-9]{3,4}).*", Pattern.DOTALL);

  public static Optional<String> parse(Context context, String messageBody) {
    if (messageBody == null) {
      return Optional.absent();
    }

    Matcher challengeMatcher = CHALLENGE_PATTERN.matcher(messageBody);

    if (!challengeMatcher.matches() || !TextSecurePreferences.isVerifying(context)) {
      return Optional.absent();
    }

    return Optional.of(challengeMatcher.group(2) + challengeMatcher.group(3));
  }
}
