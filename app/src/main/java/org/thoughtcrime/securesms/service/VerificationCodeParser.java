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




import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerificationCodeParser {

  private static final Pattern CHALLENGE_PATTERN = Pattern.compile("(.*\\D|^)([0-9]{3,4})-?([0-9]{3,4}).*", Pattern.DOTALL);

  public static Optional<String> parse(String messageBody) {
    if (messageBody == null) {
      return Optional.empty();
    }

    Matcher challengeMatcher = CHALLENGE_PATTERN.matcher(messageBody);

    if (!challengeMatcher.matches()) {
      return Optional.empty();
    }

    return Optional.of(challengeMatcher.group(challengeMatcher.groupCount() - 1) +
                       challengeMatcher.group(challengeMatcher.groupCount()));
  }
}
