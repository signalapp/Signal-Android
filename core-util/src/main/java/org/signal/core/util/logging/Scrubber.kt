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
package org.signal.core.util.logging

import java.util.regex.Matcher
import java.util.regex.Pattern

/** Given a [Matcher], update the [StringBuilder] with the scrubbed output you want for a given match. */
private typealias MatchProcessor = (Matcher, StringBuilder) -> Unit

/**
 * Scrub data for possibly sensitive information.
 */
object Scrubber {
  /**
   * The middle group will be censored.
   * Supposedly, the shortest international phone numbers in use contain seven digits.
   * Handles URL encoded +, %2B
   */
  private val E164_PATTERN = Pattern.compile("(\\+|%2B)(\\d{5,13})(\\d{2})")
  private const val E164_CENSOR = "*************"

  /** The second group will be censored.*/
  private val CRUDE_EMAIL_PATTERN = Pattern.compile("\\b([^\\s/])([^\\s/]*@[^\\s]+)")
  private const val EMAIL_CENSOR = "...@..."

  /** The middle group will be censored. */
  private val GROUP_ID_V1_PATTERN = Pattern.compile("(__)(textsecure_group__![^\\s]+)([^\\s]{2})")
  private const val GROUP_ID_V1_CENSOR = "...group..."

  /** The middle group will be censored. */
  private val GROUP_ID_V2_PATTERN = Pattern.compile("(__)(signal_group__v2__![^\\s]+)([^\\s]{2})")
  private const val GROUP_ID_V2_CENSOR = "...group_v2..."

  /** The middle group will be censored. */
  private val UUID_PATTERN = Pattern.compile("(JOB::)?([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{9})([0-9a-f]{3})", Pattern.CASE_INSENSITIVE)
  private const val UUID_CENSOR = "********-****-****-****-*********"

  /**
   * The entire string is censored. Note: left as concatenated strings because kotlin string literals leave trailing newlines, and removing them breaks
   * syntax highlighting.
   */
  private val IPV4_PATTERN = Pattern.compile(
    "\\b" +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
      "\\b"
  )
  private const val IPV4_CENSOR = "...ipv4..."

  /** The entire string is censored. */
  private val IPV6_PATTERN = Pattern.compile("([0-9a-fA-F]{0,4}:){3,7}([0-9a-fA-F]){0,4}")
  private const val IPV6_CENSOR = "...ipv6..."

  /** The domain name except for TLD will be censored. */
  private val DOMAIN_PATTERN = Pattern.compile("([a-z0-9]+\\.)+([a-z0-9\\-]*[a-z\\-][a-z0-9\\-]*)", Pattern.CASE_INSENSITIVE)
  private const val DOMAIN_CENSOR = "***."
  private val TOP_100_TLDS: Set<String> = setOf(
    "com", "net", "org", "jp", "de", "uk", "fr", "br", "it", "ru", "es", "me", "gov", "pl", "ca", "au", "cn", "co", "in",
    "nl", "edu", "info", "eu", "ch", "id", "at", "kr", "cz", "mx", "be", "tv", "se", "tr", "tw", "al", "ua", "ir", "vn",
    "cl", "sk", "ly", "cc", "to", "no", "fi", "us", "pt", "dk", "ar", "hu", "tk", "gr", "il", "news", "ro", "my", "biz",
    "ie", "za", "nz", "sg", "ee", "th", "io", "xyz", "pe", "bg", "hk", "lt", "link", "ph", "club", "si", "site",
    "mobi", "by", "cat", "wiki", "la", "ga", "xxx", "cf", "hr", "ng", "jobs", "online", "kz", "ug", "gq", "ae", "is",
    "lv", "pro", "fm", "tips", "ms", "sa", "app"
  )

  /** Base16 Call Link Key Pattern */
  private val CALL_LINK_PATTERN = Pattern.compile("([bBcCdDfFgGhHkKmMnNpPqQrRsStTxXzZ]{4})(-[bBcCdDfFgGhHkKmMnNpPqQrRsStTxXzZ]{4}){7}")
  private const val CALL_LINK_CENSOR_SUFFIX = "-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX"

  @JvmStatic
  fun scrub(input: CharSequence): CharSequence {
    return input
      .scrubE164()
      .scrubEmail()
      .scrubGroupsV1()
      .scrubGroupsV2()
      .scrubUuids()
      .scrubDomains()
      .scrubIpv4()
      .scrubIpv6()
      .scrubCallLinkKeys()
  }

  private fun CharSequence.scrubE164(): CharSequence {
    return scrub(this, E164_PATTERN) { matcher, output ->
      output
        .append(matcher.group(1))
        .append(E164_CENSOR, 0, matcher.group(2)!!.length)
        .append(matcher.group(3))
    }
  }

  private fun CharSequence.scrubEmail(): CharSequence {
    return scrub(this, CRUDE_EMAIL_PATTERN) { matcher, output ->
      output
        .append(matcher.group(1))
        .append(EMAIL_CENSOR)
    }
  }

  private fun CharSequence.scrubGroupsV1(): CharSequence {
    return scrub(this, GROUP_ID_V1_PATTERN) { matcher, output ->
      output
        .append(matcher.group(1))
        .append(GROUP_ID_V1_CENSOR)
        .append(matcher.group(3))
    }
  }

  private fun CharSequence.scrubGroupsV2(): CharSequence {
    return scrub(this, GROUP_ID_V2_PATTERN) { matcher, output ->
      output
        .append(matcher.group(1))
        .append(GROUP_ID_V2_CENSOR)
        .append(matcher.group(3))
    }
  }

  private fun CharSequence.scrubUuids(): CharSequence {
    return scrub(this, UUID_PATTERN) { matcher, output ->
      if (matcher.group(1) != null && matcher.group(1)!!.isNotEmpty()) {
        output
          .append(matcher.group(1))
          .append(matcher.group(2))
          .append(matcher.group(3))
      } else {
        output
          .append(UUID_CENSOR)
          .append(matcher.group(3))
      }
    }
  }

  private fun CharSequence.scrubDomains(): CharSequence {
    return scrub(this, DOMAIN_PATTERN) { matcher, output ->
      val match: String = matcher.group(0)!!
      if (matcher.groupCount() == 2 && TOP_100_TLDS.contains(matcher.group(2)!!.lowercase()) && !match.endsWith("signal.org")) {
        output
          .append(DOMAIN_CENSOR)
          .append(matcher.group(2))
      } else {
        output.append(match)
      }
    }
  }

  private fun CharSequence.scrubIpv4(): CharSequence {
    return scrub(this, IPV4_PATTERN) { _, output -> output.append(IPV4_CENSOR) }
  }

  private fun CharSequence.scrubIpv6(): CharSequence {
    return scrub(this, IPV6_PATTERN) { _, output -> output.append(IPV6_CENSOR) }
  }

  private fun CharSequence.scrubCallLinkKeys(): CharSequence {
    return scrub(this, CALL_LINK_PATTERN) { matcher, output ->
      val match = matcher.group(1)
      output
        .append(match)
        .append(CALL_LINK_CENSOR_SUFFIX)
    }
  }

  private fun scrub(input: CharSequence, pattern: Pattern, processMatch: MatchProcessor): CharSequence {
    val output = StringBuilder(input.length)
    val matcher: Matcher = pattern.matcher(input)
    var lastEndingPos = 0

    while (matcher.find()) {
      output.append(input, lastEndingPos, matcher.start())
      processMatch(matcher, output)
      lastEndingPos = matcher.end()
    }

    return if (lastEndingPos == 0) {
      // there were no matches, save copying all the data
      input
    } else {
      output.append(input, lastEndingPos, input.length)
      output
    }
  }
}
