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

package org.signal.core.util.logging;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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
  private static final Pattern GROUP_ID_V1_PATTERN = Pattern.compile("(__)(textsecure_group__![^\\s]+)([^\\s]{2})");
  private static final String  GROUP_ID_V1_CENSOR = "...group...";

  /**
   * The middle group will be censored.
   */
  private static final Pattern GROUP_ID_V2_PATTERN = Pattern.compile("(__)(signal_group__v2__![^\\s]+)([^\\s]{2})");
  private static final String  GROUP_ID_V2_CENSOR  = "...group_v2...";

  /**
   * The middle group will be censored.
   */
  private static final Pattern UUID_PATTERN = Pattern.compile("(JOB::)?([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{9})([0-9a-f]{3})", Pattern.CASE_INSENSITIVE);
  private static final String  UUID_CENSOR  = "********-****-****-****-*********";

  /**
   * The entire string is censored.
   */
  private static final Pattern IPV4_PATTERN = Pattern.compile("\\b" +
                                                              "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                                                              "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                                                              "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                                                              "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
                                                              "\\b");
  private static final String IPV4_CENSOR   = "...ipv4...";

  /**
   * The domain name except for TLD will be censored.
   */
  private static final Pattern     DOMAIN_PATTERN = Pattern.compile("([a-z0-9]+\\.)+([a-z0-9\\-]*[a-z\\-][a-z0-9\\-]*)", Pattern.CASE_INSENSITIVE);
  private static final String      DOMAIN_CENSOR  = "***.";
  private static final Set<String> TOP_100_TLDS   = new HashSet<>(Arrays.asList("com", "net", "org", "jp", "de", "uk", "fr", "br", "it", "ru", "es", "me", "gov", "pl", "ca", "au", "cn", "co", "in",
                                                                                "nl", "edu", "info", "eu", "ch", "id", "at", "kr", "cz", "mx", "be", "tv", "se", "tr", "tw", "al", "ua", "ir", "vn",
                                                                                "cl", "sk", "ly", "cc", "to", "no", "fi", "us", "pt", "dk", "ar", "hu", "tk", "gr", "il", "news", "ro", "my", "biz",
                                                                                "ie", "za", "nz", "sg", "ee", "th", "io", "xyz", "pe", "bg", "hk", "lt", "link", "ph", "club", "si", "site",
                                                                                "mobi", "by", "cat", "wiki", "la", "ga", "xxx", "cf", "hr", "ng", "jobs", "online", "kz", "ug", "gq", "ae", "is",
                                                                                "lv", "pro", "fm", "tips", "ms", "sa", "app"));

  public static CharSequence scrub(@NonNull CharSequence in) {

    in = scrubE164(in);
    in = scrubEmail(in);
    in = scrubGroupsV1(in);
    in = scrubGroupsV2(in);
    in = scrubUuids(in);
    in = scrubDomains(in);
    in = scrubIpv4(in);

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

  private static CharSequence scrubGroupsV1(@NonNull CharSequence in) {
    return scrub(in,
                 GROUP_ID_V1_PATTERN,
                 (matcher, output) -> output.append(matcher.group(1))
                                            .append(GROUP_ID_V1_CENSOR)
                                            .append(matcher.group(3)));
  }

  private static CharSequence scrubGroupsV2(@NonNull CharSequence in) {
    return scrub(in,
                 GROUP_ID_V2_PATTERN,
                 (matcher, output) -> output.append(matcher.group(1))
                                            .append(GROUP_ID_V2_CENSOR)
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

  private static CharSequence scrubDomains(@NonNull CharSequence in) {
    return scrub(in,
                 DOMAIN_PATTERN,
                 (matcher, output) -> {
                   String match = matcher.group(0);
                   if (matcher.groupCount() == 2 &&
                       TOP_100_TLDS.contains(matcher.group(2).toLowerCase(Locale.US)) &&
                       !match.endsWith("whispersystems.org") &&
                       !match.endsWith("signal.org")) {
                     output.append(DOMAIN_CENSOR)
                           .append(matcher.group(2));
                   } else {
                     output.append(match);
                   }
                 });
  }

  private static CharSequence scrubIpv4(@NonNull CharSequence in) {
    return scrub(in,
                 IPV4_PATTERN,
                 (matcher, output) -> output.append(IPV4_CENSOR));
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
