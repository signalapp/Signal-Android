/*
 * Copyright (C) 2014 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.thoughtcrime.securesms.logsubmit.util;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrub data for possibly sensitive information.
 */
public final class Scrubber {

  private Scrubber() {
  }

  /**
   * The middle group will be censored.
   * Supposedly, the shortest international phone numbers in use contain seven digits.
   * Handles URL encoded +, %2B
   */
  private static final Pattern E164_PATTERN = Pattern.compile("(\\+|%2B)(\\d{5,13})(\\d{2})");
  private static final String  E164_CENSOR  = "*************";

  /**
   * The second group will be censored.
   */
  private static final Pattern CRUDE_EMAIL_PATTERN = Pattern.compile("\\b([^\\s/])([^\\s/]*@[^\\s]+)");
  private static final String  EMAIL_CENSOR        = "...@...";

  /**
   * The middle group will be censored.
   */
  private static final Pattern GROUP_ID_PATTERN = Pattern.compile("(__)(textsecure_group__![^\\s]+)([^\\s]{2})");
  private static final String  GROUP_ID_CENSOR  = "...group...";

  /**
   * The middle group will be censored.
   */
  private static final Pattern UUID_PATTERN = Pattern.compile("(JOB::)?([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{10})([0-9a-f]{2})", Pattern.CASE_INSENSITIVE);
  private static final String  UUID_CENSOR  = "********-****-****-****-**********";

  public static CharSequence scrub(@NonNull CharSequence in) {

    in = scrubE164(in);
    in = scrubEmail(in);
    in = scrubGroups(in);
    in = scrubUuids(in);

    return in;
  }

  private static CharSequence scrubE164(@NonNull CharSequence in) {
    return scrub(in,
                 E164_PATTERN,
                 (matcher, output) -> output.append(matcher.group(1))
                                            .append(E164_CENSOR, 0, matcher.group(2).length())
                                            .append(matcher.group(3)));
  }

  private static CharSequence scrubEmail(@NonNull CharSequence in) {
    return scrub(in,
                 CRUDE_EMAIL_PATTERN,
                 (matcher, output) -> output.append(matcher.group(1))
                                            .append(EMAIL_CENSOR));
  }

  private static CharSequence scrubGroups(@NonNull CharSequence in) {
    return scrub(in,
                 GROUP_ID_PATTERN,
                 (matcher, output) -> output.append(matcher.group(1))
                                            .append(GROUP_ID_CENSOR)
                                            .append(matcher.group(3)));
  }

  private static CharSequence scrubUuids(@NonNull CharSequence in) {
    return scrub(in,
                 UUID_PATTERN,
                 (matcher, output) -> {
                   if (matcher.group(1) != null && !matcher.group(1).isEmpty()) {
                     output.append(matcher.group(1))
                           .append(matcher.group(2))
                           .append(matcher.group(3));
                   } else {
                     output.append(UUID_CENSOR)
                           .append(matcher.group(3));
                   }
                 });
  }

  private static CharSequence scrub(@NonNull CharSequence in, @NonNull Pattern pattern, @NonNull ProcessMatch processMatch) {
    final StringBuilder output  = new StringBuilder(in.length());
    final Matcher matcher = pattern.matcher(in);

    int lastEndingPos = 0;

    while (matcher.find()) {
      output.append(in, lastEndingPos, matcher.start());

      processMatch.scrubMatch(matcher, output);

      lastEndingPos = matcher.end();
    }

    if (lastEndingPos == 0) {
      // there were no matches, save copying all the data
      return in;
    } else {
      output.append(in, lastEndingPos, in.length());

      return output;
    }
  }

  private interface ProcessMatch {
    void scrubMatch(@NonNull Matcher matcher, @NonNull StringBuilder output);
  }
}
